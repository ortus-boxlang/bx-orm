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

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

/**
 * Boots a full ORM application/SessionFactory against an embedded, in-memory Apache Derby datasource using the modern Hibernate 7 {@code mapping.xml}
 * writer (root {@code <entity-mappings>}), enabled via {@code ormSettings.ormXmlMapping = true}.
 * <p>
 * This is the CI counterpart to {@link DerbyDialectBootTest} (which uses the legacy HBM writer). It exercises the modern writer end-to-end across the
 * mapping constructs it supports for dynamic (MAP) entities: {@code increment} id generation, JPA {@code AttributeConverter}s, single-table
 * inheritance
 * with a discriminator and a secondary join table, joined inheritance, {@code many-to-one}/{@code one-to-many} associations, and a composite id.
 * <p>
 * Note: {@code uuid} id generation is intentionally NOT exercised here — see {@code MappingXMLWriter.appendGenerator} — because Hibernate 7.4.8's
 * {@code GeneratorBinder} instantiates {@code @UuidGenerator} via a reflective {@code Member} that is {@code null} for a class-less dynamic entity.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class DerbyOrmXmlMappingBootTest {

	private static BoxRuntime	instance;
	private static final Key	result	= Key.of( "result" );

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/derbyOrmXmlApp/index.bxs" ).toAbsolutePath().toUri() );
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
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMDerbyOrmXmlTest" ) );
	}

	@DisplayName( "It boots an ORM SessionFactory from the modern mapping.xml writer and round-trips a simple entity" )
	@Test
	public void testModernMappingSimpleRoundTrip() {
		// @formatter:off
		instance.executeSource( """
			sessionFactory = ormGetSessionFactory();

			transaction {
				entitySave( entityNew( "Gadget", { name : "Sprocket", price : 9.99, active : true, createdDate : now() } ) );
			}

			result = queryExecute( "SELECT * FROM gadgets WHERE name = 'Sprocket'" );
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "sessionFactory" ) ) ).isNotNull();
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Sprocket" );
	}

	@DisplayName( "It round-trips a single-table inheritance subclass with a secondary join table and a collection" )
	@Test
	public void testModernMappingInheritanceAndAssociation() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "Dog", { name : "Rex", breed : "Lab", goodBoy : true } ) );
				entitySave( entityNew( "Cat", { name : "Tom", livesLeft : 9 } ) );
			}

			// Single-table root `animals` holds the base columns + discriminator for BOTH subclasses; Dog's extra columns live in the `dogs` secondary
			// join table (Cat shares the root table).
			animalRows = queryExecute( "SELECT * FROM animals ORDER BY name" );
			result     = entityLoad( "Dog", { name : "Rex" }, true );
			breed      = result.getBreed();
			// The inverse one-to-many collection maps and loads (empty here) — proving the association wiring.
			toyCount   = arrayLen( result.getToys() );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "animalRows" ) ).size() ).isEqualTo( 2 );
		// Ordered by name: "Rex" (dog) then "Tom" (cat).
		assertThat( variables.getAsQuery( Key.of( "animalRows" ) ).getRowAsStruct( 0 ).get( "animalType" ) ).isEqualTo( "dog" );
		assertThat( variables.getAsQuery( Key.of( "animalRows" ) ).getRowAsStruct( 1 ).get( "animalType" ) ).isEqualTo( "cat" );
		assertThat( variables.get( Key.of( "result" ) ) ).isNotNull();
		// The subclass's secondary-table column round-trips.
		assertThat( variables.get( Key.of( "breed" ) ) ).isEqualTo( "Lab" );
		assertThat( variables.getAsInteger( Key.of( "toyCount" ) ) ).isEqualTo( 0 );
	}

	@DisplayName( "It round-trips a joined-inheritance subclass and a composite-id entity" )
	@Test
	public void testModernMappingJoinedAndCompositeId() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "SavingsAccount", { owner : "Jane", rate : 1.25 } ) );
				entitySave( entityNew( "Enrollment", { studentId : 1, courseId : "CS101", grade : "A" } ) );
			}

			// Joined inheritance: base columns in `accounts`, subclass columns in `savings_accounts` (joined by id).
			savings = queryExecute( "SELECT * FROM savings_accounts" );
			accounts = queryExecute( "SELECT * FROM accounts" );
			// Composite id round-trips through the DB.
			enrollmentRow = queryExecute( "SELECT * FROM enrollments WHERE studentId = 1 AND courseId = 'CS101'" );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "savings" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "accounts" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "enrollmentRow" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "enrollmentRow" ) ).getRowAsStruct( 0 ).get( "grade" ) ).isEqualTo( "A" );
	}
}
