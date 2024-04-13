package com.ortussolutions.interceptors;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ortussolutions.ORMEngine;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.types.Struct;

public class ApplicationLifecycleTest {

	static BoxRuntime			runtime;
	static InterceptorService	interceptorService;
	IBoxContext					context;
	IScope						variables;
	static Key					result	= new Key( "result" );

	@BeforeAll
	public static void setUp() {
		runtime				= BoxRuntime.getInstance( true );
		interceptorService	= runtime.getInterceptorService();
	}

	@BeforeEach
	public void setupEach() {
	}

	@DisplayName( "Test my interceptor" )
	@Test
	public void testInterceptor() {
		// Register the interceptor with the interceptor service
		ORMEngine ormEngine = new ORMEngine( runtime );
		interceptorService.register(
		    new ApplicationLifecycle()
		);

		context		= new ScriptingRequestBoxContext( runtime.getRuntimeContext(), Paths.get( "resources/app/Application.bx" ).toUri() );
		variables	= context.getScopeNearby( VariablesScope.name );

		assertEquals( ormEngine.getSessionFactoryForName( Key.of( "MyAppName" ) ) );
		// Announce the event the interceptor listens to
		// interceptorService.announce(
		// Key.of( "afterApplicationListenerLoad" ),
		// Struct.of(
		// "listener", listener,
		// "context", context,
		// "template", template
		// )
		// );

		// Assertions go here

	}

}
