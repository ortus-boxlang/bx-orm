package ortus.boxlang.orm.mapping;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import ortus.boxlang.runtime.scopes.Key;

public class HibernateXMLWriter implements IPersistenceWriter {

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

			doc.getDocumentElement().appendChild( populateClassElement( doc, inspector ) );

			return doc;
		} catch ( ParserConfigurationException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private Element populateClassElement( Document doc, ORMAnnotationInspector inspector ) {
		Element classElement = doc.createElement( "class" );
		classElement.setAttribute( "entity-name", inspector.getEntityName() );
		classElement.setAttribute( "table", inspector.getTable() );

		Element idEl = doc.createElement( "id" );
		idEl.setAttribute( "name", inspector.getIdPropertyName() );
		idEl.setAttribute( "column", inspector.getIdPropertyName() );
		idEl.setAttribute( "type", inspector.getIDPropertyType() );
		idEl.setAttribute( "column", inspector.getIdPropertyName() );

		Element idGeneratorEl = doc.createElement( "generator" );
		idGeneratorEl.setAttribute( "class", inspector.getIDPropertyGenerator() );

		idEl.appendChild( idGeneratorEl );

		classElement.appendChild( idEl );
		// get properties

		inspector.getProperties().stream()
		    .filter( ( prop ) -> !prop.annotations().containsKey( Key.id ) )
		    .forEach( ( prop ) -> {
			    Element propEl = doc.createElement( "property" );
			    propEl.setAttribute( "name", inspector.getPropertyName( prop ) );
			    propEl.setAttribute( "type", inspector.getPropertyType( prop ) );

			    classElement.appendChild( propEl );
		    } );

		return classElement;
	}

}