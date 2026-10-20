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
package ortus.boxlang.modules.orm.observability;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Tests ORM observability events. The JDBC wrapper is unit tested against an in-memory SQLite connection; the wiring (connection provider,
 * flush listener, HQL hint, statistics API) is tested against a booted ORM application on a file-based SQLite datasource.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class ORMObservabilityTest {

	/**
	 * Records every observability event announced.
	 */
	public static class Recorder extends BaseInterceptor {

		public final List<IStruct>	queries		= new CopyOnWriteArrayList<>();
		public final List<IStruct>	flushes		= new CopyOnWriteArrayList<>();
		public final List<IStruct>	exceptions	= new CopyOnWriteArrayList<>();

		@InterceptionPoint
		public void onORMQuery( IStruct args ) {
			queries.add( args );
		}

		@InterceptionPoint
		public void onORMFlush( IStruct args ) {
			flushes.add( args );
		}

		@InterceptionPoint
		public void onORMException( IStruct args ) {
			exceptions.add( args );
		}

		public void clear() {
			queries.clear();
			flushes.clear();
			exceptions.clear();
		}
	}

	private static BoxRuntime			instance;
	private static InterceptorService	interceptors;
	private static Path					dbFilePath;
	private static final Key			appName		= Key.of( "BXORMSQLiteTest" );

	private final Recorder				recorder	= new Recorder();
	private RequestBoxContext			context;
	private List<IStruct>				startupQueries;
	private Connection					raw;

	@BeforeAll
	public void setUp() throws IOException {
		dbFilePath = Paths.get( "build/test-sqlite/orm-observability-test.db" ).toAbsolutePath();
		Files.createDirectories( dbFilePath.getParent() );
		Files.deleteIfExists( dbFilePath );
		System.setProperty( "SQLITE_DB_PATH", dbFilePath.toString() );

		instance		= BoxRuntime.getInstance( false );
		interceptors	= instance.getInterceptorService();

		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Paths.get( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}

		// Listen BEFORE the application boots, so startup DDL is captured too.
		interceptors.register( recorder );

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/sqliteApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		startupQueries = List.copyOf( recorder.queries );
	}

	@AfterAll
	public void teardown() {
		interceptors.unregister( recorder );
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( appName );
	}

	@BeforeEach
	public void clear() {
		recorder.clear();
	}

	@AfterEach
	public void closeRaw() throws SQLException {
		if ( raw != null ) {
			raw.close();
			raw = null;
		}
	}

	/**
	 * The ORM service lives in the module's classloader, so it is driven reflectively from this test's classloader.
	 */
	private Object service() {
		return instance.getGlobalService( ORMKeys.ORMService );
	}

	private IStruct statistics() throws Exception {
		Object svc = service();
		return ( IStruct ) svc.getClass().getMethod( "getStatistics", Key.class ).invoke( svc, appName );
	}

	private void setStatistics( boolean enabled ) throws Exception {
		Object svc = service();
		svc.getClass().getMethod( "setStatisticsEnabled", Key.class, boolean.class ).invoke( svc, appName, enabled );
	}

	private Connection observed( boolean captureParams ) throws SQLException {
		raw = DriverManager.getConnection( "jdbc:sqlite::memory:" );
		try ( Statement s = raw.createStatement() ) {
			s.execute( "CREATE TABLE t ( id INTEGER PRIMARY KEY, name TEXT )" );
		}
		recorder.clear();
		return ORMObserver.wrap( raw, Key.of( "unit" ), "unitApp", captureParams );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Zero cost without listeners + classification
	 * ------------------------------------------------------------------------------------------------------------
	 */

	@DisplayName( "It does not wrap connections when nobody is listening" )
	@Test
	public void testNoWrapWithoutListeners() throws SQLException {
		interceptors.unregister( recorder );
		try ( Connection c = DriverManager.getConnection( "jdbc:sqlite::memory:" ) ) {
			assertThat( ORMObserver.isObserving() ).isFalse();
			assertThat( ORMObserver.wrap( c, Key.of( "unit" ), "unitApp", false ) ).isSameInstanceAs( c );
		} finally {
			interceptors.register( recorder );
		}
	}

	@DisplayName( "It classifies SQL" )
	@Test
	public void testClassify() {
		assertThat( ORMObserver.classify( "select * from t" ) ).isEqualTo( "select" );
		assertThat( ORMObserver.classify( "  /* hint */ SELECT 1" ) ).isEqualTo( "select" );
		assertThat( ORMObserver.classify( "-- c\nwith x as (select 1) select * from x" ) ).isEqualTo( "select" );
		assertThat( ORMObserver.classify( "insert into t values (1)" ) ).isEqualTo( "insert" );
		assertThat( ORMObserver.classify( "update t set a=1" ) ).isEqualTo( "update" );
		assertThat( ORMObserver.classify( "delete from t" ) ).isEqualTo( "delete" );
		assertThat( ORMObserver.classify( "create table x (id int)" ) ).isEqualTo( "ddl" );
		assertThat( ORMObserver.classify( "drop table x" ) ).isEqualTo( "ddl" );
		assertThat( ORMObserver.classify( "vacuum" ) ).isEqualTo( "other" );
		assertThat( ORMObserver.classify( null ) ).isEqualTo( "other" );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * JDBC wrapper
	 * ------------------------------------------------------------------------------------------------------------
	 */

	@DisplayName( "It reports kind, rows, timing, datasource and app for DML and selects" )
	@Test
	public void testWrapperQueries() throws SQLException {
		Connection c = observed( false );
		try ( PreparedStatement ins = c.prepareStatement( "INSERT INTO t ( id, name ) VALUES ( ?, ? )" ) ) {
			ins.setInt( 1, 1 );
			ins.setString( 2, "a" );
			ins.executeUpdate();
		}
		try ( PreparedStatement sel = c.prepareStatement( "SELECT * FROM t" ); ResultSet rs = sel.executeQuery() ) {
			while ( rs.next() ) {
				// consume
			}
		}

		assertThat( recorder.queries ).hasSize( 2 );
		IStruct insert = recorder.queries.get( 0 );
		assertThat( insert.get( ORMKeys.kind ) ).isEqualTo( "insert" );
		assertThat( insert.get( ORMKeys.rows ) ).isEqualTo( 1L );
		assertThat( insert.get( ORMKeys.datasource ) ).isEqualTo( "unit" );
		assertThat( insert.get( ORMKeys.appName ) ).isEqualTo( "unitApp" );
		assertThat( ( Long ) insert.get( ORMKeys.elapsedNanos ) ).isAtLeast( 0L );
		assertThat( insert.get( ORMKeys.error ) ).isNull();

		IStruct select = recorder.queries.get( 1 );
		assertThat( select.get( ORMKeys.kind ) ).isEqualTo( "select" );
		assertThat( select.get( ORMKeys.rows ) ).isEqualTo( 1L );
		assertThat( select.get( ORMKeys.sql ).toString() ).contains( "SELECT * FROM t" );
	}

	@DisplayName( "It never includes params unless asked" )
	@Test
	public void testParamsOffByDefault() throws SQLException {
		Connection c = observed( false );
		try ( PreparedStatement ins = c.prepareStatement( "INSERT INTO t ( id, name ) VALUES ( ?, ? )" ) ) {
			ins.setInt( 1, 1 );
			ins.setString( 2, "secret" );
			ins.executeUpdate();
		}
		assertThat( recorder.queries.get( 0 ).containsKey( ORMKeys.params ) ).isFalse();
	}

	@DisplayName( "It includes ordered param values when asked" )
	@Test
	public void testParamsOn() throws SQLException {
		Connection c = observed( true );
		try ( PreparedStatement ins = c.prepareStatement( "INSERT INTO t ( id, name ) VALUES ( ?, ? )" ) ) {
			ins.setInt( 1, 7 );
			ins.setString( 2, "seven" );
			ins.executeUpdate();
		}
		Array params = ( Array ) recorder.queries.get( 0 ).get( ORMKeys.params );
		assertThat( params.toArray() ).asList().containsExactly( 7, "seven" ).inOrder();
	}

	@DisplayName( "It reports errors on onORMQuery and onORMException, and rethrows" )
	@Test
	public void testErrors() throws SQLException {
		Connection c = observed( false );
		assertThrows( SQLException.class, () -> {
			try ( PreparedStatement bad = c.prepareStatement( "SELECT * FROM missing_table" ) ) {
				bad.executeQuery();
			}
		} );
		assertThat( recorder.queries ).hasSize( 1 );
		assertThat( recorder.queries.get( 0 ).get( ORMKeys.error ) ).isInstanceOf( SQLException.class );
		assertThat( recorder.exceptions ).hasSize( 1 );
		assertThat( recorder.exceptions.get( 0 ).get( ORMKeys.sql ).toString() ).contains( "missing_table" );
		assertThat( recorder.exceptions.get( 0 ).get( ORMKeys.appName ) ).isEqualTo( "unitApp" );
	}

	@DisplayName( "It reports DDL and batches" )
	@Test
	public void testDdlAndBatch() throws SQLException {
		Connection c = observed( false );
		try ( Statement s = c.createStatement() ) {
			s.execute( "CREATE TABLE u ( id INTEGER )" );
		}
		try ( PreparedStatement ins = c.prepareStatement( "INSERT INTO t ( id, name ) VALUES ( ?, ? )" ) ) {
			for ( int i = 1; i <= 3; i++ ) {
				ins.setInt( 1, i );
				ins.setString( 2, "n" + i );
				ins.addBatch();
			}
			ins.executeBatch();
		}
		assertThat( recorder.queries.get( 0 ).get( ORMKeys.kind ) ).isEqualTo( "ddl" );
		assertThat( recorder.queries.get( 1 ).get( ORMKeys.kind ) ).isEqualTo( "insert" );
		assertThat( recorder.queries.get( 1 ).get( ORMKeys.rows ) ).isEqualTo( 3L );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * ORM wiring
	 * ------------------------------------------------------------------------------------------------------------
	 */

	@DisplayName( "It captures startup DDL" )
	@Test
	public void testStartupDdlCaptured() {
		assertThat( startupQueries.stream().anyMatch( q -> "ddl".equals( q.get( ORMKeys.kind ) ) ) ).isTrue();
		assertThat( startupQueries.get( 0 ).get( ORMKeys.appName ) ).isEqualTo( appName.getName() );
		assertThat( startupQueries.get( 0 ).get( ORMKeys.datasource ) ).isEqualTo( "SQLiteTestDB" );
	}

	@DisplayName( "It announces insert queries and one flush with counts" )
	@Test
	public void testInsertAndFlush() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "Product", { name : "Observed" } ) );
				ormFlush();
			}
		""", context );
		// @formatter:on

		assertThat( recorder.queries.stream().anyMatch( q -> "insert".equals( q.get( ORMKeys.kind ) ) ) ).isTrue();
		assertThat( recorder.flushes ).isNotEmpty();
		int inserts = recorder.flushes.stream().mapToInt( f -> ( Integer ) f.get( ORMKeys.inserts ) ).sum();
		assertThat( inserts ).isAtLeast( 1 );
		IStruct flush = recorder.flushes.get( 0 );
		assertThat( flush.get( ORMKeys.appName ) ).isEqualTo( appName.getName() );
		assertThat( flush.get( ORMKeys.datasource ) ).isEqualTo( "SQLiteTestDB" );
		assertThat( ( Long ) flush.get( ORMKeys.elapsedNanos ) ).isAtLeast( 0L );
		assertThat( recorder.queries.stream().anyMatch( q -> !q.containsKey( ORMKeys.params ) ) ).isTrue();
	}

	@DisplayName( "It keeps ORM writes inside a transaction working while observed, and commits them" )
	@Test
	public void testObservedTransaction() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "Product", { name : "InTransaction" } ) );
				ormFlush();
			}
			result = queryExecute( "SELECT * FROM products WHERE name = 'InTransaction'" );
		""", context );
		// @formatter:on

		assertThat( context.getScopeNearby( ortus.boxlang.runtime.scopes.VariablesScope.name ).getAsQuery( Key.of( "result" ) ).size() ).isEqualTo( 1 );
		assertThat( recorder.queries.stream().anyMatch( q -> "insert".equals( q.get( ORMKeys.kind ) ) ) ).isTrue();
	}

	@DisplayName( "It reports HQL for HQL queries" )
	@Test
	public void testHqlHint() {
		// @formatter:off
		instance.executeSource( """
			ormExecuteQuery( "FROM Product" );
		""", context );
		// @formatter:on

		IStruct select = recorder.queries.stream().filter( q -> "select".equals( q.get( ORMKeys.kind ) ) ).findFirst().orElseThrow();
		assertThat( select.get( ORMKeys.hql ) ).isEqualTo( "FROM Product" );
	}

	@DisplayName( "It reports the entity for entityLoad" )
	@Test
	public void testEntityHint() {
		// @formatter:off
		instance.executeSource( """
			entityLoad( "Product" );
		""", context );
		// @formatter:on

		assertThat( recorder.queries.stream().anyMatch( q -> "Product".equals( q.get( ORMKeys.entityName ) ) ) ).isTrue();
	}

	@DisplayName( "It returns statistics: disabled by default, enabled on demand" )
	@Test
	public void testStatistics() throws Exception {
		IStruct	before	= statistics();
		IStruct	ds		= before.getAsStruct( Key.of( "datasources" ) ).getAsStruct( Key.of( "SQLiteTestDB" ) );
		assertThat( before.get( ORMKeys.appName ) ).isEqualTo( appName.getName() );
		assertThat( ds.get( Key.of( "enabled" ) ) ).isEqualTo( false );

		setStatistics( true );
		try {
			instance.executeSource( "ormExecuteQuery( \"FROM Product\" );", context );
			IStruct after = statistics().getAsStruct( Key.of( "datasources" ) ).getAsStruct( Key.of( "SQLiteTestDB" ) );
			assertThat( after.get( Key.of( "enabled" ) ) ).isEqualTo( true );
			assertThat( ( Long ) after.get( Key.of( "queryExecutionCount" ) ) ).isAtLeast( 1L );
		} finally {
			setStatistics( false );
		}
	}
}
