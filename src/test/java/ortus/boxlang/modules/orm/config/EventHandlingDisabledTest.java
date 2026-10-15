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
 * Boots an app with {@code eventHandling=false} against embedded Derby and proves no ORM event runs, neither the entity's
 * own event methods nor the configured global {@code eventHandler}.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class EventHandlingDisabledTest {

	/** The BoxLang runtime. */
	private static BoxRuntime	instance;

	/** The request context the app runs in. */
	private RequestBoxContext	context;

	/** The request's variables scope. */
	private IScope				variables;

	/**
	 * Load the ORM module if needed and start the app.
	 */
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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/noEventsApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	/**
	 * End the request and shut the app down.
	 */
	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMNoEventsTest" ) );
	}

	/**
	 * Saving an entity runs neither its preInsert method nor the global handler's.
	 */
	@DisplayName( "eventHandling=false disables entity and global events, including postNew" )
	@Test
	public void testEventsDisabled() {
		// @formatter:off
		instance.executeSource( """
			note = entityNew( "Note", { title : "original" } );
			afterNew = note.getTitle();
			transaction {
				entitySave( note );
			}
			ormClearSession();
			title       = entityLoadByPK( "Note", note.getId() ).getTitle();
			globalCalls = application.globalPreInsertCalls ?: 0;
		""", context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "afterNew" ) ) ).isEqualTo( "original" );
		assertThat( variables.getAsString( Key.of( "title" ) ) ).isEqualTo( "original" );
		assertThat( variables.get( Key.of( "globalCalls" ) ) ).isEqualTo( 0 );
	}
}
