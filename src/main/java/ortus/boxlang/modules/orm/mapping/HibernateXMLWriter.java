package ortus.boxlang.modules.orm.mapping;

import java.util.function.Predicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

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

		inspector.getPrimaryKeyProperties().stream()
		    .forEach( ( prop ) -> {
			    IStruct propStruct = ( IStruct ) prop;
			    Element keyColumn = doc.createElement( "id" );
			    keyColumn.setAttribute( "name", propStruct.getAsString( Key._name ) );
			    if ( propStruct.containsKey( Key.type ) ) {
				    keyColumn.setAttribute( "type", propStruct.getAsString( Key.type ) );
			    }
			    // @TODO: Where's the best place to compute these if absent? Here or in the ORMMetaInspector?
			    if ( propStruct.containsKey( Key.column ) ) {
				    keyColumn.setAttribute( "column", propStruct.getAsString( Key.column ) );
			    }
			    // Element idGeneratorEl = doc.createElement( "generator" );
			    // idGeneratorEl.setAttribute( "class", inspector.getIDPropertyGenerator() );
			    // keyColumn.appendChild( idGeneratorEl );

			    classElement.appendChild( keyColumn );
		    } );

		// get properties

		inspector.getProperties().stream()
		    .filter( Predicate.not( ORMAnnotationInspector::isIDProperty ) )
		    .filter( ORMAnnotationInspector::isMappableProperty )
		    .forEach( ( prop ) -> {
			    Element propEl = doc.createElement( "property" );
			    propEl.setAttribute( "name", inspector.getPropertyName( prop ) );
			    propEl.setAttribute( "type", inspector.getPropertyType( prop ) );

			    String column = inspector.getPropertyColumn( prop );
			    if ( column != null ) {
				    propEl.setAttribute( "column", column );
			    }

			    classElement.appendChild( propEl );
		    } );

		return classElement;
	}

}
