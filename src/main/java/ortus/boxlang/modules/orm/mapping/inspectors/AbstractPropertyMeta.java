package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.runtime.types.IStruct;

public class AbstractPropertyMeta implements IPropertyMeta {

	protected IStruct meta;

	public AbstractPropertyMeta( IStruct meta ) {
		this.meta = meta;
	}

	@Override
	public boolean isPersistent() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isPersistent'" );
	}

	@Override
	public IStruct getType() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getType'" );
	}

	@Override
	public boolean isImmutable() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isImmutable'" );
	}

	@Override
	public boolean isUnique() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isUnique'" );
	}

	@Override
	public boolean isNullable() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isNullable'" );
	}

	@Override
	public boolean isOptimisticLock() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isOptimisticLock'" );
	}

	@Override
	public boolean isInsertable() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isInsertable'" );
	}

	@Override
	public boolean isUpdatable() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isUpdatable'" );
	}

	@Override
	public boolean isLazy() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isLazy'" );
	}

	@Override
	public IStruct getGenerator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getGenerator'" );
	}

}
