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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

@TestInstance( TestInstance.Lifecycle.PER_CLASS )
@Execution( ExecutionMode.SAME_THREAD )
public abstract class BaseORMTest {

	public static BoxRuntime	instance;
	public static ModuleRecord	moduleRecord;
	public static Key			derbyModule				= new Key( "bx-derby" );
	public static String		moduleDependenciesPath	= Paths.get( "./src/test/resources/modules" ).toAbsolutePath().toString();
	public static Key			result					= Key.of( "result" );
	public RequestBoxContext	context;
	public IScope				variables;
	public Boolean				seededDB				= false;

	@BeforeAll
	public void setUp() {
		instance = BoxRuntime.getInstance( true );
		// Load the module
		loadModule( instance.getRuntimeContext() );
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
		JDBCTestUtils.resetTables( ( ( IJDBCCapableContext ) context ).getConnectionManager().getDefaultDatasourceOrThrow(), context );
		JDBCTestUtils.resetAlternateTables( ( ( IJDBCCapableContext ) context ).getConnectionManager().getDatasourceOrThrow( Key.of( "dsn2" ) ),
		    context );
	}

	@AfterAll
	public void teardown() {
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMTest" ) );
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	@AfterEach
	public void teardownEach() {
		variables.clear();
		context.getApplicationListener().onRequestEnd( context, null );
	}

	protected static void loadModule( IBoxContext context ) {
		// Is Derby module loaded?
		if ( !instance.getModuleService().hasModule( derbyModule ) ) {
			getLogger().debug( "Loading Derby module..." );
			String derbyPath = moduleDependenciesPath + "/bx-derby";

			if ( !Files.exists( Paths.get( derbyPath ) ) ) {
				getLogger().debug( "Derby module not found at " + derbyPath );
				getLogger().debug( "Please run 'gradle installModuleDependencies' to install the required modules." );
				System.exit( 1 );
			}

			ModuleRecord derbyRecord = new ModuleRecord( derbyPath );
			instance.getModuleService().getRegistry().put( derbyModule, derbyRecord );
			derbyRecord
			    .loadDescriptor( context )
			    .register( context )
			    .activate( context );
		} else {
			getLogger().debug( "Derby module already loaded, skipping..." );
		}

		// Is ORM module loaded?
		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			getLogger().debug( "Loading ORM module..." );
			String physicalPath = Paths.get( "./build/module" ).toAbsolutePath().toString();
			moduleRecord = new ModuleRecord( physicalPath );

			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, moduleRecord );

			moduleRecord
			    .loadDescriptor( context )
			    .register( context )
			    .activate( context );
		} else {
			getLogger().debug( "ORM module already loaded, skipping..." );
		}
	}

	protected static BoxLangLogger getLogger() {
		return instance.getLoggingService().getLogger( "orm" );
	}
}
