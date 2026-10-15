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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

import org.hibernate.SessionEventListener;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Holds one ORM context's {@code postCommit} events until the writes they describe are committed, then fires them on the
 * entity and on the global event handler.
 * <p>
 * The {@code EventListener} records an event for every insert, update and delete it sees. Inside a BoxLang
 * {@code transaction{}}, the {@code TransactionManager} interceptor marks the recorded events committed when BoxLang
 * announces the commit, drops the uncommitted ones on rollback, and fires the committed ones when the transaction ends
 * (BoxLang announces the end after the JDBC commit). Outside a transaction each statement commits on its own, so events
 * fire when the flush that wrote them finishes, or at once for a write made outside a flush (an identity insert).
 * <p>
 * Registered on each of the context's sessions as a Hibernate {@link SessionEventListener} (a public SPI), which is how it
 * sees flushes start and end.
 */
public final class PostCommitQueue implements SessionEventListener {

	private static final long							serialVersionUID	= 1L;

	/** Each open session's queue, so the session-less event listener can find it. Weak: a closed session drops out. */
	private static final Map<Object, PostCommitQueue>	REGISTRY			= Collections.synchronizedMap( new WeakHashMap<>() );

	/**
	 * One recorded event.
	 *
	 * @param dispatcher The application's event dispatcher.
	 * @param entity     The entity.
	 * @param entityName The BoxLang entity name.
	 * @param action     {@code insert}, {@code update} or {@code delete}.
	 */
	private record Entry( ORMEventDispatcher dispatcher, IClassRunnable entity, String entityName, String action ) {
	}

	/** Whether a BoxLang transaction is active for the owning context. */
	private final transient BooleanSupplier	inTransaction;

	/** Events whose writes are not committed yet. */
	private final List<Entry>				pending		= new ArrayList<>();

	/** Events whose writes BoxLang is committing (fired when the transaction ends). */
	private final List<Entry>				committed	= new ArrayList<>();

	/** How many flushes are running on this queue's sessions. */
	private int								flushing	= 0;

	/**
	 * Create the queue of an ORM context.
	 *
	 * @param inTransaction Whether a BoxLang transaction is active for the context.
	 */
	public PostCommitQueue( BooleanSupplier inTransaction ) {
		this.inTransaction = inTransaction;
	}

	/**
	 * Attach this queue to a session of its context.
	 *
	 * @param session The Hibernate session.
	 */
	public void attach( org.hibernate.Session session ) {
		session.addEventListeners( this );
		REGISTRY.put( session, this );
	}

	/**
	 * Record a write for a {@code postCommit} event.
	 *
	 * @param session    The session that wrote it (from the Hibernate event).
	 * @param dispatcher The application's event dispatcher.
	 * @param entity     The entity.
	 * @param entityName The BoxLang entity name.
	 * @param action     {@code insert}, {@code update} or {@code delete}.
	 */
	public static void record( Object session, ORMEventDispatcher dispatcher, IClassRunnable entity, String entityName, String action ) {
		PostCommitQueue queue = REGISTRY.get( session );
		if ( queue == null || entity == null ) {
			return;
		}
		queue.pending.add( new Entry( dispatcher, entity, entityName, action ) );
		if ( queue.flushing == 0 && !queue.inTransaction.getAsBoolean() ) {
			queue.fire();
		}
	}

	/**
	 * BoxLang is committing: the recorded writes are part of the commit.
	 */
	public void markCommitted() {
		committed.addAll( pending );
		pending.clear();
	}

	/**
	 * BoxLang rolled back: forget the writes that were not committed.
	 */
	public void dropUncommitted() {
		pending.clear();
	}

	/**
	 * Fire every recorded event, committed first, in the order the writes happened. Events recorded while firing (a
	 * handler that saves) are fired in the next round.
	 */
	public void fire() {
		List<Entry> ready = new ArrayList<>( committed );
		ready.addAll( pending );
		committed.clear();
		pending.clear();
		for ( Entry entry : ready ) {
			IStruct args = Struct.of(
			    ORMKeys.entity, entry.entity(),
			    ORMKeys.entityName, entry.entityName(),
			    ORMKeys.action, entry.action()
			);
			ORMEventDispatcher.announceEntity( entry.entity(), ORMKeys.postCommit, args );
			entry.dispatcher().announceGlobal( ORMKeys.postCommit, args );
		}
	}

	/**
	 * Forget everything (the context is shutting down).
	 */
	public void clear() {
		pending.clear();
		committed.clear();
	}

	/**
	 * A flush started.
	 */
	@Override
	public void flushStart() {
		flushing++;
	}

	/**
	 * A flush ended: outside a transaction its writes are committed, so fire their events.
	 *
	 * @param numberOfEntities    Entities flushed.
	 * @param numberOfCollections Collections flushed.
	 */
	@Override
	public void flushEnd( int numberOfEntities, int numberOfCollections ) {
		flushing = Math.max( 0, flushing - 1 );
		if ( flushing == 0 && !inTransaction.getAsBoolean() && !pending.isEmpty() ) {
			fire();
		}
	}

	/**
	 * An automatic (partial) flush started.
	 */
	@Override
	public void partialFlushStart() {
		flushStart();
	}

	/**
	 * An automatic (partial) flush ended.
	 *
	 * @param numberOfEntities    Entities flushed.
	 * @param numberOfCollections Collections flushed.
	 */
	@Override
	public void partialFlushEnd( int numberOfEntities, int numberOfCollections ) {
		flushEnd( numberOfEntities, numberOfCollections );
	}
}
