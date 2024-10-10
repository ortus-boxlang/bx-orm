package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

// @Disabled
public class EntityNewTest extends BaseORMTest {

	@DisplayName( "It can create new entities" )
	@Test
	public void testEntityNew() {
		// @formatter:off
		instance.executeSource(
			"""
				result = entityNew( "Manufacturer" );
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( IClassRunnable.class, variables.get( result ) );
	}

	@DisplayName( "It can populate new entities with data" )
	@Test
	public void testEntityNewWithProperties() {
		// @formatter:off
		instance.executeSource(
			"""
				result = entityNew( "Manufacturer", { name : "Bugatti Automobiles", address : "101 Bugatti Way" } );
				name = result.getName();
				address = result.getAddress();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( IClassRunnable.class, variables.get( result ) );
		assertEquals( "Bugatti Automobiles", variables.get( Key._NAME ) );
		assertEquals( "101 Bugatti Way", variables.get( Key.of( "address" ) ) );
	}

}
