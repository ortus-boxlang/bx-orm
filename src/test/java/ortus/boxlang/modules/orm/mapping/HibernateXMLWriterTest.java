/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

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
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.hibernate.converters.DateTimeConverter;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ParseException;

public class HibernateXMLWriterTest {

	static BoxRuntime	instance;
	IBoxContext			context;
	IScope				variables;
	static Key			result	= new Key( "result" );
	ORMConfig			ormConfig;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( true );
	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );
		IStruct properties = new Struct();
		properties.put( "ignoreParseErrors", "true" );
		ormConfig = new ORMConfig( properties, context.getRequestContext() );
	}

	/**********************
	 * Entity tests
	 *********************/

	@DisplayName( "It generates the entity-name from the class name" )
	@Test
	public void testMapping() {
		IStruct		meta		= getClassMetaFromFile( "src/test/resources/app/models/Manufacturer.bx" );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Vehicle", "models.Vehicle" ), ormConfig ).generateXML();

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
	    """
	} )
	@ParameterizedTest
	public void testEntityAttributes( String sourceCode ) {
		IStruct			meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc				= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();
		Node			classEl			= doc.getDocumentElement().getFirstChild();
		NamedNodeMap	classAttributes	= classEl.getAttributes();

		assertThat( classAttributes.getNamedItem( "entity-name" ).getTextContent() )
		    .isEqualTo( "Car" );

		assertThat( classAttributes.getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "vehicles" );

		assertThat( classAttributes.getNamedItem( "schema" ).getTextContent() )
		    .isEqualTo( "foo" );

		assertThat( classAttributes.getNamedItem( "catalog" ).getTextContent() )
		    .isEqualTo( "morefoo" );

		assertThat( classAttributes.getNamedItem( "optimistic-lock" ).getTextContent() )
		    .isEqualTo( "all" );
	}

	@DisplayName( "It recognizes immutable entities" )
	@ValueSource( strings = {
	    "class persistent readonly=\"true\" {}"
	} )
	@ParameterizedTest
	public void testImmutableEntities( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();

		assertThat( classEL.getAttributes().getNamedItem( "mutable" ).getTextContent() )
		    .isEqualTo( "false" );
	}

	// @formatter:off
	@DisplayName( "It maps discriminator info" )
	@ValueSource( strings = {
	    "class persistent discriminatorValue=\"Ford\" discriminatorColumn=\"autoType\" {}"
	} )
	// @formatter:on
	@ParameterizedTest
	public void testDiscriminator( String sourceCode ) {
		IStruct		meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc					= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEL				= doc.getDocumentElement().getFirstChild();
		// lastChild is a bit brittle, but it's the simplest way to get the discriminator node
		Node		discriminatorNode	= classEL.getLastChild();
		assertThat( classEL.getAttributes().getNamedItem( "discriminator-value" ).getTextContent() )
		    .isEqualTo( "Ford" );
		assertThat( discriminatorNode.getAttributes().getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "autoType" );
	}

	/**********************
	 * Identifier tests
	 *********************/
	@DisplayName( "It generates an id element from the Id annotation" )
	@ParameterizedTest
	@ValueSource( strings = {
	    "class persistent{ property name=\"the_id\" fieldtype=\"id\"; }"
	} )
	public void testIDAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEl.getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_id" );
	}

	// @formatter:off
	@DisplayName( "It can generate a composite id" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
		class persistent{
			property name="id1" fieldtype="id" ormType="integer";
			property name="id2" fieldtype="id" sqltype="varchar(50)" ormType="string";
		}
		"""
	} )
	// @formatter:on
	public void testCompositeID( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		String		xml			= xmlToString( doc );

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		assertThat( classEl.getNodeName() ).isEqualTo( "class" );
		Node compositeNode = classEl.getFirstChild();

		assertThat( compositeNode.getNodeName() ).isEqualTo( "composite-id" );
		Node	firstIDNode	= compositeNode.getFirstChild();
		Node	lastIDNode	= compositeNode.getLastChild();

		assertThat( firstIDNode.getNodeName() ).isEqualTo( "key-property" );
		assertThat( lastIDNode.getNodeName() ).isEqualTo( "key-property" );

		NamedNodeMap	firstIDAttrs	= firstIDNode.getAttributes();
		NamedNodeMap	lastIDAttrs		= lastIDNode.getAttributes();
		assertThat( firstIDAttrs.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "id1" );
		assertThat( firstIDAttrs.getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "integer" );
		assertThat( lastIDAttrs.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "id2" );
		assertThat( lastIDAttrs.getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
	}

	@DisplayName( "It supports various ormType aliases" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property name="the_id" fieldtype="id" ormtype="int";
	    }
	    """
	} )
	public void testIDTypeAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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
	    """
	} )
	// @formatter:on
	public void testPropertyTypeAnnotation( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEl			= doc.getDocumentElement().getFirstChild();
		Node		propertyNode	= classEl.getLastChild();

		assertThat( propertyNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
	}

	// @formatter:off
	@DisplayName( "It aliases ORM types to their proper counterpart, and properly defaults ormType to 'string'" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
		    property name="the_name" ormtype="varchar(50)";
		    property name="foo" ormtype="java.sql.Timestamp";
		    property name="bar";
		    property name="barNone" ormType="";
	    }
	    """
	} )
	// @formatter:on
	public void testFunkyORMTypes( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEl		= doc.getDocumentElement().getFirstChild();
		Node		nameNode	= classEl.getFirstChild();
		Node		fooNode		= nameNode.getNextSibling();
		Node		barNode		= fooNode.getNextSibling();
		Node		barNoneNode	= barNode.getNextSibling();

		assertThat( nameNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );

		assertThat( fooNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "converted::" + DateTimeConverter.class.getName() );

		assertThat( barNode.getAttributes().getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );

		assertThat( barNoneNode.getAttributes().getNamedItem( "type" ).getTextContent() )
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
	    """
	} )
	// @formatter:on
	@ParameterizedTest
	public void testIDGeneratorAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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
				name      = "the_id"
				fieldtype = "id"
				generator = "select"
				generated = "insert"
				selectKey = "foo";
	    }
	    """
	} )
	// @formatter:on
	@ParameterizedTest
	public void testSelectGenerator( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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

	// @formatter:off
	@DisplayName( "It can use a struct of generator params" )
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
				name      = "the_id"
				fieldtype = "id"
				generator = "foreign"
				params    = { property :"user"};
	    }
	    """,
	    """
	    class persistent {
	    	property
				name      = "the_id"
				fieldtype = "id"
				generator = "foreign"
				params    = '{"property":"user"}';
	    }
	    """,
	    """
	    class persistent {
	    	property
				name      = "the_id"
				fieldtype = "id"
				generator = "foreign"
				params    = "{property='user'}";
	    }
	    """
	} )
	// @formatter:on
	@ParameterizedTest
	public void testGeneratorParams( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		String		xml				= xmlToString( doc );

		Node		classEl			= doc.getDocumentElement().getFirstChild();
		Node		idNode			= classEl.getFirstChild();
		Node		generatorNode	= idNode.getLastChild();

		assertThat( generatorNode.getAttributes().getNamedItem( "class" ).getTextContent() )
		    .isEqualTo( "foreign" );

		NodeList	paramNodes	= generatorNode.getChildNodes();
		Node		paramNode	= null;

		for ( int i = 0; i < paramNodes.getLength(); i++ ) {
			Node node = paramNodes.item( i );
			if ( node.getNodeType() == Node.ELEMENT_NODE ) {
				Node nameAttr = node.getAttributes().getNamedItem( "name" );
				if ( nameAttr != null && "property".equals( nameAttr.getTextContent() ) ) {
					paramNode = node;
					break;
				}
			}
		}

		assertNotNull( paramNode );
		assertThat( paramNode.getTextContent() ).isEqualTo( "user" );
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
	    """
	} )
	public void testPropertyFormula( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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
	    		sqltype="varchar"
	    		index="nameIndex";
	    	property
	    		name="bar"
	    		column="bar"
	    		sqltype=""
	    		index="";
	    }
	    """
	} )
	public void testProperty( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node			classEL				= doc.getDocumentElement().getFirstChild();
		Node			propertyNode		= classEL.getFirstChild();
		NamedNodeMap	propertyAttributes	= propertyNode.getAttributes();

		assertThat( propertyAttributes.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "the_name" );
		assertThat( propertyAttributes.getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
		assertThat( propertyAttributes.getNamedItem( "index" ).getTextContent() )
		    .isEqualTo( "nameIndex" );

		NamedNodeMap nameColumnAttrs = propertyNode.getFirstChild().getAttributes();
		assertThat( nameColumnAttrs.getNamedItem( "sql-type" ).getTextContent() )
		    .isEqualTo( "varchar" );
		assertThat( nameColumnAttrs.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "NameColumn" );
		Node			barNode			= propertyNode.getNextSibling();
		NamedNodeMap	barColumnNode	= barNode.getFirstChild().getAttributes();
		assertThat( barColumnNode.getNamedItem( "sql-type" ).getTextContent() )
		    .isEqualTo( "varchar" );
		assertThat( barColumnNode.getNamedItem( "index" ) ).isNull();
	}

	@DisplayName( "It escapes reserved words in class and property names" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent table="case" {
	    	property name="order" column="order";
	    }
	    """
	} )
	public void testReservedWordEscaping( String sourceCode ) {
		IStruct			meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc				= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node			classEL			= doc.getDocumentElement().getFirstChild();
		NamedNodeMap	classAttributes	= classEL.getAttributes();
		assertThat( classAttributes.getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "`case`" );

		Node			propertyNode		= classEL.getFirstChild();
		NamedNodeMap	propertyAttributes	= propertyNode.getAttributes();

		assertThat( propertyAttributes.getNamedItem( "name" ).getTextContent() ).isEqualTo( "order" );
		assertThat( propertyAttributes.getNamedItem( "type" ).getTextContent() ).isEqualTo( "string" );

		Node columnNode = propertyNode.getFirstChild();
		assertThat( columnNode.getAttributes().getNamedItem( "name" ).getTextContent() ).isEqualTo( "`order`" );
	}

	@DisplayName( "It does not map properties annotated with @Persistent false" )
	@ValueSource( strings = {
	    "class persistent { property name=\"name\"; property name=\"notMapped\" persistent=\"false\"; }"
	} )
	@ParameterizedTest
	public void testPersistentFalseAnnotation( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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
	    """
	} )
	@ParameterizedTest
	public void testImmutableProperties( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		node		= classEL.getFirstChild();

		assertThat( node.getAttributes().getNamedItem( "insert" ).getTextContent() )
		    .isEqualTo( "false" );
		assertThat( node.getAttributes().getNamedItem( "update" ).getTextContent() )
		    .isEqualTo( "false" );
	}

	@DisplayName( "It generates correct xml for insert/update booleans on to-one properties" )
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="name"
	    		fieldtype="one-to-one"
	    		class="Vehicle"
	    		insert=false
	    		update=false;
	    }
	    """
	} )
	@ParameterizedTest
	public void testInsertUpdateOnOneToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Vehicle", "models.Vehicle" ), ormConfig )
		    .generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();
		Node		propertyNode	= classEL.getFirstChild();

		assertThat( propertyNode.getAttributes().getNamedItem( "insert" ) ).isNull();
		assertThat( propertyNode.getAttributes().getNamedItem( "update" ) ).isNull();

		// Node columnNode = propertyNode.getFirstChild();
		// NamedNodeMap columnAttributes = columnNode.getAttributes();
		// assertThat( columnAttributes.getNamedItem( "insert" ).getTextContent() )
		// .isEqualTo( "false" );
		// assertThat( columnAttributes.getNamedItem( "update" ).getTextContent() )
		// .isEqualTo( "false" );
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
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testLengthPrecisionScale( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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
				uniqueKey="beMoreUnique"
				table="foo"
				notNull=true
				dbDefault="test"
				name="title";
		}
		"""
	} )
	// @formatter:on
	@ParameterizedTest
	public void testOtherColumnAttributes( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

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
		assertThat( columnAttributes.getNamedItem( "unique-key" ).getTextContent() )
		    .isEqualTo( "beMoreUnique" );
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
	} )
	// @formatter:on
	@ParameterizedTest
	public void testVersionProperty( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node			classEL				= doc.getDocumentElement().getFirstChild();
		Node			versionNode			= classEL.getFirstChild();
		NamedNodeMap	versionAttributes	= versionNode.getAttributes();

		assertThat( versionNode.getNodeName() ).isEqualTo( "version" );
		assertThat( versionAttributes.getNamedItem( "name" ).getTextContent() ).isEqualTo( "version" );
		assertThat( versionAttributes.getNamedItem( "column" ).getTextContent() ).isEqualTo( "itemVersion" );
		assertThat( versionAttributes.getNamedItem( "insert" ).getTextContent() ).isEqualTo( "false" );
		assertThat( versionAttributes.getNamedItem( "type" ).getTextContent() ).startsWith( "converted::" );
		assertThat( versionAttributes.getNamedItem( "type" ).getTextContent() ).endsWith( "IntegerConverter" );
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
				class="Person"
				foreignKey="fooID"
				cascade="all-delete-orphan"
				constrained="true"
				fetch="join"
				lazy="extra";
		}
		"""
		} )
	// @formatter:on
	public void testOneToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ), ormConfig ).generateXML();

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
	    """
	} )
	// @formatter:on
	public void testOneToOneAsManyToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ), ormConfig ).generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();
		Node		oneToOneNode	= classEL.getFirstChild();
		assertEquals( "many-to-one", oneToOneNode.getNodeName() );

		NamedNodeMap attrs = oneToOneNode.getAttributes();

		assertEquals( "owner", attrs.getNamedItem( "name" ).getTextContent() );
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
	    		linkTable="link_person_owners"
	    		linkCatalog="myDB"
	    		linkSchema="dbo"
	    		fkcolumn="FK_owner"
	    		inversejoincolumn="FK_person"
	    		mappedBy="owners"
	    		orderBy="name";
	    }
	    """
	} )
	// @formatter:on
	public void testManyToManyLinkTable( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ), ormConfig ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();

		Node		bagNode		= classEL.getFirstChild();
		assertNotNull( bagNode );
		assertEquals( "bag", bagNode.getNodeName() );

		NamedNodeMap bagAttrs = bagNode.getAttributes();
		assertEquals( "owners", bagAttrs.getNamedItem( "name" ).getTextContent() );
		assertEquals( "link_person_owners", bagAttrs.getNamedItem( "table" ).getTextContent() );
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

		Node ManyToManyNode = bagNode.getLastChild();
		assertNotNull( ManyToManyNode );
		assertEquals( "many-to-many", ManyToManyNode.getNodeName() );

		NamedNodeMap manyToManyAttrs = ManyToManyNode.getAttributes();
		assertEquals( "Person", manyToManyAttrs.getNamedItem( "entity-name" ).getTextContent() );
		assertEquals( "FK_person", manyToManyAttrs.getNamedItem( "column" ).getTextContent() );
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
				class="Person"
	    		fkcolumn="FK_owner"
	    		mappedBy="owners"
	    		orderBy="name DESC"
	    		where="Age IS NOT NULL"
	    		optimisticLock="false";
	    }
	    """
	} )
	// @formatter:on
	public void testOneToMany( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ), ormConfig ).generateXML();

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
				index="idx_createdBy"
	    		persistent=true;
	    }
	    """
	} )
	// @formatter:on
	public void testManyToOne( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Manufacturer", "models.Manufacturer" ), ormConfig )
		    .generateXML();

		Node		classEL			= doc.getDocumentElement().getFirstChild();

		Node		manyToOneNode	= classEL.getLastChild();
		assertNotNull( manyToOneNode );
		assertThat( manyToOneNode.getNodeName() ).isEqualTo( "many-to-one" );

		NamedNodeMap manyToOneAttrs = manyToOneNode.getAttributes();
		assertThat( manyToOneAttrs.getNamedItem( "name" ).getTextContent() ).isEqualTo( "createdBy" );
		assertThat( manyToOneAttrs.getNamedItem( "fetch" ).getTextContent() ).isEqualTo( "select" );
		assertThat( manyToOneAttrs.getNamedItem( "cascade" ).getTextContent() ).isEqualTo( "all" );
		assertThat( manyToOneAttrs.getNamedItem( "entity-name" ).getTextContent() ).isEqualTo( "Manufacturer" );
		assertThat( manyToOneAttrs.getNamedItem( "insert" ).getTextContent() ).isEqualTo( "false" );
		assertThat( manyToOneAttrs.getNamedItem( "update" ).getTextContent() ).isEqualTo( "false" );
		assertThat( manyToOneAttrs.getNamedItem( "index" ).getTextContent() ).isEqualTo( "idx_createdBy" );
		// @TODO: Fix!
		// assertThat( manyToOneAttrs.getNamedItem( "column" ).getTextContent() ).isEqualTo( "FK_manufacturer" );
	}

	// @formatter:off
	@DisplayName( "It maps cache meta" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent cacheuse="transactional" cacheName="foo" cacheInclude="non-lazy" {}
	    """
	} )
	// @formatter:on
	public void testCacheMapping( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc			= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		Node		classEL		= doc.getDocumentElement().getFirstChild();
		Node		cacheNode	= classEL.getFirstChild();

		assertNotNull( cacheNode );
		assertEquals( "cache", cacheNode.getNodeName() );

		NamedNodeMap cacheAttrs = cacheNode.getAttributes();
		assertEquals( "transactional", cacheAttrs.getNamedItem( "usage" ).getTextContent() );
		assertEquals( "foo", cacheAttrs.getNamedItem( "region" ).getTextContent() );
		assertEquals( "non-lazy", cacheAttrs.getNamedItem( "include" ).getTextContent() );
	}

	@DisplayName( "It can handle string-delimited column names for a FOREIGN composite key" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
	    	property
	    		name="vehicles"
	    		cfc="Vehicle"
	    		fieldtype="one-to-many"
	    		class="Vehicle"
	    		linkTable="vehicles"
	    		fkcolumn="make,model";
	    }
	    """
	} )
	// @formatter:on
	public void testMultipleFKColumns( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Vehicle", "models.Vehicle" ), ormConfig )
		    .generateXML();

		Node		classEl			= doc.getDocumentElement().getFirstChild();
		Node		bagNode			= classEl.getFirstChild();
		Node		keyNode			= bagNode.getFirstChild();
		Node		manyToManyNode	= bagNode.getLastChild();
		assertThat( keyNode.getNodeName() ).isEqualTo( "key" );
		assertThat( manyToManyNode.getNodeName() ).isEqualTo( "many-to-many" );
		assertThat( keyNode.getChildNodes().getLength() ).isEqualTo( 2 );

		Node	makeKeyColumnNode	= keyNode.getFirstChild();
		Node	modelKeyColumnNode	= makeKeyColumnNode.getNextSibling();
		assertThat( makeKeyColumnNode.getNodeName() ).isEqualTo( "column" );
		assertThat( modelKeyColumnNode.getNodeName() ).isEqualTo( "column" );
		assertThat( makeKeyColumnNode.getAttributes().getNamedItem( "name" ).getTextContent() ).isEqualTo( "make" );
		assertThat( modelKeyColumnNode.getAttributes().getNamedItem( "name" ).getTextContent() ).isEqualTo( "model" );
	}

	// @formatter:off
	@DisplayName( "It can handle string-delimited column names for a composite key" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
		class persistent{
			property name="NAP" column="name,address" fieldtype="id" ormType="integer";
		}
		"""
	} )
	// @formatter:on
	public void testMultiColumnSupport( String sourceCode ) {
		IStruct		meta			= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta		= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document	doc				= new HibernateXMLWriter( entityMeta, null, ormConfig ).generateXML();

		String		xml				= xmlToString( doc );

		Node		classEl			= doc.getDocumentElement().getFirstChild();
		Node		keyNode			= classEl.getFirstChild();

		Node		nameColumn		= keyNode.getFirstChild();
		Node		addressColumn	= nameColumn.getNextSibling();

		assertThat( nameColumn.getNodeName() ).isEqualTo( "column" );
		assertThat( addressColumn.getNodeName() ).isEqualTo( "column" );

		NamedNodeMap	nameColumnAttrs		= nameColumn.getAttributes();
		NamedNodeMap	AddressColumnAttrs	= addressColumn.getAttributes();
		assertThat( nameColumnAttrs.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "name" );
		assertThat( AddressColumnAttrs.getNamedItem( "name" ).getTextContent() )
		    .isEqualTo( "address" );
	}

	@DisplayName( "It can customize the table and column names with a naming strategy" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent entityName="PersonAddress" {
	    	property
	    		name="PersonID"
	    		fieldtype="id"
	    		ormtype="integer";
	    }
	    """
	} )
	public void testNamingStrategy( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );

		IStruct		properties	= new Struct();
		properties.put( "ignoreParseErrors", "true" );
		properties.put( "namingStrategy", "src.test.resources.app.CustomNamingStrategy" );
		ORMConfig		configWithNamingStrategy	= new ORMConfig( properties, context.getRequestContext() );
		Document		doc							= new HibernateXMLWriter( entityMeta, null, configWithNamingStrategy ).generateXML();

		Node			classEL						= doc.getDocumentElement().getFirstChild();
		NamedNodeMap	classAttributes				= classEL.getAttributes();
		assertThat( classAttributes.getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "tbl_PersonAddress" );

		Node	propertyNode	= classEL.getFirstChild();
		Node	columnNode		= propertyNode.getFirstChild();
		assertThat( columnNode.getAttributes().getNamedItem( "name" ).getTextContent() ).isEqualTo( "col_PersonID" );
	}

	@DisplayName( "It can customize the table and column names with the 'smart' naming strategy" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent entityName="PersonAddress" {
	    	property
	    		name="PersonId"
	    		fieldtype="id"
	    		ormtype="integer";
	    }
	    """
	} )
	public void testMacroNamingStrategy( String sourceCode ) {
		IStruct		meta		= getClassMetaFromCode( sourceCode );

		IEntityMeta	entityMeta	= AbstractEntityMeta.autoDiscoverMetaType( meta );

		IStruct		properties	= new Struct();
		properties.put( "ignoreParseErrors", "true" );
		properties.put( "namingStrategy", "smart" );
		ORMConfig		configWithNamingStrategy	= new ORMConfig( properties, context.getRequestContext() );
		Document		doc							= new HibernateXMLWriter( entityMeta, null, configWithNamingStrategy ).generateXML();

		Node			classEL						= doc.getDocumentElement().getFirstChild();
		NamedNodeMap	classAttributes				= classEL.getAttributes();
		assertThat( classAttributes.getNamedItem( "table" ).getTextContent() )
		    .isEqualTo( "PERSON_ADDRESS" );

		Node	propertyNode	= classEL.getFirstChild();
		Node	columnNode		= propertyNode.getFirstChild();
		assertThat( columnNode.getAttributes().getNamedItem( "name" ).getTextContent() ).isEqualTo( "PERSON_ID" );
	}

	@Disabled( "Unimplemented" )
	@DisplayName( "It can map an array/bag collection" )
	@Test
	public void testArrayCollection() {
	}

	// @formatter:off
	@DisplayName( "It can map a struct/map collection" )
	@ParameterizedTest
	@ValueSource( strings = {
	    """
	    class persistent {
		    property
				name="businessYear"
				fieldtype="one-to-many"
				type="struct"
				structKeyType="string"
				elementType="date"
				structKeyColumn="year"
				class="Person"
				elementColumn="date";
	    }
	    """,
		// test that structkeytype defaults to 'string'
	    """
	    class persistent {
		    property
				name="businessYear"
				fieldtype="one-to-many"
				type="struct"
				// structKeyType="string"
				elementType="date"
				class="Person"
				structKeyColumn="year"
				elementColumn="date";
	    }
	    """
	} )
	// @formatter:on
	public void testStructCollection( String sourceCode ) {
		IStruct			meta				= getClassMetaFromCode( sourceCode );

		IEntityMeta		entityMeta			= AbstractEntityMeta.autoDiscoverMetaType( meta );
		Document		doc					= new HibernateXMLWriter( entityMeta, ( a, b ) -> new EntityRecord( "Person", "models.Person" ), ormConfig )
		    .generateXML();

		Node			classEl				= doc.getDocumentElement().getFirstChild();
		Node			mapNode				= classEl.getLastChild();
		Node			mapKeyNode			= mapNode.getFirstChild();
		NamedNodeMap	mapKeyAttributes	= mapKeyNode.getAttributes();

		assertThat( mapKeyAttributes.getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "`year`" );
		assertThat( mapKeyAttributes.getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "string" );
		NamedNodeMap elementNodeAttributes = mapKeyNode.getNextSibling().getAttributes();

		assertThat( elementNodeAttributes.getNamedItem( "column" ).getTextContent() )
		    .isEqualTo( "`date`" );
		assertThat( elementNodeAttributes.getNamedItem( "type" ).getTextContent() )
		    .isEqualTo( "date" );
	}

	/**
	 * @TODO: The following ORM annotations are still lacking tests at either the entity level, the property level, or both:
	 *        embedXml
	 *        missingRowIgnored
	 *        joinColumn
	 *        inverse
	 *        structkeydatatype ?? ACF only?
	 *        unSavedValue - deprecated
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
