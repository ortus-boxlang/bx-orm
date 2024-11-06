package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

// @TODO: This is implemented, and working, but is a MAJOR hack
// and needs revisiting to address connection management and transaction management concerns.
// @Disabled( "connection management issues when run in a full suite. Passes when run solo." )
public class EntityDeleteTest extends BaseORMTest {

	// @Disabled( "Not working until we have a better transaction management strategy." )
	@DisplayName( "It can save new entities to the database" )
	@Test
	public void testEntityDelete() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entityDelete( entityLoadByPK( "Vehicle", '1HGCM82633A123456' ) );
				ormFlush();
			}
			result = queryExecute( "SELECT * FROM vehicles WHERE vin = '1HGCM82633A123456'" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 0, variables.getAsQuery( result ).size() );
	}

}
