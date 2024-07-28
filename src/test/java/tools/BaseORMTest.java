package tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.DatasourceService;
import ortus.boxlang.runtime.types.IStruct;

public class BaseORMTest {

	public static BoxRuntime		instance;
	public RequestBoxContext		context;
	public IScope					variables;
	public static ORMService		ormService;
	public static Key				ORMAppName	= Key.of( "BXORMTest" );
	public static Key				result		= Key.of( "result" );

	static DataSource				datasource;
	static DatasourceService		datasourceService;
	static ApplicationBoxContext	applicationContext;

	static final Logger				log			= LoggerFactory.getLogger( BaseORMTest.class );

	@BeforeAll
	public static void setUp() {
		instance	= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		ormService	= ORMService.getInstance();
		RequestBoxContext startupContext = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		datasourceService	= instance.getDataSourceService();
		datasource			= JDBCTestUtils.constructTestDataSource( "TestDB" );

		// Start up an application with ORM settings
		instance.executeSource(
		    """
		        application
		    		name="BXORMTest"
		    		datasources={
		    			"TestDB" = {
		    				driver = "derby",
		    				database = "test",
		    				connectionString = "jdbc:derby:memory:ORMTest;create=true"
		    			}
		    		}
		    		ormEnabled = "true"
		    		ormsettings = {
		    			datasource = "TestDB",
		    			cfcLocation = ["src/test/resources/app/models"]
		    		};
		    """, startupContext );

		// then grab and store the application context
		applicationContext = startupContext.getParentOfType( ApplicationBoxContext.class );

		// and start up the ORM service
		IStruct ormSettings = ( IStruct ) startupContext.getConfigItems( Key.applicationSettings, ORMKeys.ORMSettings );
		ormService.setSessionFactoryForName( ORMAppName,
		    new SessionFactoryBuilder( startupContext, ORMAppName, ormSettings ).build() );
	}

	public static void teardown() throws SQLException {
		// BaseJDBCTest.teardown();
		JDBCTestUtils.dropDevelopersTable( datasource );
		datasource.shutdown();
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		context.injectParentContext( applicationContext );

		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );

		variables = context.getScopeNearby( VariablesScope.name );
		context.getConnectionManager().setDefaultDatasource( datasource );
		assertDoesNotThrow( () -> JDBCTestUtils.resetDevelopersTable( datasource ) );
	}

}
