package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;

import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.loader.ClassLocator;

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
		String		bxClassFQN	= SessionFactoryBuilder.lookupBoxLangClass( entityMetamodel.getSessionFactory(),
		    entityMetamodel.getName() );
		IBoxContext	context		= SessionFactoryBuilder
		    .getApplicationContext( entityMetamodel.getSessionFactory() );

		return classLocator.load( context, bxClassFQN, "bx" ).invokeConstructor( context ).unWrapBoxLangClass();
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
