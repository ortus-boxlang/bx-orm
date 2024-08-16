package ortus.boxlang.modules.orm.mapping;

import java.util.Map;
import java.util.function.Predicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.JSONUtil;

public class HibernateXMLWriter implements IPersistenceWriter {

	private static final Logger logger = LoggerFactory.getLogger( HibernateXMLWriter.class );

	@Override
	public Document generateXML( ORMAnnotationInspector inspector ) {
		DocumentBuilderFactory	factory	= DocumentBuilderFactory.newInstance();
		DocumentBuilder			builder;
		try {
			builder = factory.newDocumentBuilder();

			DocumentType	doctype	= builder.getDOMImplementation().createDocumentType( "doctype", "-//Hibernate/Hibernate Mapping DTD 3.0//EN",
			    "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd" );
			Document		doc		= builder.getDOMImplementation().createDocument( null,
			    "hibernate-mapping", doctype );

			doc.getDocumentElement().appendChild( generateClassElement( doc, inspector ) );

			return doc;
		} catch ( ParserConfigurationException e ) {
			// @TODO: Check ORMConfig.skipCFCWithError and throw if false.
			e.printStackTrace();
			logger.error( "Error creating Hibernate XML document: {}", e.getMessage(), e );
		}

		return null;
	}

	/**
	 * Generate a &lt;property /&gt; element for the given property metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>name</li>
	 * <li>type</li>
	 * <li>column</li>
	 * <li>unsavedValue</li>
	 * <li>and many, many more to come</li>
	 * </ul>
	 * 
	 * @param doc       Parent document to use for creating the element
	 * @param prop      Property metadata in struct form
	 * @param inspector ORMAnnotationInspector instance to use for inspecting and manipulating property metadata
	 * 
	 * @return A &lt;property /&gt; element ready to add to a Hibernate mapping document
	 */
	private Element generatePropertyElement( Document doc, IStruct prop, ORMAnnotationInspector inspector ) {
		Element theNode = doc.createElement( "property" );
		theNode.setAttribute( "name", inspector.getPropertyName( prop ) );
		theNode.setAttribute( "type", inspector.getPropertyType( prop ) );

		String column = inspector.getPropertyColumn( prop );
		if ( column != null ) {
			theNode.setAttribute( "column", column );
		}
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.notNull ) ) {
			theNode.setAttribute( "not-null", inspector.getPropertyAnnotation( prop, ORMKeys.notNull ) );
		}
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.unsavedValue ) ) {
			theNode.setAttribute( "unsaved-value", inspector.getPropertyAnnotation( prop, ORMKeys.unsavedValue ) );
		}

		return theNode;
	}

	/**
	 * Generate a &lt;id /&gt; element for the given property metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>name</li>
	 * <li>type</li>
	 * <li>column</li>
	 * <li>unsavedValue</li>
	 * <li>access</li>
	 * <li>length</li>
	 * <li>and many, many more to come</li>
	 * </ul>
	 * 
	 * @param doc       Parent document to use for creating the element
	 * @param prop      Property metadata in struct form
	 * @param inspector ORMAnnotationInspector instance to use for inspecting and manipulating property metadata
	 * 
	 * @return A &lt;id /&gt; element ready to add to a Hibernate mapping document
	 */
	private Element generateIdElement( Document doc, IStruct prop, ORMAnnotationInspector inspector ) {
		Element	theNode		= doc.createElement( "id" );
		String	propName	= prop.getAsString( Key._name );
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.generator ) ) {
			theNode.appendChild( generateGeneratorElement( doc, prop, inspector ) );
			// @TODO: Determine ID type from generator type IF a generator is specified.
		}

		// compute defaults - move to ORMAnnotationInspector?
		// prop.getAsStruct( Key.annotations ).computeIfAbsent( ORMKeys.ORMType, ( key ) -> "string" );

		// set common attributes
		theNode.setAttribute( "name", propName );
		theNode.setAttribute( "type", inspector.getPropertyAnnotation( prop, ORMKeys.ORMType ) );

		// set conditional attributes
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.unsavedValue ) ) {
			theNode.setAttribute( "unsaved-value", inspector.getPropertyAnnotation( prop, ORMKeys.unsavedValue ) );
		}
		if ( inspector.hasPropertyAnnotation( prop, Key.column ) ) {
			theNode.setAttribute( "column", inspector.getPropertyAnnotation( prop, Key.column ) );
		}

		return theNode;
	}

	/**
	 * Generate a &lt;generator/&gt; element for the given property metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>generator</li>
	 * <li>params</li>
	 * <li>sequence</li>
	 * <li>selectKey</li>
	 * <li>generated</li>
	 * </ul>
	 * 
	 * @param doc       Parent document to use for creating the element
	 * @param prop      Property metadata in struct form
	 * @param inspector ORMAnnotationInspector instance to use for inspecting and manipulating property metadata
	 * 
	 * @return A &lt;generator /&gt; element ready to add to a Hibernate mapping document
	 */
	private Element generateGeneratorElement( Document doc, IStruct prop, ORMAnnotationInspector inspector ) {
		String	propName		= prop.getAsString( Key._name );

		Element	theNode			= doc.createElement( "generator" );
		String	generatorType	= inspector.getPropertyAnnotation( prop, ORMKeys.generator );
		theNode.setAttribute( "class", generatorType );
		IStruct params = new Struct();

		// generator=foreign
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.property ) ) {
			params.put( "property", inspector.getPropertyAnnotation( prop, ORMKeys.property ) );
		}
		// generator=select
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.selectKey ) ) {
			params.put( "key", inspector.getPropertyAnnotation( prop, ORMKeys.selectKey ) );
		}
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.generated ) ) {
			params.put( "generated", inspector.getPropertyAnnotation( prop, ORMKeys.generated ) );
		}
		// generator=sequence|sequence-identity
		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.sequence ) ) {
			params.put( "sequence", inspector.getPropertyAnnotation( prop, ORMKeys.sequence ) );
		}
		if ( inspector.hasPropertyAnnotation( prop, Key.params ) ) {
			Object	paramValue			= prop.getAsStruct( Key.annotations ).getAsString( Key.params );
			IStruct	additionalParams	= Struct.fromMap( ( Map ) JSONUtil.fromJSON( paramValue ) );
			if ( params == null ) {
				logger.warn( "Property '{}' has a 'params' annotation that could not be cast to a struct: {}", propName, paramValue );
				return theNode;
			} else {
				params.putAll( additionalParams );
			}
		}
		params.forEach( ( key, value ) -> {
			Element paramEl = doc.createElement( "param" );
			paramEl.setAttribute( "name", key.getName() );
			paramEl.setTextContent( value.toString() );
			theNode.appendChild( paramEl );
		} );
		return theNode;
	}

	/**
	 * Generate the top-level &lt;class /&gt; element containing entity mapping metadata.
	 * 
	 * @param doc       Parent document to use for creating the element
	 * @param inspector ORMAnnotationInspector instance preloaded with all entity metadata.
	 * 
	 * @return A &lt;class /&gt; element containing entity keys, properties, and other Hibernate mapping metadata.
	 */
	private Element generateClassElement( Document doc, ORMAnnotationInspector inspector ) {
		Element classElement = doc.createElement( "class" );
		classElement.setAttribute( "entity-name", inspector.getEntityName() );
		classElement.setAttribute( "table", inspector.getTableName() );

		// general class attributes:
		// entity-name
		// batch-size
		// dynamic-insert
		// dynamic-update
		// lazy
		// select-before-update
		// optimistic-lock
		// mutable
		// rowid
		// where
		// And, if no discriminator or joinColumn is present:
		// schema
		// catalog
		// table

		// generate keys, aka <id> elements
		inspector.getPrimaryKeyProperties().stream()
		    .forEach( ( prop ) -> {
			    classElement.appendChild( generateIdElement( doc, ( IStruct ) prop, inspector ) );
		    } );

		// generate properties, aka <property> elements
		inspector.getProperties().stream()
		    .map( IStruct.class::cast )
		    .filter( Predicate.not( ORMAnnotationInspector::isIDProperty ) )
		    .filter( ORMAnnotationInspector::isPersistentProperty )
		    .forEach( ( prop ) -> {
			    classElement.appendChild( generatePropertyElement( doc, prop, inspector ) );
		    } );

		// @TODO: generate <discriminator> elements
		// @TODO: generate <subclass> elements
		// @TODO: generate <joined-subclass> elements
		// @TODO: generate <union-subclass> elements
		// @TODO: generate <version>
		// @TODO: generate <many-to-one>
		// @TODO: generate <one-to-one>
		// @TODO: generate <version>

		return classElement;
	}

}
