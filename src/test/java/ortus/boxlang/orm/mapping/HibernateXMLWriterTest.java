package ortus.boxlang.orm.mapping;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.runnables.IBoxRunnable;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

public class HibernateXMLWriterTest {

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

	@DisplayName( "It generates the entity-name" )
	@Test
	public void testMapping() {

		// @formatter:off
		Class<IBoxRunnable> bxClass = RunnableLoader.getInstance().loadClass(
			"""
				@Entity "Car"
				class {
					@id
					property name="id";
				}
			""",
			context,
			BoxSourceType.BOXSCRIPT
		);
		// @formatter:on

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) DynamicObject.of( bxClass ).invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "entity-name" ).getTextContent() ).isEqualTo( "Car" );
	}

}
