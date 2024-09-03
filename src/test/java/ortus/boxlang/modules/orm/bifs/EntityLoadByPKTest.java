package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

	// @Disabled( "Lacking proper support for key types; aka, we've hardcoded integer types for now." )
	@DisplayName( "It can load an entity by varchar key" )
	@Test
	public void testEntityLoadByVarcharKey() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				result = entityLoadByPK( "Auto", "1HGCM82633A123456" ).getMake();
			""",
			context
		);
		// @formatter:on
		assertEquals( "Honda", variables.get( result ) );
	}
}
