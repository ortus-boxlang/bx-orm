package com.ortussolutions.interceptors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ortussolutions.ORMEngine;

import ortus.boxlang.compiler.parser.BoxSourceType;
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
import ortus.boxlang.runtime.types.Struct;

public class ApplicationLifecycleTest {

	static BoxRuntime			instance;
	static ORMEngine			ormEngine;
	static InterceptorService	interceptorService;
	IBoxContext					context;
	IScope						variables;
	static Key					result	= new Key( "result" );

	@BeforeAll
	public static void setUp() {
		instance			= BoxRuntime.getInstance( true );
		ormEngine			= ORMEngine.getInstance();
		interceptorService	= instance.getInterceptorService();
		interceptorService.register( new ApplicationLifecycle() );
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
	}

	// @Disabled("Can't get this working. Need to revisit.")
	@DisplayName( "Test my interceptor" )
	@Test
	void testApplicationStartupListener() {
		assertNull( ormEngine.getSessionFactoryForName( Key.of( "MyAppName" ) ) );

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
		        "ormSettings", Struct.of( "datasource", "testDB" ),
		        "datasources", Struct.of(
		            "testDB", Struct.of(
		                "driver", "derby",
		                "connectionString", "jdbc:derby:memory:myDB;create=true" ) ),
		        "name", "MyAppName" ) );
		context.pushTemplate( template );
		// Announce the event the interceptor listens to
		interceptorService.announce(
		    Key.of( "afterApplicationListenerLoad" ),
		    Struct.of(
		        "listener", listener,
		        "context", context,
		        "template", template ) );

		assertNotNull( ormEngine.getSessionFactoryForName( Key.of( "MyAppName" ) ) );

	}

	@DisplayName( "It creates a SessionFactory on application startup" )
	@Test
	void testItStartsOnApplicationStart() {
		assertNull( ormEngine.getSessionFactoryForName( Key.of( "MyAppName" ) ) );
		instance.executeSource(
		    """
		    application name="MyAppName" ormEnabled=true ormSettings={ datasource:"testDB" };
		       """,
		    context );

		Application targetApp = context.getParentOfType( ApplicationBoxContext.class ).getApplication();
		assertTrue( targetApp.hasStarted() );

		assertNotNull( ormEngine.getSessionFactoryForName( Key.of( "MyAppName" ) ) );
	}

}
