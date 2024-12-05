package tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import ortus.boxlang.modules.orm.config.ORMKeys;
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
	public static RequestBoxContext	context;
	public IScope					variables;

	@BeforeAll
	public static void setUp() {
		System.out.println( "Running @BeforeAll" );
		instance	= BoxRuntime.getInstance( true );

		// Load the module
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		loadModule( context );
		context.loadApplicationDescriptor( Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
	}

	@AfterAll
	public static void teardown() throws SQLException {
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMTest" ) );
		instance.removeGlobalService( ORMKeys.ORMService );
		instance.getModuleService().unload( moduleName );
	}

	@BeforeEach
	public void setupEach() {
		System.out.println( "Running @BeforeEach" );
		// context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
		variables = context.getScopeNearby( VariablesScope.name );
		assertNotNull( context.getParentOfType( ApplicationBoxContext.class ) );
	}

	protected static void loadModule( IBoxContext context ) {
		System.out.println( "Loading module" );
		if ( !instance.getModuleService().hasModule( moduleName ) ) {
			String physicalPath = Paths.get( "./build/module" ).toAbsolutePath().toString();
			moduleRecord = new ModuleRecord( physicalPath );

			instance.getModuleService().getRegistry().put( moduleName, moduleRecord );

			moduleRecord
			    .loadDescriptor( context )
			    .register( context )
			    .activate( context );
		}
	}
}
