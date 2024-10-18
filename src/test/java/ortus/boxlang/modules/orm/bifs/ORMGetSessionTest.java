package ortus.boxlang.modules.orm.bifs;

import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.DatabaseException;
import tools.BaseORMTest;

public class ORMGetSessionTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testDefaultDatasource() {
		Session session = ormService.getORMApp( context ).getSession( context );
		assertNotNull( session );

		instance.executeSource( "result = ormGetSession() ", context );
		assertNotNull( variables.get( result ) );
		assertEquals( session, variables.get( result ) );
	}

	@DisplayName( "It throws if the named datasource does not exist or is not configured for ORM" )
	@Test
	public void testBadDSN() {
		Session session = ormService.getORMApp( context ).getSession( context );
		assertNotNull( session );

		assertThrows(
		    DatabaseException.class,
		    () -> instance.executeSource( "result = ormGetSession( 'nonexistentDSN' ) ", context )
		);
	}

	@DisplayName( "It can get the ORM session from a named datasource" )
	@Test
	public void testNamedDatasource() {
		Session defaultORMSession = ormService.getORMApp( context ).getSession( context );
		assertNotNull( defaultORMSession );

		Session secondDSNSession = ormService.getORMApp( context ).getSession( context, alternateDataSource.getConfiguration().name );
		assertNotNull( secondDSNSession );

		instance.executeSource( "result = ormGetSession( 'dsn2' ) ", context );

		assertNotNull( variables.get( result ) );
		assertNotEquals( defaultORMSession, variables.get( result ) );
		assertEquals( secondDSNSession, variables.get( result ) );
	}

	@DisplayName( "It can use queryExecute after opening ORM session" )
	@Test
	public void testSharedTransaction() {
		instance.executeSource(
		    """
		    transaction{
		    	ormGetSession();
		    	dev1 = entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } );
		    	entitySave( dev1 );
		    	ORMFlush();
		    	result = queryExecute( "SELECT COUNT(*) FROM vehicles" );
		    }
		    """, context );

		assertNotNull( variables.get( result ) );
		assertEquals( 1, variables.getAsQuery( result ).size() );
	}

}