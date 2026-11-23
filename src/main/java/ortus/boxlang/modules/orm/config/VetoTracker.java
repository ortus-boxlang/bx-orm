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
package ortus.boxlang.modules.orm.config;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Remembers the inserts and deletes a {@code preInsert} or {@code preDelete} handler vetoed.
 * <p>
 * Hibernate skips the SQL of a vetoed insert or delete but still calls its post-event listeners, so without this the
 * entity's {@code postInsert} / {@code postDelete} and its {@code postCommit} would fire for a row that was never
 * written. The pre-event listener marks the entity; the post-event listener consumes the mark and stays silent.
 * <p>
 * Marks are kept per Hibernate session (weakly, so a closed session leaves nothing behind) and compared by identity.
 */
public final class VetoTracker {

	private static final Map<Object, Set<Object>> VETOED = Collections.synchronizedMap( new WeakHashMap<>() );

	private VetoTracker() {
	}

	/**
	 * Mark an entity whose operation was vetoed in this session.
	 *
	 * @param session The Hibernate session.
	 * @param entity  The entity Hibernate passed to the pre-event.
	 */
	public static void mark( Object session, Object entity ) {
		if ( session == null || entity == null ) {
			return;
		}
		VETOED.computeIfAbsent( session, key -> Collections.newSetFromMap( new IdentityHashMap<>() ) ).add( entity );
	}

	/**
	 * Remove the mark of an entity, if it has one.
	 *
	 * @param session The Hibernate session.
	 * @param entity  The entity Hibernate passed to the post-event.
	 *
	 * @return True when the entity's operation was vetoed, so its post-events must not fire.
	 */
	public static boolean consume( Object session, Object entity ) {
		if ( session == null || entity == null ) {
			return false;
		}
		Set<Object> vetoed = VETOED.get( session );
		if ( vetoed == null ) {
			return false;
		}
		boolean found = vetoed.remove( entity );
		if ( vetoed.isEmpty() ) {
			VETOED.remove( session );
		}
		return found;
	}
}
