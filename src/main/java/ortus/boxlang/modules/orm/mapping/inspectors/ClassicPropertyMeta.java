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
package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.Map;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * A "Classic", aka traditional, implementation of the property metadata configuration.
 *
 * i.e. handles translating CFML property annotations like `sqltype="varchar"` into the IPropertyMeta interface for consistent reference by the
 * HibernateXMLWriter.
 */
public class ClassicPropertyMeta extends AbstractPropertyMeta {

	public ClassicPropertyMeta( String entityName, IStruct meta, IEntityMeta definingEntity ) {
		super( entityName, meta, definingEntity );

		annotations.putIfAbsent( ORMKeys.fieldtype, "column" );
		if ( this.fieldType == null ) {
			this.fieldType = FIELDTYPE.fromString( annotations.getAsString( ORMKeys.fieldtype ) );
			if ( this.fieldType == null ) {
				throw new BoxRuntimeException( String.format( "Unknown field type '%s' for property '%s' on entity '%s'",
				    annotations.getAsString( ORMKeys.fieldtype ), this.name, this.entityName ) );
			}
		}

		// General property annotations
		if ( this.annotations.containsKey( ORMKeys.lazy ) ) {
			this.lazy = this.annotations.getAsString( ORMKeys.lazy );
		}
		if ( this.annotations.containsKey( ORMKeys.readOnly ) ) {
			this.isImmutable = BooleanCaster.cast( this.annotations.get( ORMKeys.readOnly ) );
		}
		if ( this.annotations.containsKey( ORMKeys.optimisticLock ) ) {
			this.isOptimisticLock = BooleanCaster.cast( this.annotations.get( ORMKeys.optimisticLock ) );
		}
		if ( this.annotations.containsKey( ORMKeys.formula ) ) {
			this.formula = this.annotations.getAsString( ORMKeys.formula );
		}
		if ( annotations.containsKey( ORMKeys.fieldtype ) ) {
			String fieldType = annotations.getAsString( ORMKeys.fieldtype );
			if ( fieldType == "collection" ) {
				logger.warn( "Property {} on entity {} has fieldtype=collection, which is not yet supported. Please forward to your local Ortus agency.",
				    this.name, entityName );
			}
		}
	}

	protected IStruct parseAssociation( IStruct annotations ) {
		if ( !annotations.containsKey( ORMKeys.fieldtype ) ) {
			return Struct.EMPTY;
		}

		var association = new Struct();
		association.put( Key._NAME, this.name );

		String associationType = annotations.getAsString( ORMKeys.fieldtype );
		if ( annotations.containsKey( ORMKeys.linkTable ) && !annotations.containsKey( ORMKeys.fkcolumn ) ) {
			// @TODO: Implement compatibility shim in bx-compat or bx-orm-compat
			throw new BoxRuntimeException( String.format( "Missing 'fkcolumn' annotation for property [{}] on entity [{}]", this.name, this.entityName ) );
		}
		if ( associationType.equalsIgnoreCase( "one-to-one" )
		    && ( annotations.containsKey( ORMKeys.fkcolumn ) || annotations.containsKey( ORMKeys.linkTable ) ) ) {
			associationType = "many-to-one";
			association.put( Key.column, translateColumnName( annotations.getAsString( ORMKeys.fkcolumn ) ) );
		}
		if ( associationType.equalsIgnoreCase( "one-to-many" ) && annotations.containsKey( ORMKeys.linkTable ) ) {
			associationType = "many-to-many";
			association.put( ORMKeys.unique, true );
			association.put( ORMKeys.inverseJoinColumn, translateColumnName( annotations.getAsString( ORMKeys.inverseJoinColumn ) ) );
		}
		association.put( Key.type, associationType.toLowerCase().trim() );

		// Update fieldtype annotation just in case we altered it.
		annotations.put( ORMKeys.fieldtype, associationType );

		final String finalAssociationType = associationType;

		if ( finalAssociationType.endsWith( "-to-many" ) ) {
			// IS A COLLECTION
			association.compute( ORMKeys.collectionType, ( key, object ) -> {
				String propertyType = annotations.getAsString( Key.type );
				if ( propertyType == null || propertyType.isBlank() || propertyType.trim().toLowerCase().equals( "any" ) ) {
					propertyType = "array";
				}
				return propertyType.equalsIgnoreCase( "array" ) ? "bag" : "map";
			} );
			if ( association.get( ORMKeys.collectionType ).equals( "map" ) ) {
				if ( annotations.containsKey( ORMKeys.structKeyColumn ) ) {
					association.put( ORMKeys.structKeyColumn, translateColumnName( annotations.getAsString( ORMKeys.structKeyColumn ) ) );
				}
				if ( annotations.containsKey( ORMKeys.structKeyType ) ) {
					association.put( ORMKeys.structKeyType, annotations.getAsString( ORMKeys.structKeyType ) );
				}
				// NEW in BoxLang.
				if ( annotations.containsKey( ORMKeys.structKeyFormula ) ) {
					association.put( ORMKeys.structKeyFormula, annotations.getAsString( ORMKeys.structKeyFormula ) );
				}
			}
			if ( annotations.containsKey( ORMKeys.singularName ) ) {
				association.put( ORMKeys.singularName, annotations.getAsString( ORMKeys.singularName ) );
			}
		}
		if ( annotations.containsKey( ORMKeys.lazy ) ) {
			association.compute( ORMKeys.lazy, ( key, object ) -> {
				// TODO: figure out what "extra" maps to in Hibernate 5.
				// helpx.adobe.com/coldfusion/developing-applications/coldfusion-orm/performance-optimization/lazy-loading.html
				String lazy = annotations.getAsString( ORMKeys.lazy ).trim().toLowerCase();
				if ( "true".equals( lazy ) ) {
					lazy = finalAssociationType.endsWith( "-to-many" )
					    && ( association.get( ORMKeys.collectionType ).equals( "map" ) || association.get( ORMKeys.collectionType ).equals( "bag" ) )
					        ? "true"
					        : "proxy";
				}
				return lazy;
			} );
		}
		// Column name properties- these are translated later in this same method.
		if ( annotations.containsKey( ORMKeys.inverseJoinColumn ) ) {
			association.put( ORMKeys.inverseJoinColumn, annotations.getAsString( ORMKeys.inverseJoinColumn ) );
		}
		if ( annotations.containsKey( ORMKeys.fkcolumn ) ) {
			association.put( Key.column, annotations.getAsString( ORMKeys.fkcolumn ) );
		}

		if ( annotations.containsKey( Key._CLASS ) ) {
			association.put( Key._CLASS, annotations.getAsString( Key._CLASS ) );
		}
		association.putIfAbsent( Key._CLASS, annotations.getAsString( ORMKeys.cfc ) );
		if ( annotations.containsKey( ORMKeys.insert ) ) {
			association.put( ORMKeys.insertable, BooleanCaster.cast( annotations.get( ORMKeys.insert ) ) );
		}
		if ( annotations.containsKey( ORMKeys.update ) ) {
			association.put( ORMKeys.updateable, BooleanCaster.cast( annotations.get( ORMKeys.update ) ) );
		}
		if ( annotations.containsKey( ORMKeys.fetch ) ) {
			association.put( ORMKeys.fetch, annotations.getAsString( ORMKeys.fetch ) );
		}
		if ( annotations.containsKey( ORMKeys.formula ) ) {
			association.put( ORMKeys.formula, annotations.getAsString( ORMKeys.formula ) );
		}
		if ( annotations.containsKey( ORMKeys.mappedBy ) ) {
			association.put( ORMKeys.mappedBy, annotations.getAsString( ORMKeys.mappedBy ) );
		}
		if ( annotations.containsKey( ORMKeys.embedXML ) ) {
			association.put( ORMKeys.embedXML, annotations.getAsString( ORMKeys.embedXML ) );
		}
		if ( annotations.containsKey( ORMKeys.orderBy ) ) {
			association.put( ORMKeys.orderBy, annotations.getAsString( ORMKeys.orderBy ) );
		}
		if ( annotations.containsKey( ORMKeys.access ) ) {
			association.put( ORMKeys.access, annotations.getAsString( ORMKeys.access ) );
		}
		if ( annotations.containsKey( ORMKeys.where ) ) {
			association.put( ORMKeys.where, annotations.getAsString( ORMKeys.where ) );
		}
		if ( annotations.containsKey( ORMKeys.missingRowIgnored ) ) {
			association.compute( ORMKeys.missingRowIgnored,
			    ( key, object ) -> BooleanCaster.cast( annotations.get( ORMKeys.missingRowIgnored ) ) ? "ignore" : "exception" );
		}
		if ( annotations.containsKey( ORMKeys.constrained ) ) {
			association.put( ORMKeys.constrained, BooleanCaster.cast( annotations.getAsString( ORMKeys.constrained ) ) );
		}
		if ( annotations.containsKey( ORMKeys.cascade ) ) {
			/*
			 * @TODO: Map to JPA cascade types: ALL, PERSIST, MERGE, REMOVE, REFRESH, DETACH
			 * https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0#a15100
			 *
			 * The possible values are:
			 * persist, merge, delete, save-update, evict, replicate, lock, refresh, all, none
			 */
			association.put( ORMKeys.cascade, annotations.getAsString( ORMKeys.cascade ) );
		}
		// Alias 'foreignKeyName' to 'foreignKey'
		if ( annotations.containsKey( ORMKeys.foreignKeyName ) ) {
			annotations.put( ORMKeys.foreignKey, annotations.getAsString( ORMKeys.foreignKeyName ) );
		}
		if ( annotations.containsKey( ORMKeys.foreignKey ) ) {
			association.put( ORMKeys.foreignKey, annotations.getAsString( ORMKeys.foreignKey ) );
		}
		if ( annotations.containsKey( ORMKeys.inverse ) ) {
			association.put( ORMKeys.inverse, BooleanCaster.cast( annotations.get( ORMKeys.inverse ) ) );
		}
		if ( annotations.containsKey( ORMKeys.linkTable ) ) {
			association.putIfAbsent( Key.table, translateTableName( annotations.getAsString( ORMKeys.linkTable ) ) );
			// if there's a linkTable, we need to set the schema and catalog
			association.put( ORMKeys.schema, annotations.getAsString( ORMKeys.linkSchema ) );
			association.put( ORMKeys.catalog, annotations.getAsString( ORMKeys.linkCatalog ) );
		}
		association.putIfAbsent( Key.table, translateTableName( annotations.getAsString( Key.table ) ) );
		association.putIfAbsent( ORMKeys.schema, annotations.getAsString( ORMKeys.schema ) );
		association.putIfAbsent( ORMKeys.catalog, annotations.getAsString( ORMKeys.catalog ) );

		// Fire up the translator for all column name properties
		association.computeIfPresent( ORMKeys.inverseJoinColumn, ( key, value ) -> translateColumnName( ( String ) value ) );
		association.computeIfPresent( Key.column, ( key, value ) -> translateColumnName( ( String ) value ) );
		return association;
	}

	protected IStruct parseColumnAnnotations( IStruct annotations ) {
		IStruct column = new Struct();
		if ( annotations.containsKey( Key.column ) ) {
			column.put( Key._name, translateColumnName( annotations.getAsString( Key.column ) ) );
		}
		if ( annotations.containsKey( Key.length ) ) {
			column.put( Key.length, annotations.getAsString( Key.length ) );
		}
		if ( annotations.containsKey( ORMKeys.precision ) ) {
			column.put( ORMKeys.precision, annotations.getAsString( ORMKeys.precision ) );
		}
		if ( annotations.containsKey( ORMKeys.scale ) ) {
			column.put( ORMKeys.scale, annotations.getAsString( ORMKeys.scale ) );
		}
		if ( annotations.containsKey( ORMKeys.unique ) ) {
			column.put( ORMKeys.unique, BooleanCaster.cast( annotations.get( ORMKeys.unique ) ) );
		}
		if ( annotations.containsKey( ORMKeys.notNull ) ) {
			column.put( ORMKeys.nullable, Boolean.FALSE.equals( BooleanCaster.cast( annotations.getOrDefault( ORMKeys.notNull, true ) ) ) );
		}
		if ( annotations.containsKey( ORMKeys.insert ) ) {
			column.put( ORMKeys.insertable, BooleanCaster.cast( annotations.get( ORMKeys.insert ) ) );
		}
		if ( annotations.containsKey( ORMKeys.update ) ) {
			column.put( ORMKeys.updateable, BooleanCaster.cast( annotations.get( ORMKeys.update ) ) );
		}
		if ( annotations.containsKey( Key.table ) ) {
			column.put( Key.table, translateTableName( annotations.getAsString( Key.table ) ) );
		}
		if ( annotations.containsKey( ORMKeys.schema ) ) {
			column.put( ORMKeys.schema, annotations.getAsString( ORMKeys.schema ) );
		}
		if ( annotations.containsKey( ORMKeys.dbDefault ) ) {
			column.put( Key._DEFAULT, annotations.getAsString( ORMKeys.dbDefault ) );
		}
		if ( annotations.containsKey( Key.sqltype ) ) {
			column.put( Key.sqltype, annotations.getAsString( Key.sqltype ) );
		}
		if ( annotations.containsKey( ORMKeys.uniqueKey ) ) {
			column.put( ORMKeys.uniqueKey, annotations.getAsString( ORMKeys.uniqueKey ) );
		}

		// Column should ALWAYS have a name.
		column.putIfAbsent( Key._NAME, translateColumnName( this.name ) );
		return column;
	}

	protected IStruct parseGeneratorAnnotations( IStruct annotations ) {
		IStruct generator = new Struct();
		if ( annotations.containsKey( ORMKeys.generator ) ) {
			generator.put( Key._CLASS, annotations.getAsString( ORMKeys.generator ) );
		}
		if ( annotations.containsKey( ORMKeys.property ) ) {
			generator.put( ORMKeys.property, annotations.getAsString( ORMKeys.property ) );
		}
		if ( annotations.containsKey( ORMKeys.selectKey ) ) {
			generator.put( ORMKeys.selectKey, annotations.getAsString( ORMKeys.selectKey ) );
		}
		if ( annotations.containsKey( ORMKeys.generated ) ) {
			generator.put( ORMKeys.generated, annotations.getAsString( ORMKeys.generated ) );
		}
		if ( annotations.containsKey( ORMKeys.sequence ) ) {
			generator.put( ORMKeys.sequence, annotations.getAsString( ORMKeys.sequence ) );
		}
		if ( annotations.containsKey( Key.params ) ) {
			Object	paramValue	= annotations.getAsString( Key.params );
			IStruct	theParams	= Struct.fromMap( ( Map ) JSONUtil.fromJSON( paramValue ) );
			if ( theParams == null ) {
				// logger.warn( "Property '{}' has a 'params' annotation that could not be cast to a struct: {}", propName, paramValue );
			} else {
				generator.put( Key.params, theParams );
			}
		}
		return generator;
	}
}
