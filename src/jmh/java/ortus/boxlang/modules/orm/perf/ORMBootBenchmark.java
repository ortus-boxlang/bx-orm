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
 * Measures cold ORM application boot: entity discovery + mapping generation ({@code MappingGenerator},
 * {@code MappingXMLWriter}) plus the Hibernate {@code SessionFactory} build for the {@code derbyApp} entities.
 * <p>
 * The BoxLang runtime and ORM module are registered once per trial; each invocation boots (and then tears down) a
 * fresh ORM application so it is a clean cold boot. A sampling profiler run against this benchmark shows the split
 * between bx-orm's own mapping work and Hibernate's metadata build.
 * <p>
 * The ORM module is loaded from {@code -Dorm.module.path} (default {@code ./build/module}).
 */
@State( Scope.Benchmark )
@BenchmarkMode( Mode.SingleShotTime )
@OutputTimeUnit( TimeUnit.MILLISECONDS )
public class ORMBootBenchmark {

	private BoxRuntime			instance;
	private RequestBoxContext	context;
	private IScope				variables;
	private static final Key	result	= Key.of( "result" );

	@Setup( Level.Trial )
	public void setUpRuntime() {
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
	}

	/**
	 * Tear down the freshly booted ORM application after each invocation so the next invocation boots from cold.
	 */
	@TearDown( Level.Invocation )
	public void resetApplication() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
			context = null;
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMDerbyTest" ) );
	}

	@Benchmark
	public void bootSessionFactory( Blackhole bh ) {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/derbyApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
		instance.executeSource( "result = ormGetSessionFactory();", context );
		bh.consume( variables.get( result ) );
	}
}
