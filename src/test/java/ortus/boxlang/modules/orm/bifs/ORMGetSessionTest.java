package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class ORMGetSessionTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testORMGetSession() {
		Session session = ormService.getSessionForContext( context );
		assertNotNull( session );

		// @formatter:off
		instance.executeSource(
			"""
				result = ormGetSession();
			""",
			context
		);
		// @formatter:on
		assertNotNull( variables.get( result ) );
		assertEquals( session, variables.get( result ) );
	}
}