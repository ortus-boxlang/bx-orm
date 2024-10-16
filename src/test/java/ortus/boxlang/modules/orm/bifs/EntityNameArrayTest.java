package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityNameArrayTest extends BaseORMTest {

	@DisplayName( "It returns an array of entity names" )
	@Test
	public void testEntityNameArray() {
		instance.executeSource( "result = entityNameArray()", context );
		assertInstanceOf( Array.class, variables.get( result ) );
		assertTrue( Array.of( "Manufacturer", "Vehicle" ).equals( variables.getAsArray( result ) ) );
	}

	@DisplayName( "It can get entities for a custom datasource name" )
	@Test
	public void testEntityNameArrayWithCustomDatasource() {
		instance.executeSource( "result = entityNameArray( datasource = 'dsn2' )", context );
		assertInstanceOf( Array.class, variables.get( result ) );
		// @TODO: Set up other entities for the alternate datasource.
		assertTrue( Array.of( "Manufacturer", "Vehicle" ).equals( variables.getAsArray( result ) ) );
	}
}
