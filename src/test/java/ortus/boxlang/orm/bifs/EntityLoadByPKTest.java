package ortus.boxlang.orm.bifs;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.orm.ORMService;
import ortus.boxlang.orm.interceptors.ApplicationLifecycle;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.ApplicationListener;
import ortus.boxlang.runtime.application.ApplicationTemplateListener;
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
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;
import tools.JDBCTestUtils;

public class EntityLoadByPKTest {

	static BoxRuntime			instance;
	ScriptingRequestBoxContext	context;
	IScope						variables;
	static Key					result	= new Key( "result" );
	static InterceptorService	interceptorService;
	static ORMService			ormService;

	static DataSource			datasource;
	static DatasourceService	datasourceService;

	@BeforeAll
	public static void setUp() {
		instance			= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		ormService			= ORMService.getInstance();
		interceptorService	= instance.getInterceptorService();
		interceptorService.register( new ApplicationLifecycle() );
		datasourceService	= instance.getDataSourceService();
		datasource			= JDBCTestUtils.constructTestDataSource( "TestDB" );
	}

	public static void teardown() throws SQLException {
		// BaseJDBCTest.teardown();
		JDBCTestUtils.dropDevelopersTable( datasource );
		datasource.shutdown();
	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );
		context.getConnectionManager().setDefaultDatasource( datasource );
		assertDoesNotThrow( () -> JDBCTestUtils.resetDevelopersTable( datasource ) );
	}

	@DisplayName( "It can load an entity by pk" )
	@Test
	public void testEntityLoadByPK() {
		assertNull( ormService.getSessionFactoryForName( Key.of( "MyAppName" ) ) );

		BoxTemplate			template	= new BoxTemplate() {

											@Override
											public List<ImportDefinition> getImports() {
												return null;
											}

											@Override
											public void _invoke( IBoxContext context ) {
											}

											@Override
											public long getRunnableCompileVersion() {
												return 1;
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
											public Path getRunnablePath() {
												return Path.of( "src/test/resources/app/Application.bx" );
											}

											public BoxSourceType getSourceType() {
												return BoxSourceType.BOXSCRIPT;
											}

										};
		ApplicationListener	listener	= new ApplicationTemplateListener( template, ( RequestBoxContext ) context );
		listener.updateSettings(
		    Struct.of(
		        "ormEnabled", true,
		        "ormSettings", Struct.of(
		            "datasource", "TestDB",
		            "cfcLocation", Array.of( "models" )
		        ),
		        "datasources", Struct.of(
		            "TestDB", Struct.of(
		                "driver", "derby",
		                "properties", Struct.of(
		                    "connectionString", "jdbc:derby:memory:TestDB;create=true" ) ) ),
		        "name", "MyAppName" ) );
		context.pushTemplate( template );
		// Announce the event the interceptor listens to
		interceptorService.announce(
		    Key.of( "afterApplicationListenerLoad" ),
		    Struct.of(
		        "listener", listener,
		        "context", context,
		        "template", template ) );

		assertNotNull( ormService.getSessionFactoryForName( Key.of( "MyAppName" ) ) );

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
