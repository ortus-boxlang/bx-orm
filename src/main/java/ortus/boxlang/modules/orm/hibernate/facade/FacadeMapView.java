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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;

/**
 * A live {@link Map} view over a Hibernate-managed map of entity facades (a struct-typed {@code one-to-many} /
 * {@code many-to-many}), presenting each value as the BoxLang {@link IClassRunnable} the developer works with.
 * <p>
 * Reads unwrap facade values; writes wrap an {@code IClassRunnable} value to its facade and a BoxLang {@link Key} key to
 * its scalar name, then go straight to the managed map, so Hibernate keeps tracking the one collection instance it owns.
 * The map counterpart of {@link FacadeCollectionView}.
 */
public final class FacadeMapView extends AbstractMap<Object, Object> {

	private final Map<Object, Object> backing;

	/**
	 * @param backing The Hibernate-managed map of facade values.
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public FacadeMapView( Map<?, ?> backing ) {
		this.backing = ( Map ) backing;
	}

	/**
	 * The Hibernate-managed map this view wraps.
	 *
	 * @return The backing map of facade values.
	 */
	public Map<Object, Object> backing() {
		return this.backing;
	}

	@Override
	public Object get( Object key ) {
		return FacadeSupport.unwrapIfFacade( this.backing.get( toScalarKey( key ) ) );
	}

	@Override
	public boolean containsKey( Object key ) {
		return this.backing.containsKey( toScalarKey( key ) );
	}

	@Override
	public Object put( Object key, Object value ) {
		return FacadeSupport.unwrapIfFacade( this.backing.put( toScalarKey( key ), toFacade( value ) ) );
	}

	@Override
	public Object remove( Object key ) {
		return FacadeSupport.unwrapIfFacade( this.backing.remove( toScalarKey( key ) ) );
	}

	@Override
	public int size() {
		return this.backing.size();
	}

	@Override
	public void clear() {
		this.backing.clear();
	}

	@Override
	public Set<Entry<Object, Object>> entrySet() {
		return new AbstractSet<>() {

			@Override
			public Iterator<Entry<Object, Object>> iterator() {
				Iterator<Entry<Object, Object>> delegate = backing.entrySet().iterator();
				return new Iterator<>() {

					@Override
					public boolean hasNext() {
						return delegate.hasNext();
					}

					@Override
					public Entry<Object, Object> next() {
						Entry<Object, Object> entry = delegate.next();
						return new SimpleEntry<>( entry.getKey(), FacadeSupport.unwrapIfFacade( entry.getValue() ) ) {

							@Override
							public Object setValue( Object value ) {
								return FacadeSupport.unwrapIfFacade( entry.setValue( toFacade( value ) ) );
							}
						};
					}

					@Override
					public void remove() {
						delegate.remove();
					}
				};
			}

			@Override
			public int size() {
				return backing.size();
			}
		};
	}

	private static Object toScalarKey( Object key ) {
		return key instanceof Key boxKey ? boxKey.getName() : key;
	}

	private static Object toFacade( Object value ) {
		if ( value instanceof IClassRunnable runnable ) {
			return FacadeSupport.wrapInstance( runnable );
		}
		return value;
	}
}
