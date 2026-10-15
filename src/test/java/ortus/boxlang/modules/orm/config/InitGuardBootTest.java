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
 * The ORM boots without creating any entity, so an entity whose {@code init()} needs the application (like a cborm
 * {@code ActiveEntity} reading {@code application.wirebox} before ColdBox has loaded) does not break ORM startup.
 * <p>
 * Every entity in {@code initGuardApp} throws from {@code init()} until the test sets {@code application.ready}. Hibernate
 * creates a throwaway instance of an entity whose id or version has no declared unsaved value, so the app covers each
 * case bx-orm must declare: an assigned id, a uuid id with {@code default=""}, a version, a joined subclass, and a
 * composite id. Booting the app at all proves no entity was created; the round trips then prove
 * new and existing entities are still told apart correctly (insert first, update after).
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class InitGuardBootTest {

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

		// Starting the application boots the ORM; it fails here if Hibernate creates any entity.
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/initGuardApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );

		// From here on the application is "ready", so entities can be created.
		instance.executeSource( "application.ready = true;", context );
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMInitGuardTest" ) );
	}

	@DisplayName( "It boots the ORM without creating any entity" )
	@Test
	public void testBootCreatesNoEntity() {
		instance.executeSource( "factory = ormGetSessionFactory();", context );
		assertThat( variables.get( Key.of( "factory" ) ) ).isNotNull();
	}

	@DisplayName( "It inserts and then updates an entity with an assigned id" )
	@Test
	public void testAssignedId() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "AssignedSetting", { name : "site.title", value : "First" } ) );
			}
			ormClearSession();
			transaction {
				setting = entityLoadByPK( "AssignedSetting", "site.title" );
				setting.setValue( "Second" );
				entitySave( setting );
			}
			ormClearSession();
			value = entityLoadByPK( "AssignedSetting", "site.title" ).getValue();
			total = ormExecuteQuery( "select count(*) from AssignedSetting", true );
		""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "value" ) ) ).isEqualTo( "Second" );
		assertThat( ( ( Number ) variables.get( Key.of( "total" ) ) ).intValue() ).isEqualTo( 1 );
	}

	@DisplayName( "It inserts and then updates an entity whose uuid id defaults to an empty string" )
	@Test
	public void testIdWithDefault() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				thing = entityNew( "DefaultIdThing", { label : "First" } );
				entitySave( thing );
			}
			newId = thing.getId();
			ormClearSession();
			transaction {
				thing = entityLoadByPK( "DefaultIdThing", newId );
				thing.setLabel( "Second" );
				entitySave( thing );
			}
			ormClearSession();
			label = entityLoadByPK( "DefaultIdThing", newId ).getLabel();
			total = ormExecuteQuery( "select count(*) from DefaultIdThing", true );
		""", context );
		// @formatter:on
		assertThat( ( String ) variables.get( Key.of( "newId" ) ) ).isNotEmpty();
		assertThat( variables.get( Key.of( "label" ) ) ).isEqualTo( "Second" );
		assertThat( ( ( Number ) variables.get( Key.of( "total" ) ) ).intValue() ).isEqualTo( 1 );
	}

	@DisplayName( "It inserts and then updates a versioned entity, bumping the version" )
	@Test
	public void testVersion() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				thing = entityNew( "VersionedThing", { label : "First" } );
				entitySave( thing );
			}
			newId = thing.getId();
			ormClearSession();
			transaction {
				thing = entityLoadByPK( "VersionedThing", newId );
				firstRevision = thing.getRevision();
				thing.setLabel( "Second" );
				entitySave( thing );
			}
			ormClearSession();
			thing        = entityLoadByPK( "VersionedThing", newId );
			label        = thing.getLabel();
			lastRevision = thing.getRevision();
			total        = ormExecuteQuery( "select count(*) from VersionedThing", true );
		""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "label" ) ) ).isEqualTo( "Second" );
		assertThat( ( ( Number ) variables.get( Key.of( "total" ) ) ).intValue() ).isEqualTo( 1 );
		assertThat( ( ( Number ) variables.get( Key.of( "lastRevision" ) ) ).intValue() )
		    .isGreaterThan( ( ( Number ) variables.get( Key.of( "firstRevision" ) ) ).intValue() );
	}

	@DisplayName( "It inserts and then updates a joined subclass" )
	@Test
	public void testSubclass() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				invoice = entityNew( "Invoice", { title : "INV-1", total : 10 } );
				entitySave( invoice );
			}
			newId = invoice.getId();
			ormClearSession();
			transaction {
				invoice = entityLoadByPK( "Invoice", newId );
				invoice.setTotal( 20 );
				entitySave( invoice );
			}
			ormClearSession();
			total     = entityLoadByPK( "Invoice", newId ).getTotal();
			asBase    = entityLoadByPK( "BaseDocument", newId ).getTitle();
			documents = ormExecuteQuery( "select count(*) from BaseDocument", true );
		""", context );
		// @formatter:on
		assertThat( ( ( Number ) variables.get( Key.of( "total" ) ) ).intValue() ).isEqualTo( 20 );
		assertThat( variables.get( Key.of( "asBase" ) ) ).isEqualTo( "INV-1" );
		assertThat( ( ( Number ) variables.get( Key.of( "documents" ) ) ).intValue() ).isEqualTo( 1 );
	}

	@DisplayName( "It inserts and then updates an entity with a composite id" )
	@Test
	public void testCompositeId() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "GuardedBooking", { flightID : 7, seatID : "1A", passenger : "Ada" } ) );
			}
			ormClearSession();
			transaction {
				booking = entityLoadByPK( "GuardedBooking", { flightID : 7, seatID : "1A" } );
				booking.setPassenger( "Grace" );
				entitySave( booking );
			}
			ormClearSession();
			passenger = entityLoadByPK( "GuardedBooking", { flightID : 7, seatID : "1A" } ).getPassenger();
			total     = ormExecuteQuery( "select count(*) from GuardedBooking", true );
		""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "passenger" ) ) ).isEqualTo( "Grace" );
		assertThat( ( ( Number ) variables.get( Key.of( "total" ) ) ).intValue() ).isEqualTo( 1 );
	}
}
