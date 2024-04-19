package ortus.boxlang.orm.hibernate;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.type.CompositeType;

public class BoxProxyFactory implements ProxyFactory {

	public PersistentClass	mappingInfo;
	public Getter			idGetter;
	public Setter			idSetter;

	public BoxProxyFactory( PersistentClass mappingInfo, Getter idGetter, Setter idSetter ) {
		this.mappingInfo	= mappingInfo;
		this.idGetter		= idGetter;
		this.idSetter		= idSetter;
	}

	@Override
	public void postInstantiate( String entityName, Class persistentClass, Set<Class> interfaces,
	    Method getIdentifierMethod, Method setIdentifierMethod, CompositeType componentIdType )
	    throws HibernateException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'postInstantiate'" );
	}

	@Override
	public HibernateProxy getProxy( Serializable id, SharedSessionContractImplementor session )
	    throws HibernateException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getProxy'" );
	}

}
