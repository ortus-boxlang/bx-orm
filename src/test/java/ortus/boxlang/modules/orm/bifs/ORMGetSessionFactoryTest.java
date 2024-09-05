package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class ORMGetSessionFactoryTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testORMGetSessionFactory() {
		SessionFactory sessionFactory = ormService.getSessionFactoryForName( BaseORMTest.appName );
		assertNotNull( sessionFactory );

		// @formatter:off
		instance.executeSource(
			"""
				result = ormGetSessionFactory();
			""",
			context
		);
		// @formatter:on
		assertNotNull( variables.get( result ) );
		assertEquals( sessionFactory, variables.get( result ) );
	}
}