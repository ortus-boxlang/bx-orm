package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public abstract class AbstractEntityMeta implements IEntityMeta {

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
		this.allProperties			= this.meta.getAsArray( Key.properties );

		// Parse extended entity metadata
		this.parentMeta				= this.isExtended
		    ? this.meta.getAsStruct( Key._EXTENDS )
		    : Struct.EMPTY;

		if ( this.isExtended ) {
			// Copy parent properties into the allProperties array
			this.allProperties.addAll( this.parentMeta.getAsArray( Key.properties ) );
		}

	}

	/**
	 * Auto-discovers the entity metadata type (Modern or Classic) based on the presence of the `persistent` annotation.
	 * 
	 * @param meta Struct of entity metadata to inspect.
	 * 
	 * @return Instance of IEntityMeta, either ClassicEntityMeta or ModernEntityMeta.
	 */
	public static IEntityMeta autoDiscoverMetaType( IStruct meta ) {
		var annotations = meta.getAsStruct( Key.annotations );
		if ( annotations.containsKey( ORMKeys.persistent ) ) {
			return new ClassicEntityMeta( meta );
		}
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
