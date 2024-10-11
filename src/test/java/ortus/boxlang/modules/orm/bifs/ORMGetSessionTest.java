package ortus.boxlang.modules.orm.bifs;

import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.DatabaseException;
import tools.BaseORMTest;

public class ORMGetSessionTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testDefaultDatasource() {
		Session session = ormService.getSessionForContext( context );
		assertNotNull( session );

		instance.executeSource( "result = ormGetSession() ", context );
		assertNotNull( variables.get( result ) );
		assertEquals( session, variables.get( result ) );
	}

	@DisplayName( "It throws if the named datasource does not exist or is not configured for ORM" )
	@Test
	public void testBadDSN() {
		Session session = ormService.getSessionForContext( context );
		assertNotNull( session );

		assertThrows(
		    DatabaseException.class,
		    () -> instance.executeSource( "result = ormGetSession( 'nonexistentDSN' ) ", context )
		);
	}

	@DisplayName( "It can get the ORM session from a named datasource" )
	@Test
	public void testNamedDatasource() {
		Session defaultORMSession = ormService.getSessionForContext( context );
		assertNotNull( defaultORMSession );

		// constrct a second datasource
		Key					datasource2Name		= Key.of( "dsn2" );
		ConnectionManager	connectionManager	= ( ( IJDBCCapableContext ) context ).getConnectionManager();
		connectionManager.register( datasource2Name, Struct.of(
		    "database", "dsn2",
		    "driver", "derby",
		    "connectionString", "jdbc:derby:memory:" + "dsn2" + ";create=true"
		) );

		// Construct a new session factory for the second datasource
		SessionFactoryBuilder alternateBuilder = new SessionFactoryBuilder(
		    context,
		    appName,
		    connectionManager.getDatasource( datasource2Name ),
		    new ORMConfig( Struct.of() )
		);
		ormService.setSessionFactoryForName( alternateBuilder.getUniqueName(), alternateBuilder.build() );

		Session secondDSNSession = ormService.getSessionForContext( context, datasource2Name );
		assertNotNull( secondDSNSession );

		instance.executeSource( "result = ormGetSession( 'dsn2' ) ", context );

		assertNotNull( variables.get( result ) );
		assertNotEquals( defaultORMSession, variables.get( result ) );
		assertEquals( secondDSNSession, variables.get( result ) );
	}
}