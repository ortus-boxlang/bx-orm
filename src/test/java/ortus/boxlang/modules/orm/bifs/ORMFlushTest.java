package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

@Disabled
public class ORMFlushTest {

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

	@DisplayName( "It can flush the session" )
	@Test
	public void testORMFlush() {
		// @formatter:off
		instance.executeSource(
			"""
				var manufacturer = entityNew( "Manufacturer" );
				manufacturer.setRole( "CEO" );
				entitySave( "manufacturer" );
				result = queryExecute( "SELECT * FROM manufacturers" );
				ormFlush();
				result = queryExecute( "SELECT * FROM manufacturers" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 3, variables.getAsQuery( result ).size() );
		assertEquals( 4, variables.getAsQuery( Key.of( "result2" ) ).size() );
		assertEquals( "CEO", variables.get( result ) );
	}

}
