package ortus.boxlang.modules.orm.mapping;

import java.util.function.Predicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

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

		return theNode;
	}

	/**
	 * Generate a &lt;id /&gt; element for the given property metadata.
	 * 
	 * @param doc       Parent document to use for creating the element
	 * @param prop      Property metadata in struct form
	 * @param inspector ORMAnnotationInspector instance to use for inspecting and manipulating property metadata
	 * 
	 * @return A &lt;id /&gt; element ready to add to a Hibernate mapping document
	 */
	private Element generateKeyElement( Document doc, IStruct prop, ORMAnnotationInspector inspector ) {
		Element theNode = doc.createElement( "id" );
		theNode.setAttribute( "name", prop.getAsString( Key._name ) );
		if ( prop.containsKey( Key.type ) ) {
			theNode.setAttribute( "type", prop.getAsString( Key.type ) );
		}
		// @TODO: Where's the best place to compute these if absent? Here or in the ORMAnnotationInspector?
		if ( prop.containsKey( Key.column ) ) {
			theNode.setAttribute( "column", prop.getAsString( Key.column ) );
		}
		// Element idGeneratorEl = doc.createElement( "generator" );
		// idGeneratorEl.setAttribute( "class", inspector.getIDPropertyGenerator() );
		// theNode.appendChild( idGeneratorEl );

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

		inspector.getPrimaryKeyProperties().stream()
		    .forEach( ( prop ) -> {
			    logger.warn( "Generating key element for property: {}", prop.toString() );
			    classElement.appendChild( generateKeyElement( doc, ( IStruct ) prop, inspector ) );
		    } );

		// get properties

		inspector.getProperties().stream()
		    .map( IStruct.class::cast )
		    .filter( Predicate.not( ORMAnnotationInspector::isIDProperty ) )
		    .filter( ORMAnnotationInspector::isMappableProperty )
		    .forEach( ( prop ) -> {
			    classElement.appendChild( generatePropertyElement( doc, prop, inspector ) );
		    } );

		return classElement;
	}

}
