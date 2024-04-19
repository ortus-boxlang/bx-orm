package com.ortussolutions.hibernate;

import org.hibernate.EntityMode;
import org.hibernate.EntityNameResolver;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.AbstractEntityTuplizer;
import org.hibernate.tuple.entity.EntityMetamodel;

import com.ortussolutions.SessionFactoryBuilder;

public class EntityTuplizer extends AbstractEntityTuplizer {

	EntityMetamodel entityMetamodel;

	public EntityTuplizer( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		super( entityMetamodel, mappingInfo );

		// this.entityMetamodel = entityMetamodel;
	}

	@Override
	public EntityMode getEntityMode() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getEntityMode'" );
	}

	@Override
	public Class getConcreteProxyClass() {
		return BoxProxy.class;
	}

	@Override
	public EntityNameResolver[] getEntityNameResolvers() {
		return new EntityNameResolver[] { new BoxEntityNameResolver() };
	}

	@Override
	public String determineConcreteSubclassEntityName( Object entityInstance, SessionFactoryImplementor factory ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'determineConcreteSubclassEntityName'" );
	}

	@Override
	public Class getMappedClass() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMappedClass'" );
	}

	@Override
	protected Getter buildPropertyGetter( Property mappedProperty, PersistentClass mappedEntity ) {
		return new BoxPropertyGetter( SessionFactoryBuilder.getContext( this.getEntityMetamodel().getSessionFactory() ),
		    mappedProperty, mappedEntity );
	}

	@Override
	protected Setter buildPropertySetter( Property mappedProperty, PersistentClass mappedEntity ) {
		return new BoxPropertySetter( SessionFactoryBuilder.getContext( this.getEntityMetamodel().getSessionFactory() ),
		    mappedProperty, mappedEntity );
	}

	@Override
	protected Instantiator buildInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		return new BoxClassInstantiator( entityMetamodel, mappingInfo );
	}

	@Override
	protected ProxyFactory buildProxyFactory( PersistentClass mappingInfo, Getter idGetter, Setter idSetter ) {
		return new BoxProxyFactory( mappingInfo, idGetter, idSetter );
	}

}
