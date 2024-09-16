package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class EntityLoadByPKTest extends BaseORMTest {

	@DisplayName( "It can load an entity by integer key" )
	@Test
	public void testEntityLoadByPK() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "Developer", 1 ).getRole();
				result2 = queryExecute( "SELECT * FROM developers" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 3, variables.getAsQuery( Key.of( "result2" ) ).size() );
		assertEquals( "CEO", variables.get( result ) );
	}

	@Disabled( "Unimplemented." )
	@DisplayName( "It will add has* methods for associations" )
	@Test
	public void testEntityHasMethod() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );

		instance.executeStatement( "result = entityLoadByPK( 'Developer', 1 ).hasPermissions()", context );
		assertTrue( variables.get( result ) instanceof Boolean );
		assertFalse( variables.getAsBoolean( result ) );
	}

	@Disabled( "Unimplemented." )
	@DisplayName( "It will add add* methods for associations" )
	@Test
	public void testEntityAddMethod() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );

		// @formatter:off
		instance.executeSource( """
			developer = entityLoadByPK( 'Developer', 1 );
			developer.addPermission( entityLoadByPK( 'Permission', 1 ) );
			result = developer.hasPermissions();
			""", context );
		// @formatter:on

		assertTrue( variables.get( result ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( result ) );
	}

	@Disabled( "Unimplemented." )
	@DisplayName( "It will add remove* methods for associations" )
	@Test
	public void testEntityRemoveMethod() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );

		// @formatter:off
		instance.executeSource( """
			developer = entityLoadByPK( 'Developer', 1 );
			
			developer.addPermission( entityLoadByPK( 'Permission', 1 ) );
			result = developer.hasPermissions();

			developer.removePermission( entityLoadByPK( 'Permission', 1 ) );
			hasPermissionAfterRemove = developer.hasPermissions();
			""", context );
		// @formatter:on

		// has permission after add: yep!
		assertTrue( variables.get( result ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( result ) );

		// has permission after add: nope!
		assertTrue( variables.get( Key.of( "hasPermissionAfterRemove" ) ) instanceof Boolean );
		assertFalse( variables.getAsBoolean( Key.of( "hasPermissionAfterRemove" ) ) );
	}

	// @Disabled( "Lacking proper support for key types; aka, we've hardcoded integer types for now." )
	@DisplayName( "It can load an entity by varchar key" )
	@Test
	public void testEntityLoadByVarcharKey() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );
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
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );
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
