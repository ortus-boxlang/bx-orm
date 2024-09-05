package ortus.boxlang.modules.orm;

import static org.junit.Assert.assertNotNull;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
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
	static RequestBoxContext	context;

	@BeforeAll
	public static void setUp() {
		instance	= BoxRuntime.getInstance( true );
		datasource	= JDBCTestUtils.constructTestDataSource( "TestDB1" );

		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		BaseORMTest.setupApplicationContext( context );
	}

	@DisplayName( "It can setup a session factory" )
	@Test
	public void testSimpleCase() {
		IStruct			ormSettings		= Struct.of(
		    ORMKeys.datasource, "TestDB1"
		);
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, appName, ormSettings ).build();

		assertNotNull( sessionFactory );
	}

	@DisplayName( "It can set dialects with alias names" )
	@Test
	public void testDerbyShortNameDialect() {
		IStruct			ormSettings		= Struct.of(
		    ORMKeys.datasource, "TestDB1",
		    ORMKeys.dialect, "DerbyTenSeven"
		);
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, appName, ormSettings ).build();

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
		    			connectionString = "jdbc:derby:memory:TestDB1;create=true"
		    		};
		    """, context );
		IStruct			ormSettings		= Struct.of();
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, appName, ormSettings ).build();

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
		IStruct			ormSettings		= Struct.of(
		    ORMKeys.datasource, "TestDB2"
		);
		SessionFactory	sessionFactory	= new SessionFactoryBuilder( context, appName, ormSettings ).build();

		assertNotNull( sessionFactory );
	}
}
