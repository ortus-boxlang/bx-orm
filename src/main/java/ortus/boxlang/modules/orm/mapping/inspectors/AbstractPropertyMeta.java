package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public class AbstractPropertyMeta implements IPropertyMeta {

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

	public AbstractPropertyMeta( IStruct meta ) {
		this.meta			= meta;
		this.annotations	= this.meta.getAsStruct( Key.annotations );
		this.name			= this.meta.getAsString( Key._NAME );
		// this.types.put( "type", this.meta.getAsString( Key.type ) );
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

}
