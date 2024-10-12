package ortus.boxlang.modules.orm;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;
import tools.BaseORMTest;
import tools.JDBCTestUtils;

public class SessionFactoryBuilderTest {

	static BoxRuntime			instance;
	static Key					appName	= Key.of( "BXORMTest" );
	static Key					result	= Key.of( "result" );
	static DataSource			datasource;
	static RequestBoxContext	context;

	@BeforeAll
	public static void setUp() {
		instance	= BoxRuntime.getInstance( true );
		datasource	= JDBCTestUtils.constructTestDataSource( "TestDB" );

		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		BaseORMTest.setupApplicationContext( context );
	}

	@DisplayName( "It can setup a session factory" )
	@Test
	public void testSimpleCase() {
		ORMConfig		config			= new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB"
		) );
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, datasource, config ).build();

		assertNotNull( sessionFactory );
	}

	@DisplayName( "It can set dialects with alias names" )
	@Test
	public void testDerbyShortNameDialect() {
		ORMConfig		config			= new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.dialect, "DerbyTenSeven"
		) );
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, datasource, config ).build();

		assertNotNull( sessionFactory );
	}

	@DisplayName( "It can use the default application datasource" )
	@Test
	public void testApplicationDefaultDatasource() {
		context.getRuntime().executeSource(
		    """
		        application
		    		name="BXORMTest"
		    		datasource={
		    			driver = "derby",
		    			database = "test",
		    			connectionString = "jdbc:derby:memory:TestDB;create=true"
		    		};
		    """, context );
		ORMConfig		config			= new ORMConfig( Struct.of() );
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, datasource, config ).build();

		assertNotNull( sessionFactory );
	}

	@DisplayName( "It can use a named application datasource" )
	@Test
	public void testApplicationNamedDatasource() {
		context.getRuntime().executeSource(
		    """
		        application
		    		name="BXORMTest"
		    		datasources={
		    			"TestDB2" = {
		    				driver = "derby",
		    				database = "test",
		    				connectionString = "jdbc:derby:memory:TestDB2;create=true"
		    			}
		    		};
		    """, context );
		ORMConfig		config			= new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB2"
		) );
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, datasource, config ).build();

		assertNotNull( sessionFactory );
	}

	@DisplayName( "It can generate a unique key" )
	@Test
	public void testUniqueKey() {
		Key key = SessionFactoryBuilder.getUniqueName( context, datasource );

		assertNotNull( key );
		assertEquals( "BXORMTest_" + datasource.getUniqueName().getName(), key.getName() );
	}
}
