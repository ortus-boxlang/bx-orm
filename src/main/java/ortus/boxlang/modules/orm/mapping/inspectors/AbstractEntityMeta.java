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
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

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

	protected List<IPropertyMeta>	idProperties;

	protected List<IPropertyMeta>	properties;

	protected List<IPropertyMeta>	associations;

	protected IPropertyMeta			versionProperty;

	protected String				datasource;

	protected String				entityName;

	protected boolean				isSimpleEntity;

	protected boolean				isExtended;

	protected boolean				isImmutable;

	protected boolean				isDynamicInsert;

	protected boolean				isDynamicUpdate;

	protected boolean				isLazy;

	protected boolean				isSelectBeforeUpdate;

	protected String				tableName;

	protected String				schemaName;

	protected String				catalogName;

	protected Integer				batchsize;

	protected String				optimisticLock;

	protected String				rowid;

	protected String				where;

	protected IStruct				discriminator	= Struct.EMPTY;

	protected IStruct				cache			= Struct.EMPTY;

	public AbstractEntityMeta( IStruct entityMeta ) {
		this.logger					= runtime.getLoggingService().getLogger( "orm" );

		// Setup the basic entity metadata
		this.meta					= entityMeta;
		this.annotations			= this.meta.getAsStruct( Key.annotations );

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
		// logger.debug( "Class contains 'persistent' annotation; using ClassicEntityMeta: [{}]", meta.getAsString( Key.path ) );
		var annotations = meta.getAsStruct( Key.annotations );
		if ( annotations.containsKey( ORMKeys.persistent ) ) {
			return new ClassicEntityMeta( meta );
		}
		// logger.debug( "Using ModernEntityMeta: [{}]", meta.getAsString( Key.path ) );
		return new ModernEntityMeta( meta );
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
	 * Translate the table name using the configured table naming strategy.
	 *
	 * @param tableName Table name to translate, like 'owner'.
	 * 
	 * @return Translated table name, like 'tblOwners'.
	 */
	protected String translateTableName( String tableName ) {
		// TODO: Translate the table name using the configured table naming strategy.
		return tableName;
	}

	/**
	 * Translate the column name using the configured column naming strategy.
	 *
	 * @param columnName column name to translate, like 'owner'.
	 * 
	 * @return Translated column name, like 'tblOwners'.
	 */
	protected String translateColumnName( String columnName ) {
		// TODO: Translate the column name using the configured column naming strategy.
		return columnName;
	}
}
