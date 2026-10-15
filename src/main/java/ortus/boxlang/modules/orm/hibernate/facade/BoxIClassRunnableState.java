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

/**
 * A {@link BoxEntityState} backed by a live BoxLang entity instance ({@link IClassRunnable}).
 * <p>
 * This is the production bridge between a generated POJO facade and the BoxLang class it stands in for. Every mapped
 * property read/written by Hibernate on the facade is delegated here, straight onto the BoxLang instance's scopes, so
 * BoxLang code and Hibernate share exactly one state store. Writes mirror {@code hibernate.BoxPropertySetter.set()}:
 * the value goes into BOTH the {@code this} and {@code variables} scopes so implicit accessors and direct scope reads
 * both see it.
 *
 * @since 2.0.0
 */
public class BoxIClassRunnableState implements BoxEntityState {

	/**
	 * Process-wide cache of mapped-property name -> {@link Key}. Hibernate reads/writes every mapped property on every
	 * hydrate, dirty-check and flush, so the same fixed property strings recur constantly; memoizing the {@link Key}
	 * here keeps each distinct property name from being re-resolved on every access.
	 */
	private static final Map<String, Key>	KEY_CACHE	= new ConcurrentHashMap<>();

	/**
	 * The wrapped BoxLang entity instance whose scopes hold the mapped property state.
	 */
	private final IClassRunnable			runnable;

	/**
	 * @param runnable The BoxLang entity instance to delegate mapped property state to.
	 */
	public BoxIClassRunnableState( IClassRunnable runnable ) {
		this.runnable = runnable;
	}

	/**
	 * Expose the wrapped BoxLang instance so bx-orm's BIF boundary can unwrap a facade back to the IClassRunnable the
	 * BoxLang developer expects.
	 *
	 * @return The wrapped BoxLang entity instance.
	 */
	public IClassRunnable getRunnable() {
		return this.runnable;
	}

	@Override
	public Object get( String property ) {
		// Read from the variables scope, mirroring MAP-mode's BoxPropertyGetter and the setter below (which writes both
		// scopes). A BoxLang implicit property setter (e.g. a developer's entity.setManufacturer(x)) writes the variables
		// scope, so reading the Map view (the `this` scope) here would miss developer-assigned association values.
		return this.runnable.getVariablesScope().get( keyFor( property ) );
	}

	@Override
	public void set( String property, Object value ) {
		Key propertyName = keyFor( property );
		// Mirror BoxPropertySetter.set(): write both scopes so implicit accessors and direct scope reads agree.
		this.runnable.getThisScope().put( propertyName, value );
		this.runnable.getVariablesScope().put( propertyName, value );
	}

	/**
	 * Resolve (and memoize) the {@link Key} for a mapped property name.
	 *
	 * @param property The mapped property name Hibernate is accessing.
	 *
	 * @return The cached {@link Key} for that property.
	 */
	private static Key keyFor( String property ) {
		return KEY_CACHE.computeIfAbsent( property, Key::of );
	}
}
