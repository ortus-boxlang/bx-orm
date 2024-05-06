package ortus.boxlang.orm.mapping;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.modules.orm.mapping.HibernateXMLWriter;
import ortus.boxlang.modules.orm.mapping.ORMAnnotationInspector;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
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

	@DisplayName( "It generates the entity-name from the class name" )
	@Test
	public void testMapping() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Developer2" );
	}

	@DisplayName( "It generates the table name from the class name" )
	@Test
	public void testTableNameFromFile() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "Developer2" );
	}

	@DisplayName( "It generates the entity-name from the annotation" )
	@Test
	public void testEntityNameValue() {
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

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Car" );
	}

	@DisplayName( "It generates the table name from the annotation" )
	@Test
	public void testEntityTableNameValue() {
		// @formatter:off
		Class<IBoxRunnable> bxClass = RunnableLoader.getInstance().loadClass(
			"""
				@Entity "Car"
				@Table "cars"
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

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "cars" );
	}

	@DisplayName( "It generates an id element from the Id annotation" )
	@Test
	public void testIDAnnotation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "theId" );
	}

	@DisplayName( "It sets the type of the id proprety via an annotation" )
	@Test
	public void testIDTypeAnnotation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "integer" );
	}

	@DisplayName( "It sets the default generator of the id proprety as increment" )
	@Test
	public void testIDGeneratorAnnotation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 0 ).getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "class" ).getTextContent() )
		    .isEqualTo( "increment" );
	}

	@DisplayName( "It sets the default column of the id proprety to the name of the property" )
	@Test
	public void testIDColumnAnnotation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "theId" );
	}

	@DisplayName( "It generates property element for a property" )
	@Test
	public void testProperty() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 1 );

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "name" );
	}

	@DisplayName( "It sets the type of the proprety via an annotation" )
	@Test
	public void testPropertyTypeAnnotation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 1 );

		assertThat( node.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
	}

	@DisplayName( "It sets the column of the proprety to the name of the property" )
	@Test
	public void testPropertyColumnAnnotation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 1 );

		assertThat( node.getAttributes().getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "the_name" );
	}

	@DisplayName( "It does not map properties annotated with @Persistent false" )
	@Test
	public void testPersistentFalseAnnoatation() {
		DynamicObject			bxClass		= ClassLocator.getInstance().load( context, "src.test.resources.app.models.Developer2", "bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector(
		    ( IClassRunnable ) bxClass.invokeConstructor( context ).getTargetInstance() );
		Document				doc			= new HibernateXMLWriter().generateXML( inspector );

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 2 );

		assertThat( node )
		    .isEqualTo( null );
	}

}
