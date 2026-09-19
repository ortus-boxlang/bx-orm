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
 * Broad, facade-mode (default) coverage of documented Hibernate mapping features that previously had no end-to-end test,
 * booted against embedded Derby. Each test round-trips a documented feature through the real ORM BIFs to prove the
 * facade / modern {@code mapping.xml} path still supports what the docs promise.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class HibernateFeatureCoverageBootTest {

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/featureApp/index.bxs" ).toAbsolutePath().toUri() );
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
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMFeatureCoverageTest" ) );
	}

	@DisplayName( "It supports an optimistic-lock <version> column and a computed formula property" )
	@Test
	public void testVersionAndFormula() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				p = entityNew( "Product", { name : "Widget", price : 100 } );
				entitySave( p );
				id = p.getId();
			}
			ormFlush();
			ormClearSession();

			loaded        = entityLoadByPK( "Product", id );
			initialRev    = loaded.getRevision();
			taxed         = loaded.getPriceWithTax();

			transaction {
				loaded.setName( "Widget v2" );
				entitySave( loaded );
			}
			ormFlush();
			ormClearSession();

			reloaded      = entityLoadByPK( "Product", id );
			updatedRev    = reloaded.getRevision();
			finalName     = reloaded.getName();
		""", context );
		// @formatter:on

		// Version column is populated on insert and increments on update.
		assertThat( variables.get( Key.of( "initialRev" ) ) ).isNotNull();
		int	initialRev	= ( ( Number ) variables.get( Key.of( "initialRev" ) ) ).intValue();
		int	updatedRev	= ( ( Number ) variables.get( Key.of( "updatedRev" ) ) ).intValue();
		assertThat( updatedRev ).isGreaterThan( initialRev );
		assertThat( variables.get( Key.of( "finalName" ) ) ).isEqualTo( "Widget v2" );
		// Formula: price + price/10 = 110.
		assertThat( ( ( Number ) variables.get( Key.of( "taxed" ) ) ).intValue() ).isEqualTo( 110 );
	}

	@DisplayName( "It round-trips a many-to-many association through a link table" )
	@Test
	public void testManyToMany() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				s = entityNew( "Student", { name : "Ada" } );
				entitySave( s );
				c1 = entityNew( "Course", { title : "Math" } );
				c2 = entityNew( "Course", { title : "Physics" } );
				entitySave( c1 );
				entitySave( c2 );
				s.addCourse( c1 );
				s.addCourse( c2 );
				entitySave( s );
				sid = s.getId();
			}
			ormFlush();
			ormClearSession();

			loaded      = entityLoadByPK( "Student", sid );
			courseCount = loaded.getCourses().len();
			hasMath     = loaded.hasCourse();

			transaction {
				firstCourse = loaded.getCourses()[ 1 ];
				loaded.removeCourse( firstCourse );
				entitySave( loaded );
			}
			ormFlush();
			ormClearSession();

			reloaded      = entityLoadByPK( "Student", sid );
			afterRemove   = reloaded.getCourses().len();
		""", context );
		// @formatter:on

		assertThat( ( ( Number ) variables.get( Key.of( "courseCount" ) ) ).intValue() ).isEqualTo( 2 );
		assertThat( variables.getAsBoolean( Key.of( "hasMath" ) ) ).isTrue();
		assertThat( ( ( Number ) variables.get( Key.of( "afterRemove" ) ) ).intValue() ).isEqualTo( 1 );
	}

	@DisplayName( "It supports the identity id generator with dynamic insert/update" )
	@Test
	public void testIdentityGenerator() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				c = entityNew( "Counter", { label : "hits", tally : 5 } );
				entitySave( c );
			}
			ormFlush();
			ormClearSession();

			row       = ormExecuteQuery( "FROM Counter WHERE label = :l", { l : "hits" }, true );
			genId     = row.getId();
			tally     = row.getTally();
		""", context );
		// @formatter:on

		// identity generator assigned a non-null, DB-generated integer key.
		assertThat( variables.get( Key.of( "genId" ) ) ).isNotNull();
		assertThat( ( ( Number ) variables.get( Key.of( "genId" ) ) ).intValue() ).isGreaterThan( 0 );
		assertThat( ( ( Number ) variables.get( Key.of( "tally" ) ) ).intValue() ).isEqualTo( 5 );
	}

	@DisplayName( "It round-trips a shared-primary-key one-to-one (foreign generator + constrained)" )
	@Test
	public void testOneToOne() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				person = entityNew( "Person", { fullName : "Grace Hopper" } );
				passport = entityNew( "Passport", { number : "P-12345" } );
				passport.setOwner( person );
				person.setPassport( passport );
				entitySave( person );
				pid = person.getId();
			}
			ormFlush();
			ormClearSession();

			loaded         = entityLoadByPK( "Person", pid );
			hasPassport    = !isNull( loaded.getPassport() );
			passportNumber = loaded.getPassport().getNumber();
			// Shared PK: the passport's id equals the person's id.
			sharedId       = loaded.getPassport().getId();
		""", context );
		// @formatter:on

		assertThat( variables.getAsBoolean( Key.of( "hasPassport" ) ) ).isTrue();
		assertThat( variables.get( Key.of( "passportNumber" ) ) ).isEqualTo( "P-12345" );
		assertThat( variables.get( Key.of( "sharedId" ) ) ).isEqualTo( variables.get( Key.of( "pid" ) ) );
	}
}
