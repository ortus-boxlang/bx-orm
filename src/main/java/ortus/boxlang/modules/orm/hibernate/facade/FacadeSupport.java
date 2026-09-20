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
package ortus.boxlang.modules.orm.hibernate.facade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Wrap/unwrap layer between BoxLang entity instances ({@link IClassRunnable}) and their generated Hibernate facades.
 * <p>
 * BoxLang developers only ever handle the {@code IClassRunnable}; Hibernate only ever handles the facade. This class is
 * the single boundary the two cross:
 * <ul>
 * <li>{@link #register(String, Class)} records the generated facade class for an entity at boot.</li>
 * <li>{@link #wrap(String, IClassRunnable)} returns the (cached, per-instance) facade for a BoxLang instance, so the
 * same instance always maps to the same facade within a session.</li>
 * <li>{@link #unwrap(Object)} / {@link #unwrapIfFacade(Object)} recover the BoxLang instance at the BIF boundary.</li>
 * </ul>
 * The per-instance facade is memoized on a hidden key in the instance's variables scope (an {@code IClassRunnable}'s
 * own hashCode is content-based and mutable, so it cannot key an external identity map).
 *
 * @since 2.0.0
 */
public final class FacadeSupport {

	/**
	 * Hidden variables-scope key under which an instance's facade is memoized. Not a mapped property, so Hibernate never
	 * sees it.
	 */
	private static final Key					FACADE_KEY		= Key.of( "$bxORMFacade" );

	/**
	 * Hidden variables-scope key under which an instance's facade namespace (its owning ORM application) is stamped, so
	 * the wrap/unwrap layer can find the right application's facade class for a not-yet-wrapped instance. Not a mapped
	 * property.
	 */
	private static final Key					NAMESPACE_KEY	= Key.of( "$bxORMNamespace" );

	/**
	 * Generated facade classes, keyed by {@code namespace|lower-cased-entity-name}. Populated at boot by the session
	 * factory builder. The namespace segregates same-named entities across ORM applications sharing this JVM.
	 */
	private static final Map<String, Class<?>>	REGISTRY		= new ConcurrentHashMap<>();

	private FacadeSupport() {
	}

	/**
	 * Build the composite registry key for a facade: {@code namespace|lower-cased-entity-name}.
	 *
	 * @param namespace  The owning application's facade namespace.
	 * @param entityName The BoxLang entity name.
	 *
	 * @return The composite registry key.
	 */
	private static String key( String namespace, String entityName ) {
		return ( namespace == null ? "default" : namespace ) + "|" + entityName.toLowerCase().trim();
	}

	/**
	 * Memoize a facade on its backing BoxLang instance under the hidden variables-scope key, so the same instance always
	 * maps to the same facade.
	 *
	 * @param instance The BoxLang entity instance.
	 * @param facade   The facade wrapping it.
	 */
	public static void memoize( IClassRunnable instance, Object facade ) {
		instance.getVariablesScope().put( FACADE_KEY, facade );
	}

	/**
	 * Register a generated facade class for an entity within an application's namespace.
	 *
	 * @param namespace   The owning application's facade namespace.
	 * @param entityName  The BoxLang entity name.
	 * @param facadeClass The generated facade class.
	 */
	public static void register( String namespace, String entityName, Class<?> facadeClass ) {
		REGISTRY.put( key( namespace, entityName ), facadeClass );
	}

	/**
	 * @param namespace  The owning application's facade namespace.
	 * @param entityName The BoxLang entity name.
	 *
	 * @return True if a facade class has been registered for the given namespace and entity name.
	 */
	public static boolean hasFacade( String namespace, String entityName ) {
		return REGISTRY.containsKey( key( namespace, entityName ) );
	}

	/**
	 * @param namespace  The owning application's facade namespace.
	 * @param entityName The BoxLang entity name.
	 *
	 * @return The registered facade class, or null if none.
	 */
	public static Class<?> facadeClassFor( String namespace, String entityName ) {
		return REGISTRY.get( key( namespace, entityName ) );
	}

	/**
	 * Return the facade for a BoxLang instance, creating and memoizing it on first use so the same instance always maps
	 * to the same facade.
	 *
	 * @param entityName The BoxLang entity name.
	 * @param instance   The BoxLang entity instance.
	 *
	 * @return The facade wrapping the instance.
	 */
	/**
	 * Return the facade for a BoxLang instance, resolving its entity name automatically.
	 * <p>
	 * Used at the association boundary where only the target BoxLang instance is in hand (e.g. a to-one value a
	 * developer assigned, or a to-many element being added): the entity name is resolved from the instance itself and
	 * the (memoized) facade is returned.
	 *
	 * @param instance The BoxLang entity instance.
	 *
	 * @return The facade wrapping the instance.
	 */
	public static Object wrapInstance( IClassRunnable instance ) {
		// A managed/created instance already carries its memoized facade, so it round-trips without a registry lookup. Only
		// a not-yet-wrapped instance reaches the registry, and it must be resolved within its own application's namespace
		// (stamped on the instance at creation), never by bare entity name - which could collide across applications.
		Object existing = instance.getVariablesScope().get( FACADE_KEY );
		if ( existing instanceof BoxEntityFacade ) {
			return existing;
		}
		Object stamped = instance.getVariablesScope().get( NAMESPACE_KEY );
		if ( ! ( stamped instanceof String namespace ) ) {
			throw new BoxRuntimeException(
			    "Cannot resolve the ORM application namespace for a facade of entity ["
			        + ortus.boxlang.modules.orm.ORMService.getEntityName( instance ) + "]; the instance was not created through the ORM." );
		}
		return wrap( namespace, ortus.boxlang.modules.orm.ORMService.getEntityName( instance ), instance );
	}

	/**
	 * Stamp an instance with its owning application's facade namespace so a later {@link #wrapInstance(IClassRunnable)}
	 * can resolve the correct facade class. Called when the ORM creates the instance.
	 *
	 * @param instance  The BoxLang entity instance.
	 * @param namespace The owning application's facade namespace.
	 */
	public static void stampNamespace( IClassRunnable instance, String namespace ) {
		if ( namespace != null ) {
			instance.getVariablesScope().put( NAMESPACE_KEY, namespace );
		}
	}

	/**
	 * Return the object a Hibernate {@link org.hibernate.Session} operation (refresh, contains, evict, lock, ...) should
	 * receive for this entity: the entity's facade (wrapping the instance). Non-{@link IClassRunnable} values pass through
	 * untouched.
	 *
	 * @param namespace  The owning application's facade namespace.
	 * @param entityName The BoxLang entity name.
	 * @param entity     The entity instance (or any value).
	 *
	 * @return The Hibernate-managed representation of the entity.
	 */
	public static Object managed( String namespace, String entityName, Object entity ) {
		if ( entity instanceof IClassRunnable runnable ) {
			return wrap( namespace, entityName, runnable );
		}
		return entity;
	}

	public static Object wrap( String namespace, String entityName, IClassRunnable instance ) {
		Object existing = instance.getVariablesScope().get( FACADE_KEY );
		if ( existing instanceof BoxEntityFacade ) {
			return existing;
		}
		Class<?> facadeClass = facadeClassFor( namespace, entityName );
		if ( facadeClass == null ) {
			throw new BoxRuntimeException( "No entity facade registered for entity [" + entityName + "] in namespace [" + namespace + "]" );
		}
		try {
			Object facade = facadeClass.getConstructor( BoxEntityState.class )
			    .newInstance( new BoxIClassRunnableState( instance ) );
			instance.getVariablesScope().put( FACADE_KEY, facade );
			instance.getVariablesScope().put( NAMESPACE_KEY, namespace );
			return facade;
		} catch ( ReflectiveOperationException e ) {
			throw new BoxRuntimeException( "Unable to instantiate entity facade for entity [" + entityName + "]", e );
		}
	}

	/**
	 * Recover the BoxLang instance from a facade (or pass an {@link IClassRunnable} through unchanged).
	 *
	 * @param object A facade or an IClassRunnable.
	 *
	 * @return The underlying IClassRunnable, or null if the argument is neither.
	 */
	public static IClassRunnable unwrap( Object object ) {
		if ( object instanceof BoxEntityFacade facade ) {
			BoxEntityState state = facade.boxState();
			if ( state instanceof BoxIClassRunnableState runnableState ) {
				return runnableState.getRunnable();
			}
			return null;
		}
		if ( object instanceof IClassRunnable runnable ) {
			return runnable;
		}
		return null;
	}

	/**
	 * Unwrap a facade to its BoxLang instance, or return the argument unchanged when it is not a facade.
	 * <p>
	 * Safe to call on any query result: scalars and projections pass through untouched.
	 *
	 * @param object Any value (possibly a facade).
	 *
	 * @return The BoxLang instance if {@code object} is a facade, else {@code object} itself.
	 */
	public static Object unwrapIfFacade( Object object ) {
		if ( object instanceof BoxEntityFacade facade ) {
			BoxEntityState state = facade.boxState();
			if ( state instanceof BoxIClassRunnableState runnableState ) {
				return runnableState.getRunnable();
			}
		}
		return object;
	}
}
