package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public abstract class AbstractEntityMeta implements IEntityMeta {

	public final Logger				logger			= LoggerFactory.getLogger( AbstractEntityMeta.class );

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

		// Setup the basic entity metadata
		this.meta					= entityMeta;
		this.annotations			= this.meta.getAsStruct( Key.annotations );

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

	public String getDatasource() {
		return this.datasource;
	}

	public String getEntityName() {
		return this.entityName;
	}

	public boolean isSimpleEntity() {
		return this.isSimpleEntity;
	}

	public boolean isExtended() {
		return this.isExtended;
	}

	public boolean isImmutable() {
		return this.isImmutable;
	}

	public boolean isDynamicInsert() {
		return this.isDynamicInsert;
	}

	public boolean isDynamicUpdate() {
		return this.isDynamicUpdate;
	}

	public boolean isSelectBeforeUpdate() {
		return this.isSelectBeforeUpdate;
	}

	public String getTableName() {
		// @TODO: Use the naming strategy to generate or massage the table name
		return this.tableName;
	}

	public String getSchema() {
		return this.schemaName;
	}

	public String getCatalog() {
		return this.catalogName;
	}

	public IStruct getDiscriminator() {
		return this.discriminator;
	}

	public IStruct getCache() {
		return this.cache;
	}

	public Integer getBatchSize() {
		return this.batchsize;
	}

	public String getRowID() {
		return this.rowid;
	}

	public boolean isLazy() {
		return this.isLazy;
	}

	public String getOptimisticLock() {
		return this.optimisticLock;
	}

	public String getWhere() {
		return this.where;
	}

	public IStruct getParentMeta() {
		return this.parentMeta;
	}

	/**
	 * Property methods
	 */
	public List<IPropertyMeta> getIdProperties() {
		return this.idProperties;
	}

	public List<IPropertyMeta> getProperties() {
		return this.properties;
	}

	public IPropertyMeta getVersionProperty() {
		return this.versionProperty;
	}

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
