package ortus.boxlang.modules.orm.mapping;

import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class HibernateXMLWriter implements IPersistenceWriter {

	private static final Logger	logger	= LoggerFactory.getLogger( HibernateXMLWriter.class );

	/**
	 * IEntityMeta instance which represents the parsed entity metadata in a normalized form.
	 * <p>
	 * The source of this metadata could be CFML persistent
	 * annotations like `persistent=true` and `fieldtype="id"` OR modern BoxLang-syntax, JPA-style annotations like `@Entity` and `@Id`.
	 */
	IEntityMeta					entity;

	/**
	 * XML Document root, created by the constructor.
	 * <p>
	 * This is the root element of the Hibernate mapping document, and will be returned by {@link #generateXML()}.
	 */
	Document					document;

	public HibernateXMLWriter( IEntityMeta entity ) {
		this.entity = entity;

		// Validation
		if ( entity.getIdProperties().isEmpty() ) {
			logger.error( "Entity {} has no ID properties. Hibernate requires at least one.", entity.getEntityName() );
			// @TODO: Check ORMConfig.ignoreParseErrors and throw if false.
			// throw new BoxRuntimeException( "Entity %s has no ID properties. Hibernate requires at least one.".formatted( entity.getEntityName() ) );
		}

		this.document = createDocument();
	}

	public Document createDocument() {
		DocumentBuilderFactory	factory	= DocumentBuilderFactory.newInstance();
		DocumentBuilder			builder;
		try {
			builder = factory.newDocumentBuilder();

			DocumentType	doctype			= builder.getDOMImplementation().createDocumentType( "doctype", "-//Hibernate/Hibernate Mapping DTD 3.0//EN",
			    "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd" );
			Document		rootDocument	= builder.getDOMImplementation().createDocument( null, "hibernate-mapping", doctype );
			rootDocument.insertBefore( rootDocument.createComment(
			    """
			    \n~ Generated by the Ortus BoxLang ORM module for use in BoxLang web applications.
			    ~
			    ~ https://github.com/ortus-boxlang/bx-orm
			    ~ https://boxlang.io
			    ~ https://docs.jboss.org/hibernate/orm/5.0/manual/en-US/html/ch05.html
			    """
			), rootDocument.getDocumentElement() );
			return rootDocument;
		} catch ( ParserConfigurationException e ) {
			// @TODO: Check ORMConfig.ignoreParseErrors and throw if false.
			e.printStackTrace();
			logger.error( "Error creating Hibernate XML document: {}", e.getMessage(), e );
			throw new BoxRuntimeException( "Error creating Hibernate XML document: " + e.getMessage(), e );
		}
	}

	@Override
	public Document generateXML() {
		this.document.getDocumentElement().appendChild( generateClassElement() );
		return this.document;
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
	 * @param prop Property metadata in struct form
	 *
	 * @return A &lt;property /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generatePropertyElement( IPropertyMeta prop ) {
		IStruct	columnInfo	= prop.getColumn();
		IStruct	types		= prop.getTypes();

		Element	theNode		= this.document.createElement( "property" );
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", types.getAsString( ORMKeys.ORMType ) );

		if ( prop.getFormula() != null ) {
			theNode.setAttribute( "formula", "(" + prop.getFormula() + ")" );
		} else {
			theNode.appendChild( generateColumnElement( prop ) );
		}
		if ( columnInfo.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.insertable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.updateable ) ) {
			theNode.setAttribute( "update", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.updateable ) ) );
		}
		if ( prop.getLazy() != null ) {
			theNode.setAttribute( "lazy", prop.getLazy() );
		}
		if ( !prop.isOptimisticLock() ) {
			theNode.setAttribute( "optimistic-lock", "false" );
		}

		return theNode;
	}

	/**
	 * Create a *-to-many association in these steps:
	 * 1. Create a collection element (bag, set, list, map, array, primitive-array)
	 * 2. Add attributes to the collection element
	 * 3. Add a key element
	 * 4. Add a one-to-many or many-to-many element
	 * 
	 * @param prop Property meta, containing association and column metadata
	 * 
	 * @return
	 */
	public Element generateToManyAssociation( IPropertyMeta prop ) {
		IStruct	association		= prop.getAssociation();
		IStruct	columnInfo		= prop.getColumn();
		Element	collectionNode	= generateCollectionElement( prop, association, columnInfo );
		if ( association.containsKey( ORMKeys.table ) ) {
			collectionNode.setAttribute( "table", association.getAsString( ORMKeys.table ) );
		}
		if ( association.containsKey( ORMKeys.schema ) ) {
			collectionNode.setAttribute( "schema", association.getAsString( ORMKeys.schema ) );
		}
		if ( association.containsKey( ORMKeys.catalog ) ) {
			collectionNode.setAttribute( "catalog", association.getAsString( ORMKeys.catalog ) );
		}
		Element toManyNode = this.document.createElement( association.getAsString( Key.type ) );
		// now here, we create the <one-to-many> or <many-to-many> element
		if ( association.containsKey( ORMKeys.inverseJoinColumn ) ) {
			// @TODO: Loop over all column values and create multiple <column> elements.
			toManyNode.setAttribute( "column", association.getAsString( ORMKeys.inverseJoinColumn ) );
		}
		collectionNode.appendChild( toManyNode );
		return collectionNode;
	}

	/**
	 * Create a bag or map collection element for the given property metadata.
	 * <p>
	 * May also create &lt;map-key&gt; &lt;element&gt; xml nodes.
	 * 
	 * @param prop        Property meta, containing association and column metadata
	 * @param association Association-specific metadata
	 * @param columnInfo  Column-specific metadata
	 * 
	 * @return The collection node of either bag or map type.
	 */
	private Element generateCollectionElement( IPropertyMeta prop, IStruct association, IStruct columnInfo ) {
		String	type	= association.getAsString( ORMKeys.collectionType );
		// Cite: https://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/collections.html#d0e10663
		Element	theNode	= this.document.createElement( type );

		if ( type == "map" ) {
			if ( association.containsKey( ORMKeys.structKeyColumn ) ) {
				Element mapKeyNode = this.document.createElement( "map-key" );
				theNode.appendChild( mapKeyNode );
				// Note that Lucee doesn't support comma-delimited values in structKeyColumn
				mapKeyNode.setAttribute( "column", association.getAsString( ORMKeys.structKeyColumn ) );
				if ( association.containsKey( ORMKeys.structKeyType ) ) {
					mapKeyNode.setAttribute( "type", association.getAsString( ORMKeys.structKeyType ) );
				}
				// NEW in BoxLang.
				if ( association.containsKey( ORMKeys.structKeyFormula ) ) {
					mapKeyNode.setAttribute( "formula", association.getAsString( ORMKeys.structKeyFormula ) );
				}
			}
		}
		if ( association.containsKey( ORMKeys.elementColumn ) ) {
			Element elementNode = this.document.createElement( "element" );
			theNode.appendChild( elementNode );
			// Note that Lucee doesn't support comma-delimited values in elementColumn
			elementNode.setAttribute( "column", association.getAsString( ORMKeys.elementColumn ) );
			if ( association.containsKey( ORMKeys.elementType ) ) {
				elementNode.setAttribute( "type", association.getAsString( ORMKeys.elementType ) );
			}
			if ( association.containsKey( ORMKeys.elementFormula ) ) {
				elementNode.setAttribute( "formula", association.getAsString( ORMKeys.elementFormula ) );
			}
		}

		theNode.setAttribute( "name", prop.getName() );
		if ( columnInfo.containsKey( ORMKeys.table ) ) {
			theNode.setAttribute( "table", columnInfo.getAsString( Key.table ) );
		}
		if ( columnInfo.containsKey( ORMKeys.schema ) ) {
			theNode.setAttribute( "schema", columnInfo.getAsString( ORMKeys.schema ) );
		}
		if ( association.containsKey( ORMKeys.lazy ) ) {
			theNode.setAttribute( "lazy", association.getAsString( ORMKeys.lazy ) );
		}
		if ( association.containsKey( ORMKeys.inverse ) ) {
			theNode.setAttribute( "inverse", trueFalseFormat( association.getAsBoolean( ORMKeys.inverse ) ) );
		}
		if ( association.containsKey( ORMKeys.cascade ) ) {
			theNode.setAttribute( "cascade", association.getAsString( ORMKeys.cascade ) );
		}
		if ( association.containsKey( ORMKeys.orderBy ) ) {
			theNode.setAttribute( "order-by", association.getAsString( ORMKeys.orderBy ) );
		}
		if ( association.containsKey( ORMKeys.where ) ) {
			theNode.setAttribute( "where", association.getAsString( ORMKeys.where ) );
		}
		if ( association.containsKey( ORMKeys.fetch ) ) {
			theNode.setAttribute( "fetch", association.getAsString( ORMKeys.fetch ) );
		}
		if ( association.containsKey( ORMKeys.optimisticLock ) ) {
			theNode.setAttribute( "optimistic-lock", association.getAsString( ORMKeys.optimisticLock ) );
		}
		if ( association.containsKey( ORMKeys.immutable ) ) {
			theNode.setAttribute( "mutable", trueFalseFormat( !association.getAsBoolean( ORMKeys.immutable ) ) );
		}
		if ( association.containsKey( ORMKeys.embedXML ) ) {
			theNode.setAttribute( "embedXML", association.getAsString( ORMKeys.embedXML ) );
		}

		// @JoinColumn - https://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/collections.html#collections-foreignkeys
		if ( association.containsKey( ORMKeys.fkcolumn ) ) {
			Element keyNode = this.document.createElement( "key" );
			// @TODO: Loop over all column values and create multiple <column> elements.
			keyNode.setAttribute( "column", association.getAsString( ORMKeys.fkcolumn ) );

			if ( association.containsKey( ORMKeys.mappedBy ) ) {
				keyNode.setAttribute( "property-ref", association.getAsString( ORMKeys.mappedBy ) );
			}
			theNode.appendChild( keyNode );
		}
		return theNode;
	}

	/**
	 * Generate a &lt;one-to-one/&gt; or &lt;many-to-one/&gt; association element for the given property metadata.
	 *
	 * @param prop Property metadata in struct form
	 *
	 * @return A &lt;one-to-one/&gt; or &lt;many-to-one/&gt;, element ready to add to a Hibernate mapping document
	 */
	public Element generateToOneAssociation( IPropertyMeta prop ) {
		IStruct	association	= prop.getAssociation();
		String	type		= association.getAsString( Key.type );
		Element	theNode		= this.document.createElement( type );
		if ( association.containsKey( ORMKeys.cascade ) ) {
			theNode.setAttribute( "cascade", association.getAsString( ORMKeys.cascade ) );
		}
		if ( association.containsKey( ORMKeys.constrained ) && association.getAsBoolean( ORMKeys.constrained ) ) {
			theNode.setAttribute( "constrained", "true" );
		}
		if ( association.containsKey( ORMKeys.fetch ) ) {
			theNode.setAttribute( "fetch", association.getAsString( ORMKeys.fetch ) );
		}
		if ( association.containsKey( ORMKeys.mappedBy ) ) {
			theNode.setAttribute( "property-ref", association.getAsString( ORMKeys.mappedBy ) );
		}
		if ( association.containsKey( ORMKeys.access ) ) {
			theNode.setAttribute( "access", association.getAsString( ORMKeys.access ) );
		}
		if ( prop.getFormula() != null ) {
			theNode.setAttribute( "formula", prop.getFormula() );
		}
		if ( association.containsKey( ORMKeys.lazy ) ) {
			theNode.setAttribute( "lazy", association.getAsString( ORMKeys.lazy ) );
		}
		// @TODO: entity-name="EntityName"
		if ( association.containsKey( ORMKeys.embedXML ) ) {
			theNode.setAttribute( "embed-xml", association.getAsString( ORMKeys.embedXML ) );
		}
		if ( association.containsKey( ORMKeys.foreignKey ) ) {
			theNode.setAttribute( "foreign-key", association.getAsString( ORMKeys.foreignKey ) );
		}
		if ( association.containsKey( Key.column ) ) {
			// @TODO: Loop over all column values and create multiple <column> elements.
			Element columnNode = this.document.createElement( "column" );
			columnNode.setAttribute( "name", association.getAsString( Key.column ) );
			theNode.appendChild( columnNode );
		}

		// for attributes specific to each association type
		switch ( type ) {
			case "one-to-one" :
				break;
			case "many-to-one" :
				if ( association.containsKey( ORMKeys.unique ) ) {
					theNode.setAttribute( "unique", trueFalseFormat( association.getAsBoolean( ORMKeys.unique ) ) );
				}
				if ( association.containsKey( ORMKeys.missingRowIgnored ) ) {
					theNode.setAttribute( "not-found", association.getAsString( ORMKeys.missingRowIgnored ) );
				}
				// @TODO: unique-key
				break;
		}
		return theNode;
	}

	/**
	 * Generate a &lt;column /&gt; element for the given column metadata.
	 * <p>
	 * A column element can be used in a property, id, key, or other element to define column metadata.
	 *
	 * @TODO: Refactor all key logic into a getPropertyColumn() method which groups and combines all the various column-specific annotations.
	 *
	 * @param prop Column metadata
	 *
	 * @return A &lt;column /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateColumnElement( IPropertyMeta prop ) {
		Element	theNode		= this.document.createElement( "column" );
		IStruct	columnInfo	= prop.getColumn();
		IStruct	types		= prop.getTypes();

		theNode.setAttribute( "name", prop.getName() );
		if ( columnInfo.containsKey( ORMKeys.nullable ) ) {
			theNode.setAttribute( "not-null", trueFalseFormat( !columnInfo.getAsBoolean( ORMKeys.nullable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.unique ) ) {
			theNode.setAttribute( "unique", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.unique ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.length ) ) {
			theNode.setAttribute( "length", columnInfo.getAsString( ORMKeys.length ) );
		}
		if ( columnInfo.containsKey( ORMKeys.precision ) ) {
			theNode.setAttribute( "precision", columnInfo.getAsString( ORMKeys.precision ) );
		}
		if ( columnInfo.containsKey( ORMKeys.scale ) ) {
			theNode.setAttribute( "scale", columnInfo.getAsString( ORMKeys.scale ) );
		}
		// if ( prop.hasPropertyAnnotation( prop, ORMKeys.unsavedValue ) ) {
		// columnNode.setAttribute( "unsaved-value", prop.getPropertyAnnotation( prop, ORMKeys.unsavedValue ) );
		// }
		// if ( prop.hasPropertyAnnotation( prop, ORMKeys.check ) ) {
		// columnNode.setAttribute( "check", prop.getPropertyAnnotation( prop, ORMKeys.check ) );
		// }
		if ( columnInfo.containsKey( Key._DEFAULT ) ) {
			theNode.setAttribute( "default", columnInfo.getAsString( Key._DEFAULT ) );
		}
		if ( types.containsKey( Key.sqltype ) ) {
			theNode.setAttribute( "sql-type", types.getAsString( Key.sqltype ) );
		}
		// String uniqueKey = prop.getPropertyUniqueKey( prop );
		// if ( uniqueKey != null ) {
		// columnNode.setAttribute( "unique-key", uniqueKey );
		// }
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
	 * @param prop Property metadata in struct form
	 *
	 * @return A &lt;id /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateIdElement( IPropertyMeta prop ) {
		Element theNode = this.document.createElement( "id" );

		// compute defaults - move to ORMAnnotationInspector?
		// prop.getAsStruct( Key.annotations ).computeIfAbsent( ORMKeys.ORMType, ( key ) -> "string" );

		// set common attributes
		theNode.setAttribute( "name", prop.getName() );
		IStruct types = prop.getTypes();
		if ( types.containsKey( ORMKeys.ORMType ) ) {
			theNode.setAttribute( "type", types.getAsString( ORMKeys.ORMType ) );
		}
		if ( prop.getUnsavedValue() != null ) {
			theNode.setAttribute( "unsaved-value", prop.getUnsavedValue() );
		}

		theNode.appendChild( generateColumnElement( prop ) );

		if ( !prop.getGenerator().isEmpty() ) {
			theNode.appendChild( generateGeneratorElement( prop ) );
			// @TODO: Determine ID type from generator type IF a generator is specified.
		}

		return theNode;
	}

	/**
	 * Generate a &lt;discriminator&gt; element for the entity metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>@discriminator</li>
	 * <li>@discriminatorColumn</li>
	 * <li>@discriminatorType</li>
	 * </ul>
	 * <p>
	 * The resulting XML might look something like this:
	 * <code>
	* <discriminator
	column="discriminator_column"
	type="discriminator_type"
	force="true|false"
	insert="true|false"
	formula="arbitrary sql expression"
	/>
	</code>
	 *
	 * @param classEl Parent &lt;class&gt; element to add the &lt;discriminator&gt; element to
	 * @param data    Discriminator metadata in struct form. If this is empty, no amendments will be made.
	 *
	 * @return nothing - document mutation is done in place
	 */
	public void addDiscriminatorData( Element classEl, IStruct data ) {
		if ( data.isEmpty() ) {
			return;
		}
		if ( data.containsKey( Key.value ) ) {
			classEl.setAttribute( "discriminator-value", data.getAsString( Key.value ) );
		}
		if ( data.containsKey( Key._name ) ) {
			Element theNode = this.document.createElement( "discriminator" );
			theNode.setAttribute( "column", data.getAsString( Key._name ) );

			// set conditional attributes
			if ( data.containsKey( Key.type ) ) {
				theNode.setAttribute( "type", data.getAsString( Key.type ) );
			}
			if ( data.containsKey( Key.force ) ) {
				theNode.setAttribute( "force", ( String ) data.get( Key.force ) );
			}
			if ( data.containsKey( ORMKeys.insert ) ) {
				theNode.setAttribute( "insert", ( String ) data.get( ORMKeys.insert ) );
			}
			if ( data.containsKey( ORMKeys.formula ) ) {
				theNode.setAttribute( "formula", ( String ) data.get( ORMKeys.formula ) );
			}
			classEl.appendChild( theNode );
		}
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
	 * @param prop Property metadata in struct form
	 *
	 * @return A &lt;generator /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateGeneratorElement( IPropertyMeta prop ) {
		IStruct	generator		= prop.getGenerator();
		String	generatorType	= generator.getAsString( Key._CLASS );

		Element	theNode			= this.document.createElement( "generator" );
		if ( !List.of( "assigned", "increment" ).contains( generatorType ) ) {
			logger.warn( "Untested generator type: {}. Please forward to your local Ortus agency.", generatorType );
		}
		theNode.setAttribute( "class", generatorType );
		IStruct params = new Struct();

		// generator=foreign
		if ( generator.containsKey( ORMKeys.property ) ) {
			params.put( "property", generator.getAsString( ORMKeys.property ) );
		}
		// generator=select
		if ( generator.containsKey( ORMKeys.selectKey ) ) {
			params.put( "key", generator.getAsString( ORMKeys.selectKey ) );
		}
		if ( generator.containsKey( ORMKeys.generated ) ) {
			params.put( "generated", generator.getAsString( ORMKeys.generated ) );
		}
		// generator=sequence|sequence-identity
		if ( generator.containsKey( ORMKeys.sequence ) ) {
			params.put( "sequence", generator.getAsString( ORMKeys.sequence ) );
		}
		if ( generator.containsKey( Key.params ) ) {
			params.putAll( generator.getAsStruct( Key.params ) );
		}
		params.forEach( ( key, value ) -> {
			Element paramEl = this.document.createElement( "param" );
			paramEl.setAttribute( "name", key.getName() );
			paramEl.setTextContent( value.toString() );
			theNode.appendChild( paramEl );
		} );
		return theNode;
	}

	/**
	 * Generate the top-level &lt;class /&gt; element containing entity mapping metadata.
	 * 
	 * @return A &lt;class /&gt; element containing entity keys, properties, and other Hibernate mapping metadata.
	 */
	public Element generateClassElement() {
		Element classElement = this.document.createElement( "class" );

		// general class attributes:
		if ( entity.getEntityName() != null && !entity.getEntityName().isEmpty() ) {
			classElement.setAttribute( "entity-name", entity.getEntityName() );
		}
		if ( entity.isDynamicInsert() ) {
			classElement.setAttribute( "dynamic-insert", "true" );
		}
		if ( entity.isDynamicUpdate() ) {
			classElement.setAttribute( "dynamic-update", "true" );
		}
		if ( entity.getBatchSize() != null ) {
			classElement.setAttribute( "batch-size", StringCaster.cast( entity.getBatchSize() ) );
		}
		if ( entity.isLazy() ) {
			classElement.setAttribute( "lazy", "true" );
		}
		if ( entity.isSelectBeforeUpdate() ) {
			classElement.setAttribute( "rowid", "true" );
		}
		if ( entity.getOptimisticLock() != null ) {
			classElement.setAttribute( "optimistic-lock", entity.getOptimisticLock() );
		}
		if ( entity.isImmutable() ) {
			classElement.setAttribute( "mutable", "false" );
		}
		if ( entity.getRowID() != null ) {
			classElement.setAttribute( "rowid", entity.getRowID() );
		}
		if ( entity.getWhere() != null ) {
			classElement.setAttribute( "where", entity.getWhere() );
		}

		// And, if no discriminator or joinColumn is present:
		if ( entity.isSimpleEntity() ) {
			String tableName = entity.getTableName();
			if ( tableName != null ) {
				classElement.setAttribute( "table", tableName );
			}
			if ( entity.getSchema() != null ) {
				classElement.setAttribute( "schema", entity.getSchema() );
			}
			if ( entity.getCatalog() != null ) {
				classElement.setAttribute( "catalog", entity.getCatalog() );
			}
		}

		// generate keys, aka <id> elements
		entity.getIdProperties().stream().forEach( ( propertyMeta ) ->

		{
			classElement.appendChild( generateIdElement( propertyMeta ) );
		} );

		addDiscriminatorData( classElement, entity.getDiscriminator() );

		// Both fieldtype=version and fieldtype=timestamp translate to a single <version> xml node.
		IPropertyMeta versionProperty = entity.getVersionProperty();
		if ( versionProperty != null ) {
			classElement.appendChild( generateVersionElement( versionProperty ) );
		}

		// generate properties, aka <property> elements
		entity.getProperties().stream().forEach( ( propertyMeta ) -> {
			classElement.appendChild( generatePropertyElement( propertyMeta ) );
		} );

		// generate associations, aka <one-to-one>, <one-to-many>, etc.
		entity.getAssociations().stream().forEach( ( propertyMeta ) -> {
			String associationType = propertyMeta.getAssociation().getAsString( Key.type );
			switch ( associationType ) {
				case "one-to-one" :
				case "many-to-one" :
					classElement.appendChild( generateToOneAssociation( propertyMeta ) );
					break;
				case "one-to-many" :
				case "many-to-many" :
					classElement.appendChild( generateToManyAssociation( propertyMeta ) );
					break;
				default :
					logger.warn( "Unknown association type: {}. Please forward to your local Ortus agency.",
					    associationType );
					// @TODO: Check ORMConfig.ignoreParseErrors and throw if false.
					throw new BoxRuntimeException( "Unknown association type: %s".formatted( associationType ) );
			}

		} );

		// @TODO: generate <subclass> elements
		// @TODO: generate <joined-subclass> elements
		// @TODO: generate <union-subclass> elements
		// @TODO: generate/handle optimistic lock

		return classElement;
	}

	/**
	 * Generate a &lt;version/&gt; element for the given column metadata.
	 * <p>
	 * A version element defines an entity version value.
	 *
	 * @param prop Column metadata
	 *
	 * @return A &lt;version/&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateVersionElement( IPropertyMeta prop ) {
		Element	theNode		= this.document.createElement( "version" );
		IStruct	columnInfo	= prop.getColumn();
		IStruct	types		= prop.getTypes();

		// PROPERTY name
		theNode.setAttribute( "name", prop.getName() );
		// COLUMN name
		if ( columnInfo.containsKey( Key._NAME ) ) {
			theNode.setAttribute( "column", columnInfo.getAsString( Key._NAME ) );
		}
		if ( types.containsKey( ORMKeys.ORMType ) ) {
			theNode.setAttribute( "type", types.getAsString( ORMKeys.ORMType ) );
		}
		if ( prop.getUnsavedValue() != null ) {
			theNode.setAttribute( "unsaved-value", prop.getUnsavedValue() );
		}
		if ( columnInfo.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.insertable ) ) );
		}
		return theNode;
	}

	private String trueFalseFormat( Boolean value ) {
		return Boolean.TRUE.equals( value ) ? "true" : "false";
	}
}
