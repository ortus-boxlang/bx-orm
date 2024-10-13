package ortus.boxlang.modules.orm.bifs;

import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
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
		ConnectionManager	connectionManager	= ( ( IJDBCCapableContext ) context ).getConnectionManager();
		DataSource			datasource2			= DataSource.fromStruct( "dsn2", Struct.of(
		    "database", "dsn2",
		    "driver", "derby",
		    "connectionString", "jdbc:derby:memory:" + "dsn2" + ";create=true"
		) );
		connectionManager.register( datasource2 );

		// Construct a new session factory for the second datasource
		ORMConfig						config				= new ORMConfig( Struct.of(
		    "datasource", "dsn2"
		) );
		Map<String, List<EntityRecord>>	entities			= ORMService.discoverEntities( context, config );
		SessionFactoryBuilder			alternateBuilder	= new SessionFactoryBuilder(
		    context,
		    datasource2,
		    config,
		    entities.get( "dsn2" )
		);
		ormService.setSessionFactoryForName( alternateBuilder.getUniqueName(), alternateBuilder.build() );

		Session secondDSNSession = ormService.getSessionForContext( context, datasource2.getConfiguration().name );
		assertNotNull( secondDSNSession );

		instance.executeSource( "result = ormGetSession( 'dsn2' ) ", context );

		assertNotNull( variables.get( result ) );
		assertNotEquals( defaultORMSession, variables.get( result ) );
		assertEquals( secondDSNSession, variables.get( result ) );
	}
}