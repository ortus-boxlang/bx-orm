package ortus.boxlang.modules.orm.bifs;

import static org.junit.Assert.assertFalse;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMService;
import tools.BaseORMTest;

public class ORMCloseSessionTest extends BaseORMTest {

	@DisplayName( "It can close the session for the default datasource" )
	@Test
	public void testSessionClose() {
		Session session = ORMService.getInstance().getORMApp( context ).getSession( context );
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
		Session session = ORMService.getInstance().getORMApp( context ).getSession( context, alternateDataSource.getConfiguration().name );
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
