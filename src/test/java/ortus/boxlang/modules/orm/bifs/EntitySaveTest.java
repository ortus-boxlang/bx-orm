package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

@Disabled( "Implemented, but not working until we flesh out transaction management." )
public class EntitySaveTest extends BaseORMTest {

	@DisplayName( "It can save new entities to the database" )
	@Test
	public void testEntitySave() {
		// @formatter:off
		instance.executeSource(
			"""
				dev1 = entityNew( "Developer", { name : "Dan", role : "Software Engineer" } );
				dev2 = entityNew( "Developer", { name : "Daniel", role : "Software Engineer" } );
				entitySave( dev1 );
				ormFlush();
				result = queryExecute( "SELECT * FROM developers" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 4, variables.getAsQuery( result ).size() );
		assertEquals( "Dan", variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) );
	}

}
