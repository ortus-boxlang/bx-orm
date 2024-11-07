package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class EntitySaveTest extends BaseORMTest {

	@DisplayName( "It can save new entities to the database" )
	@Test
	public void testEntitySave() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ) );
				ormFlush();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Audi Corp'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Audi Corp" );
	}

	@DisplayName( "It can save new entities to the database with forceinsert:true" )
	@Test
	public void testEntitySaveWithForceInsert() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				manufacturer = entityNew( "manufacturer", { name : "Toyota", address : "101 Toyota Way" } )
				entitySave( manufacturer, true );
				ormFlush();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Toyota'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Toyota" );
	}

}
