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
package tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

public class BaseORMTest {

	public static BoxRuntime		instance;
	public static ModuleRecord		moduleRecord;
	public static Key				moduleName				= new Key( "orm" );
	public static Key				derbyModule				= new Key( "bx-derby" );
	public static String			moduleDependenciesPath	= Paths.get( "./src/test/resources/modules" ).toAbsolutePath().toString();
	public static Key				result					= Key.of( "result" );
	public static RequestBoxContext	context;
	public IScope					variables;

	@BeforeAll
	public static void setUp() {
		instance	= BoxRuntime.getInstance( true );

		// Load the module
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		loadModule( context );
		context.loadApplicationDescriptor( Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
	}

	@AfterAll
	public static void teardown() throws SQLException {
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMTest" ) );
	}

	@BeforeEach
	public void setupEach() {
		variables = context.getScopeNearby( VariablesScope.name );
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		JDBCTestUtils.resetTables( ( ( IJDBCCapableContext ) context ).getConnectionManager().getDefaultDatasourceOrThrow(), context );
	}

	protected static void loadModule( IBoxContext context ) {
		// Is Derby module loaded?
		if ( !instance.getModuleService().hasModule( derbyModule ) ) {
			System.out.println( "Loading Derby module..." );
			ModuleRecord derbyRecord = new ModuleRecord( moduleDependenciesPath + "/bx-derby" );
			instance.getModuleService().getRegistry().put( derbyModule, derbyRecord );
			derbyRecord
			    .loadDescriptor( context )
			    .register( context )
			    .activate( context );
		} else {
			System.out.println( "Derby module already loaded, skipping..." );
		}

		// Is ORM module loaded?
		if ( !instance.getModuleService().hasModule( moduleName ) ) {
			System.out.println( "Loading ORM module..." );
			String physicalPath = Paths.get( "./build/module" ).toAbsolutePath().toString();
			moduleRecord = new ModuleRecord( physicalPath );

			instance.getModuleService().getRegistry().put( moduleName, moduleRecord );

			moduleRecord
			    .loadDescriptor( context )
			    .register( context )
			    .activate( context );
		} else {
			System.out.println( "ORM module already loaded, skipping..." );
		}
	}
}
