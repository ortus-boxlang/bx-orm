package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class EntityDeleteTest extends BaseORMTest {

	@DisplayName( "It can delete existing entities from the database" )
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
