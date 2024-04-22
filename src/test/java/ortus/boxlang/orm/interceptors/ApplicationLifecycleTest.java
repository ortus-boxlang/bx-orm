package ortus.boxlang.orm.interceptors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.orm.ORMService;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.application.ApplicationListener;
import ortus.boxlang.runtime.application.ApplicationTemplateListener;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.loader.ImportDefinition;
import ortus.boxlang.runtime.runnables.BoxTemplate;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;

public class ApplicationLifecycleTest {

	static BoxRuntime			instance;
	static ORMService			ormService;
	static InterceptorService	interceptorService;
	IBoxContext					context;
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
		            "cfcLocation", Array.of( "models" ),
		            "datasource", "testDB"
		        ),
		        "datasources", Struct.of(
		            "testDB", Struct.of(
		                "driver", "derby",
		                "properties", Struct.of(
		                    "connectionString", "jdbc:derby:memory:myDB;create=true" ) ) ),
		        "name", "ApplicationLifecycleTest" ) );
		context.pushTemplate( template );
		// Announce the event the interceptor listens to
		interceptorService.announce(
		    Key.of( "afterApplicationListenerLoad" ),
		    Struct.of(
		        "listener", listener,
		        "context", context ) );

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
		              ormSettings='{ cfcLocation: ["models/"], datasource:"testDB" }'
		              ;
		    """,
		    context );

		Application targetApp = context.getParentOfType( ApplicationBoxContext.class ).getApplication();
		assertTrue( targetApp.hasStarted() );

		assertNotNull( ormService.getSessionFactoryForName( Key.of( "ApplicationLifecycleTest2" ) ) );
	}

}
