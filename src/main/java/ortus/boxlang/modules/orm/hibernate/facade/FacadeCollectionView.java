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

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * A live {@link List} view over a Hibernate-managed to-many collection in facade (POJO) mode.
 * <p>
 * In facade mode Hibernate's managed collection holds the target entities in <em>their</em> representation - generated
 * POJO facades - but a BoxLang developer navigating {@code parent.getChildren()} must only ever see BoxLang instances
 * ({@link IClassRunnable}). This view is what bx-orm stores in the BoxLang instance's scope: it wraps the underlying
 * Hibernate collection (a {@code PersistentBag}/{@code PersistentList}) and translates at the element boundary -
 * unwrapping facades to their {@link IClassRunnable} on read, and wrapping incoming {@link IClassRunnable}s to their
 * facade on write - while every structural change flows straight through to the underlying collection so Hibernate
 * keeps managing exactly one collection instance (dirty-checking, lazy init, cascade all preserved).
 * <p>
 * The underlying collection stays authoritative and lazy: reads/iteration trigger its initialization the same way a
 * direct read would, so wrapping an uninitialized lazy bag does not force it to load.
 *
 * @since 2.0.0
 */
public final class FacadeCollectionView extends AbstractList<Object> {

	/**
	 * The underlying Hibernate-managed collection (elements are facades, or dev-supplied instances pre-flush).
	 */
	private final List<Object> backing;

	/**
	 * @param backing The underlying Hibernate-managed list to view through (never {@code null}).
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public FacadeCollectionView( List<?> backing ) {
		this.backing = ( List ) backing;
	}

	/**
	 * @return The underlying Hibernate-managed collection (facade elements), handed back to Hibernate by the facade's
	 *         collection getter so Hibernate keeps managing its own single collection instance.
	 */
	public List<Object> backing() {
		return this.backing;
	}

	@Override
	public Object get( int index ) {
		return FacadeSupport.unwrapIfFacade( this.backing.get( index ) );
	}

	@Override
	public int size() {
		return this.backing.size();
	}

	@Override
	public Object set( int index, Object element ) {
		return FacadeSupport.unwrapIfFacade( this.backing.set( index, toFacade( element ) ) );
	}

	@Override
	public void add( int index, Object element ) {
		this.backing.add( index, toFacade( element ) );
	}

	@Override
	public Object remove( int index ) {
		return FacadeSupport.unwrapIfFacade( this.backing.remove( index ) );
	}

	@Override
	public Iterator<Object> iterator() {
		Iterator<Object> delegate = this.backing.iterator();
		return new Iterator<>() {

			@Override
			public boolean hasNext() {
				return delegate.hasNext();
			}

			@Override
			public Object next() {
				return FacadeSupport.unwrapIfFacade( delegate.next() );
			}

			@Override
			public void remove() {
				delegate.remove();
			}
		};
	}

	/**
	 * Translate an element the developer is adding (a BoxLang {@link IClassRunnable}) into the facade Hibernate manages;
	 * anything already a facade (or otherwise) passes through unchanged.
	 *
	 * @param element The element being written into the collection.
	 *
	 * @return The facade to store in the underlying Hibernate collection.
	 */
	private Object toFacade( Object element ) {
		if ( element instanceof IClassRunnable runnable ) {
			return FacadeSupport.wrapInstance( runnable );
		}
		return element;
	}
}
