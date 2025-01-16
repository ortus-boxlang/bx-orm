package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class EntityLoadByPKTest extends BaseORMTest {

	@DisplayName( "It can load an entity by integer key" )
	@Test
	public void testEntityLoadByPK() {

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "Manufacturer", 1 );
				theAddress = result.getAddress();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
		assertThat( variables.get( Key.of( "theAddress" ) ) ).isEqualTo( "202 Ford Way, Dearborn MI" );
	}

	@DisplayName( "It can load an entity from the non-default datasource" )
	@Test
	public void testEntityLoadAlternateDatasource() {

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "AlternateDS", '123e4567-e89b-12d3-a456-426614174000' );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
	}

	@DisplayName( "It can load an entity from the non-default datasource and call a getter method" )
	@Test
	public void testEntityLoadAlternateDatasourceAndCallGetter() {

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "AlternateDS", '123e4567-e89b-12d3-a456-426614174000' );
				theName = result.getName();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
		assertThat( variables.get( Key.of( "theName" ) ) ).isEqualTo( "Bilbo Baggins" );
	}

	@DisplayName( "It will add has* methods for associations" )
	@Test
	public void testEntityHasMethod() {

		instance.executeStatement( "result = entityLoadByPK( 'Vehicle', '1HGCM82633A123456' ).hasManufacturer()", context );
		assertTrue( variables.get( result ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( result ) );
	}

	@Disabled( "Context and variable names are colliding, which only allows the first test to pass. These tests run fine individually." )
	@DisplayName( "It will add add* methods for *-to-many associations" )
	@Test
	public void testEntityAddMethod() {

		// @formatter:off
		instance.executeSource( """
			Manufacturer = entityLoadByPK( 'Manufacturer', 1 );
			Manufacturer.addVehicle( entityLoadByPK( 'Vehicle', '1HGCM82633A123456' ) );
			result = Manufacturer.hasVehicle();
			""", context );
		// @formatter:on

		assertTrue( variables.get( result ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( result ) );
	}

	@Disabled( "Context and variable names are colliding, which only allows the first test to pass. These tests run fine individually." )
	@DisplayName( "It will add remove* methods for associations" )
	@Test
	public void testEntityRemoveMethod() {

		// @formatter:off
		instance.executeSource( """
			honda = entityLoadByPK( 'Manufacturer', 1 );

			myVehicle = entityLoadByPK( 'Vehicle', '1HGCM82633A123456' );
			honda.addVehicle( myVehicle );
			result = honda.hasVehicle();

			honda.removeVehicle( myVehicle );
			hasVehicleAfterRemove = honda.hasVehicle();
			""", context );
		// @formatter:on

		// has vehicle after add: yep!
		assertTrue( variables.get( result ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( result ) );

		// has vehicle after add: nope!
		assertTrue( variables.get( Key.of( "hasVehicleAfterRemove" ) ) instanceof Boolean );
		assertFalse( variables.getAsBoolean( Key.of( "hasVehicleAfterRemove" ) ) );
	}

	@DisplayName( "It can load an entity by varchar key" )
	@Test
	public void testEntityLoadByVarcharKey() {
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "Vehicle", "1HGCM82633A123456" ).getMake();
			""",
			context
		);
		// @formatter:on
		assertEquals( "Honda", variables.get( result ) );
	}

	@Disabled( "Unimplemented." )
	@DisplayName( "It can load an entity by composite key" )
	@Test
	public void testEntityLoadByCompositeKey() {
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "VehicleType", { make: "Ford", model : "Fusion" } ).getMake();
			""",
			context
		);
		// @formatter:on
		assertEquals( "Honda", variables.get( result ) );
	}
}
