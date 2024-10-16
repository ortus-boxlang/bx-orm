package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class EntityNameListTest extends BaseORMTest {

	@DisplayName( "It returns a list of entity names" )
	@Test
	public void testEntityNameList() {
		instance.executeSource( "result = entityNameList()", context );
		assertEquals( "Manufacturer,Vehicle", variables.get( result ) );
	}

	@DisplayName( "It returns a list of entity names with a custom delimiter" )
	@Test
	public void testEntityNameListWithDelimiter() {
		instance.executeSource( "result = entityNameList( '|' )", context );
		assertEquals( "Manufacturer|Vehicle", variables.get( result ) );
	}

	@DisplayName( "It can get entities for a custom datasource name" )
	@Test
	public void testEntityNameListWithCustomDatasource() {
		instance.executeSource( "result = entityNameList( datasource = 'dsn2' )", context );
		// @TODO: Set up other entities for the alternate datasource.
		assertEquals( "Manufacturer,Vehicle", variables.get( result ) );
	}
}
