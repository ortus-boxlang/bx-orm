package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import tools.BaseORMTest;

/**
 * Live (MySQL) tests for {@code ormDiagnostics()}: status, entities per datasource, settings, and this request's sessions.
 */
public class ORMDiagnosticsTest extends BaseORMTest {

	/**
	 * Test: OrmDiagnostics reports a running app with entities per datasource and settings.
	 */
	@DisplayName( "ormDiagnostics reports a running app with entities per datasource and settings" )
	@Test
	public void testRunning() {
		instance.executeSource( "result = ormDiagnostics();", context );
		IStruct diag = variables.getAsStruct( result );
		assertThat( diag.getAsString( Key.of( "status" ) ) ).isEqualTo( "running" );
		assertThat( diag.getAsString( Key.of( "applicationName" ) ) ).isEqualTo( "BXORMTest" );
		assertThat( diag.getAsString( Key.of( "hibernateVersion" ) ) ).startsWith( "7." );
		IStruct datasources = diag.getAsStruct( Key.of( "datasources" ) );
		assertThat( ( Array ) datasources.get( Key.of( "TestDB" ) ) ).contains( "Manufacturer" );
		assertThat( ( Array ) datasources.get( Key.of( "dsn2" ) ) ).contains( "AlternateDS" );
		assertThat( ( Integer ) diag.get( Key.of( "entityCount" ) ) ).isGreaterThan( 10 );
		assertThat( diag.getAsStruct( Key.of( "settings" ) ).getAsString( Key.of( "dbcreate" ) ) ).isEqualTo( "drop-and-create" );
		assertThat( diag.containsKey( Key.of( "startupError" ) ) ).isFalse();
	}

	/**
	 * Test: OrmDiagnostics lists this request's open sessions without opening new ones.
	 */
	@DisplayName( "ormDiagnostics lists this request's open sessions without opening new ones" )
	@Test
	public void testSessions() {
		// @formatter:off
		instance.executeSource(
		    """
		    ormCloseAllSessions();
		    before = ormDiagnostics().sessions.count();
		    m = entityLoadByPK( "Manufacturer", 1 );
		    after = ormDiagnostics().sessions;
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( Key.of( "before" ) ) ).isEqualTo( 0 );
		IStruct	after	= variables.getAsStruct( Key.of( "after" ) );
		IStruct	testDb	= after.getAsStruct( Key.of( "TestDB" ) );
		assertThat( testDb.get( Key.of( "open" ) ) ).isEqualTo( true );
		assertThat( ( Integer ) testDb.get( Key.of( "entityCount" ) ) ).isAtLeast( 1 );
		assertThat( testDb.get( Key.of( "dirty" ) ) ).isEqualTo( false );
	}

	/**
	 * Test: OrmDiagnostics shows unsaved changes as a dirty session.
	 */
	@DisplayName( "ormDiagnostics shows unsaved changes as a dirty session" )
	@Test
	public void testDirtySession() {
		// @formatter:off
		instance.executeSource(
		    """
		    transaction {
		        try {
		            m = entityLoadByPK( "Manufacturer", 1 );
		            m.setName( m.getName() & "!" );
		            result = ormDiagnostics().sessions.TestDB.dirty;
		        } finally {
		            transactionRollback();
		        }
		    }
		    ormClearSession();
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( true );
	}
}
