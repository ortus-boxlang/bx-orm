package tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
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
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.util.ResolvedFilePath;

public class BaseORMTest {

	public static BoxRuntime		instance;
	public static RequestBoxContext	startupContext;
	public RequestBoxContext		context;
	public IScope					variables;
	public static ORMService		ormService;
	public static Key				appName	= Key.of( "BXORMTest" );
	public static Key				result	= Key.of( "result" );

	static DataSource				datasource;

	static final Logger				log		= LoggerFactory.getLogger( BaseORMTest.class );

	@BeforeAll
	public static void setUp() {
		instance		= BoxRuntime.getInstance( true );
		ormService		= ORMService.getInstance();
		startupContext	= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		datasource		= JDBCTestUtils.constructTestDataSource( "TestDB" );

		BaseORMTest.setupApplicationContext( startupContext );

		// and start up the ORM service
		IStruct ormSettings = Struct.of(
		    "datasource", "TestDB",
		    "entityPaths", Array.of( "models" ),
		    "saveMapping", "true",
		    "logSQL", "true",
		    "dialect", "DerbyTenSevenDialect"
		);
		ormService.setSessionFactoryForName( appName,
		    new SessionFactoryBuilder( startupContext, appName, ormSettings ).build() );
	}

	@AfterAll
	public static void teardown() throws SQLException {
		// BaseJDBCTest.teardown();
		JDBCTestUtils.cleanupTables( datasource );
		datasource.shutdown();
	}

	/**
	 * Static helper method for setting up the application context.
	 * <p>
	 * It's very important that we
	 * <ol>
	 * <li>use the application BX component to begin an application, and
	 * <li>push a template to the template stack to localize entity filepath lookups.
	 * </ol>
	 */
	public static void setupApplicationContext( RequestBoxContext startupContext ) {
		// Start up an application with ORM settings
		startupContext.getRuntime().executeSource(
		    """
		        application
		    		name="BXORMTest"
		    		datasources={
		    			"TestDB" = {
		    				driver = "derby",
		    				database = "test",
		    				connectionString = "jdbc:derby:memory:TestDB;create=true"
		    			}
		    		}
		    		ormEnabled = "true";
		    """, startupContext );

		startupContext
		    .getParentOfType( ApplicationBoxContext.class )
		    .pushTemplate( new BoxTemplate() {

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
				    return ResolvedFilePath.of( Path.of( "src/test/resources/app/Application.bx" ).toAbsolutePath() );
			    }

			    public BoxSourceType getSourceType() {
				    return BoxSourceType.BOXSCRIPT;
			    }

			    public List<ImportDefinition> getImports() {
				    return null;
			    }

		    } );
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		assertNotNull( startupContext.getParentOfType( ApplicationBoxContext.class ) );
		context.injectParentContext( startupContext.getParentOfType( ApplicationBoxContext.class ) );

		variables = context.getScopeNearby( VariablesScope.name );
		context.getConnectionManager().setDefaultDatasource( datasource );
		assertDoesNotThrow( () -> JDBCTestUtils.resetTables( datasource ) );
	}
}
