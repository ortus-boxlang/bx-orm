package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.BoxSourceType;
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
		IStruct		meta		= getClassMetaFromFile( "src/test/resources/app/models/Manufacturer.bx" );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Vehicle", "models.Vehicle" ) ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();

		assertThat( classEl.getAttributes().getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Manufacturer" );
	}

	@DisplayName( "It can set entity attributes" )
	@ValueSource( strings = {
	    """
	    class
	    	persistent="true"
	    	entityName="Car"
	    	table="vehicles"
	    	schema="foo"
	    	catalog="morefoo"
	    	optimisticLock="all"
	    {}
	    """,
	    """
	    @Entity "Car"
	    @Table{
	    	"name"   : "vehicles",
	    	"schema" : "foo",
	    	"catalog": "morefoo"
	    }
	    @OptimisticLock "all"
	    class {}
	    """
	} )
	@ParameterizedTest
	public void testEntityAttributes( String sourceCode ) {
		IStruct			meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc			= new HibernateXMLWriter( entityMeta ).generateXML();
		Node			classEl		= doc.getDocumentElement().getFirstChild();
		NamedNodeMap	attrs		= classEl.getAttributes();

		assertThat( attrs.getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Car" );

		assertThat( attrs.getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "vehicles" );

		assertThat( attrs.getNamedItem( "schema" ).getTextContent() )
		    .isEqualTo( "foo" );

		assertThat( attrs.getNamedItem( "catalog" ).getTextContent() )
		    .isEqualTo( "morefoo" );

		assertThat( attrs.getNamedItem( "optimistic-lock" ).getTextContent() )
		    .isEqualTo( "all" );
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
	@DisplayName( "It aliases ORM types to their proper counterpart" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
		    property name="the_name" ormtype="varchar(50)";
		    property name="foo" ormtype="java.sql.Timestamp";
	    }
	    """
	} )
	// @formatter:on
	public void testFunkyORMTypes( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		Node		nameNode	= classEl.getFirstChild();
		Node		fooNode		= classEl.getChildNodes().item( 1 );

		assertThat( nameNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );

		assertThat( fooNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "timestamp" );
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
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		Node		idNode		= classEl.getFirstChild();

		// increment, identity, and native all default to 'integer'
		assertThat( idNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "integer" );

		Node generatorNode = idNode.getLastChild();

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

		assertNotNull( paramNode );
		assertThat( paramNode.getTextContent() ).isEqualTo( "foo" );
	}

	/**********************
	 * General property tests
	 *********************/
	@DisplayName( "It generates proper formula xml" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    	name="the_name"
	    	formula="SELECT TOP 1 name from theNames";
	    }
	    """,
	    """
	    @Entity
	    class {
	    	@Column{
	    		formula="SELECT TOP 1 name from theNames"
	    	}
	     	property name="the_name";
	    }
	    """
	} )
	public void testPropertyFormula( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta ).generateXML();

		String			xml					= xmlToString( doc );

		Node			propertyNode		= doc.getDocumentElement().getFirstChild().getFirstChild();
		NamedNodeMap	propertyAttributes	= propertyNode.getAttributes();

		assertThat( propertyAttributes.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_name" );
		assertThat( propertyAttributes.getNamedItem( "formula" ).getTextContent() )
		    .isEqualTo( "( SELECT TOP 1 name from theNames )" );
	}

	@DisplayName( "It generates proper column xml" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="the_name"
	    		column="NameColumn"
	    		sqltype="varchar";
	    }
	    """,
	    """
	    @Entity
	    class {
	    	@Column{
	    		sqltype="varchar",
	    		name="NameColumn"
	    	}
	     	property name="the_name";
	    }
	    """
	} )
	public void testProperty( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta ).generateXML();

		Node			propertyNode		= doc.getDocumentElement().getFirstChild().getFirstChild();
		NamedNodeMap	propertyAttributes	= propertyNode.getAttributes();

		assertEquals( "the_name", propertyAttributes.getNamedItem( "name" ).getTextContent() );
		assertEquals( "string", propertyAttributes.getNamedItem( "type" ).getTextContent() );

		Node columnNode = propertyNode.getFirstChild();
		assertEquals( "varchar", columnNode.getAttributes().getNamedItem( "sql-type" ).getTextContent() );
		assertEquals( "NameColumn", columnNode.getAttributes().getNamedItem( "name" ).getTextContent() );
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
				column="amountCol"
				name="amount";
		}
		""",
	    """
		@Entity
		class {
			@Column{
				name      : "amountCol",
				length    : 12,
				scale     : 10,
				precision : 2
			}
			property name="amount";
		}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testLengthPrecisionScale( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta ).generateXML();

		Node			classEL				= doc.getDocumentElement().getFirstChild();
		Node			propertyNode		= classEL.getFirstChild();
		Node			columnNode			= propertyNode.getFirstChild();
		NamedNodeMap	columnAttributes	= columnNode.getAttributes();

		assertEquals( "amountCol", columnAttributes.getNamedItem( "name" ).getTextContent() );
		assertEquals( "12", columnAttributes.getNamedItem( "length" ).getTextContent() );
		assertEquals( "2", columnAttributes.getNamedItem( "precision" ).getTextContent() );
		assertEquals( "10", columnAttributes.getNamedItem( "scale" ).getTextContent() );
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
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta ).generateXML();

		Node			classEL				= doc.getDocumentElement().getFirstChild();
		Node			propertyNode		= classEL.getFirstChild();
		Node			columnNode			= propertyNode.getFirstChild();
		NamedNodeMap	propertyAttributes	= propertyNode.getAttributes();
		NamedNodeMap	columnAttributes	= columnNode.getAttributes();

		assertThat( propertyAttributes.getNamedItem( "insert" ).getTextContent() )
		    .isEqualTo( "false" );
		assertThat( propertyAttributes.getNamedItem( "update" ).getTextContent() )
		    .isEqualTo( "false" );

		assertThat( columnAttributes.getNamedItem( "unique" ).getTextContent() )
		    .isEqualTo( "true" );
		assertThat( columnAttributes.getNamedItem( "not-null" ).getTextContent() )
		    .isEqualTo( "true" );
		assertThat( columnAttributes.getNamedItem( "default" ).getTextContent() )
		    .isEqualTo( "test" );
		// assertThat( columnAttributes.getNamedItem( "table" ).getTextContent() )
		// .isEqualTo( "foo" );
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

	// @formatter:off
	@DisplayName("It maps one-to-one relationships")
	@ParameterizedTest
	@ValueSource(strings={
		"""
		class persistent {
			property
				name="owner"
				cfc="Person"
				mappedBy="id"
				fieldtype="one-to-one"
				foreignKey="fooID"
				cascade="all-delete-orphan"
				constrained="true"
				fetch="join"
				lazy="extra";
		}
		"""
		// , """
		// @Entity
		// class {
		// 	@OneToOne{
		// 	  	"mappedBy"   : "id",
		// 		"foreignKey" : "fooID",
		// 		"cascade"    : "all-delete-orphan",
		// 		"constrained": true,
		// 		"fetch"      : "join",
		// 		"lazy"       : "extra"
		// 	}
		// 	property name="owner";
		// }
		// """
		} )
	// @formatter:on
	public void testOneToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ) ).generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();
		Node		oneToOneNode	= classEL.getFirstChild();
		assertEquals( "one-to-one", oneToOneNode.getNodeName() );

		NamedNodeMap attrs = oneToOneNode.getAttributes();

		// assertEquals( "true", attrs.getNamedItem( "embed-xml" ).getTextContent() );
		assertEquals( "all-delete-orphan", attrs.getNamedItem( "cascade" ).getTextContent() );
		assertEquals( "join", attrs.getNamedItem( "fetch" ).getTextContent() );
		assertEquals( "extra", attrs.getNamedItem( "lazy" ).getTextContent() );
		assertEquals( "id", attrs.getNamedItem( "property-ref" ).getTextContent() );
		assertEquals( "fooID", attrs.getNamedItem( "foreign-key" ).getTextContent() );
		assertEquals( "true", attrs.getNamedItem( "constrained" ).getTextContent() );
	}

	@DisplayName( "It maps one-to-one relationships as many-to-one if fkcolumn is defined" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name        = "owner"
	    		cfc         = "Person"
	    		fieldtype   = "one-to-one"
	    		fkcolumn    = "FK_owner"
	    		foreignKey  = "fooID"
	    		cascade     = "all"
	    		constrained = "true";
	    }
	    """,
	    """
	    @Entity
	    class {
	    	@OneToOne {
	    		// "mappedBy" : "id",
	    		"fkcolumn"   : "FK_owner",
	    		"foreignKey" : "fooID",
	    		"cascade"    : "all",
	    		"constrained": true,
	    		"fetch"      : "join",
	    		"lazy"       : "extra"
	    	}
	    	property name="owner";
	    }
	    """
	} )
	// @formatter:on
	public void testOneToOneAsManyToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ) ).generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();
		Node		oneToOneNode	= classEL.getFirstChild();
		assertEquals( "many-to-one", oneToOneNode.getNodeName() );

		NamedNodeMap attrs = oneToOneNode.getAttributes();

		assertEquals( "owner", attrs.getNamedItem( "name" ).getTextContent() );
		assertEquals( "true", attrs.getNamedItem( "unique" ).getTextContent() );
		assertEquals( "all", attrs.getNamedItem( "cascade" ).getTextContent() );
		assertEquals( "fooID", attrs.getNamedItem( "foreign-key" ).getTextContent() );
		assertEquals( "true", attrs.getNamedItem( "constrained" ).getTextContent() );
		assertEquals( "FK_owner", attrs.getNamedItem( "column" ).getTextContent() );
	}

	@DisplayName( "It maps many-to-many relationships" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="owners"
	    		type="array"
	    		cfc="Person"
	    		cascade="all"
	    		fieldtype="many-to-many"
	    		linkTable="tblOwners"
	    		linkCatalog="myDB"
	    		linkSchema="dbo"
	    		fkcolumn="FK_owner"
	    		mappedBy="owners"
	    		orderBy="name";
	    }
	    """
	// , """
	// """
	} )
	// @formatter:on
	public void testManyToManyLinkTable( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ) ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();

		Node		bagNode		= classEL.getFirstChild();
		assertNotNull( bagNode );
		assertEquals( "bag", bagNode.getNodeName() );

		NamedNodeMap bagAttrs = bagNode.getAttributes();
		assertEquals( "owners", bagAttrs.getNamedItem( "name" ).getTextContent() );
		assertEquals( "tblOwners", bagAttrs.getNamedItem( "table" ).getTextContent() );
		assertEquals( "dbo", bagAttrs.getNamedItem( "schema" ).getTextContent() );
		assertEquals( "myDB", bagAttrs.getNamedItem( "catalog" ).getTextContent() );
		assertEquals( "all", bagAttrs.getNamedItem( "cascade" ).getTextContent() );

		Node keyNode = bagNode.getFirstChild();
		assertNotNull( keyNode );
		assertEquals( "key", keyNode.getNodeName() );

		NamedNodeMap keyAttrs = keyNode.getAttributes();
		// fkcolumn -> key.column
		assertEquals( "FK_owner", keyAttrs.getNamedItem( "column" ).getTextContent() );
		// mappedBy -> key.property-ref
		assertEquals( "owners", keyAttrs.getNamedItem( "property-ref" ).getTextContent() );

		Node oneToManyNode = bagNode.getLastChild();
		assertNotNull( oneToManyNode );
		assertEquals( "many-to-many", oneToManyNode.getNodeName() );

		// NamedNodeMap manyToManyAttrs = oneToManyNode.getAttributes();
		// assertEquals( "Person", manyToManyAttrs.getNamedItem( "class" ).getTextContent() );
	}

	// @formatter:off
	@DisplayName( "It maps one-to-many relationships" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="owners"
	    		type="array"
	    		cfc="Person"
	    		cascade="all"
	    		fieldtype="one-to-many"
	    		fkcolumn="FK_owner"
	    		mappedBy="owners"
	    		orderBy="name DESC"
	    		where="Age IS NOT NULL"
	    		optimisticLock="false";
	    }
	    """,
	    """
	    @Entity
	    class {
	    	@OneToMany{
	    		"mappedBy"       : "owners",
	    		"orderBy"        : "name DESC",
	    		"where"          : "Age IS NOT NULL",
	    		"optimisticLock" : false,
	    		"fkcolumn"       : "FK_owner",
	    		"cascade"        : "all",
	    		"class"          : "Person"
	    	}
	    	property name="owners" type="array";
	    }
	    """
	} )
	// @formatter:on
	public void testOneToMany( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ) ).generateXML();

		String		xml			= xmlToString( doc );
		// @formatter:off
		// <hibernate-mapping>
		// 	<class table="">
		// 		<bag cascade="all" name="owners" optimistic-lock="false">
		// 			<key column="FK_owner" property-ref="owners"/>
		// 			<one-to-many entity-name="Person"/>
		// 		</bag>
		// 	</class>
		// </hibernate-mapping>
		// @formatter:on

		Node		classEL		= doc.getDocumentElement().getFirstChild();

		Node		bagNode		= classEL.getFirstChild();
		assertNotNull( bagNode );
		assertEquals( "bag", bagNode.getNodeName() );

		NamedNodeMap bagAttrs = bagNode.getAttributes();
		assertEquals( "owners", bagAttrs.getNamedItem( "name" ).getTextContent() );
		assertEquals( "all", bagAttrs.getNamedItem( "cascade" ).getTextContent() );
		assertEquals( "Age IS NOT NULL", bagAttrs.getNamedItem( "where" ).getTextContent() );
		assertEquals( "name DESC", bagAttrs.getNamedItem( "order-by" ).getTextContent() );
		assertEquals( "false", bagAttrs.getNamedItem( "optimistic-lock" ).getTextContent() );

		Node keyNode = bagNode.getFirstChild();
		assertNotNull( keyNode );
		assertEquals( "key", keyNode.getNodeName() );

		NamedNodeMap keyAttrs = keyNode.getAttributes();
		// fkcolumn -> key.column
		assertEquals( "FK_owner", keyAttrs.getNamedItem( "column" ).getTextContent() );
		// mappedBy -> key.property-ref
		assertEquals( "owners", keyAttrs.getNamedItem( "property-ref" ).getTextContent() );

		Node oneToManyNode = bagNode.getLastChild();
		assertNotNull( oneToManyNode );
		assertEquals( "one-to-many", oneToManyNode.getNodeName() );

		NamedNodeMap manyToManyAttrs = oneToManyNode.getAttributes();
		assertEquals( "Person", manyToManyAttrs.getNamedItem( "entity-name" ).getTextContent() );
	}

	// @formatter:off
	@DisplayName( "It maps many-to-one relationships" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="createdBy"
	    		fieldtype="many-to-one"
	    		cfc="Vehicle"
	    		fkcolumn="FK_manufacturer"
	    		fetch="select"
	    		cascade="all"
	    		insert="false"
	    		update="false"
	    		lazy="true"
	    		persistent=true;
	    }
	    """
	} )
	// @formatter:on
	public void testManyToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Manufacturer", "models.Manufacturer" ) ).generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();

		Node		manyToOneNode	= classEL.getLastChild();
		assertNotNull( manyToOneNode );
		assertEquals( "many-to-one", manyToOneNode.getNodeName() );

		NamedNodeMap manyToOneAttrs = manyToOneNode.getAttributes();
		assertEquals( "createdBy", manyToOneAttrs.getNamedItem( "name" ).getTextContent() );
		assertEquals( "select", manyToOneAttrs.getNamedItem( "fetch" ).getTextContent() );
		assertEquals( "all", manyToOneAttrs.getNamedItem( "cascade" ).getTextContent() );
		assertEquals( "Manufacturer", manyToOneAttrs.getNamedItem( "entity-name" ).getTextContent() );
		assertEquals( "false", manyToOneAttrs.getNamedItem( "insert" ).getTextContent() );
		assertEquals( "false", manyToOneAttrs.getNamedItem( "update" ).getTextContent() );
		// @TODO: Fix!
		// assertEquals( "FK_manufacturer", manyToOneAttrs.getNamedItem( "column" ).getTextContent() );
	}

	// @formatter:off
	@DisplayName( "It maps cache meta" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent cacheuse="transactional" cacheName="foo" cacheInclude="non-lazy" {}
	    """,
	    """
	    @Cache {
	    	strategy: "transactional",
	    	region  : "foo",
	    	include   : "non-lazy"
	    }
	    @Entity
	    class {}
	    """
	} )
	// @formatter:on
	public void testCacheMapping( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		cacheNode	= classEL.getFirstChild();

		assertNotNull( cacheNode );
		assertEquals( "cache", cacheNode.getNodeName() );

		NamedNodeMap cacheAttrs = cacheNode.getAttributes();
		assertEquals( "transactional", cacheAttrs.getNamedItem( "usage" ).getTextContent() );
		assertEquals( "foo", cacheAttrs.getNamedItem( "region" ).getTextContent() );
		assertEquals( "non-lazy", cacheAttrs.getNamedItem( "include" ).getTextContent() );
	}

	@Disabled( "Unimplemented" )
	@DisplayName( "It can handle string-delimited column names for a composite key" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="owners"
	    		cfc="Person"
	    		fieldtype="one-to-many"
	    		linkTable="owners"
	    		fkcolumn="name,dob,phone";
	    }
	    """
	// , """
	// """
	} )
	// @formatter:on
	public void testMultipleFKColumns( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta ).generateXML();
		// TODO:...

	}

	@Disabled( "Unimplemented" )
	@DisplayName( "It can map an array/bag collection" )
	@Test
	public void testArrayCollection() {
	}

	@Disabled( "Unimplemented" )
	@DisplayName( "It can map a struct/map collection" )
	@Test
	public void testStructCollection() {
	}

	/**
	 * TODO: The following ORM annotations are still lacking tests at either the entity level, the property level, or both:
	 * embedXml
	 * uniqueKey
	 * missingRowIgnored
	 * joinColumn
	 * inverse
	 * inversejoincolumn
	 * structkeycolumn
	 * structkeytype
	 * structkeydatatype ?? ACF only?
	 * elementcolumn
	 * elementtype
	 * index
	 * unSavedValue - deprecated
	 */

	private IStruct getClassMetaFromFile( String entityFile ) {
		return getClassMeta( new Parser().parse( new File( entityFile ) ) );
	}

	private IStruct getClassMetaFromCode( String code ) {
		try {
			var isCFType = code.trim().contains( "component " );
			return getClassMeta( new Parser().parse( code, isCFType ? BoxSourceType.CFSCRIPT : BoxSourceType.BOXSCRIPT, true ) );
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
