/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.config;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import ortus.boxlang.modules.orm.hibernate.facade.BoxEntityFacade;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Array;

/**
 * Boots a full ORM application against embedded Derby and proves single-table INHERITANCE with a discriminator works
 * end-to-end through the real BIFs.
 * <p>
 * A root entity ({@code Animal}) has two subclasses ({@code Dog}, {@code Cat}) stored in the same table, distinguished
 * by a discriminator column. Each subclass facade extends its root facade. The test saves an instance of each subclass,
 * then a polymorphic {@code ormExecuteQuery("FROM Animal")} returns BOTH, and each loaded entity is the correct BoxLang
 * subclass (asserted via its BoxLang class name and a subclass-only accessor), never a facade. It also loads each
 * subclass directly by primary key.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FacadeInheritanceBootTest {

	private static BoxRuntime	instance;

	private RequestBoxContext	context;
	private IScope				variables;

	@BeforeAll
	public void setUp() {
		instance = BoxRuntime.getInstance( false );

		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Paths.get( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/facadeInheritanceApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMFacadeInheritanceTest" ) );
	}

	@DisplayName( "It polymorphically queries an inheritance hierarchy in facade mode, hydrating the correct BoxLang subclasses" )
	@Test
	public void testPolymorphicInheritance() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				d = entityNew( "Dog", { name : "Rex", breed : "Labrador" } );
				entitySave( d );

				c = entityNew( "Cat", { name : "Whiskers", livesLeft : 9 } );
				entitySave( c );

				dogId = d.getId();
				catId = c.getId();
			}

			// Force a fresh load from the database so rows are hydrated (polymorphically) by Hibernate.
			ormFlush();
			ormClearSession();

			// --- polymorphic query on the root: returns both subclasses, ordered by name (Rex < Whiskers) ---
			all        = ormExecuteQuery( "FROM Animal ORDER BY name" );
			animalCount= all.len();

			firstName  = all[ 1 ].getName();       // Rex
			firstBreed = all[ 1 ].getBreed();      // Dog-only accessor
			firstClass = getMetadata( all[ 1 ] ).name;

			secondName = all[ 2 ].getName();       // Whiskers
			secondLives= all[ 2 ].getLivesLeft();  // Cat-only accessor
			secondClass= getMetadata( all[ 2 ] ).name;

			// --- direct load by primary key of each subclass ---
			loadedDog      = entityLoadByPK( "Dog", dogId );
			loadedDogBreed = loadedDog.getBreed();
			loadedCat      = entityLoadByPK( "Cat", catId );
			loadedCatLives = loadedCat.getLivesLeft();
		""", context );
		// @formatter:on

		// The polymorphic query surfaced both rows.
		assertThat( variables.get( Key.of( "animalCount" ) ) ).isEqualTo( 2 );

		Array	all		= ( Array ) variables.get( Key.of( "all" ) );
		Object	first	= all.get( 0 );
		Object	second	= all.get( 1 );

		// Each is a real BoxLang instance, never a facade.
		assertThat( first ).isInstanceOf( IClassRunnable.class );
		assertThat( first ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( second ).isInstanceOf( IClassRunnable.class );
		assertThat( second ).isNotInstanceOf( BoxEntityFacade.class );

		// The first row hydrated to the Dog BoxLang subclass (its Dog-only accessor and class name), the second to Cat.
		assertThat( variables.get( Key.of( "firstName" ) ) ).isEqualTo( "Rex" );
		assertThat( variables.get( Key.of( "firstBreed" ) ) ).isEqualTo( "Labrador" );
		assertThat( ( ( String ) variables.get( Key.of( "firstClass" ) ) ).toLowerCase() ).endsWith( "dog" );

		assertThat( variables.get( Key.of( "secondName" ) ) ).isEqualTo( "Whiskers" );
		assertThat( variables.get( Key.of( "secondLives" ) ) ).isEqualTo( 9 );
		assertThat( ( ( String ) variables.get( Key.of( "secondClass" ) ) ).toLowerCase() ).endsWith( "cat" );

		// Direct primary-key loads return the correct subclass instances too.
		assertThat( variables.get( Key.of( "loadedDogBreed" ) ) ).isEqualTo( "Labrador" );
		assertThat( variables.get( Key.of( "loadedCatLives" ) ) ).isEqualTo( 9 );
	}

	@DisplayName( "It round-trips a joined-subclass hierarchy in facade mode, hydrating the correct BoxLang subclass" )
	@Test
	public void testJoinedSubclass() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				sq = entityNew( "Square", { color : "red", side : 5 } );
				entitySave( sq );
				squareId = sq.getId();
			}

			ormFlush();
			ormClearSession();

			// Polymorphic query on the joined root returns the subclass row.
			shapes        = ormExecuteQuery( "FROM Shape" );
			shapeCount    = shapes.len();

			// Direct load by primary key: joins the two tables and hydrates a Square carrying both inherited and local state.
			loadedSquare  = entityLoadByPK( "Square", squareId );
			loadedColor   = loadedSquare.getColor();   // inherited from Shape (root table)
			loadedSide    = loadedSquare.getSide();    // local to Square (join table)
			loadedClass   = getMetadata( loadedSquare ).name;
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "shapeCount" ) ) ).isEqualTo( 1 );

		Object	shapes	= variables.get( Key.of( "shapes" ) );
		Object	first	= ( ( Array ) shapes ).get( 0 );
		assertThat( first ).isInstanceOf( IClassRunnable.class );
		assertThat( first ).isNotInstanceOf( BoxEntityFacade.class );

		Object loadedSquare = variables.get( Key.of( "loadedSquare" ) );
		assertThat( loadedSquare ).isInstanceOf( IClassRunnable.class );
		assertThat( loadedSquare ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( variables.get( Key.of( "loadedColor" ) ) ).isEqualTo( "red" );
		assertThat( variables.get( Key.of( "loadedSide" ) ) ).isEqualTo( 5 );
		assertThat( ( ( String ) variables.get( Key.of( "loadedClass" ) ) ).toLowerCase() ).endsWith( "square" );
	}

	@DisplayName( "An INHERITED to-many can be structurally modified while iterating its getter" )
	@Test
	public void testInheritedToManyRemoveDuringIteration() {
		// @formatter:off
		instance.executeSource( """
			// Cat inherits the toys one-to-many declared on the Animal root. Persist a cat with toys, then reload it so its
			// getToys() returns the MANAGED FacadeCollectionView - the case the snapshot getX() override protects. Then run
			// the remove-during-iteration pattern on the INHERITED getter; it only stays safe if the snapshot getter is
			// installed on the subclass instance too.
			transaction {
				cat = entityNew( "Cat", { name : "Felix", livesLeft : 9 } );
				[ "ball", "mouse", "string" ].each( ( lbl ) => {
					toy = entityNew( "Toy", { label : lbl } );
					toy.setAnimal( cat );
					cat.addToy( toy );
				} );
				entitySave( cat );
			}
			ormFlush();
			ormClearSession();

			reloaded    = entityLoad( "Cat", { name : "Felix" }, true );
			beforeCount = reloaded.getToys().len();
			reloaded.getToys().each( ( toy ) => reloaded.removeToy( toy ) );
			afterCount  = reloaded.getToys().len();

			// Clean up so this shared PER_CLASS Derby app's fac_animals table is not polluted for the other tests.
			transaction {
				entityDelete( reloaded );
			}
			ormFlush();
		""", context );
		// @formatter:on

		// Without the inherited snapshot getter, index-based iteration over the live managed view drops elements / errors and
		// afterCount != 0.
		assertThat( variables.getAsInteger( Key.of( "beforeCount" ) ) ).isEqualTo( 3 );
		assertThat( variables.getAsInteger( Key.of( "afterCount" ) ) ).isEqualTo( 0 );
	}
}
