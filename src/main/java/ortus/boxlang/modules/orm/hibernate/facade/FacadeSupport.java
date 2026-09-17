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
	private static final Key					FACADE_KEY	= Key.of( "$bxORMFacade" );

	/**
	 * Generated facade classes, keyed by lower-cased entity name. Populated at boot by the session factory builder.
	 */
	private static final Map<String, Class<?>>	REGISTRY	= new ConcurrentHashMap<>();

	private FacadeSupport() {
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
	 * Register a generated facade class for an entity.
	 *
	 * @param entityName  The BoxLang entity name.
	 * @param facadeClass The generated facade class.
	 */
	public static void register( String entityName, Class<?> facadeClass ) {
		REGISTRY.put( entityName.toLowerCase().trim(), facadeClass );
	}

	/**
	 * @param entityName The BoxLang entity name.
	 *
	 * @return True if a facade class has been registered for the given entity name.
	 */
	public static boolean hasFacade( String entityName ) {
		return REGISTRY.containsKey( entityName.toLowerCase().trim() );
	}

	/**
	 * @param entityName The BoxLang entity name.
	 *
	 * @return The registered facade class, or null if none.
	 */
	public static Class<?> facadeClassFor( String entityName ) {
		return REGISTRY.get( entityName.toLowerCase().trim() );
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
		return wrap( ortus.boxlang.modules.orm.ORMService.getEntityName( instance ), instance );
	}

	public static Object wrap( String entityName, IClassRunnable instance ) {
		Object existing = instance.getVariablesScope().get( FACADE_KEY );
		if ( existing instanceof BoxEntityFacade ) {
			return existing;
		}
		Class<?> facadeClass = facadeClassFor( entityName );
		if ( facadeClass == null ) {
			throw new BoxRuntimeException( "No entity facade registered for entity [" + entityName + "]" );
		}
		try {
			Object facade = facadeClass.getConstructor( BoxEntityState.class )
			    .newInstance( new BoxIClassRunnableState( instance ) );
			instance.getVariablesScope().put( FACADE_KEY, facade );
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
