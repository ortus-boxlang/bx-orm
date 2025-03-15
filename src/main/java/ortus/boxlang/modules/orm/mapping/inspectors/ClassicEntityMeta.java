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

import java.util.stream.Collectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * A "Class", aka traditional, implementation of the entity metadata configuration.
 *
 * i.e. handles translating traditional CFML component annotations like `persistent="true"` into the IPropertyMeta interface for consistent reference
 * by the HibernateXMLWriter.
 */
public class ClassicEntityMeta extends AbstractEntityMeta {

	public ClassicEntityMeta( IStruct entityMeta ) {
		super( entityMeta );

		this.annotations.computeIfAbsent( ORMKeys.entityName, key -> this.meta.getAsString( Key._name ) );
		this.entityName = this.annotations.getAsString( ORMKeys.entityName );

		this.annotations.computeIfAbsent( ORMKeys.table, key -> this.getEntityName() );
		this.tableName = this.annotations.getAsString( Key.table );
		if ( this.annotations.containsKey( ORMKeys.optimisticLock ) ) {
			this.optimisticLock = this.annotations.getAsString( ORMKeys.optimisticLock );
		}

		if ( this.annotations.containsKey( ORMKeys.schema ) ) {
			this.schemaName = this.annotations.getAsString( ORMKeys.schema );
		}

		if ( this.annotations.containsKey( ORMKeys.catalog ) ) {
			this.catalogName = this.annotations.getAsString( ORMKeys.catalog );
		}
		this.isImmutable = this.annotations.containsKey( ORMKeys.readOnly )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.readOnly, "false" ), false );

		if ( this.annotations.containsKey( ORMKeys.discriminatorColumn ) || this.annotations.containsKey( ORMKeys.discriminatorValue ) ) {
			this.discriminator = new Struct();
			// copy the old-school discriminatorColumn and discriminatorValue annotations into the new struct
			this.discriminator.computeIfAbsent( Key._name, key -> this.annotations.getAsString( ORMKeys.discriminatorColumn ) );
			this.discriminator.computeIfAbsent( Key.value, key -> this.annotations.getAsString( ORMKeys.discriminatorValue ) );
		}

		if ( this.annotations.containsKey( ORMKeys.batchsize ) ) {
			batchsize = IntegerCaster.cast( this.annotations.get( ORMKeys.batchsize ) );
		}

		if ( this.annotations.containsKey( ORMKeys.cacheUse ) ) {
			this.cache = new Struct();
			this.cache.computeIfAbsent( ORMKeys.strategy, key -> this.annotations.getAsString( ORMKeys.cacheUse ) );
			this.cache.computeIfAbsent( Key.region, key -> this.annotations.getAsString( ORMKeys.cacheName ) );
			this.cache.computeIfAbsent( ORMKeys.include, key -> this.annotations.getAsString( ORMKeys.cacheInclude ) );
		}

		this.allPersistentProperties	= this.allProperties.stream()
		    .map( IStruct.class::cast )
		    .filter( ( IStruct prop ) -> {
											    var annotations = prop.getAsStruct( Key.annotations );
											    return BooleanCaster.cast(
											        annotations.getOrDefault( ORMKeys.persistent, true )
											    );
										    } )
		    .map( prop -> new ClassicPropertyMeta( this.getEntityName(), prop, this ) )
		    .collect( Collectors.toList() );

		this.idProperties				= this.allPersistentProperties.stream()
		    .filter( ( IPropertyMeta prop ) -> prop.getFieldType() == IPropertyMeta.FIELDTYPE.ID )
		    .collect( Collectors.toList() );

		if ( !this.isSubclass && this.idProperties.size() == 0 ) {
			this.idProperties = this.allPersistentProperties.stream()
			    .filter( ( IPropertyMeta prop ) -> prop.getName().equalsIgnoreCase( "id" ) )
			    .collect( Collectors.toList() );

			if ( this.idProperties.size() > 0 ) {
				logger.warn(
				    "Entity {} has no ID properties; am mutating property {} to an id fieldtype. Please mark your property as fieldtype='id' to avoid this implicit and deprecated behavior",
				    this.entityName, idProperties.get( 0 ).getName() );
			}

			// This ensures it is marked as an ID property, and will NOT be included in the standard COLUMN properties
			this.idProperties.forEach( ( IPropertyMeta prop ) -> prop.setFieldType( IPropertyMeta.FIELDTYPE.ID ) );
		}

		this.properties			= this.allPersistentProperties.stream().filter( (
		    IPropertyMeta prop ) -> prop.getFieldType() == IPropertyMeta.FIELDTYPE.COLUMN ).collect( Collectors.toList() );

		this.versionProperty	= this.allPersistentProperties.stream().filter( (
		    IPropertyMeta prop ) -> prop.getFieldType() == IPropertyMeta.FIELDTYPE.VERSION || prop.getFieldType() == IPropertyMeta.FIELDTYPE.TIMESTAMP )
		    .findFirst().orElse( null );

		this.associations		= this.allPersistentProperties.stream().filter( (
		    IPropertyMeta prop ) -> prop.isAssociationType() ).collect( Collectors.toList() );
	}
}