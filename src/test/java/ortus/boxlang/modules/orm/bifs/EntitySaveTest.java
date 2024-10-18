package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hibernate.Transaction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMService;
import tools.BaseORMTest;

// @TODO: This is implemented, and working, but is a MAJOR hack
// and needs revisiting to address connection management and transaction management concerns.
@Disabled( "connection management issues when run in a full suite. Passes when run solo." )
public class EntitySaveTest extends BaseORMTest {

	@DisplayName( "It can save new entities to the database" )
	@Test
	public void testEntitySave() {
		// @TODO: Plan and implement ORM transaction management.
		Transaction tx = ORMService.getInstance().getORMApp( context ).getSession( context ).beginTransaction();
		// @formatter:off
		instance.executeSource(
			"""
				dev1 = entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } );
				dev2 = entityNew( "manufacturer", { name : "Toyota", address : "101 Toyota Way" } );
				entitySave( dev1 );
				ormFlush();
				// ugh. This is a hack to close the transaction so we don't block the SELECT query.
				ormGetSession().getTransaction().commit();
				result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Audi Corp'" );
			""",
			context
		);
		// @formatter:on
		// tx.commit();
		assertEquals( 1, variables.getAsQuery( result ).size() );
		assertEquals( "Audi Corp", variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) );
	}

}
