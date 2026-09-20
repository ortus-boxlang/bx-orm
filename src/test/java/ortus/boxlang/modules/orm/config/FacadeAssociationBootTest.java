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

/**
 * Boots a full ORM application against embedded Derby and proves ASSOCIATIONS and LAZY PROXIES work end-to-end through
 * the real BIFs.
 * <p>
 * Covers (all uuid-keyed):
 * <ul>
 * <li>a to-one (Vehicle -&gt; Manufacturer, eager) navigated to a BoxLang instance;</li>
 * <li>a to-many (Manufacturer -&gt; Vehicles, one-to-many) navigated to a collection of BoxLang instances;</li>
 * <li>a lazy to-one (Vehicle -&gt; Dealer) that arrives as an uninitialized proxy and initializes on access, still
 * yielding a BoxLang instance.</li>
 * </ul>
 * The critical invariant asserted throughout: navigated objects are {@link IClassRunnable}s, never {@link BoxEntityFacade}s.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FacadeAssociationBootTest {

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

	@DisplayName( "It navigates an eager to-one and a to-many collection in facade mode, yielding BoxLang instances" )
	@Test
	public void testEagerToOneAndToMany() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				m = entityNew( "Manufacturer", { name : "Acme" } );
				entitySave( m );

				v1 = entityNew( "Vehicle", { model : "Model S" } );
				v1.setManufacturer( m );
				entitySave( v1 );

				v2 = entityNew( "Vehicle", { model : "Model X" } );
				v2.setManufacturer( m );
				entitySave( v2 );

				manufacturerId = m.getId();
				v1Id           = v1.getId();
			}

			// Force a fresh load from the database so the association is hydrated by Hibernate, not the unit of work.
			ormFlush();
			ormClearSession();

			// --- to-one (eager) ---
			loadedV             = entityLoadByPK( "Vehicle", v1Id );
			loadedVModel        = loadedV.getModel();
			navManufacturer     = loadedV.getManufacturer();
			navManufacturerName = navManufacturer.getName();

			// --- to-many (one-to-many) ---
			loadedM          = entityLoadByPK( "Manufacturer", manufacturerId );
			vehicles         = loadedM.getVehicles();
			vehicleCount     = vehicles.len();
			firstVehicle     = vehicles[ 1 ];
			firstVehicleModel= firstVehicle.getModel();
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "loadedVModel" ) ) ).isEqualTo( "Model S" );

		// The navigated to-one is a real BoxLang instance (never a facade) and carries the right value.
		Object navManufacturer = variables.get( Key.of( "navManufacturer" ) );
		assertThat( navManufacturer ).isInstanceOf( IClassRunnable.class );
		assertThat( navManufacturer ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( variables.get( Key.of( "navManufacturerName" ) ) ).isEqualTo( "Acme" );

		// The to-many collection surfaced two BoxLang instances (never facades).
		assertThat( variables.get( Key.of( "vehicleCount" ) ) ).isEqualTo( 2 );
		Object firstVehicle = variables.get( Key.of( "firstVehicle" ) );
		assertThat( firstVehicle ).isInstanceOf( IClassRunnable.class );
		assertThat( firstVehicle ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( variables.get( Key.of( "firstVehicleModel" ) ) ).isAnyOf( "Model S", "Model X" );
	}

	@DisplayName( "It lazily proxies a to-one in facade mode: uninitialized on load, initializes on access to a BoxLang instance" )
	@Test
	public void testLazyToOne() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				d = entityNew( "Dealer", { name : "BestCars" } );
				entitySave( d );

				lv = entityNew( "Vehicle", { model : "Lazy Ride" } );
				lv.setDealer( d );
				entitySave( lv );

				lazyVehicleId = lv.getId();
			}

			ormFlush();
			ormClearSession();

			reloaded  = entityLoadByPK( "Vehicle", lazyVehicleId );
			dealerRef = reloaded.getDealer();
		""", context );
		// @formatter:on

		Object dealerRef = variables.get( Key.of( "dealerRef" ) );

		// The lazy to-one arrives as a BoxProxy (a lazy BoxLang instance), never a facade and never the eagerly-loaded
		// concrete class. (The ORM module runs in an isolated classloader, so the Hibernate proxy interface is asserted by
		// name/reflection rather than a cross-classloader instanceof.)
		assertThat( dealerRef ).isInstanceOf( IClassRunnable.class );
		assertThat( dealerRef ).isNotInstanceOf( BoxEntityFacade.class );
		assertThat( dealerRef.getClass().getSimpleName() ).isEqualTo( "BoxProxy" );
		assertThat( isUninitialized( dealerRef ) ).isTrue();

		// Accessing a property initializes the proxy and yields the real value from the backing BoxLang instance.
		// @formatter:off
		instance.executeSource( """
			dealerName = dealerRef.getName();
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "dealerName" ) ) ).isEqualTo( "BestCars" );
		assertThat( isUninitialized( dealerRef ) ).isFalse();
	}

	@DisplayName( "It reloads and checks attachment of a facade-mode entity through entityReload/entityIsAttached" )
	@Test
	public void testReloadAndIsAttached() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				rm            = entityNew( "Manufacturer", { name : "Reloadable" } );
				isNewAttached = entityIsAttached( rm );
				entitySave( rm );
				ormFlush();
				isSavedAttached = entityIsAttached( rm );
				entityReload( rm );
				reloadedName = rm.getName();
			}
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "isNewAttached" ) ) ).isEqualTo( false );
		assertThat( variables.get( Key.of( "isSavedAttached" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "reloadedName" ) ) ).isEqualTo( "Reloadable" );
	}

	@DisplayName( "It add/has a to-many association element in facade mode (FacadeCollectionView helpers)" )
	@Test
	public void testToManyAddHas() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				am = entityNew( "Manufacturer", { name : "Collector" } );
				entitySave( am );

				av = entityNew( "Vehicle", { model : "Added" } );
				av.setManufacturer( am );
				entitySave( av );

				am.addVehicle( av );
				hasIt    = am.hasVehicle( av );
				vehCount = am.getVehicles().len();
			}
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "hasIt" ) ) ).isEqualTo( true );
		assertThat( ( ( Number ) variables.get( Key.of( "vehCount" ) ) ).intValue() ).isAtLeast( 1 );
	}

	@DisplayName( "It fires update lifecycle events exactly once (not doubled) in facade mode" )
	@Test
	public void testUpdateEventsFireOnce() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				seed  = entityNew( "Manufacturer", { name : "EventSeed" } );
				entitySave( seed );
				seedId = seed.getId();
			}
			ormFlush();
			ormClearSession();

			transaction {
				updateEntity = entityLoadByPK( "Manufacturer", seedId );
				updateEntity.setName( "EventChanged" );
				entitySave( updateEntity );
			}
			eventLog = updateEntity.getEventLog();
		""", context );
		// @formatter:on

		@SuppressWarnings( "unchecked" )
		java.util.List<Object> log = ( java.util.List<Object> ) variables.get( Key.of( "eventLog" ) );
		assertThat( log ).containsExactly( "preLoad", "postLoad", "preUpdate", "postUpdate" ).inOrder();
	}

	@DisplayName( "It removes to-many elements while iterating in facade mode (removeX during each)" )
	@Test
	public void testToManyRemoveDuringEach() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				rm = entityNew( "Manufacturer", { name : "RemoveCo" } );
				entitySave( rm );

				rv1 = entityNew( "Vehicle", { model : "R1" } );
				rv1.setManufacturer( rm );
				entitySave( rv1 );
				rm.addVehicle( rv1 );

				rv2 = entityNew( "Vehicle", { model : "R2" } );
				rv2.setManufacturer( rm );
				entitySave( rv2 );
				rm.addVehicle( rv2 );

				rmId = rm.getId();
			}
			ormFlush();
			ormClearSession();

			loaded        = entityLoadByPK( "Manufacturer", rmId );
			hasPreRemove  = loaded.hasVehicle();
			loaded.getVehicles().each( ( v ) => {
				loaded.removeVehicle( v );
			} );
			hasPostRemove = loaded.hasVehicle();
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "hasPreRemove" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "hasPostRemove" ) ) ).isEqualTo( false );
	}

	@DisplayName( "It removes a to-many element whose id property name differs from the owner's (removeX by element identity)" )
	@Test
	public void testToManyRemoveDifferentIdName() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				fl = entityNew( "Fleet", { name : "FleetCo" } );
				entitySave( fl );
				tk = entityNew( "Truck", { model : "T1" } );
				entitySave( tk );
				fl.addTruck( tk );
				entitySave( fl );
				flId = fl.getFleetId();
			}
			ormFlush();
			ormClearSession();

			loadedFleet   = entityLoadByPK( "Fleet", flId );
			hasPreRemove2 = loadedFleet.hasTruck();
			loadedFleet.getTrucks().each( ( t ) => {
				loadedFleet.removeTruck( t );
			} );
			hasPostRemove2 = loadedFleet.hasTruck();
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "hasPreRemove2" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "hasPostRemove2" ) ) ).isEqualTo( false );
	}

	@DisplayName( "It removes a single to-many element via removeX in facade mode (removal sticks)" )
	@Test
	public void testToManyRemoveSingle() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				sm = entityNew( "Manufacturer", { name : "SingleRemoveCo" } );
				entitySave( sm );
				sv = entityNew( "Vehicle", { model : "OnlyOne" } );
				sv.setManufacturer( sm );
				entitySave( sv );
				sm.addVehicle( sv );
				smId = sm.getId();
			}
			ormFlush();
			ormClearSession();

			loaded1       = entityLoadByPK( "Manufacturer", smId );
			hasPreRemove1 = loaded1.hasVehicle();
			theVehicle    = loaded1.getVehicles()[ 1 ];
			loaded1.removeVehicle( theVehicle );
			hasPostRemove1 = loaded1.hasVehicle();
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "hasPreRemove1" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "hasPostRemove1" ) ) ).isEqualTo( false );
	}

	@DisplayName( "It returns BoxLang instances (never facades) from entityLoadByExample in facade mode" )
	@Test
	public void testLoadByExample() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				em = entityNew( "Manufacturer", { name : "Exemplar" } );
				entitySave( em );
			}
			ormFlush();
			ormClearSession();

			example     = entityNew( "Manufacturer", { name : "Exemplar" } );
			foundUnique = entityLoadByExample( example, true );
			foundArray  = entityLoadByExample( example );
			firstFound  = foundArray[ 1 ];
		""", context );
		// @formatter:on

		Object foundUnique = variables.get( Key.of( "foundUnique" ) );
		assertThat( foundUnique ).isInstanceOf( IClassRunnable.class );
		assertThat( foundUnique ).isNotInstanceOf( BoxEntityFacade.class );

		Object firstFound = variables.get( Key.of( "firstFound" ) );
		assertThat( firstFound ).isInstanceOf( IClassRunnable.class );
		assertThat( firstFound ).isNotInstanceOf( BoxEntityFacade.class );
	}

	@DisplayName( "It binds a primary key as an association query parameter in facade mode (ormExecuteQuery)" )
	@Test
	public void testPrimaryKeyAsQueryParameter() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				pm = entityNew( "Manufacturer", { name : "Pointed" } );
				entitySave( pm );

				pv = entityNew( "Vehicle", { model : "PointerCar" } );
				pv.setManufacturer( pm );
				entitySave( pv );

				pmId = pm.getId();
			}
			ormFlush();
			ormClearSession();

			// Bind a raw primary key where the query expects the associated entity (Hibernate 7 requires a managed
			// reference; in facade mode that must be the facade, not a BoxProxy).
			results  = ormExecuteQuery( "from Vehicle where manufacturer = :m", { m : pmId } );
			resCount = results.len();
			firstRes = results[ 1 ];
		""", context );
		// @formatter:on

		assertThat( ( ( Number ) variables.get( Key.of( "resCount" ) ) ).intValue() ).isEqualTo( 1 );
		Object firstRes = variables.get( Key.of( "firstRes" ) );
		assertThat( firstRes ).isInstanceOf( IClassRunnable.class );
		assertThat( firstRes ).isNotInstanceOf( BoxEntityFacade.class );
	}

	/**
	 * Read a BoxProxy's Hibernate lazy-initializer {@code isUninitialized()} via reflection, so the assertion survives the
	 * ORM module's isolated classloader (a direct {@code instanceof HibernateProxy} would compare against the test
	 * classloader's copy of the interface).
	 *
	 * @param proxy The BoxProxy instance.
	 *
	 * @return Whether the proxy is still uninitialized.
	 */
	private boolean isUninitialized( Object proxy ) {
		try {
			Object li = proxy.getClass().getMethod( "getHibernateLazyInitializer" ).invoke( proxy );
			return ( Boolean ) li.getClass().getMethod( "isUninitialized" ).invoke( li );
		} catch ( Exception e ) {
			throw new RuntimeException( e );
		}
	}
}
