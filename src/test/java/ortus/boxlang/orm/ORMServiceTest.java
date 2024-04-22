package ortus.boxlang.orm;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.IStruct;

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

	@Disabled( "Not implemented." )
	@DisplayName( "It can start up, register a runtime-wide session factory, and shut down." )
	@Test
	public void testRuntimeSessionFactoryLifeCycle() {
		ORMService	ormService	= ORMService.getInstance();
		IStruct		ORMSettings	= ( IStruct ) context.getConfigItem( ORMKeys.ORMSettings );
		assertNotNull( ORMSettings );

		ormService.setSessionFactoryForName( Key.runtime,
		    new SessionFactoryBuilder( ( IJDBCCapableContext ) context, Key.runtime, ORMSettings ).build() );
		SessionFactory sessionFactory = ormService.getSessionFactoryForName( Key.runtime );

		ormService.onShutdown( true );
		assertTrue( sessionFactory.isClosed() );
	}

}
