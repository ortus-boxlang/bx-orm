package ortus.boxlang.modules.orm;

public class ORMServiceTest {

	// static BoxRuntime instance;
	// IBoxContext context;
	// IScope variables;
	// static Key result = new Key( "result" );

	// @BeforeAll
	// public static void setUp() {
	// instance = BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
	// }

	// @BeforeEach
	// public void setupEach() {
	// context = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
	// variables = context.getScopeNearby( VariablesScope.name );
	// }

	// @Test
	// public void testAppStartupAndShutdown() {
	// Key appName = BaseORMTest.appName;
	// ORMService ormService = ORMService.getInstance();
	// assertNull( ormService.getSessionFactoryForName( appName ) );

	// // ensure we have a test datasource in the context connection manager
	// Key testDatasourceName = Key.of( "foo" );
	// ConnectionManager connectionManager = ( ( IJDBCCapableContext ) context ).getConnectionManager();
	// connectionManager.register( testDatasourceName, Struct.of(
	// "database", testDatasourceName.getName(),
	// "driver", "derby",
	// "connectionString", "jdbc:derby:memory:" + testDatasourceName.getName() + ";create=true"
	// ) );

	// // ensure we have an application context in the context stack
	// BaseORMTest.setupApplicationContext( ( RequestBoxContext ) context );

	// ormService.startupApp( ( RequestBoxContext ) context, new ORMConfig( Struct.of(
	// "datasource", testDatasourceName.getName()
	// ) ) );
	// SessionFactory theFactory = ormService.getORMApp( context ).getSessionFactoryOrThrow( connectionManager.getDatasource( testDatasourceName ) );
	// assertNotNull( theFactory );

	// ormService.shutdownApp( context );
	// assertTrue( theFactory.isClosed() );
	// }

}
