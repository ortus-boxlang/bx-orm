package com.ortussolutions.interceptors;

import static org.junit.Assert.assertNotNull;

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
import ortus.boxlang.runtime.application.ApplicationListener;
import ortus.boxlang.runtime.application.ApplicationTemplateListener;
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
	void testInterceptor() {
		// Register the interceptor with the interceptor service
		ORMEngine ormEngine = ORMEngine.getInstance();
		interceptorService.register( new ApplicationLifecycle() );

		context = new ScriptingRequestBoxContext( runtime.getRuntimeContext() );
		// variables = context.getScopeNearby( VariablesScope.name );

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
		        "ormSettings", Struct.of( "database", "testDB" ),
		        "name", "MyAppName" ) );
		context.pushTemplate( template );
		// Announce the event the interceptor listens to
		interceptorService.announce(
		    Key.of( "afterApplicationListenerLoad" ),
		    Struct.of(
		        "listener", listener,
		        "context", context,
		        "template", template ) );

		// Assertions go here
		assertNotNull( ormEngine.getSessionFactoryForName( Key.of( "MyAppName" ) ) );

	}

}
