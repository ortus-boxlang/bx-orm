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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a managed entity's to-many getter ({@code getChildren()}) returns: reads come from a snapshot, writes go live.
 * <p>
 * Two classic patterns must both keep working:
 * <ul>
 * <li>{@code parent.getChildren().append( c )} / {@code arrayAppend( parent.getChildren(), c )} on a managed parent must
 * change the Hibernate-managed collection, so the new element is persisted (Hibernate 5 returned the live bag).</li>
 * <li>{@code parent.getChildren().each( c -> parent.removeChild( c ) )} must visit every element: BoxLang iterates an
 * array by index, so iterating the live collection while {@code removeChild} shrinks it would skip elements.</li>
 * </ul>
 * So {@link #get}/{@link #size} (and therefore iteration) read a snapshot taken when the getter was called, while every
 * structural change made <em>through this view</em> is applied to the snapshot and to the live collection. Changes made
 * elsewhere (e.g. {@code removeChild}) touch only the live collection and never disturb an in-progress iteration.
 */
public final class ToManyGetterView extends AbstractList<Object> {

	private final List<Object>	snapshot;
	private final List<Object>	live;

	/**
	 * @param live The entity's live collection (a {@link FacadeCollectionView} over Hibernate's managed collection).
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public ToManyGetterView( List<?> live ) {
		this.live		= ( List ) live;
		this.snapshot	= new ArrayList<>( live );
	}

	@Override
	public Object get( int index ) {
		return this.snapshot.get( index );
	}

	@Override
	public int size() {
		return this.snapshot.size();
	}

	@Override
	public void add( int index, Object element ) {
		this.snapshot.add( index, element );
		this.live.add( element );
	}

	@Override
	public Object set( int index, Object element ) {
		Object	previous	= this.snapshot.set( index, element );
		int		liveIndex	= indexOfSame( this.live, previous );
		if ( liveIndex >= 0 ) {
			this.live.set( liveIndex, element );
		} else {
			this.live.add( element );
		}
		return previous;
	}

	@Override
	public Object remove( int index ) {
		Object	removed		= this.snapshot.remove( index );
		int		liveIndex	= indexOfSame( this.live, removed );
		if ( liveIndex >= 0 ) {
			this.live.remove( liveIndex );
		}
		return removed;
	}

	/**
	 * Index of an element in a list, preferring an identity match (two content-equal entities are still distinct rows),
	 * then {@code equals}.
	 */
	private static int indexOfSame( List<Object> list, Object element ) {
		for ( int i = 0; i < list.size(); i++ ) {
			if ( list.get( i ) == element ) {
				return i;
			}
		}
		for ( int i = 0; i < list.size(); i++ ) {
			if ( Objects.equals( list.get( i ), element ) ) {
				return i;
			}
		}
		return -1;
	}
}
