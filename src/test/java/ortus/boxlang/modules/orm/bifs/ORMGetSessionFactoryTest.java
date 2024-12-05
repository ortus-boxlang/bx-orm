package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.DatabaseException;
import tools.BaseORMTest;

public class ORMGetSessionFactoryTest extends BaseORMTest {

	@DisplayName( "It can get the default ORM session factory" )
	@Test
	public void testORMGetSessionFactory() {
		SessionFactory defaultSessionFactory = ormService.getORMApp( context ).getDefaultSessionFactoryOrThrow();
		assertNotNull( defaultSessionFactory );

		instance.executeSource( "result = ormGetSessionFactory()", context );
		assertNotNull( variables.get( result ) );
		assertEquals( defaultSessionFactory, variables.get( result ) );
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
		SessionFactory defaultSessionFactory = ormService.getORMApp( context ).getDefaultSessionFactoryOrThrow();
		assertNotNull( defaultSessionFactory );

		instance.executeSource( "result = ormGetSessionFactory( 'dsn2' )", context );

		assertNotNull( variables.get( result ) );
		assertNotEquals( defaultSessionFactory, variables.get( result ) );
		assertInstanceOf( SessionFactory.class, variables.get( result ) );
	}
}