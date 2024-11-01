package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityNameArrayTest extends BaseORMTest {

	@DisplayName( "It returns an array of entity names for ALL datasources" )
	@Test
	public void testEntityNameArray() {
		instance.executeSource( "result = entityNameArray()", context );
		assertInstanceOf( Array.class, variables.get( result ) );
		assertThat( variables.getAsArray( result ) ).isEqualTo( Array.of( "Manufacturer", "MappingFromAnotherMother", "Vehicle" ) );
	}

	@DisplayName( "It can get entities for a custom datasource name" )
	@Test
	public void testEntityNameArrayWithCustomDatasource() {
		instance.executeSource( "result = entityNameArray( datasource = 'dsn2' )", context );
		assertInstanceOf( Array.class, variables.get( result ) );
		assertThat( variables.getAsArray( result ) ).isEqualTo( Array.of( "MappingFromAnotherMother" ) );
	}
}
