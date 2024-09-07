package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.BoxScriptParser;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
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

	/**********************
	 * Entity tests
	 *********************/

	@DisplayName( "It generates the entity-name from the class name" )
	@Test
	public void testMapping() {
		IStruct		meta		= getClassMetaFromFile( "src/test/resources/app/models/Developer.bx" );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();

		assertThat( classEl.getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Developer" );
	}

	@DisplayName( "It generates the entity-name from the annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    // CFML style
	    "class persistent entityName=\"Car\"{}",
	    // JPA style
	    "@Entity \"Car\" class{}"
	} )
	public void testEntityNameValue( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();

		assertThat( classEl.getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Car" );
	}

	@DisplayName( "It can set a table name, schema, and catalog" )
	@ValueSource( strings = {
	    "class persistent table=\"developers\" schema=\"foo\" catalog=\"morefoo\" {}",
	    """
	    @Table{
	    	"name"   : "developers",
	    	"schema" : "foo",
	    	"catalog": "morefoo"
	    }
	    class {}
	    """
	} )
	@ParameterizedTest
	public void testTableNameFromAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();
		Node		classEl		= doc.getDocumentElement().getFirstChild();

		assertThat( classEl.getAttributes().getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "developers" );

		assertThat( classEl.getAttributes().getNamedItem( "schema" ).getTextContent() )
		    .isEqualTo( "foo" );

		assertThat( classEl.getAttributes().getNamedItem( "catalog" ).getTextContent() )
		    .isEqualTo( "morefoo" );
	}

	@DisplayName( "It recognizes immutable entities" )
	@ValueSource( strings = {
	    "class persistent readonly=\"true\" {}",
	    "@Immutable \r\nclass {}"
	} )
	@ParameterizedTest
	public void testImmutableEntities( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();

		assertThat( classEL.getAttributes().getNamedItem( "mutable" ).getTextContent() )
		    .isEqualTo( "false" );
	}

	// @formatter:off
	@DisplayName( "It maps discriminator info" )
	@ValueSource( strings = {
	    "class persistent discriminatorValue=\"Ford\" discriminatorColumn=\"autoType\" {}",
		// Note we can't test the `type`, `formula`, `force`, and `insert` keys with the parameterized test, as the older annotation style doesn't support them
	    """
			@Discriminator {
				"name"    : "autoType",
				"value"   : "Ford",
				"type"    : "string",
				"formula" : "foo",
				"force"   : "false",
				"insert"  : "true",
			}
			class {}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testDiscriminator( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEL.getFirstChild();
		assertThat( classEL.getAttributes().getNamedItem( "discriminator-value" ).getTextContent() )
		    .isEqualTo( "Ford" );
		assertThat( node.getAttributes().getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "autoType" );
	}

	/**********************
	 * Identifier tests
	 *********************/
	@DisplayName( "It generates an id element from the Id annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    "class persistent{ property name=\"the_id\" fieldtype=\"id\"; }",
	    "class{ @Id property name=\"the_id\"; }"
	} )
	public void testIDAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();
		String		xml			= xmlToString( doc );

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEl.getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_id" );
	}

	@DisplayName( "It sets the type of the id property via an annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="the_id"
	    		fieldtype="id"
	    		ormtype="integer";
	    }
	    """,
	    """
	    class {
	    	@Id
	    	@ORMType "integer"
	    	property name="the_id";
	    }
	    """
	} )
	public void testIDTypeAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();
		String		xml			= xmlToString( doc );

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEl.getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "integer" );
	}

	// @formatter:off
	@DisplayName( "It defaults property types to 'string'" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
		    property name="the_name";
	    }
	    """,
	    """
	    @Entity
	    class {
	    	@Column
			property name="the_name";
	    }
	    """
	} )
	// @formatter:on
	public void testPropertyTypeAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		node		= doc.getDocumentElement().getFirstChild().getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
	}

	// @formatter:off
	@DisplayName( "It can set an id generator" )
	@ValueSource( strings = {

	    """
	    class persistent {
	    	property
	    		name="the_id"
	    		fieldtype="id"
	    		generator="increment";
	    }
	    """,
		"""
		@Entity
		class {
			@Id
			@GeneratedValue{
				"strategy" : "increment"
			}
			property name="the_id";
		}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testIDGeneratorAnnotation( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta ).generateXML();

		String		xml				= xmlToString( doc );

		Node		classEl			= doc.getDocumentElement().getFirstChild();
		Node		idNode			= classEl.getFirstChild();
		Node		generatorNode	= idNode.getLastChild();

		assertThat( generatorNode.getAttributes().getNamedItem( "class" ).getTextContent() )
		    .isEqualTo( "increment" );
	}

	// @formatter:off
	@DisplayName( "It can set a select generator" )
	@ValueSource( strings = {

	    """
	    class persistent {
	    	property
	    		name="the_id"
	    		fieldtype="id"
	    		generator="select"
				generated="insert"
				selectKey="foo";
	    }
	    """,
		"""
		@Entity
		class {
			@Id
			@GeneratedValue{
				"strategy"  : "select",
				"selectKey" : "foo",
				"generated" : "insert"
			}
			property name="the_id";
		}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testSelectGenerator( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta ).generateXML();

		String		xml				= xmlToString( doc );

		Node		classEl			= doc.getDocumentElement().getFirstChild();
		Node		idNode			= classEl.getFirstChild();
		Node		generatorNode	= idNode.getLastChild();

		assertThat( generatorNode.getAttributes().getNamedItem( "class" ).getTextContent() )
		    .isEqualTo( "select" );

		NodeList	paramNodes	= generatorNode.getChildNodes();
		Node		paramNode	= null;

		for ( int i = 0; i < paramNodes.getLength(); i++ ) {
			Node node = paramNodes.item( i );
			if ( node.getNodeType() == Node.ELEMENT_NODE ) {
				Node nameAttr = node.getAttributes().getNamedItem( "name" );
				if ( nameAttr != null && "key".equals( nameAttr.getTextContent() ) ) {
					paramNode = node;
					break;
				}
			}
		}

		assertThat( paramNode ).isNotNull();
		assertThat( paramNode.getTextContent() ).isEqualTo( "foo" );
	}

	/**********************
	 * General property tests
	 *********************/
	@DisplayName( "It generates property element for a property" )
	@Test
	public void testProperty() {
		IStruct		meta		= getClassMetaFromCode(
		    """
		    	class persistent {
		    		property name="the_name";
		    	}
		    """
		);

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		node		= doc.getDocumentElement().getFirstChild().getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_name" );
	}

	@DisplayName( "It does not map properties annotated with @Persistent false" )
	@ValueSource( strings = {
	    "class persistent { property name=\"name\"; property name=\"notMapped\" persistent=\"false\"; }",
	    "class { @Column property name=\"name\"; @Transient property name=\"notMapped\"; }"
	} )
	@ParameterizedTest
	public void testPersistentFalseAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEL.getChildNodes().item( 1 );

		assertThat( node )
		    .isEqualTo( null );
	}

	@DisplayName( "It maps immutable properties" )
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="name"
	    		insert=false
	    		update=false;
	    }
	    """,
	    """
	    @Entity
	    class {
	    	@Immutable
	    	@Column
	    	property name="name";
	    }
	    """,
	} )
	@ParameterizedTest
	public void testImmutableProperties( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEL.getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "insert" ).getTextContent() )
		    .isEqualTo( "false" );
		assertThat( node.getAttributes().getNamedItem( "update" ).getTextContent() )
		    .isEqualTo( "false" );
	}

	// @formatter:off
	@DisplayName( "It maps name, length, precision, and scale" )
	@ValueSource( strings = {
	    """
		class persistent {
			property 
				length=12 
				scale=10 
				precision=2 
				name="amount";
		}
		""",
	    """
		@Entity
		class {
			@Column{
				"length"    : 12,
				"scale"     : 10,
				"precision" : 2
			}
			property name="amount";
		}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testLengthPrecisionScale( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEL.getFirstChild().getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "amount" );
		assertThat( node.getAttributes().getNamedItem( "length" ).getTextContent() )
		    .isEqualTo( "12" );
		assertThat( node.getAttributes().getNamedItem( "precision" ).getTextContent() )
		    .isEqualTo( "2" );
		assertThat( node.getAttributes().getNamedItem( "scale" ).getTextContent() )
		    .isEqualTo( "10" );
	}

	// @formatter:off
	@DisplayName( "It maps other column attributes" )
	@ValueSource( strings = {
	    """
		class persistent {
			property 
				insert=false 
				update=false 
				unique=true 
				table="foo"
				notNull=true
				dbDefault="test"
				name="title";
		}
		""",
	    """
		@Entity
		class {
			@Column{
				"table"      : "foo",
				"unique"     : true,
				"nullable"   : false,
				"insertable" : false,
				"updateable" : false,
				"default"    : "test"
			}
			property name="title";
		}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testOtherColumnAttributes( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();
		Node		propertyNode	= classEL.getFirstChild();
		Node		columnNode		= propertyNode.getFirstChild();

		// assertThat( columnNode.getAttributes().getNamedItem( "table" ).getTextContent() )
		// .isEqualTo( "foo" );
		assertThat( propertyNode.getAttributes().getNamedItem( "insert" ).getTextContent() )
		    .isEqualTo( "false" );
		assertThat( propertyNode.getAttributes().getNamedItem( "update" ).getTextContent() )
		    .isEqualTo( "false" );

		assertThat( columnNode.getAttributes().getNamedItem( "unique" ).getTextContent() )
		    .isEqualTo( "true" );
		assertThat( columnNode.getAttributes().getNamedItem( "not-null" ).getTextContent() )
		    .isEqualTo( "true" );
		assertThat( columnNode.getAttributes().getNamedItem( "default" ).getTextContent() )
		    .isEqualTo( "test" );
	}

	// @formatter:off
	@DisplayName( "It maps version properties" )
	@ValueSource( strings = {
	    """
		class persistent {
			property 
				fieldtype="version"
				generated="never"
				column="itemVersion"
				ormType="integer"
				insert="false"
				name="version";
		}
		"""
		// 	@TODO: simplified modern syntax
	    // ,"""
		// @Entity
		// class {
		// 	@Version{
		// 		"column" : "itemVersion",
		// 		"insertable" : false
		// 		"generated": "never"
		// 	}
		// 	property name="version" ormType="integer";
		// }
		// """
	} )
	// @formatter:on
	@ParameterizedTest
	public void testVersionProperty( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		versionNode	= classEL.getFirstChild();

		assertEquals( "version", versionNode.getNodeName() );
		assertEquals( "version", versionNode.getAttributes().getNamedItem( "name" ).getTextContent() );
		assertEquals( "itemVersion", versionNode.getAttributes().getNamedItem( "column" ).getTextContent() );
		assertEquals( "false", versionNode.getAttributes().getNamedItem( "insert" ).getTextContent() );
		// assertEquals( "never", versionNode.getAttributes().getNamedItem( "generated" ).getTextContent() );
	}

	/**
	 * TODO: Test each below property annotation:
	 * column
	 * formula
	 * persistent / transient
	 * where
	 * sqltype
	 * cfc
	 * mappedBy
	 * optimisticlock
	 * uniqueKey
	 * constrained
	 * cascade
	 * fetch
	 * lazy
	 * orderby
	 * missingRowIgnored
	 * linktable
	 * linkcatalog
	 * linkschema
	 * joinColumn
	 * inverse
	 * inversejoincolumn
	 * structkeycolumn
	 * structkeytype
	 * structkeydatatype ?? ACF only?
	 * elementcolumn
	 * elementtype
	 * index
	 * ormType
	 * fieldtype
	 * unSavedValue - deprecated
	 */

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
