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

import org.hibernate.EntityMode;
import org.hibernate.EntityNameResolver;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.AbstractEntityTuplizer;
import org.hibernate.tuple.entity.EntityMetamodel;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Hibernate {@link AbstractEntityTuplizer} implementation that bridges Hibernate's tuple-based
 * persistence model with BoxLang entity classes.
 * <p>
 * Hibernate uses a tuplizer to perform all reflective operations on a mapped entity: instantiation,
 * property access (get/set), identifier management, proxy creation, and entity-name resolution.
 * This implementation replaces every Java-reflection-based component with BoxLang-aware equivalents
 * so that ORM entities written as {@code .bx} classes are handled correctly throughout the
 * Hibernate lifecycle.
 * <p>
 * Key responsibilities:
 * <ul>
 * <li>Resolves entity identifiers from both live {@link IClassRunnable} instances and
 * {@link BoxProxy} proxies.</li>
 * <li>Normalises {@link Key}-typed identifiers to plain {@link String} values before passing
 * them to Hibernate.</li>
 * <li>Reports {@link EntityMode#MAP} so that Hibernate treats entities as dynamic-map objects
 * rather than plain Java beans.</li>
 * <li>Delegates property access to {@link BoxPropertyGetter} / {@link BoxPropertySetter},
 * instantiation to {@link BoxClassInstantiator}, proxy creation to {@link BoxProxyFactory},
 * and name resolution to {@link BoxEntityNameResolver}.</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class EntityTuplizer extends AbstractEntityTuplizer {

	/**
	 * Constructs a new {@code EntityTuplizer} for the given entity metamodel and mapping.
	 *
	 * @param entityMetamodel the Hibernate metamodel describing the entity
	 * @param mappingInfo     the Hibernate persistent-class mapping for the entity
	 */
	public EntityTuplizer( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		super( entityMetamodel, mappingInfo );
	}

	/**
	 * Extracts the serializable identifier from the given entity.
	 * <p>
	 * Handles both live {@link IClassRunnable} instances and {@link BoxProxy} lazy-loaded proxies
	 * by delegating to {@link ORMService#getEntityIdentifier(IClassRunnable)}. If the resolved
	 * value is not {@link Serializable}, an {@link IllegalArgumentException} is thrown.
	 *
	 * @param entity the entity instance or proxy whose identifier is needed
	 *
	 * @return the serializable primary-key value
	 *
	 * @throws IllegalArgumentException if the resolved identifier cannot be cast to {@link Serializable}
	 */
	@Override
	public Serializable getIdentifier( Object entity ) {
		Object identifier = entity;
		if ( entity instanceof BoxProxy proxyEntity ) {
			identifier = ORMService.getEntityIdentifier( proxyEntity.getRunnable() );
		} else if ( entity instanceof IClassRunnable runnable ) {
			identifier = ORMService.getEntityIdentifier( runnable );
		}
		if ( identifier instanceof Serializable serializableId ) {
			return serializableId;
		} else {
			throw new IllegalArgumentException( "Entity identifier [" + StringCaster.cast( identifier ) + "] is not serializable." );
		}

	}

	/**
	 * Sets the identifier value on the given entity instance.
	 * <p>
	 * If the provided {@code id} is a BoxLang {@link Key} instance, it is unwrapped to its
	 * underlying {@link String} name before being forwarded to the superclass implementation.
	 * This ensures that Hibernate stores a plain string identifier rather than a BoxLang key
	 * wrapper object.
	 *
	 * @param entity  the entity instance on which to set the identifier
	 * @param id      the identifier value to assign
	 * @param session the active Hibernate session
	 */
	@Override
	public void setIdentifier( Object entity, Serializable id, SharedSessionContractImplementor session ) {
		if ( id instanceof Key keyClass ) {
			id = keyClass.getName();
		}
		super.setIdentifier( entity, id, session );
	}

	/**
	 * Returns the Hibernate {@link EntityMode} used by this tuplizer.
	 * <p>
	 * Always returns {@link EntityMode#MAP} so that Hibernate treats BoxLang entities as
	 * dynamic-map objects rather than reflective Java beans.
	 *
	 * @return {@link EntityMode#MAP}
	 */
	@Override
	public EntityMode getEntityMode() {
		return EntityMode.MAP;
	}

	/**
	 * Returns the concrete proxy class used for lazy-loading BoxLang entities.
	 *
	 * @return {@link BoxProxy}{@code .class}
	 */
	@Override
	public Class getConcreteProxyClass() {
		return BoxProxy.class;
	}

	/**
	 * Returns the {@link EntityNameResolver} instances used by Hibernate to determine entity names
	 * at runtime.
	 * <p>
	 * Provides a single {@link BoxEntityNameResolver} that knows how to extract entity names from
	 * BoxLang class instances and proxies.
	 *
	 * @return an array containing the {@link BoxEntityNameResolver}
	 */
	@Override
	public EntityNameResolver[] getEntityNameResolvers() {
		return new EntityNameResolver[] { new BoxEntityNameResolver() };
	}

	/**
	 * Determines the concrete entity name for the given instance, supporting inheritance hierarchies.
	 * <p>
	 * Resolves the name from a {@link BoxProxy} or an {@link IClassRunnable} via
	 * {@link ORMService#getEntityName(IClassRunnable)}. When the instance is neither (e.g. it is a
	 * raw identifier value in an HQL query), {@code null} is returned to let Hibernate treat it as
	 * an identifier.
	 *
	 * @param entityInstance the entity instance, proxy, or identifier whose entity name is needed
	 * @param factory        the current session factory (unused)
	 *
	 * @return the resolved entity name, or {@code null} to signal an identifier value
	 */
	@Override
	public String determineConcreteSubclassEntityName( Object entityInstance, SessionFactoryImplementor factory ) {
		if ( entityInstance instanceof BoxProxy proxy ) {
			return ORMService.getEntityName( proxy.getRunnable() );
		} else if ( entityInstance instanceof IClassRunnable runnable ) {
			return ORMService.getEntityName( runnable );
		} else {
			// Returning null here allows hibernate to treat it as an identifier for HQL operations
			return null;
		}
	}

	/**
	 * Returns the mapped Java class representing BoxLang ORM entities.
	 *
	 * @return {@link BoxProxy}{@code .class}
	 */
	@Override
	public Class<BoxProxy> getMappedClass() {
		return BoxProxy.class;
	}

	/**
	 * Builds a {@link Getter} for the specified entity property.
	 *
	 * @param mappedProperty the Hibernate property descriptor
	 * @param mappedEntity   the owning persistent class descriptor
	 *
	 * @return a {@link BoxPropertyGetter} configured for the property
	 */
	@Override
	protected Getter buildPropertyGetter( Property mappedProperty, PersistentClass mappedEntity ) {
		return new BoxPropertyGetter( mappedProperty, mappedEntity );
	}

	/**
	 * Builds a {@link Setter} for the specified entity property.
	 *
	 * @param mappedProperty the Hibernate property descriptor
	 * @param mappedEntity   the owning persistent class descriptor
	 *
	 * @return a {@link BoxPropertySetter} configured for the property
	 */
	@Override
	protected Setter buildPropertySetter( Property mappedProperty, PersistentClass mappedEntity ) {
		return new BoxPropertySetter(
		    mappedProperty,
		    mappedEntity
		);
	}

	/**
	 * Builds the {@link Instantiator} responsible for creating new entity instances.
	 *
	 * @param entityMetamodel the entity metamodel
	 * @param mappingInfo     the persistent class mapping
	 *
	 * @return a {@link BoxClassInstantiator} that creates BoxLang class instances
	 */
	@Override
	protected Instantiator buildInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		return new BoxClassInstantiator( entityMetamodel, mappingInfo );
	}

	/**
	 * Builds the {@link ProxyFactory} used to create lazy-loading proxies for BoxLang entities.
	 *
	 * @param mappingInfo the persistent class mapping
	 * @param idGetter    the getter for the entity identifier
	 * @param idSetter    the setter for the entity identifier
	 *
	 * @return a {@link BoxProxyFactory} that produces {@link BoxProxy} instances
	 */
	@Override
	protected ProxyFactory buildProxyFactory( PersistentClass mappingInfo, Getter idGetter, Setter idSetter ) {
		return new BoxProxyFactory( mappingInfo, idGetter, idSetter );
	}

}
