package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class EntitySaveTest extends BaseORMTest {

	@Disabled( "Not working until we have a better transaction management strategy." )
	@DisplayName( "It can save new entities to the database" )
	@Test
	public void testEntitySave() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				dev1 = entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } );
				dev2 = entityNew( "manufacturer", { name : "Toyota", address : "101 Toyota Way" } );
				entitySave( dev1 );
				ormFlush();
			}
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
