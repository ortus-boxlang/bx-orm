package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

// @Disabled
public class EntityReloadTest extends BaseORMTest {

	@Disabled( "Not working yet." )
	@DisplayName( "It can reload entities using a string variable name, for compat" )
	@Test
	public void testEntityRefreshByVariableName() {
		// @formatter:off
		instance.executeSource(
			"""
				myEntity = entityLoadByPK( "Manufacturer", 1 );
				result = myEntity.getAddress();
				
				queryExecute( "UPDATE Manufacturers SET address='101 Ford Circle, Detroit MI' WHERE id=1" );

				entityReload( "myEntity" );
				reloadedAddress = myEntity.getAddress();
			""",
			context
		);
		// @formatter:on
		assertEquals( "202 Ford Way, Dearborn MI", variables.get( result ) );
		assertEquals( "101 Ford Circle, Detroit MI", variables.get( Key.of( "reloadedAddress" ) ) );
	}

	@DisplayName( "It can reload entities" )
	@Test
	public void testEntityRefreshFromDB() {
		// @formatter:off
		instance.executeSource(
			"""
				myEntity = entityLoadByPK( "Manufacturer", 1 );
				result = myEntity.getAddress();
				
				queryExecute( "UPDATE Manufacturers SET address='101 Ford Circle, Detroit MI' WHERE id=1" );

				entityReload( myEntity );
				reloadedAddress = myEntity.getAddress();
			""",
			context
		);
		// @formatter:on
		assertEquals( "202 Ford Way, Dearborn MI", variables.get( result ) );
		assertEquals( "101 Ford Circle, Detroit MI", variables.get( Key.of( "reloadedAddress" ) ) );
	}

	@DisplayName( "It throws if the argument is not a valid entity" )
	@Test
	public void testBadEntityName() {
		assertThrows( IllegalArgumentException.class, () -> {
			instance.executeSource(
			    """
			    	entityReload( "Fooey" );
			    """,
			    context
			);
		} );
	}

}
