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
package ortus.boxlang.modules.orm.tools;

import java.nio.file.Path;
import java.util.HashMap;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * I am a CLI tool for testing the Hibernate mapping generator.
 * <p>
 * I must be executed with the boxlang.jar in the classpath, as well as the bx-orm jar itself (duh, no duh).
 * <p>
 * You can pass any of the following options:
 * <ul>
 * <li><code>--path MY_PATH</code> - Required. Relative path to the ORM entity files.</li>
 * <li><code>--failFast</code> - Inverse of the legacy CFML configuration `skipCFCWithError`. If `--failFast` is passed, the .xml generation will
 * abort if
 * any entity class files fail to parse.</li>
 * <li><code>--debug</code> - Spit out debug logging. Not necessary for your first run, but this may be helpful in debugging mapping errors.</li>
 * </ul>
 */
public class MappingGenerator {

	public static void main( String[] args ) {
		BoxRuntime		runtime	= BoxRuntime.getInstance();
		BoxLangLogger	logger	= runtime.getLoggingService().getLogger( "orm" );

		try {
			Boolean					debugMode	= false;
			Boolean					failFast	= false;
			Array					entityPaths	= new Array();
			HashMap<String, String>	mappings	= new HashMap<>();

			logger.info( "Parsing arguments" );

			for ( int i = 0; i < args.length; i++ ) {
				if ( args[ i ].equalsIgnoreCase( "--path" ) ) {
					if ( i + 1 >= args.length || args[ i + 1 ].startsWith( "--" ) ) {
						throw new BoxRuntimeException( "--path requires a path" );
					}
					entityPaths.add( args[ i + 1 ] );
				}
				if ( args[ i ].equalsIgnoreCase( "--mapping" ) ) {
					if ( i + 1 >= args.length || args[ i + 1 ].startsWith( "--" ) ) {
						throw new BoxRuntimeException( "--mapping requires a name:path value" );
					}
					String[] mapping = args[ i + 1 ].split( ":" );
					if ( mapping.length != 2 || mapping[ 0 ].isEmpty() || mapping[ 1 ].isEmpty() ) {
						throw new BoxRuntimeException( "--mapping requires a name:path value" );
					}
					mappings.put( mapping[ 0 ], mapping[ 1 ] );
				}
				if ( args[ i ].equalsIgnoreCase( "--failFast" ) ) {
					failFast = true;
				}
				if ( args[ i ].equalsIgnoreCase( "--debug" ) ) {
					debugMode = true;
				}
			}
			// Note that none of our logging will print out until we set the debug mode.
			// if ( debugMode ) {
			// LoggingConfigurator.reconfigureDebugMode( debugMode );
			// }

			if ( mappings.size() > 0 ) {
				for ( String key : mappings.keySet() ) {
					logger.info( "Mapping: " + key + " -> " + mappings.get( key ) );
					runtime.getConfiguration().registerMapping( key, Path.of( mappings.get( key ) ).toAbsolutePath().toString() );
				}
			}
			ORMConfig											ormConfig	= new ORMConfig( Struct.of(
			    // required that the user pass the correct path to their entity locations.
			    ORMKeys.entityPaths, entityPaths,
			    // Don't "fail fast" by default, aka let the user opt-in to fail at parse error.
			    ORMKeys.ignoreParseErrors, !failFast,
			    // by default, save the generated hbm.xml files to the same directory.
			    ORMKeys.saveMapping, true
			) );

			// @TODO: Add a custom toString() to ORMConfig for logging purposes.
			// log.info( "Using ORM Config: {}", ormConfig );

			ortus.boxlang.modules.orm.mapping.MappingGenerator	generator	= new ortus.boxlang.modules.orm.mapping.MappingGenerator(
			    new ScriptingRequestBoxContext( runtime.getRuntimeContext() ),
			    ormConfig
			);

			logger.info( "========= Generating XML mappings! " );
			generator.generateMappings();
			logger.info( "========= Done! " );

			System.exit( 0 );
		} finally {
			runtime.shutdown();
		}
	}

}