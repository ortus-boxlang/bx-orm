package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public abstract class AbstractPropertyMeta implements IPropertyMeta {

	protected IStruct	meta;
	protected IStruct	annotations;

	protected String	name;
	protected boolean	isImmutable			= false;
	protected boolean	isOptimisticLock	= true;
	protected boolean	isLazy				= false;
	protected String	formula;
	protected IStruct	types;
	protected IStruct	generator;
	protected IStruct	column;
	protected String	unsavedValue;

	public AbstractPropertyMeta( IStruct meta ) {
		this.meta			= meta;
		this.annotations	= this.meta.getAsStruct( Key.annotations );
		this.name			= this.meta.getAsString( Key._NAME );
		this.column			= parseColumnAnnotations( this.annotations );
		this.types			= parseTypeAnnotations( this.annotations );
		this.generator		= parseGeneratorAnnotations( this.annotations );
	}

	protected abstract IStruct parseColumnAnnotations( IStruct annotations );

	protected abstract IStruct parseGeneratorAnnotations( IStruct annotations );

	// @TODO: Switch to abstract and override in the implementations.
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
		if ( annotations.containsKey( ORMKeys.ORMType ) ) {
			typeInfo.put( ORMKeys.ORMType, annotations.getAsString( ORMKeys.ORMType ) );
		}
		return typeInfo;
	}

	@Override
	public IStruct getAnnotations() {
		return this.annotations;
	}

	@Override
	public boolean isImmutable() {
		return this.isImmutable;
	}

	@Override
	public boolean isOptimisticLock() {
		return this.isOptimisticLock;
	}

	@Override
	public boolean isLazy() {
		return this.isLazy;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getFormula() {
		return this.formula;
	}

	@Override
	public IStruct getTypes() {
		return this.types;
	}

	@Override
	public IStruct getColumn() {
		return this.column;
	}

	@Override
	public IStruct getGenerator() {
		return this.generator;
	}

	public String getUnsavedValue() {
		return this.unsavedValue;
	}
}
