package com.ortussolutions.hibernate;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;

import ortus.boxlang.runtime.context.IBoxContext;

public class BoxPropertyGetter implements Getter {

	private Property		mappedProperty;
	private PersistentClass	mappedEntity;
	private IBoxContext		context;

	public BoxPropertyGetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
		this.context		= context;
	}

	@Override
	public Object get( Object owner ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'get'" );
	}

	@Override
	public Object getForInsert( Object owner, Map mergeMap, SharedSessionContractImplementor session ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getForInsert'" );
	}

	@Override
	public Class getReturnType() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getReturnType'" );
	}

	@Override
	public Member getMember() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMember'" );
	}

	@Override
	public String getMethodName() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMethodName'" );
	}

	@Override
	public Method getMethod() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMethod'" );
	}

}
