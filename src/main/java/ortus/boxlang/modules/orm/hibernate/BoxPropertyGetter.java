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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * Hibernate {@link Getter} implementation that reads a mapped property value from a BoxLang entity.
 * <p>
 * When Hibernate needs to read a property from an entity during persistence operations (e.g. dirty
 * checking, hydration, association resolution), it delegates to this getter. Two cases are handled:
 * <ul>
 * <li><strong>Direct entity access</strong> — if the {@code owner} is an {@link IClassRunnable},
 * the value is read directly from its variables scope.</li>
 * <li><strong>Primary-key lookup</strong> — if the {@code owner} is a raw identifier (e.g. during
 * association resolution), the ORM service loads the full entity by that id and then reads
 * the requested property from it.</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class BoxPropertyGetter implements Getter {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime		= BoxRuntime.getInstance();

	/**
	 * The ORM service, used to load entities by id when Hibernate requests a property value via a primary-key lookup.
	 */
	private static final ORMService	ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );

	/**
	 * Logger for ORM-related diagnostic output.
	 */
	private BoxLangLogger			logger;

	/**
	 * The Hibernate property mapping that describes which property this getter is responsible for.
	 */
	private Property				mappedProperty;

	/**
	 * The Hibernate persistent-class mapping of the entity that owns the property.
	 */
	private PersistentClass			mappedEntity;

	/**
	 * Constructs a new {@code BoxPropertyGetter} for the given entity property.
	 *
	 * @param mappedProperty the Hibernate {@link Property} descriptor for the property to read
	 * @param mappedEntity   the Hibernate {@link PersistentClass} descriptor for the owning entity
	 */
	public BoxPropertyGetter( Property mappedProperty, PersistentClass mappedEntity ) {
		this.logger			= ormService.getLogger();
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
	}

	/**
	 * Returns the value of the mapped property for the given {@code owner}.
	 * <p>
	 * If {@code owner} is an {@link IClassRunnable} (i.e. a BoxLang entity instance), the value is
	 * read directly from the variables scope. Otherwise {@code owner} is treated as a primary-key
	 * value; the entity is loaded via the ORM service and the property is read from the loaded
	 * instance. Returns {@code null} when no matching entity can be found.
	 *
	 * @param owner the entity instance or its primary-key value
	 *
	 * @return the current value of the mapped property, or {@code null} if unresolvable
	 */
	@Override
	public Object get( Object owner ) {

		if ( logger.isTraceEnabled() ) {
			logger.trace( "getting property {} on entity {}", mappedProperty.getName(), mappedEntity.getEntityName() );
		}

		if ( owner instanceof IClassRunnable castRunnable ) {
			// If the being assigned from an object return the property directly
			return castRunnable.getVariablesScope().get( mappedProperty.getName() );
		} else {

			// Otherwise we assume this is a primary key lookup and load the entity to get the property
			// FYI: We use RequestBoxContext.runInContext here to ensure that the entity loading happens within the correct request context, which is necessary
			// for the ORM service to function properly (e.g. to access the correct database connection/session). This is important because Hibernate may call
			// this getter outside of the normal BoxLang execution flow, including threads
			IClassRunnable entity = ( IClassRunnable ) RequestBoxContext.runInContext( ctx -> {
				return ormService.getORMAppByContext( ctx ).loadEntityById( ctx, mappedEntity.getEntityName(), owner );
			} );

			if ( entity == null ) {
				logger.warn(
				    String.format( "Entity %s with id %s not found for property %s",
				        mappedEntity.getEntityName(),
				        owner,
				        mappedProperty.getName()
				    )
				);
				return null;
			}

			return entity.get( mappedProperty.getName() );
		}
	}

	/**
	 * Returns the property value to be used during an INSERT operation.
	 * <p>
	 * Delegates directly to {@link #get(Object)}; the merge map and session are not needed for
	 * BoxLang entity property access.
	 *
	 * @param owner    the entity instance or its primary-key value
	 * @param mergeMap map of objects currently being merged in the session (unused)
	 * @param session  the active Hibernate session (unused)
	 *
	 * @return the current value of the mapped property, or {@code null} if unresolvable
	 */
	@SuppressWarnings( "rawtypes" )
	@Override
	public Object getForInsert( Object owner, Map mergeMap, SharedSessionContractImplementor session ) {
		return get( owner );
	}

	/**
	 * Returns the declared return type of this getter.
	 * <p>
	 * Always returns {@link Object}{@code .class} because BoxLang properties are dynamically typed.
	 *
	 * @return {@code Object.class}
	 */
	@Override
	public Class<?> getReturnType() {
		return Object.class;
	}

	/**
	 * Returns the Java reflection {@link Member} backing this getter.
	 * <p>
	 * Not applicable for BoxLang entities; always returns {@code null}.
	 *
	 * @return {@code null}
	 */
	@Override
	public Member getMember() {
		return null;
	}

	/**
	 * Returns the name of the getter method.
	 * <p>
	 * Not applicable for BoxLang entities; always returns {@code null}.
	 *
	 * @return {@code null}
	 */
	@Override
	public String getMethodName() {
		return null;
	}

	/**
	 * Returns the Java reflection {@link Method} for this getter.
	 * <p>
	 * Not applicable for BoxLang entities; always returns {@code null}.
	 *
	 * @return {@code null}
	 */
	@Override
	public Method getMethod() {
		return null;
	}

}
