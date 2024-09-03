package ortus.boxlang.modules.orm;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import tools.BaseORMTest;
import tools.JDBCTestUtils;

public class SessionFactoryBuilderTest {

	static BoxRuntime			instance;
	static Key					appName	= Key.of( "BXORMTest" );
	static Key					result	= Key.of( "result" );
	static DataSource			datasource;
	static RequestBoxContext	startupContext;

	@BeforeAll
	public static void setUp() {
		instance		= BoxRuntime.getInstance( true );
		datasource		= JDBCTestUtils.constructTestDataSource( "TestDB1" );

		startupContext	= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		BaseORMTest.setupApplicationContext( startupContext );
	}

	@BeforeEach
	public void setupEach() {
		// startupContext = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		// assertNotNull( startupContext.getParentOfType( ApplicationBoxContext.class ) );
		// startupContext.injectParentContext( startupContext.getParentOfType( ApplicationBoxContext.class ) );
	}

	@DisplayName( "It can setup a session factory" )
	@Test
	public void testSimpleCase() {
		IStruct			ormSettings		= Struct.of(
		    ORMKeys.datasource, "TestDB1"
		);
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( startupContext, appName, ormSettings ).build();

		assertNotNull( sessionFactory );
		assertEquals( sessionFactory.getSessionFactoryOptions().getSessionFactoryName(), appName.toString() );
	}

	@Disabled( "Unimplemented" )
	@DisplayName( "It can set dialects with alias names" )
	@Test
	public void testDerbyShortNameDialect() {
		IStruct			ormSettings		= Struct.of(
		    ORMKeys.datasource, "TestDB1",
		    ORMKeys.dialect, "DerbyTenSevenDialect"
		);
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( startupContext, appName, ormSettings ).build();

		assertNotNull( sessionFactory );
		assertEquals( sessionFactory.getSessionFactoryOptions().getSessionFactoryName(), appName.toString() );
	}
}
