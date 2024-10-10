package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
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

	@Disabled( "Unimplemented." )
	@DisplayName( "It can populate new entities with association UDFs" )
	@Test
	public void testEntityNewAssociationUDFs() {
		instance.executeStatement( "result = entityNew( 'Vehicle', { name : 'Dodge Powerwagon' } ).hasManufacturer()", context );
		assertTrue( variables.get( result ) instanceof Boolean );
		assertFalse( variables.getAsBoolean( result ) );
	}
}
