package ortus.boxlang.orm.mapping;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.BoxScriptParser;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.modules.orm.mapping.HibernateXMLWriter;
import ortus.boxlang.modules.orm.mapping.ORMAnnotationInspector;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ParseException;

public class HibernateXMLWriterTest {

	static BoxRuntime			instance;
	IBoxContext					context;
	IScope						variables;
	static Key					result	= new Key( "result" );

	private static final Logger	logger	= LoggerFactory.getLogger( HibernateXMLWriterTest.class );

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
		IStruct					entityMeta	= getClassMetaFromFile( "src/test/resources/app/models/Developer.bx" );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Developer" );
	}

	@DisplayName( "It can set a table name" )
	@Test
	public void testTableNameFromAnnotation() {
		IStruct					entityMeta	= getClassMetaFromCode(
		    """
		    	class table="developers" {
		    		property name="the_id" fieldtype="id";
		    		property name="the_name";
		    	}
		    """
		);

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "developers" );
	}

	@DisplayName( "It generates the entity-name from the annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    // CFML style
	    "class entityName=\"Car\"{ property name=\"the_id\" fieldtype=\"id\"; }",
	    // JPA style
	    "@Entity \"Car\" class{ @Id property name=\"the_id\"; }"
	} )
	public void testEntityNameValue( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		String					xml			= xmlToString( doc );

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Car" );
	}

	@DisplayName( "It sets the entity table name from the table annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    // CFML style
	    "class table=\"cars\"{ property name=\"the_id\" fieldtype=\"id\"; }",
	    // JPA style
	    "@Entity @Table \"cars\" class{ @Id property name=\"the_id\"; }",
	    // test default table name
	    "@Entity \"cars\" class{ @Id property name=\"the_id\"; }"
	} )
	public void testEntityTableNameValue( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		assertThat( doc.getDocumentElement().getChildNodes().item( 0 ).getAttributes().getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "cars" );
	}

	@DisplayName( "It generates an id element from the Id annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    "class{ property name=\"the_id\" fieldtype=\"id\"; }",
	    "class{ @Id property name=\"the_id\"; }"
	} )
	public void testIDAnnotation( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_id" );
	}

	@DisplayName( "It sets the type of the id property via an annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    "class { property name=\"the_id\" fieldtype=\"id\" ormtype=\"integer\"; }",
	    "class { @ORMType \"integer\" property name=\"the_id\" fieldtype=\"id\"; }"
	// "class { @ORMType integer property name=\"the_id\" fieldtype=\"id\"; }"
	} )
	public void testIDTypeAnnotation( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					classEl		= doc.getDocumentElement().getChildNodes().item( 0 );
		Node					node		= classEl.getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "integer" );
	}

	@DisplayName( "It can set an id generator" )
	@Test
	public void testIDGeneratorAnnotation() {
		IStruct					entityMeta	= getClassMetaFromCode(
		    """
		    	class table="developers" {
		    		property name="the_id" fieldtype="id" generator="increment";
		    		property name="the_name";
		    	}
		    """
		);

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 0 ).getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "class" ).getTextContent() )
		    .isEqualTo( "increment" );
	}

	@DisplayName( "It generates property element for a property" )
	@Test
	public void testProperty() {
		IStruct					entityMeta	= getClassMetaFromCode(
		    """
		    	class table="developers" {
		    		property name="the_id" fieldtype="id";
		    		property name="the_name";
		    	}
		    """
		);

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 1 );

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_name" );
	}

	@DisplayName( "It sets the type of the property via an annotation" )
	@Test
	public void testPropertyTypeAnnotation() {
		IStruct					entityMeta	= getClassMetaFromCode(
		    """
		    	class table="developers" {
		    		property name="the_id" fieldtype="id";
		    		property name="the_name";
		    	}
		    """
		);

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 1 );

		assertThat( node.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
	}

	@DisplayName( "It sets the column of the property to the name of the property" )
	@Test
	public void testPropertyColumnAnnotation() {
		IStruct					entityMeta	= getClassMetaFromCode(
		    """
		    	class table="developers" {
		    		property name="id" fieldtype="id";
		    		property name="the_name";
		    	}
		    """
		);

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					node		= doc.getDocumentElement().getChildNodes().item( 0 ).getChildNodes().item( 1 );

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_name" );
	}

	@DisplayName( "It does not map properties annotated with @Persistent false" )
	@ValueSource( strings = {
	    "class { property name=\"name\"; property name=\"notMapped\" persistent=\"false\"; }",
	    "class { @Persistent property name=\"name\"; @Persistent false property name=\"notMapped\"; }"
	} )
	@ParameterizedTest
	public void testPersistentFalseAnnotation( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					classEL		= doc.getDocumentElement().getChildNodes().item( 0 );
		Node					node		= classEL.getChildNodes().item( 1 );

		assertThat( node )
		    .isEqualTo( null );
	}

	@DisplayName( "It recognizes immutable entities" )
	@ValueSource( strings = {
	    "class readonly=\"true\" {}",
	    "@Immutable \r\nclass {}"
	} )
	@ParameterizedTest
	public void testImmutableEntities( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					classEL		= doc.getDocumentElement().getChildNodes().item( 0 );

		assertThat( classEL.getAttributes().getNamedItem( "mutable" ).getTextContent() )
		    .isEqualTo( "false" );
	}

	@DisplayName( "It maps immutable properties" )
	@ValueSource( strings = {
	    "class { property name=\"name\" insert=false update=false; }",
	    "class { @insert false @update false property name=\"name\" insert=false update=false; }"
	} )
	@ParameterizedTest
	public void testImmutableProperties( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					classEL		= doc.getDocumentElement().getChildNodes().item( 0 );
		Node					node		= classEL.getChildNodes().item( 0 );

		assertThat( node.getAttributes().getNamedItem( "insert" ).getTextContent() )
		    .isEqualTo( "false" );
		assertThat( node.getAttributes().getNamedItem( "update" ).getTextContent() )
		    .isEqualTo( "false" );
	}

	// @formatter:off
	@DisplayName( "It maps discriminator info" )
	@ValueSource( strings = {
	    "class discriminatorValue=\"Ford\" discriminatorColumn=\"autoType\" {}",
	    "@DiscriminatorValue \"Ford\" @DiscriminatorColumn \"autoType\"\r\nclass {}",
	    // Hopefully we can support this in the future. Currently stuck on BL's parser not supporting structs in annotations.
	    """
			@Discriminator {
				"name" : "autoType",
				"value" : "Ford"
			}
			class {}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testDiscriminator( String sourceCode ) {
		IStruct					entityMeta	= getClassMetaFromCode( sourceCode );

		ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( entityMeta );
		Document				doc			= new HibernateXMLWriter( inspector ).generateXML();

		Node					classEL		= doc.getDocumentElement().getChildNodes().item( 0 );
		Node					node		= classEL.getChildNodes().item( 0 );

		assertThat( classEL.getAttributes().getNamedItem( "discriminator-value" ).getTextContent() )
		    .isEqualTo( "Ford" );
		assertThat( node.getAttributes().getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "autoType" );
	}

	private IStruct getClassMetaFromFile( String entityFile ) {
		return getClassMeta( new Parser().parse( new File( entityFile ) ) );
	}

	private IStruct getClassMetaFromCode( String code ) {
		try {
			return getClassMeta( new BoxScriptParser().parse( code, true ) );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( String.format( "Failed to parse metadata from source: [%s]", code ), e );
		}
	}

	private IStruct getClassMeta( ParsingResult result ) {
		if ( !result.isCorrect() ) {
			throw new ParseException( result.getIssues(), "" );
		}
		ClassMetadataVisitor visitor = new ClassMetadataVisitor();
		result.getRoot().accept( visitor );
		return visitor.getMetadata();
	}

	private String xmlToString( Document doc ) {
		try {
			TransformerFactory	tf			= TransformerFactory.newInstance();
			Transformer			transformer	= tf.newTransformer();

			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "no" );
			transformer.setOutputProperty( OutputKeys.METHOD, "xml" );
			transformer.setOutputProperty( OutputKeys.DOCTYPE_PUBLIC, doc.getDoctype().getPublicId() );
			transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, doc.getDoctype().getSystemId() );

			StringWriter writer = new StringWriter();

			// transform document to string
			transformer.transform( new DOMSource( doc ), new StreamResult( writer ) );

			return writer.getBuffer().toString();
		} catch ( TransformerException e ) {
			throw new BoxRuntimeException( "Failed to transform XML to string", e );
		}
	}
}
