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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Boots a full ORM application/SessionFactory against a real, file-based SQLite datasource.
 * <p>
 * This is a standalone boot test (does not extend {@code tools.BaseORMTest}) because it uses its own,
 * isolated BoxLang application (`src/test/resources/sqliteApp`) with a SQLite datasource, rather than
 * the shared MySQL application every other ORM test boots against.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class SQLiteDialectBootTest {

	private static BoxRuntime	instance;
	private static Path			dbFilePath;
	private static final Key	result	= Key.of( "result" );

	private RequestBoxContext	context;
	private IScope				variables;

	@BeforeAll
	public void setUp() throws IOException {
		dbFilePath = Paths.get( "build/test-sqlite/orm-sqlite-boot-test.db" ).toAbsolutePath();
		Files.createDirectories( dbFilePath.getParent() );
		Files.deleteIfExists( dbFilePath );
		System.setProperty( "SQLITE_DB_PATH", dbFilePath.toString() );

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/sqliteApp/index.bxs" ).toAbsolutePath().toUri() );
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
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMSQLiteTest" ) );
	}

	@DisplayName( "It can boot an ORM SessionFactory against a SQLite datasource, and save/query an entity" )
	@Test
	public void testSQLiteORMBoot() {
		// @formatter:off
		instance.executeSource( """
			sessionFactory = ormGetSessionFactory();

			transaction {
				entitySave( entityNew( "Product", { name : "Widget" } ) );
			}

			tableCheck = queryExecute( "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'products'" );
			result     = queryExecute( "SELECT * FROM products WHERE name = 'Widget'" );
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "sessionFactory" ) ) ).isNotNull();
		assertThat( variables.getAsQuery( Key.of( "tableCheck" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Widget" );
	}
}
