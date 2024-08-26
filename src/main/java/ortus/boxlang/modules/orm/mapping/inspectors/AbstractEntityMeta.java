package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.List;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public abstract class AbstractEntityMeta implements IEntityMeta {

	protected IStruct				meta;
	protected IStruct				annotations;

	protected List<IPropertyMeta>	idProperties;

	protected List<IPropertyMeta>	properties;

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
		this.annotations			= entityMeta.getAsStruct( Key.annotations );

		// Handle generic entity metadata, i.e. metadata that is common to both classic and modern annotation syntax.
		this.isExtended				= this.meta.containsKey( Key._EXTENDS )
		    && !this.meta.getAsStruct( Key._EXTENDS ).isEmpty();

		this.isDynamicInsert		= this.annotations.containsKey( ORMKeys.dynamicInsert )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicInsert, false ) );

		this.isDynamicUpdate		= this.annotations.containsKey( ORMKeys.dynamicUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicUpdate, false ) );

		this.isSelectBeforeUpdate	= this.annotations.containsKey( ORMKeys.selectBeforeUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.selectBeforeUpdate, false ) );
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

	// public String getCatalog() {
	// return this.catalogName;
	// }

	// public String getCatalog() {
	// return this.catalogName;
	// }

	// public String getCatalog() {
	// return this.catalogName;
	// }

	/**
	 * Property methods
	 */
	public List<IPropertyMeta> getIdProperties() {
		return this.idProperties;
	}

	public List<IPropertyMeta> getProperties() {
		return this.properties;
	}
}
