package tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

public class BaseORMTest {

	public static BoxRuntime		instance;
	protected static ModuleRecord	moduleRecord;
	protected static Key			moduleName	= new Key( "bxorm" );
	public static Key				result		= Key.of( "result" );
	public static RequestBoxContext	startupContext;
	public RequestBoxContext		context;
	public IScope					variables;

	@BeforeAll
	public static void setUp() {
		System.out.println( "Running @BeforeAll" );
		instance		= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );

		// Load the module
		startupContext	= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		BaseORMTest.loadModule( startupContext );
	}

	@AfterAll
	public static void teardown() throws SQLException {
	}

	@BeforeEach
	public void setupEach() {
		System.out.println( "Running @BeforeEach" );
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext(), Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
		variables	= context.getScopeNearby( VariablesScope.name );
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );
	}

	@AfterEach
	public void teardownEach() {
		System.out.println( "Running @AfterEach" );
		context		= null;
		variables	= null;
	}

	protected static void loadModule( IBoxContext context ) {
		System.out.println( "Loading module" );
		String physicalPath = Paths.get( "./build/module" ).toAbsolutePath().toString();
		moduleRecord = new ModuleRecord( physicalPath );

		instance.getModuleService().getRegistry().put( moduleName, moduleRecord );

		moduleRecord
		    .loadDescriptor( context )
		    .register( context )
		    .activate( context );
	}
}
