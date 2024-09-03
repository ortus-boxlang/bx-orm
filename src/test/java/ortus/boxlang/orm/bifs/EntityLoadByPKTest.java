package ortus.boxlang.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.ApplicationBoxContext;
import tools.BaseORMTest;

public class EntityLoadByPKTest extends BaseORMTest {

	@DisplayName( "It can load an entity by pk" )
	@Test
	public void testEntityLoadByPK() {
		assertNotNull( ormService.getSessionFactoryForName( BaseORMTest.appName ) );
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// @formatter:off
		instance.executeSource(
			"""
				developer = entityLoadByPK( "Developer", 1 );
				result = developer.getRole();
			""",
			context
		);
		// @formatter:on
		assertEquals( "CEO", variables.get( result ) );
	}

}
