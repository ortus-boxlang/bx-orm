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
 * Boots a full ORM application/SessionFactory against an embedded, in-memory Apache Derby datasource with the
 * {@code entityFacades} flag ON, proving the POJO-facade representation works end-to-end through bx-orm's real BIFs.
 * <p>
 * The entity uses a {@code uuid}-generated string id, which is impossible for class-less dynamic (MAP) entities: this
 * test round-trips {@code entityNew}/{@code entitySave}/{@code entityLoadByPK}/{@code ormExecuteQuery} and asserts a
 * non-empty uuid was generated onto the entity. Standalone (does not extend {@code tools.BaseORMTest}) so it runs
 * everywhere against embedded Derby with no database server.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FacadeRepresentationBootTest {

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/facadeApp/index.bxs" ).toAbsolutePath().toUri() );
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
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMFacadeTest" ) );
	}

	@DisplayName( "It boots a facade-mode ORM SessionFactory and round-trips a uuid-keyed entity through the real BIFs" )
	@Test
	public void testFacadeUuidRoundTrip() {
		// @formatter:off
		instance.executeSource( """
			sessionFactory = ormGetSessionFactory();

			thing = entityNew( "Thing", { name : "Widget" } );
			transaction {
				entitySave( thing );
			}

			// The uuid generator wrote a real id back onto the BoxLang instance through its facade.
			generatedId = thing.getId();

			// Read back through the real bx-orm stack.
			loaded    = entityLoadByPK( "Thing", generatedId );
			loadedName = loaded.getName();
			hqlResult = ormExecuteQuery( "FROM Thing WHERE name = :n", { n : "Widget" } );
			result    = queryExecute( "SELECT * FROM things WHERE name = 'Widget'" );
		""", context );
		// @formatter:on

		// Booted.
		assertThat( variables.get( Key.of( "sessionFactory" ) ) ).isNotNull();

		// A non-empty uuid was generated onto the entity (the whole point of facade mode).
		Object generatedId = variables.get( Key.of( "generatedId" ) );
		assertThat( generatedId ).isInstanceOf( String.class );
		assertThat( ( String ) generatedId ).isNotEmpty();

		// The row persisted with the delegated property value.
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Widget" );

		// entityLoadByPK returned the BoxLang instance (never the facade) and it round-trips.
		assertThat( variables.get( Key.of( "loaded" ) ) ).isNotNull();
		assertThat( variables.get( Key.of( "loadedName" ) ) ).isEqualTo( "Widget" );

		// ormExecuteQuery returned the entity too.
		assertThat( variables.getAsArray( Key.of( "hqlResult" ) ).size() ).isEqualTo( 1 );
	}
}
