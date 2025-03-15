/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.hibernate;

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

/**
 * Assists in generating BoxProxy classes for Hibernate.
 */
public class BoxProxyFactory implements ProxyFactory {

	@SuppressWarnings( "unused" )
	private String			className; // needed for compilation
	private String			entityName;
	private PersistentClass	mappingInfo;
	@SuppressWarnings( "unused" )
	private Getter			idGetter;  // needed for compilation
	@SuppressWarnings( "unused" )
	private Setter			idSetter;  // needed for compilation

	public BoxProxyFactory( PersistentClass mappingInfo, Getter idGetter, Setter idSetter ) {
		this.mappingInfo	= mappingInfo;
		this.idGetter		= idGetter;
		this.idSetter		= idSetter;
		this.className		= mappingInfo.getClassName();
		this.entityName		= mappingInfo.getEntityName();
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
		return new BoxProxy( entityName, id, session, mappingInfo );
	}

}
