package com.ortussolutions.bifs;

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

// TODO implement test
@Disabled
public class EntityLoadTest {

	static BoxRuntime instance;
	IBoxContext context;
	IScope variables;
	static Key result = new Key("result");

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance(true, Path.of("src/test/resources/boxlang.json").toString());
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext(instance.getRuntimeContext());
		variables = context.getScopeNearby(VariablesScope.name);
	}

	@DisplayName("It can test the ExampleBIF")
	@Test
	public void testExampleBIF() {
		instance.executeSource("result = ORMFlush()", context);
		assertEquals("Hello from an ORMFlush!", variables.get(result));
	}

	@DisplayName("It can test the ExampleBIF")
	@Test
	public void testTestBIF() {
		instance.executeSource("result = ORMTestBIF()", context);
		assertEquals("Hello from an ORMTestBIF!", variables.get(result));
	}

}
