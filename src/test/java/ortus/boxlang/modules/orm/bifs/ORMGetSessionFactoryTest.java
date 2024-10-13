package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
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

public class ORMGetSessionFactoryTest extends BaseORMTest {

	@DisplayName( "It can get the default ORM session factory" )
	@Test
	public void testORMGetSessionFactory() {
		SessionFactory sessionFactory = ormService.getSessionFactoryForName( builder.getUniqueName() );
		assertNotNull( sessionFactory );

		instance.executeSource( "result = ormGetSessionFactory()", context );
		assertNotNull( variables.get( result ) );
		assertEquals( sessionFactory, variables.get( result ) );
	}

	@DisplayName( "It throws if the named datasource does not exist" )
	@Test
	public void testBadDSN() {
		assertThrows(
		    DatabaseException.class,
		    () -> instance.executeSource( "result = ormGetSessionFactory( 'nonexistentDSN' ) ", context )
		);
	}

	@DisplayName( "It can get the session factory from a named datasource" )
	@Test
	public void testNamedDatasource() {
		SessionFactory defaultSessionFactory = ormService.getSessionFactoryForContext( context );
		assertNotNull( defaultSessionFactory );

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

		SessionFactory alternateSessionFactory = ormService.getSessionFactoryForContext( context, datasource2.getConfiguration().name );
		assertNotNull( alternateSessionFactory );

		instance.executeSource( "result = ormGetSessionFactory( 'dsn2' )", context );

		assertNotNull( variables.get( result ) );
		assertNotEquals( defaultSessionFactory, variables.get( result ) );
		assertEquals( alternateSessionFactory, variables.get( result ) );
	}
}