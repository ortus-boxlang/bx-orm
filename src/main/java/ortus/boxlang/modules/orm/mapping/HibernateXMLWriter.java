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

import java.util.List;
import java.util.function.BiFunction;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Generate a Hibernate XML mapping document for a given IEntityMeta instance, which represents the parsed entity metadata (whether classic or modern
 * syntax) in a normalized form.
 */
public class HibernateXMLWriter {

	/**
	 * Runtime
	 */
	private static final BoxRuntime			runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger					logger;

	/**
	 * IEntityMeta instance which represents the parsed entity metadata in a normalized form.
	 * <p>
	 * The source of this metadata could be CFML persistent
	 * annotations like `persistent=true` and `fieldtype="id"` OR modern BoxLang-syntax, JPA-style annotations like `@Entity` and `@Id`.
	 */
	IEntityMeta								entity;

	/**
	 * XML Document root, created by the constructor.
	 * <p>
	 * This is the root element of the Hibernate mapping document, and will be returned by {@link #generateXML()}.
	 */
	Document								document;

	/**
	 * A function that takes a class name and returns an EntityRecord instance.
	 * <p>
	 * This is used to look up the metadata for associated entities when generating association elements.
	 */
	BiFunction<String, Key, EntityRecord>	entityLookup;

	/**
	 * Whether to throw an exception when an error occurs during XML generation.
	 */
	boolean									throwOnErrors;

	/**
	 * Create a new Hibernate XML writer for the given entity metadata.
	 * 
	 * @param entity The entity metadata to generate XML for.
	 */
	public HibernateXMLWriter( IEntityMeta entity ) {
		this( entity, null, true );
	}

	/**
	 * Create a new Hibernate XML writer for the given entity metadata, using the provided entity lookup function to find associated entities.
	 * 
	 * @param entity       The entity metadata to generate XML for.
	 * @param entityLookup A function that takes an entity name and returns an EntityRecord instance.
	 */
	public HibernateXMLWriter( IEntityMeta entity, BiFunction<String, Key, EntityRecord> entityLookup ) {
		this( entity, entityLookup, true );
	}

	/**
	 * Create a new Hibernate XML writer for the given entity metadata, using the provided entity lookup function to find associated entities.
	 * 
	 * @param entity        The entity metadata to generate XML for.
	 * @param entityLookup  A function that takes 1) an entity name, and 2) a datasource name, and returns an EntityRecord instance matching this combo
	 *                      (or null.)
	 * @param throwOnErrors Whether to throw an exception when an error occurs during XML generation.
	 */
	public HibernateXMLWriter( IEntityMeta entity, BiFunction<String, Key, EntityRecord> entityLookup, boolean throwOnErrors ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.entity			= entity;
		this.entityLookup	= entityLookup;
		this.throwOnErrors	= throwOnErrors;

		// Validation
		if ( entity.getIdProperties().isEmpty() ) {
			logger.error( "Entity {} has no ID properties. Hibernate requires at least one.", entity.getEntityName() );
			if ( throwOnErrors ) {
				throw new BoxRuntimeException( "Entity %s has no ID properties. Hibernate requires at least one.".formatted( entity.getEntityName() ) );
			}
		}

		this.document = createDocument();
	}

	/**
	 * Create a new XML document for populating with Hibernate mapping data.
	 * 
	 * @return A new, empty XML document.
	 */
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

	/**
	 * Generate the Hibernate XML mapping document, beginning with the &lt;class /&gt; element.
	 * 
	 * @return The complete Hibernate XML mapping document.
	 */
	public Document generateXML() {
		// TODO: Track execution time and record in an XML comment prepended to the document.
		// comment with: source, compilation-time, datasource
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

		Element	theNode		= this.document.createElement( "property" );
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", toHibernateType( prop.getORMType() ) );

		if ( prop.getFormula() != null ) {
			theNode.setAttribute( "formula", "( " + prop.getFormula() + " )" );
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
		IStruct		association			= prop.getAssociation();
		IStruct		columnInfo			= prop.getColumn();

		// <bag>, <map>, <set>, etc.
		Element		collectionNode		= generateCollectionElement( prop, association, columnInfo );

		List<Key>	stringProperties	= List.of( ORMKeys.table, ORMKeys.schema, ORMKeys.catalog );
		populateStringAttributes( collectionNode, association, stringProperties );

		// <one-to-many> or <many-to-many>
		Element toManyNode = this.document.createElement( association.getAsString( Key.type ) );
		if ( association.containsKey( ORMKeys.inverseJoinColumn ) ) {
			// @TODO: Loop over all column values and create multiple <column> elements.
			toManyNode.setAttribute( "column", association.getAsString( ORMKeys.inverseJoinColumn ) );
		}
		if ( association.containsKey( Key._CLASS ) ) {
			setEntityName( toManyNode, association.getAsString( Key._CLASS ), prop );
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
		// non-string, non-simple attributes
		if ( association.containsKey( ORMKeys.inverse ) ) {
			theNode.setAttribute( "inverse", trueFalseFormat( association.getAsBoolean( ORMKeys.inverse ) ) );
		}
		if ( association.containsKey( ORMKeys.immutable ) ) {
			theNode.setAttribute( "mutable", trueFalseFormat( !association.getAsBoolean( ORMKeys.immutable ) ) );
		}
		if ( !prop.isOptimisticLock() ) {
			theNode.setAttribute( "optimistic-lock", "false" );
		}

		List<Key> stringProperties = List.of( ORMKeys.table, ORMKeys.schema, ORMKeys.lazy, ORMKeys.cascade, ORMKeys.orderBy, ORMKeys.where,
		    ORMKeys.fetch, ORMKeys.embedXML, ORMKeys.orderBy );
		populateStringAttributes( theNode, association, stringProperties );

		// @JoinColumn - https://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/collections.html#collections-foreignkeys
		if ( association.containsKey( Key.column ) ) {
			Element keyNode = this.document.createElement( "key" );
			// @TODO: Loop over all column values and create multiple <column> elements.
			keyNode.setAttribute( "column", association.getAsString( Key.column ) );

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

		if ( association.containsKey( Key._CLASS ) ) {
			setEntityName( theNode, association.getAsString( Key._CLASS ), prop );
		}

		List<Key> stringProperties = List.of( Key._NAME, ORMKeys.cascade, ORMKeys.fetch, ORMKeys.mappedBy, ORMKeys.access,
		    ORMKeys.lazy, ORMKeys.embedXML, ORMKeys.foreignKey );
		populateStringAttributes( theNode, association, stringProperties );

		if ( association.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( association.getAsBoolean( ORMKeys.insertable ) ) );
		}
		if ( association.containsKey( ORMKeys.updateable ) ) {
			theNode.setAttribute( "update", trueFalseFormat( association.getAsBoolean( ORMKeys.updateable ) ) );
		}

		// non-simple attributes
		if ( association.containsKey( ORMKeys.constrained ) && association.getAsBoolean( ORMKeys.constrained ) ) {
			theNode.setAttribute( "constrained", "true" );
		}
		if ( prop.getFormula() != null ) {
			theNode.setAttribute( "formula", prop.getFormula() );
		}
		if ( association.containsKey( Key.column ) ) {
			// @TODO: Loop over all column values and create multiple <column> elements.
			// Element columnNode = this.document.createElement( "column" );
			// columnNode.setAttribute( "name", association.getAsString( Key.column ) );
			// theNode.appendChild( columnNode );
			theNode.setAttribute( "column", association.getAsString( Key.column ) );
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
		Element		theNode				= this.document.createElement( "column" );
		IStruct		columnInfo			= prop.getColumn();

		List<Key>	stringProperties	= List.of( Key._DEFAULT, Key._name, Key.sqltype, ORMKeys.length, ORMKeys.precision, ORMKeys.scale );
		populateStringAttributes( theNode, columnInfo, stringProperties );

		// non-simple attributes
		if ( columnInfo.containsKey( ORMKeys.nullable ) ) {
			theNode.setAttribute( "not-null", trueFalseFormat( !columnInfo.getAsBoolean( ORMKeys.nullable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.unique ) ) {
			theNode.setAttribute( "unique", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.unique ) ) );
		}
		// if ( prop.hasPropertyAnnotation( prop, ORMKeys.unsavedValue ) ) {
		// columnNode.setAttribute( "unsaved-value", prop.getPropertyAnnotation( prop, ORMKeys.unsavedValue ) );
		// }
		// if ( prop.hasPropertyAnnotation( prop, ORMKeys.check ) ) {
		// columnNode.setAttribute( "check", prop.getPropertyAnnotation( prop, ORMKeys.check ) );
		// }
		// String uniqueKey = prop.getPropertyUniqueKey( prop );
		// if ( uniqueKey != null ) {
		// columnNode.setAttribute( "unique-key", uniqueKey );
		// }
		return theNode;
	}

	/**
	 * Generate a &lt;id /&gt; OR &lt;key-property /&gt; element for the given property metadata.
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
	 * @return An id or key-property XML node ready to add to a Hibernate mapping class or composite-id element
	 */
	public Element generateIdElement( String elementName, IPropertyMeta prop ) {
		Element theNode = this.document.createElement( elementName );

		// compute defaults - move to ORMAnnotationInspector?
		// prop.getAsStruct( Key.annotations ).computeIfAbsent( ORMKeys.ORMType, ( key ) -> "string" );

		// set common attributes
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", toHibernateType( prop.getORMType() ) );
		if ( prop.getUnsavedValue() != null ) {
			theNode.setAttribute( "unsaved-value", prop.getUnsavedValue() );
		}

		theNode.appendChild( generateColumnElement( prop ) );

		if ( !prop.getGenerator().isEmpty() ) {
			theNode.appendChild( generateGeneratorElement( prop.getGenerator() ) );
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
	 * Returns nothing - document mutation is done in place
	 *
	 * @param classEl Parent &lt;class&gt; element to add the &lt;discriminator&gt; element to
	 * @param data    Discriminator metadata in struct form. If this is empty, no amendments will be made.
	 *
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
	 * @param generatorInfo Struct of fenerator metadata
	 *
	 * @return A &lt;generator /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateGeneratorElement( IStruct generatorInfo ) {
		Element theNode = this.document.createElement( "generator" );
		theNode.setAttribute( "class", generatorInfo.getAsString( Key._CLASS ) );

		IStruct params = new Struct();

		// generator=foreign
		if ( generatorInfo.containsKey( ORMKeys.property ) ) {
			params.put( "property", generatorInfo.getAsString( ORMKeys.property ) );
		}
		// generator=select
		if ( generatorInfo.containsKey( ORMKeys.selectKey ) ) {
			params.put( "key", generatorInfo.getAsString( ORMKeys.selectKey ) );
		}
		if ( generatorInfo.containsKey( ORMKeys.generated ) ) {
			params.put( "generated", generatorInfo.getAsString( ORMKeys.generated ) );
		}
		// generator=sequence|sequence-identity
		if ( generatorInfo.containsKey( ORMKeys.sequence ) ) {
			params.put( "sequence", generatorInfo.getAsString( ORMKeys.sequence ) );
		}
		if ( generatorInfo.containsKey( Key.params ) ) {
			params.putAll( generatorInfo.getAsStruct( Key.params ) );
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

		if ( !entity.getCache().isEmpty() ) {
			classElement.appendChild( generateCacheElement( entity.getCache() ) );
		}

		// generate keys, aka <id> elements
		List<IPropertyMeta> idProperties = entity.getIdProperties();
		if ( idProperties.size() == 0 ) {
			// we've already logged an error in the constructor.
		} else if ( idProperties.size() == 1 ) {
			classElement.appendChild( generateIdElement( "id", idProperties.get( 0 ) ) );
		} else {
			Element compositeIdNode = this.document.createElement( "composite-id" );
			idProperties.stream().forEach( ( prop ) -> {
				compositeIdNode.appendChild( generateIdElement( "key-property", prop ) );
			} );
			classElement.appendChild( compositeIdNode );
		}

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
			switch ( propertyMeta.getFieldType() ) {
				case ONE_TO_ONE :
				case MANY_TO_ONE :
					classElement.appendChild( generateToOneAssociation( propertyMeta ) );
					break;
				case ONE_TO_MANY :
				case MANY_TO_MANY :
					classElement.appendChild( generateToManyAssociation( propertyMeta ) );
					break;
				default :
					logger.warn( "Unhandled association/field type: {} on property {}", propertyMeta.getFieldType(), propertyMeta.getName() );
			}

		} );

		// @TODO: generate <subclass> elements
		// @TODO: generate <joined-subclass> elements
		// @TODO: generate <union-subclass> elements
		// @TODO: generate/handle optimistic lock

		return classElement;
	}

	/**
	 * Generate a &lt;cache/&gt; element for the given cache metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>strategy</li>
	 * <li>region</li>
	 * </ul>
	 *
	 * @param cache Cache metadata in struct form
	 *
	 * @return A &lt;cache /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateCacheElement( IStruct cache ) {
		Element		theNode				= this.document.createElement( "cache" );

		List<Key>	stringProperties	= List.of( ORMKeys.strategy, Key.region, ORMKeys.include );
		populateStringAttributes( theNode, cache, stringProperties );

		return theNode;
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

		// PROPERTY name
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", toHibernateType( prop.getORMType() ) );
		// COLUMN name
		if ( columnInfo.containsKey( Key._NAME ) ) {
			theNode.setAttribute( "column", columnInfo.getAsString( Key._NAME ) );
		}
		if ( prop.getUnsavedValue() != null ) {
			theNode.setAttribute( "unsaved-value", prop.getUnsavedValue() );
		}
		if ( columnInfo.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.insertable ) ) );
		}
		return theNode;
	}

	/**
	 * Look up an entity from the entity map by class name and set it into the entity-name attribute on the provided node.
	 * 
	 * @param theNode           XML node onw hich to populate entity-name attribute
	 * @param relationClassName Class name of the associated entity. If null or empty, this method will do nothing.
	 * @param prop              Property metadata, used for log messages if the entity lookup fails.
	 */
	private void setEntityName( Element theNode, String relationClassName, IPropertyMeta prop ) {
		if ( relationClassName == null || relationClassName.isBlank() ) {
			return;
		}
		Key				datasourceName		= this.entity.getDatasource().isEmpty() ? Key.defaultDatasource : Key.of( this.entity.getDatasource() );
		EntityRecord	associatedEntity	= entityLookup.apply( relationClassName, datasourceName );
		if ( associatedEntity == null ) {
			String message = String.format( "Could not find entity '%s' referenced in property '%s' on entity '%s'", relationClassName, prop.getName(),
			    prop.getDefiningEntity().getEntityName() );
			logger.error( message );
			if ( this.throwOnErrors ) {
				throw new BoxRuntimeException( message );
			}
		} else {
			theNode.setAttribute( "entity-name", associatedEntity.getEntityName() );
		}
	}

	/**
	 * Convert a boolean value to a string representation of "true" or "false". Useful for XML-ifying booleans.
	 * 
	 * @param value Boolean value to convert
	 */
	private String trueFalseFormat( Boolean value ) {
		return Boolean.TRUE.equals( value ) ? "true" : "false";
	}

	/**
	 * Populate simple string attributes on the given XML node for the given list of Keys existing in the given struct.
	 * 
	 * @param theNode          XML node to populate
	 * @param association      Struct containing the attribute values. Any null or empty values will be skipped.
	 * @param stringProperties List of keys to populate as attributes on the XML node. i.e., `List.of( ORMKeys.table, ORMKeys.schema )`
	 */
	private void populateStringAttributes( Element theNode, IStruct association, List<Key> stringProperties ) {
		for ( Key propertyName : stringProperties ) {
			if ( association.containsKey( propertyName ) ) {
				String value = association.getAsString( propertyName );
				if ( value != null && !value.isBlank() ) {
					theNode.setAttribute( toHibernateAttributeName( propertyName ), value.trim() );
				}
			}
		}
	}

	/**
	 * Convert a BoxLang key name to a Hibernate XML attribute name.
	 * 
	 * @param key BoxLang annotation name, like `sqltype` or `selectkey`
	 * 
	 * @return Correct Hibernate XML attribute name, like `sql-type` or `key`
	 */
	private String toHibernateAttributeName( Key key ) {
		String name = key.getName().toLowerCase();
		switch ( name ) {
			// Warning: This may backfire if 'strategy' is used in a different context than the <cache> node.
			case "strategy" :
				return "usage";
			case "sqltype" :
				return "sql-type";
			case "selectkey" :
				return "key";
			case "mappedby" :
				return "property-ref";
			case "foreignkey" :
				return "foreign-key";
			case "embedxml" :
				return "embed-xml";
			case "orderby" :
				return "order-by";
			default :
				return name;
		}
	}

	/**
	 * Caster to convert a property `ormType` field value to a Hibernate type.
	 * 
	 * @param propertyType Property type, like `datetime` or `string`
	 * 
	 * @return The Hibernate-safe type, like `timestamp` or `string`
	 */
	public static String toHibernateType( String propertyType ) {
		// basic normalization
		propertyType	= propertyType.trim().toLowerCase();
		// grab "varchar" from "varchar(50)"
		propertyType	= propertyType.replaceAll( "\\(.+\\)", "" );
		// grab "biginteger" from "java.math.biginteger", etc.
		propertyType	= propertyType.substring( propertyType.lastIndexOf( "." ) + 1 );

		return switch ( propertyType ) {
			case "blob", "byte[]" -> "binary";
			case "bit", "bool" -> "boolean";
			case "yes-no", "yesno", "yes_no" -> "yes_no";
			case "true-false", "truefalse", "true_false" -> "true_false";
			case "big-decimal", "bigdecimal" -> "big_decimal";
			case "big-integer", "bigint", "biginteger" -> "big_integer";
			case "int" -> "integer";
			case "numeric", "number", "decimal" -> "double";
			case "datetime", "eurodate", "usdate" -> "timestamp";
			case "char", "nchar" -> "character";
			case "varchar", "nvarchar" -> "string";
			case "clob" -> "text";
			default -> propertyType;
		};
	}
}
