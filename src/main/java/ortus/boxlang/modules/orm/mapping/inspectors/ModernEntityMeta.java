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

/**
 * A "Modern", aka JPA-style, implementation of the entity metadata configuration.
 *
 * i.e. handles translating JPA-style property annotations like `@Entity` or `@Discriminator{...}` into the IEntityMeta interface for consistent
 * reference by the HibernateXMLWriter.
 */
public class ModernEntityMeta extends AbstractEntityMeta {

	public ModernEntityMeta( IStruct entityMeta ) {
		super( entityMeta );

		// @TODO: Attempt to support @Entity.foo syntax
		annotations.computeIfAbsent( ORMKeys.entity, key -> meta.getAsString( Key._name ) );
		entityName = ( String ) annotations.getAsString( ORMKeys.entity );
		if ( entityName == null || entityName.isEmpty() ) {
			entityName = meta.getAsString( Key._name );
		}

		/**
		 * All properties set here MUST be specific to the modern entity metadata. If they are applicable to both modern and classic metadata, they should be
		 * set in the AbstractEntityMeta constructor.
		 */
		isImmutable				= annotations.containsKey( ORMKeys.immutable );

		isDynamicInsert			= annotations.containsKey( ORMKeys.dynamicInsert )
		    && BooleanCaster.cast( annotations.getOrDefault( ORMKeys.dynamicInsert, false ) );

		isDynamicUpdate			= annotations.containsKey( ORMKeys.dynamicUpdate )
		    && BooleanCaster.cast( annotations.getOrDefault( ORMKeys.dynamicUpdate, false ) );

		isSelectBeforeUpdate	= annotations.containsKey( ORMKeys.selectBeforeUpdate )
		    && BooleanCaster.cast( annotations.getOrDefault( ORMKeys.selectBeforeUpdate, false ) );

		if ( annotations.containsKey( ORMKeys.discriminator ) ) {
			discriminator = annotations.getAsStruct( ORMKeys.discriminator );
		}

		if ( annotations.containsKey( ORMKeys.lazy ) ) {
			isLazy = BooleanCaster.cast( annotations.getOrDefault( ORMKeys.lazy, true ) );
		}

		if ( annotations.containsKey( ORMKeys.batchsize ) ) {
			batchsize = IntegerCaster.cast( annotations.get( ORMKeys.batchsize ) );
		}

		if ( annotations.containsKey( ORMKeys.optimisticLock ) ) {
			optimisticLock = annotations.getAsString( ORMKeys.optimisticLock );
		}

		if ( annotations.containsKey( ORMKeys.rowid ) ) {
			rowid = annotations.getAsString( ORMKeys.rowid );
		}

		if ( annotations.containsKey( ORMKeys.where ) ) {
			where = annotations.getAsString( ORMKeys.where );
		}
		if ( annotations.containsKey( ORMKeys.optimisticLock ) ) {
			optimisticLock = annotations.getAsString( ORMKeys.optimisticLock );
		}

		// https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0#table-annotation
		if ( annotations.containsKey( Key.table ) ) {
			var table = annotations.getAsStruct( Key.table );
			tableName = table.getAsString( Key._NAME );

			if ( table.containsKey( ORMKeys.schema ) ) {
				schemaName = table.getAsString( ORMKeys.schema );
			}

			if ( table.containsKey( ORMKeys.catalog ) ) {
				catalogName = table.getAsString( ORMKeys.catalog );
			}
		}

		if ( annotations.containsKey( Key.cache ) ) {
			cache = annotations.getAsStruct( Key.cache );
		}

		allPersistentProperties	= allProperties.stream()
		    .map( IStruct.class::cast )
		    // Filter out transient properties, aka @Transient
		    .filter( ( prop ) -> !prop.getAsStruct( Key.annotations ).containsKey( ORMKeys._transient ) )
		    .map( prop -> new ModernPropertyMeta( getEntityName(), prop, this ) )
		    .collect( Collectors.toList() );

		idProperties			= allPersistentProperties.stream()
		    .filter( ( IPropertyMeta prop ) -> prop.getFieldType() == IPropertyMeta.FIELDTYPE.ID )
		    .collect( Collectors.toList() );

		// @TODO: Assume a property with name 'id' is the ID property.
		if ( !isSubclass && idProperties.size() == 0 ) {
			idProperties = allPersistentProperties.stream()
			    .filter( ( IPropertyMeta prop ) -> prop.getName().equalsIgnoreCase( "id" ) )
			    .collect( Collectors.toList() );

			if ( idProperties.size() > 0 ) {
				logger.warn(
				    "Entity {} has no ID properties; am mutating property {} to an id fieldtype. Please mark your property as @Id to avoid this implicit and deprecated behavior",
				    entityName, idProperties.get( 0 ).getName() );
			}

			// This ensures it is marked as an ID property, and will NOT be included in the standard COLUMN properties
			idProperties.forEach( ( IPropertyMeta prop ) -> prop.setFieldType( IPropertyMeta.FIELDTYPE.ID ) );
		}

		properties		= allPersistentProperties.stream()
		    .filter( ( IPropertyMeta prop ) -> prop.getFieldType() == IPropertyMeta.FIELDTYPE.COLUMN )
		    .collect( Collectors.toList() );

		versionProperty	= allPersistentProperties.stream()
		    .filter(
		        ( IPropertyMeta prop ) -> prop.getFieldType() == IPropertyMeta.FIELDTYPE.VERSION || prop.getFieldType() == IPropertyMeta.FIELDTYPE.TIMESTAMP )
		    .findFirst()
		    .orElse( null );

		associations	= allPersistentProperties.stream()
		    .filter( ( IPropertyMeta prop ) -> prop.isAssociationType() )
		    .collect( Collectors.toList() );
	}
}
