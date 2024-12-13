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

import ortus.boxlang.modules.orm.SessionFactoryBuilder;

/**
 * Hibernate implementation class for helping convert tuples (database rows) into boxlang classes.
 */
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
		return new BoxPropertyGetter( SessionFactoryBuilder.getRequestContext( this.getEntityMetamodel().getSessionFactory() ),
		    mappedProperty, mappedEntity );
	}

	@Override
	protected Setter buildPropertySetter( Property mappedProperty, PersistentClass mappedEntity ) {
		return new BoxPropertySetter( SessionFactoryBuilder.getRequestContext( this.getEntityMetamodel().getSessionFactory() ),
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
