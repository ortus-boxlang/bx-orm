package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

		// @formatter:off
		instance.executeStatement( """
			vehicle = entityLoadByPK( 'Vehicle', '1HGCM82633A123456' );
			result = vehicle.hasManufacturer()
			""", context );
		// @formatter:on
		assertTrue( variables.get( result ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( result ) );
	}

	@DisplayName( "addVehicle() - It will add the association" )
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

	@DisplayName( "AddVehicle() - It will happily add duplicate items to an array collection" )
	@Test
	public void testEntityAddWithDuplicate() {

		// @formatter:off
		instance.executeSource( """
			Manufacturer = entityNew( 'Manufacturer' );
			vehicle = entityLoadByPK( 'Vehicle', '1HGCM82633A123456' );
			
			vehicleCountPreAdd = manufacturer.getVehicles().len() ?: 0;
			
			Manufacturer.addVehicle( vehicle );
			vehicleCountPostAdd1 = manufacturer.getVehicles().len();
			
			Manufacturer.addVehicle( vehicle );
			vehicleCountPostAdd2 = manufacturer.getVehicles().len();
			""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "vehicleCountPreAdd" ) ) ).isEqualTo( 0 );
		assertThat( variables.get( Key.of( "vehicleCountPostAdd1" ) ) ).isEqualTo( 1 );
		assertThat( variables.get( Key.of( "vehicleCountPostAdd2" ) ) ).isEqualTo( 2 );
	}

	@DisplayName( "It will add remove* methods for associations" )
	@Test
	public void testEntityRemoveMethod() {

		// @formatter:off
		instance.executeSource( """
			honda = entityLoadByPK( 'Manufacturer', 1 );

			hasVehiclePreRemove = honda.hasVehicle();
			myVehicle = honda.getVehicles().each( ( vehicle ) => {
				honda.removeVehicle( vehicle );
			})
			hasVehiclePostRemove = honda.hasVehicle();
			""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "hasVehiclePreRemove" ) ) ).isInstanceOf( Boolean.class );
		assertThat( variables.getAsBoolean( Key.of( "hasVehiclePreRemove" ) ) ).isTrue();

		assertThat( variables.get( Key.of( "hasVehiclePostRemove" ) ) ).isInstanceOf( Boolean.class );
		assertThat( variables.getAsBoolean( Key.of( "hasVehiclePostRemove" ) ) ).isFalse();
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

	@DisplayName( "It can load a subclass entity from the parent" )
	@Test
	public void testSubclassLoadThroughParent() {
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "cbContent", "779cc4e2-a444-11eb-ab6f-0290cc502ae3" ).getSlug();
			""",
			context
		);
		// @formatter:on
		assertEquals( "another-test", variables.get( result ) );
	}

	@DisplayName( "It can load a subclass entity from the parent" )
	@Test
	public void testSubClassLoadThroughChild() {
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				entry = entityLoadByPK( "cbEntry", "779cc4e2-a444-11eb-ab6f-0290cc502ae3" );
				result = entry.getSlug();
				hasContentVersion = entry.hasContentVersion();
			""",
			context
		);
		// @formatter:on
		assertEquals( "another-test", variables.get( result ) );
		assertEquals( true, variables.getAsBoolean( Key.of( "hasContentVersion" ) ) );
	}

	@DisplayName( "It can load an entity with a case-insensitive entity name" )
	@Test
	public void canLoadAnEntityCaseInsensitively() {
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				entry = entityLoadByPK( "cbentry", "779cc4e2-a444-11eb-ab6f-0290cc502ae3" );
				result = entry.getSlug();
			""",
			context
		);
		// @formatter:on
		assertEquals( "another-test", variables.get( result ) );
	}
}
