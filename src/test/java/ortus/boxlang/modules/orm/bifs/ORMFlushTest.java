package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hibernate.Transaction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

@Disabled( "Breaks the test suite." )
public class ORMFlushTest extends BaseORMTest {

	@Disabled( "Not working until we have a better transaction management strategy." )
	@DisplayName( "It can flush the session" )
	@Test
	public void testORMFlush() {
		// @TODO: Plan and implement ORM transaction management.
		Transaction tx = ORMService.getInstance().getORMApp( context ).getSession( context ).beginTransaction();
		// @formatter:off
		instance.executeSource(
			"""
				manufacturer = entityNew( "Manufacturer" );
				manufacturer.setAddress( "101 Dodge Circle" ).setName( "Dodge" );
				entitySave( manufacturer );
				result = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
				ormFlush();
				// ugh. This is a hack to close the transaction so we don't block the SELECT query.
				ormGetSession().getTransaction().commit();
				result2 = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			""",
			context
		);
		// @formatter:on
		// tx.commit();
		assertEquals( 0, variables.getAsQuery( result ).size() );
		assertEquals( 1, variables.getAsQuery( Key.of( "result2" ) ).size() );
		assertEquals( "101 Dodge Circle", variables.getAsQuery( Key.of( "result2" ) ).getRowAsStruct( 0 ).get( "address" ) );
	}

}
