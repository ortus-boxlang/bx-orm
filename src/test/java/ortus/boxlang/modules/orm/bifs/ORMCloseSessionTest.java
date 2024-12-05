package ortus.boxlang.modules.orm.bifs;

import static org.junit.Assert.assertFalse;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class ORMCloseSessionTest extends BaseORMTest {

	@DisplayName( "It can close the session for the default datasource" )
	@Test
	public void testSessionClose() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		;
		// @formatter:off
		instance.executeSource(
			"""
				ormCloseSession();
			""",
			context
		);
		// @formatter:on

		assertFalse( session.isOpen() );
	}

	@DisplayName( "It can close the session on a named (alternate) datasource" )
	@Test
	public void testSessionCloseOnNamedDatasource() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession( Key.of( "dsn2" ) );
		// @formatter:off
		instance.executeSource(
			"""
				ormCloseSession( "dsn2" );
			""",
			context
		);
		// @formatter:on

		assertFalse( session.isOpen() );
	}

}
