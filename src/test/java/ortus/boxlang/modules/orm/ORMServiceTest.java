package ortus.boxlang.modules.orm;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Struct;
import tools.BaseORMTest;

public class ORMServiceTest {

	static BoxRuntime	instance;
	IBoxContext			context;
	IScope				variables;
	static Key			result	= new Key( "result" );

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );
	}

	@Test
	public void testAppStartup() {
		Key			appName		= Key.of( "testApp" );
		ORMService	ormService	= ORMService.getInstance();
		assertNull( ormService.getSessionFactoryForName( appName ) );

		// ensure we have a test datasource in the context connection manager
		Key					testDatasourceName	= Key.of( "foo" );
		ConnectionManager	connectionManager	= ( ( IJDBCCapableContext ) context ).getConnectionManager();
		connectionManager.register( testDatasourceName, Struct.of(
		    "database", testDatasourceName.getName(),
		    "driver", "derby",
		    "connectionString", "jdbc:derby:memory:" + testDatasourceName.getName() + ";create=true"
		) );

		// ensure we have an application context in the context stack
		BaseORMTest.setupApplicationContext( ( RequestBoxContext ) context );

		ormService.startupApp( ( RequestBoxContext ) context, appName, new ORMConfig( Struct.of(
		    "datasource", testDatasourceName.getName()
		) ) );
		assertNotNull( ormService.getSessionFactoryForNameAndDataSource( appName, connectionManager.getDatasource( testDatasourceName ) ) );
	}

}
