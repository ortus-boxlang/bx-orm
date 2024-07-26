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
		assertNotNull( ormService.getSessionFactoryForName( ORMAppName ) );
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		// Session session = ORMEngine.getInstance().getSessionFactoryForName( Key.of( "MyAppName" ) ).openSession();
		// Transaction transaction = session.beginTransaction();

		// @formatter:off
		instance.executeSource(
			"""
				developer = entityLoadByPK( "Developer", 1 );
				result = developer.getRole();
			""",
			context
		);
// @formatter:on

		// transaction.commit();
		// session.close();
		assertEquals( "CEO", variables.get( result ) );
	}

}
