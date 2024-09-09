package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public abstract class AbstractPropertyMeta implements IPropertyMeta {

	protected IStruct	meta;
	protected IStruct	annotations;

	/**
	 * Parent entity name, for logging purposes
	 */
	protected String	entityName;
	protected String	name;
	protected boolean	isImmutable			= false;
	protected boolean	isOptimisticLock	= true;
	protected String	lazy;
	protected String	formula;
	protected IStruct	types;
	protected IStruct	generator;
	protected IStruct	column;
	protected IStruct	association;
	protected String	unsavedValue;

	public AbstractPropertyMeta( String entityName, IStruct meta ) {
		this.entityName		= entityName;
		this.meta			= meta;
		this.annotations	= this.meta.getAsStruct( Key.annotations );
		this.name			= this.meta.getAsString( Key._NAME );
		this.column			= parseColumnAnnotations( this.annotations );
		this.types			= parseTypeAnnotations( this.annotations );
		this.generator		= parseGeneratorAnnotations( this.annotations );
		this.association	= parseAssociation( this.annotations );
	}

	protected abstract IStruct parseColumnAnnotations( IStruct annotations );

	protected abstract IStruct parseGeneratorAnnotations( IStruct annotations );

	protected abstract IStruct parseAssociation( IStruct annotations );

	private IStruct parseTypeAnnotations( IStruct annotations ) {
		IStruct typeInfo = new Struct();
		if ( annotations.containsKey( Key.type ) ) {
			typeInfo.put( Key.type, annotations.getAsString( Key.type ) );
		}
		if ( annotations.containsKey( ORMKeys.fieldtype ) ) {
			typeInfo.put( ORMKeys.fieldtype, annotations.getAsString( ORMKeys.fieldtype ) );
		}
		if ( annotations.containsKey( Key.sqltype ) ) {
			typeInfo.put( Key.sqltype, annotations.getAsString( Key.sqltype ) );
		}
		typeInfo.put( ORMKeys.ORMType, annotations.getOrDefault( ORMKeys.ORMType, "string" ) );
		return typeInfo;
	}

	public IStruct getAnnotations() {
		return this.annotations;
	}

	public boolean isImmutable() {
		return this.isImmutable;
	}

	public boolean isOptimisticLock() {
		return this.isOptimisticLock;
	}

	public String getLazy() {
		return this.lazy;
	}

	public String getName() {
		return this.name;
	}

	public String getFormula() {
		return this.formula;
	}

	public String getUnsavedValue() {
		return this.unsavedValue;
	}

	public IStruct getTypes() {
		return this.types;
	}

	public IStruct getColumn() {
		return this.column;
	}

	public IStruct getGenerator() {
		return this.generator;
	}

	public IStruct getAssociation() {
		return this.association;
	}
}
