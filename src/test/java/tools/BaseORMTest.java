package tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.loader.ImportDefinition;
import ortus.boxlang.runtime.runnables.BoxTemplate;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.DatasourceService;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.util.ResolvedFilePath;

public class BaseORMTest {

	public static BoxRuntime		instance;
	public RequestBoxContext		context;
	public IScope					variables;
	public static ORMService		ormService;
	public static Key				appName	= Key.of( "BXORMTest" );
	public static Key				result	= Key.of( "result" );

	static DataSource				datasource;
	static DatasourceService		datasourceService;
	static ApplicationBoxContext	applicationContext;

	static final Logger				log		= LoggerFactory.getLogger( BaseORMTest.class );

	@BeforeAll
	public static void setUp() {
		instance	= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		ormService	= ORMService.getInstance();
		RequestBoxContext startupContext = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		datasourceService	= instance.getDataSourceService();
		datasource			= JDBCTestUtils.constructTestDataSource( "TestDB1" );

		// Start up an application with ORM settings
		instance.executeSource(
		    """
		        application
		    		name="BXORMTest"
		    		datasources={
		    			"TestDB1" = {
		    				driver = "derby",
		    				database = "test",
		    				connectionString = "jdbc:derby:memory:TestDB1;create=true"
		    			}
		    		}
		    		ormEnabled = "true"
		    		ormsettings = {
		    			datasource = "TestDB1",
		    			entityPaths = ["models"],
		    			saveMapping = "true"
		    		};
		    """, startupContext );

		// then grab and store the application context
		applicationContext = startupContext.getParentOfType( ApplicationBoxContext.class );

		applicationContext.pushTemplate( new BoxTemplate() {

			@Override
			public void _invoke( IBoxContext context ) {
			}

			@Override
			public long getRunnableCompileVersion() {
				return 0;
			}

			@Override
			public LocalDateTime getRunnableCompiledOn() {
				return null;
			}

			@Override
			public Object getRunnableAST() {
				return null;
			}

			@Override
			public ResolvedFilePath getRunnablePath() {
				return ResolvedFilePath.of( Path.of( "/home/michael/repos/boxlang/modules/bx-orm/src/test/resources/app/Application.bx" ) );
			}

			public BoxSourceType getSourceType() {
				return BoxSourceType.BOXSCRIPT;
			}

			public List<ImportDefinition> getImports() {
				return null;
			}

		} );

		// and start up the ORM service
		IStruct ormSettings = ( IStruct ) startupContext.getConfigItems( Key.applicationSettings, ORMKeys.ORMSettings );
		ormService.setSessionFactoryForName( appName,
		    new SessionFactoryBuilder( startupContext, appName, ormSettings ).build() );
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
