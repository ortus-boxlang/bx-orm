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

import java.lang.reflect.Method;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.type.CompositeType;

/**
 * Assists in generating BoxProxy classes for Hibernate.
 *
 * @since 1.0.0
 */
public class BoxProxyFactory implements ProxyFactory {

	private String			entityName;
	private PersistentClass	mappingInfo;

	public BoxProxyFactory( PersistentClass mappingInfo ) {
		this.mappingInfo	= mappingInfo;
		this.entityName		= mappingInfo.getEntityName();
	}

	@Override
	public void postInstantiate( String entityName, Class<?> persistentClass, Set<Class<?>> interfaces,
	    Method getIdentifierMethod, Method setIdentifierMethod, CompositeType componentIdType )
	    throws HibernateException {
		// BoxLang entities have no Java class or identifier accessor methods; only the entity name matters.
		if ( entityName != null ) {
			this.entityName = entityName;
		}
	}

	@Override
	public HibernateProxy getProxy( Object id, SharedSessionContractImplementor session )
	    throws HibernateException {
		return new BoxProxy( entityName, id, session, mappingInfo );
	}

}
