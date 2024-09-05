package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import tools.BaseORMTest;

public class ORMGetSessionFactoryTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testORMGetSessionFactory() {
		SessionFactory sessionFactory = ormService.getSessionFactoryForName( BaseORMTest.appName );
		assertNotNull( sessionFactory );

		instance.executeSource( "result = ormGetSessionFactory() ", context );
		assertNotNull( variables.get( result ) );
		assertEquals( sessionFactory, variables.get( result ) );
	}

	@DisplayName( "It throws if the named datasource does not exist or is not configured for ORM" )
	@Test
	public void testBadDSN() {
		SessionFactory sessionFactory = ormService.getSessionFactoryForName( BaseORMTest.appName );
		assertNotNull( sessionFactory );

		assertThrows(
		    BoxRuntimeException.class,
		    () -> instance.executeSource( "result = ormGetSessionFactory( 'nonexistentDSN' ) ", context )
		);

		assertThrows(
		    BoxRuntimeException.class,
		    () -> instance.executeSource( "result = ormGetSessionFactory( 'dsnNotConfiguredForORM' ) ", context )
		);
	}

	@DisplayName( "It can get the session factory from a named datasource" )
	@Test
	public void testNamedDatasource() {
		SessionFactory sessionFactory = ormService.getSessionFactoryForName( BaseORMTest.appName );
		assertNotNull( sessionFactory );

		instance.executeSource( "result = ormGetSessionFactory( 'dsn2' ) ", context );

		// @TODO: Flesh out test
	}
}