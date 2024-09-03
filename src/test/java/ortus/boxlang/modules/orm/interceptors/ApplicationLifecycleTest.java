package ortus.boxlang.modules.orm.interceptors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.application.BaseApplicationListener;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;

public class ApplicationLifecycleTest {

	static BoxRuntime			instance;
	static ORMService			ormService;
	static InterceptorService	interceptorService;
	RequestBoxContext			context;
	IScope						variables;
	static Key					result	= new Key( "result" );

	@BeforeAll
	public static void setUp() {
		instance			= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		ormService			= ORMService.getInstance();
		interceptorService	= instance.getInterceptorService();
		interceptorService.register( new ApplicationLifecycle() );
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
	}

	// @TODO: Clean this crazy-ugly test!
	@DisplayName( "Test my interceptor" )
	@Test
	void testApplicationStartupListener() {
		assertNull( ormService.getSessionFactoryForName( Key.of( "ApplicationLifecycleTest" ) ) );

		context.loadApplicationDescriptor( URI.create( "src/test/resources/app/Application.bx" ) );
		BaseApplicationListener listener = context.getApplicationListener();
		listener.updateSettings(
		    Struct.of(
		        "ormEnabled", true,
		        "ormSettings", Struct.of(
		            "datasource", "TestDB",
		            "entityPaths", Array.of( "models" )
		        ),
		        "datasources", Struct.of(
		            "TestDB", Struct.of(
		                "driver", "derby",
		                "properties", Struct.of(
		                    "connectionString", "jdbc:derby:memory:TestDB;create=true" ) ) ),
		        "name", "MyAppName" ) );
		// Announce the event the interceptor listens to
		interceptorService.announce(
		    Key.of( "afterApplicationListenerLoad" ),
		    Struct.of(
		        "listener", listener,
		        "context", context,
		        "template", null ) );

		assertNotNull( ormService.getSessionFactoryForName( Key.of( "ApplicationLifecycleTest" ) ) );

	}

	@Disabled( "Need to fix BL core to announce afterApplicationListenerLoad" )
	@DisplayName( "It creates a SessionFactory on application startup" )
	@Test
	void testItStartsOnApplicationStart() {
		assertNull( ormService.getSessionFactoryForName( Key.of( "ApplicationLifecycleTest2" ) ) );

		// @TODO: Find a better way to test this, because the application tag does not start an application (and thus doesn't fire the event)... it merely
		// updates the existing application settings... which fails to trigger any sort of interception event which we can listen for.
		instance.executeSource(
		    """
		    application
		              name="ApplicationLifecycleTest2"
		              ormEnabled="true"
		              ormSettings='{ entityPaths: ["models/"], datasource:"testDB" }'
		              ;
		    """,
		    context );

		Application targetApp = context.getParentOfType( ApplicationBoxContext.class ).getApplication();
		assertTrue( targetApp.hasStarted() );

		assertNotNull( ormService.getSessionFactoryForName( Key.of( "ApplicationLifecycleTest2" ) ) );
	}

}
