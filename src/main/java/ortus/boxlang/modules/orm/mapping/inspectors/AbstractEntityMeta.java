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

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.KeyCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.BLCollector;

/**
 * Abstract (parent) entity metadata configuration class.
 *
 * Useful for collecting entity metadata into a consistent interface, no matter whether the source is traditional CFML annotations or modern JPA-style
 * annotations.
 */
public abstract class AbstractEntityMeta implements IEntityMeta {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime			= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	protected BoxLangLogger			logger;

	protected IStruct				meta;
	protected IStruct				annotations;
	protected IStruct				parentMeta;

	/**
	 * All properties of the entity, including transient properties and parent properties.
	 */
	protected Array					allProperties;

	protected List<IPropertyMeta>	allPersistentProperties;

	protected List<IPropertyMeta>	parentPersistentProperties;

	protected List<IPropertyMeta>	idProperties;

	protected List<IPropertyMeta>	properties;

	protected List<IPropertyMeta>	associations;

	protected IPropertyMeta			versionProperty;

	protected String				datasource;

	protected String				entityName;

	protected boolean				isSimpleEntity;

	protected boolean				isExtended;

	protected boolean				isSubclass;

	protected String				joinColumn;

	protected boolean				isImmutable;

	protected boolean				isDynamicInsert;

	protected boolean				isDynamicUpdate;

	protected boolean				isLazy;

	protected boolean				isSelectBeforeUpdate;

	protected String				tableName;

	protected String				schemaName;

	protected String				catalogName;

	/**
	 * Default batch size is 25 limit the number of entities initially in memory
	 */
	protected Integer				batchsize		= 25;

	protected String				optimisticLock;

	protected String				rowid;

	protected String				where;

	protected IStruct				discriminator	= Struct.EMPTY;

	protected IStruct				cache			= Struct.EMPTY;

	public AbstractEntityMeta( IStruct entityMeta ) {

		this.logger			= runtime.getLoggingService().getLogger( "orm" );

		// Setup the basic entity metadata
		this.meta			= entityMeta;
		this.annotations	= this.meta.getAsStruct( Key.annotations );
		if ( this.annotations == null ) {
			this.annotations = Struct.of();
		}

		this.datasource				= StringCaster.cast( this.meta.getOrDefault( Key.datasource, "" ) );

		// Handle generic entity metadata, i.e. metadata that is common to both classic and modern annotation syntax.
		this.isExtended				= this.meta.containsKey( Key._EXTENDS )
		    && !this.meta.getAsStruct( Key._EXTENDS ).isEmpty();

		this.isDynamicInsert		= this.annotations.containsKey( ORMKeys.dynamicInsert )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicInsert, false ) );

		this.isDynamicUpdate		= this.annotations.containsKey( ORMKeys.dynamicUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicUpdate, false ) );

		this.isSelectBeforeUpdate	= this.annotations.containsKey( ORMKeys.selectBeforeUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.selectBeforeUpdate, false ) );

		this.associations			= new ArrayList<>();
		this.allProperties			= new Array();

		// Parse extended entity metadata
		this.parentMeta				= this.isExtended
		    ? this.meta.getAsStruct( Key._EXTENDS )
		    : Struct.EMPTY;

		this.isSimpleEntity			= true;

		// @TODO: We need to reimplement or rethink this to work recursively upwards. i.e., this current logic only works for one level of inheritance. :/
		if ( this.isExtended ) {
			IStruct	parentAnnotations			= this.parentMeta.getAsStruct( Key.annotations );
			// @Entity
			boolean	isParentPersistent			= parentAnnotations.containsKey( ORMKeys.entity )
			    // persistent="false"
			    || ( parentAnnotations.containsKey( ORMKeys.persistent )
			        && BooleanCaster.cast( parentAnnotations.getOrDefault( ORMKeys.persistent, false ) ) );
			// @mappedSuperClass
			boolean	isParentMappedSuperClass	= parentAnnotations.containsKey( ORMKeys.mappedSuperClass )
			    // Default to true to support @mappedSuperClass without a value. Otherwise, mappedSuperClass=false will be parsed as boolean.
			    && BooleanCaster.cast( parentAnnotations.getOrDefault( ORMKeys.mappedSuperClass, true ) );

			if ( !isParentPersistent && isParentMappedSuperClass ) {
				this.allProperties.addAll( this.parentMeta.getAsArray( Key.properties ) );
				this.isSimpleEntity = true;
			} else if ( isParentPersistent
			    && ( this.annotations.containsKey( ORMKeys.joinColumn ) || this.annotations.containsKey( ORMKeys.discriminatorValue ) ) ) {
				this.isSubclass	= true;
				this.joinColumn	= this.annotations.getAsString( ORMKeys.joinColumn );
				if ( this.joinColumn == null ) {
					IStruct idColumn = this.parentMeta.getAsArray( Key.properties )
					    .stream()
					    .map( StructCaster::cast )
					    .filter( item -> item.containsKey( Key.annotations ) )
					    .filter( item -> {
						    IStruct annotations = item.getAsStruct( Key.annotations );
						    return annotations.containsKey( ORMKeys.fieldtype )
						        && annotations.containsKey( Key.column )
						        && annotations.getAsString( ORMKeys.fieldtype ).equalsIgnoreCase( "id" );
					    }
					    ).findFirst().orElse( null );
					if ( idColumn != null ) {
						this.joinColumn = idColumn.getAsStruct( Key.annotations ).getAsString( Key.column );
					}
				}
				this.isSimpleEntity = false;
			}
		}

		// Only add the current entity's properties after first adding any parent properties.
		this.allProperties.addAll( this.meta.getAsArray( Key.properties ) );
	}

	/**
	 * Auto-discovers the entity metadata type (Modern or Classic) based on the presence of the `persistent` annotation.
	 *
	 * @param meta Struct of entity metadata to inspect.
	 *
	 * @return Instance of IEntityMeta, either ClassicEntityMeta or ModernEntityMeta.
	 */
	public static IEntityMeta autoDiscoverMetaType( IStruct meta ) {
		return new ClassicEntityMeta( meta );
	}

	/**
	 * Gets the datasource of the entity.
	 *
	 * @return the datasource of the entity.
	 */
	public String getDatasource() {
		return this.datasource;
	}

	/**
	 * Gets the name of the entity.
	 *
	 * @return the name of the entity.
	 */
	public String getEntityName() {
		return this.entityName;
	}

	/**
	 * Checks if the entity is a simple entity.
	 *
	 * @return true if the entity is a simple entity, false otherwise.
	 */
	public boolean isSimpleEntity() {
		return this.isSimpleEntity;
	}

	/**
	 * Checks if the entity is extended.
	 *
	 * @return true if the entity is extended, false otherwise.
	 */
	public boolean isExtended() {
		return this.isExtended;
	}

	/**
	 * Returns the meta struct of the entity.
	 *
	 * @return
	 */
	public IStruct getMeta() {
		return this.meta;
	}

	/**
	 * Returns whether an entity is a discriminated subclass of another entity
	 *
	 * @return
	 */
	public boolean isSubclass() {
		return this.isSubclass;
	}

	public String getJoinColumn() {
		return this.joinColumn;
	}

	/**
	 * Checks if the entity is immutable.
	 *
	 * @return true if the entity is immutable, false otherwise.
	 */
	public boolean isImmutable() {
		return this.isImmutable;
	}

	/**
	 * Checks if dynamic insert is enabled for the entity.
	 *
	 * @return true if dynamic insert is enabled, false otherwise.
	 */
	public boolean isDynamicInsert() {
		return this.isDynamicInsert;
	}

	/**
	 * Checks if dynamic update is enabled for the entity.
	 *
	 * @return true if dynamic update is enabled, false otherwise.
	 */
	public boolean isDynamicUpdate() {
		return this.isDynamicUpdate;
	}

	/**
	 * Checks if select before update is enabled for the entity.
	 *
	 * @return true if select before update is enabled, false otherwise.
	 */
	public boolean isSelectBeforeUpdate() {
		return this.isSelectBeforeUpdate;
	}

	/**
	 * Gets the table name of the entity.
	 *
	 * @return the table name of the entity.
	 */
	public String getTableName() {
		return this.tableName;
	}

	/**
	 * Gets the schema name of the entity.
	 *
	 * @return the schema name of the entity.
	 */
	public String getSchema() {
		return this.schemaName;
	}

	/**
	 * Gets the catalog name of the entity.
	 *
	 * @return the catalog name of the entity.
	 */
	public String getCatalog() {
		return this.catalogName;
	}

	/**
	 * Gets the discriminator of the entity.
	 *
	 * @return the discriminator of the entity.
	 */
	public IStruct getDiscriminator() {
		return this.discriminator;
	}

	/**
	 * Gets the cache configuration of the entity.
	 *
	 * @return the cache configuration of the entity.
	 */
	public IStruct getCache() {
		return this.cache;
	}

	/**
	 * Gets the batch size for the entity.
	 *
	 * @return the batch size for the entity.
	 */
	public Integer getBatchSize() {
		return this.batchsize;
	}

	/**
	 * Gets the row ID of the entity.
	 *
	 * @return the row ID of the entity.
	 */
	public String getRowID() {
		return this.rowid;
	}

	/**
	 * Checks if lazy loading is enabled for the entity.
	 *
	 * @return true if lazy loading is enabled, false otherwise.
	 */
	public boolean isLazy() {
		return this.isLazy;
	}

	/**
	 * Gets the optimistic lock strategy for the entity.
	 *
	 * @return the optimistic lock strategy for the entity.
	 */
	public String getOptimisticLock() {
		return this.optimisticLock;
	}

	/**
	 * Gets the where clause for the entity.
	 *
	 * @return the where clause for the entity.
	 */
	public String getWhere() {
		return this.where;
	}

	/**
	 * Gets the parent metadata of the entity.
	 *
	 * @return the parent metadata of the entity.
	 */
	public IStruct getParentMeta() {
		return this.parentMeta;
	}

	/**
	 * Gets the ID properties of the entity.
	 *
	 * @return the ID properties of the entity.
	 */
	public List<IPropertyMeta> getIdProperties() {
		return this.idProperties;
	}

	/**
	 * Gets ALL entity properties, including id,version,timestamp,relationship, and regular properties.
	 *
	 * @return all ORM properties.
	 */
	public List<IPropertyMeta> getAllPersistentProperties() {
		return this.allPersistentProperties;
	}

	/**
	 * Gets the properties of the entity.
	 *
	 * @return the properties of the entity.
	 */
	public List<IPropertyMeta> getProperties() {
		return this.properties;
	}

	/**
	 * Gets the version property of the entity.
	 *
	 * @return the version property of the entity.
	 */
	public IPropertyMeta getVersionProperty() {
		return this.versionProperty;
	}

	/**
	 * Gets the associations of the entity.
	 *
	 * @return the associations of the entity.
	 */
	public List<IPropertyMeta> getAssociations() {
		return this.associations;
	}

	/**
	 * Gets all persistent property names as an Array of Keys for comparison
	 *
	 * @return An Array containing keys of the names of all persistent properties.
	 */
	public Array getPropertyNamesArray() {
		Array	properties	= getAllPersistentProperties().stream().map( property -> KeyCaster.cast( property.getName() ) )
		    .collect( BLCollector.toArray() );

		Array	parentMeta	= getParentMeta().getAsArray( Key.properties );
		if ( parentMeta != null ) {
			properties.addAll(
			    parentMeta.stream().map( StructCaster::cast )
			        .map( prop -> KeyCaster.cast( prop.getAsStruct( Key.annotations ).get( Key._NAME ) ) ).collect( BLCollector.toArray() )
			);
		}
		return properties;
	}
}
