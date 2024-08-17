package ortus.boxlang.modules.orm.mapping;

import java.util.Map;

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

		if ( inspector.hasPropertyAnnotation( prop, ORMKeys.formula ) ) {
			theNode.setAttribute( "formula", "(" + inspector.getPropertyAnnotation( prop, ORMKeys.formula ) + ")" );
		} else {
			Element columnNode = doc.createElement( "column" );
			theNode.appendChild( columnNode );
			String column = inspector.getPropertyColumn( prop );
			if ( column != null ) {
				columnNode.setAttribute( "name", column );
			}
			if ( inspector.isPropertyNotNull( prop ) ) {
				columnNode.setAttribute( "not-null", "true" );
			}
			if ( inspector.hasPropertyAnnotation( prop, ORMKeys.unsavedValue ) ) {
				columnNode.setAttribute( "unsaved-value", inspector.getPropertyAnnotation( prop, ORMKeys.unsavedValue ) );
			}
			if ( inspector.hasPropertyAnnotation( prop, ORMKeys.check ) ) {
				columnNode.setAttribute( "check", inspector.getPropertyAnnotation( prop, ORMKeys.check ) );
			}
			if ( inspector.hasPropertyAnnotation( prop, ORMKeys.dbDefault ) ) {
				columnNode.setAttribute( "default", inspector.getPropertyAnnotation( prop, ORMKeys.dbDefault ) );
			}
			if ( inspector.hasPropertyAnnotation( prop, Key.length ) ) {
				columnNode.setAttribute( "length", inspector.getPropertyAnnotation( prop, Key.length ) );
			}
			if ( inspector.hasPropertyAnnotation( prop, ORMKeys.precision ) ) {
				columnNode.setAttribute( "precision", inspector.getPropertyAnnotation( prop, ORMKeys.precision ) );
			}
			if ( inspector.hasPropertyAnnotation( prop, ORMKeys.scale ) ) {
				columnNode.setAttribute( "scale", inspector.getPropertyAnnotation( prop, ORMKeys.scale ) );
			}
			if ( inspector.hasPropertyAnnotation( prop, Key.sqltype ) ) {
				columnNode.setAttribute( "sql-type", inspector.getPropertySqlType( prop ) );
			}
			if ( inspector.isPropertyUnique( prop ) ) {
				columnNode.setAttribute( "unique", "true" );
			}
			String uniqueKey = inspector.getPropertyUniqueKey( prop );
			if ( uniqueKey != null ) {
				columnNode.setAttribute( "unique-key", uniqueKey );
			}
		}
		// @TODO: generated
		if ( !inspector.isPropertyInsertable( prop ) ) {
			theNode.setAttribute( "insert", "false" );
		}
		if ( !inspector.isPropertyUpdatable( prop ) ) {
			theNode.setAttribute( "update", "false" );
		}
		if ( inspector.isPropertyLazy( prop ) ) {
			theNode.setAttribute( "lazy", "true" );
		}
		if ( !inspector.isPropertyLockable( prop ) ) {
			theNode.setAttribute( "optimistic-lock", "false" );
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

		// general class attributes:
		classElement.setAttribute( "entity-name", inspector.getEntityName() );
		if ( inspector.hasEntityAnnotation( ORMKeys.batchsize ) ) {
			classElement.setAttribute( "batch-size", inspector.getEntityAnnotation( ORMKeys.batchsize ) );
		}
		if ( inspector.isDynamicInsert() ) {
			classElement.setAttribute( "dynamic-insert", "true" );
		}
		if ( inspector.isDynamicUpdate() ) {
			classElement.setAttribute( "dynamic-update", "true" );
		}
		if ( inspector.hasEntityAnnotation( ORMKeys.lazy ) ) {
			classElement.setAttribute( "lazy", inspector.getEntityAnnotation( ORMKeys.lazy ) );
		}
		if ( inspector.isSelectBeforeUpdate() ) {
			classElement.setAttribute( "rowid", "true" );
		}
		if ( inspector.hasEntityAnnotation( ORMKeys.optimisticLock ) ) {
			classElement.setAttribute( "optimistic-lock", inspector.getEntityAnnotation( ORMKeys.optimisticLock ) );
		}
		if ( inspector.isReadOnly() ) {
			classElement.setAttribute( "mutable", "false" );
		}
		if ( inspector.hasEntityAnnotation( ORMKeys.rowid ) ) {
			classElement.setAttribute( "rowid", inspector.getEntityAnnotation( ORMKeys.rowid ) );
		}
		if ( inspector.hasEntityAnnotation( ORMKeys.where ) ) {
			classElement.setAttribute( "where", inspector.getEntityAnnotation( ORMKeys.where ) );
		}

		// And, if no discriminator or joinColumn is present:
		if ( inspector.needsTableCatalogSchema() ) {
			String tableName = inspector.getTableName();
			if ( tableName != null ) {
				classElement.setAttribute( "table", tableName );
			}
			if ( inspector.hasEntityAnnotation( ORMKeys.schema ) ) {
				classElement.setAttribute( "schema", inspector.getEntityAnnotation( ORMKeys.schema ) );
			}
			if ( inspector.hasEntityAnnotation( ORMKeys.catalog ) ) {
				classElement.setAttribute( "catalog", inspector.getEntityAnnotation( ORMKeys.catalog ) );
			}
		}

		// generate keys, aka <id> elements
		inspector.getIdProperties().stream()
		    .forEach( ( prop ) -> {
			    classElement.appendChild( generateIdElement( doc, ( IStruct ) prop, inspector ) );
		    } );

		// generate properties, aka <property> elements
		inspector.getProperties().stream()
		    .map( IStruct.class::cast )
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
