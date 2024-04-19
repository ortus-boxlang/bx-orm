package ortus.boxlang.orm.hibernate;

import java.io.Serializable;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;

import ortus.boxlang.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IClassRunnable;

public class BoxClassInstantiator implements Instantiator {

	private EntityMetamodel	entityMetamodel;
	private PersistentClass	mappingInfo;

	private ClassLocator	classLocator	= ClassLocator.getInstance();

	public BoxClassInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		this.entityMetamodel	= entityMetamodel;
		this.mappingInfo		= mappingInfo;
	}

	@Override
	public Object instantiate( Serializable id ) {
		Class<IClassRunnable>	bxClass	= SessionFactoryBuilder.lookupBoxLangClass( entityMetamodel.getSessionFactory(),
		    entityMetamodel.getName() );
		// ApplicationBoxContext context = SessionFactoryBuilder
		IBoxContext				context	= SessionFactoryBuilder
		    .getContext( entityMetamodel.getSessionFactory() );

		return DynamicObject.of( bxClass ).invokeConstructor( context ).getTargetInstance();
	}

	@Override
	public Object instantiate() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'instantiate'" );
	}

	@Override
	public boolean isInstance( Object object ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isInstance'" );
	}

}
