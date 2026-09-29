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
package ortus.boxlang.modules.orm.perf;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

/**
 * Measures bulk entity hydration throughput: loading and materializing {@code rowCount} rows into BoxLang entities.
 * <p>
 * Unlike the single-row CRUD benchmark (which is dominated by transaction/JDBC wait time), loading many rows in one
 * query makes the per-entity, per-property bx-orm work - instantiation ({@code BoxClassInstantiator}), property setters
 * ({@code BoxPropertySetter}), and attribute converters - dominate the measurement, so it can surface real hotspots.
 * <p>
 * The ORM module is loaded from {@code -Dorm.module.path} (default {@code ./build/module}).
 */
@State( Scope.Benchmark )
@BenchmarkMode( Mode.AverageTime )
@OutputTimeUnit( TimeUnit.MICROSECONDS )
public class ORMReadBenchmark {

	@Param( { "2000" } )
	private int					rowCount;

	private BoxRuntime			instance;
	private RequestBoxContext	context;
	private IScope				variables;
	private static final Key	result	= Key.of( "result" );

	@Setup( Level.Trial )
	public void setUp() {
		String modulePath = System.getProperty( "orm.module.path", "./build/module" );

		instance = BoxRuntime.getInstance( false );
		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Paths.get( modulePath ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/derbyApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );

		// Seed rowCount widgets once for the trial.
		variables.put( Key.of( "seedCount" ), rowCount );
		// @formatter:off
		instance.executeSource( """
			transaction {
				for ( i = 1; i <= seedCount; i++ ) {
					entitySave( entityNew( "Widget", {
						name        : "widget-" & i,
						quantity    : i,
						price       : i * 1.25,
						active      : ( i % 2 == 0 ),
						createdDate : now()
					} ) );
				}
			}
			ormFlush();
		""", context );
		// @formatter:on
	}

	@TearDown( Level.Trial )
	public void tearDown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMDerbyTest" ) );
	}

	@Benchmark
	public void hydrateAll( Blackhole bh ) {
		// Clear the session so every invocation re-hydrates the rows from the database rather than the first-level cache.
		// @formatter:off
		instance.executeSource( """
			ormClearSession();
			result = ormExecuteQuery( "FROM Widget" );
			sink = 0;
			for ( w in result ) {
				sink += len( w.getName() ) + w.getQuantity();
			}
			result = sink;
		""", context );
		// @formatter:on
		bh.consume( variables.get( result ) );
	}
}
