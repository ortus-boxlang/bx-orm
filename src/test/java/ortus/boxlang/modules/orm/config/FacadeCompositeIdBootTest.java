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

import ortus.boxlang.modules.orm.hibernate.facade.BoxEntityFacade;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Array;

/**
 * Boots a full facade-mode ({@code entityFacades=true}) ORM application against embedded Derby and proves a COMPOSITE
 * (multi-column) primary key works end-to-end through the real BIFs.
 * <p>
 * In facade mode a composite key is mapped as an embedded (non-aggregated) {@code <composite-id>}: the key properties
 * are typed accessors on the entity's own generated facade (no separate id class). This test round-trips a
 * two-column-keyed entity through {@code entityNew}/{@code entitySave}, then reloads it both by primary key
 * ({@code entityLoadByPK} with a struct of key values) and via an HQL query, asserting the reloaded object is a real
 * BoxLang instance (never a facade) carrying the right values.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FacadeCompositeIdBootTest {

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/facadeCompositeApp/index.bxs" ).toAbsolutePath().toUri() );
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
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMFacadeCompositeTest" ) );
	}

	@DisplayName( "It round-trips a composite-keyed entity via entityLoadByPK (struct) and HQL in facade mode, yielding a BoxLang instance" )
	@Test
	public void testCompositeIdRoundTrip() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				b = entityNew( "Booking", { flightID : 100, seatID : "12A", passenger : "Ada Lovelace" } );
				entitySave( b );
			}

			// Force a fresh load from the database so the entity is hydrated by Hibernate, not the unit of work.
			ormFlush();
			ormClearSession();

			// --- load by composite primary key (struct of key values) ---
			loaded          = entityLoadByPK( "Booking", { flightID : 100, seatID : "12A" } );
			loadedPassenger = loaded.getPassenger();
			loadedFlightID  = loaded.getFlightID();
			loadedSeatID    = loaded.getSeatID();

			// --- HQL round-trip ---
			hqlResults      = ormExecuteQuery( "FROM Booking WHERE passenger = :p", { p : "Ada Lovelace" } );
			hqlCount        = hqlResults.len();
			hqlFirst        = hqlResults[ 1 ];
			hqlPassenger    = hqlFirst.getPassenger();
		""", context );
		// @formatter:on

		// entityLoadByPK with a struct of the composite key returns the row.
		Object loaded = variables.get( Key.of( "loaded" ) );
		assertThat( loaded ).isInstanceOf( IClassRunnable.class );
		assertThat( loaded ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( variables.get( Key.of( "loadedPassenger" ) ) ).isEqualTo( "Ada Lovelace" );
		assertThat( variables.get( Key.of( "loadedFlightID" ) ) ).isEqualTo( 100 );
		assertThat( variables.get( Key.of( "loadedSeatID" ) ) ).isEqualTo( "12A" );

		// HQL against the composite-keyed entity returns the same row as a BoxLang instance.
		assertThat( variables.get( Key.of( "hqlCount" ) ) ).isEqualTo( 1 );
		Object hqlFirst = ( ( Array ) variables.get( Key.of( "hqlResults" ) ) ).get( 0 );
		assertThat( hqlFirst ).isInstanceOf( IClassRunnable.class );
		assertThat( hqlFirst ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( variables.get( Key.of( "hqlPassenger" ) ) ).isEqualTo( "Ada Lovelace" );
	}
}
