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

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AutoFlushEvent;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.ClearEvent;
import org.hibernate.event.spi.ClearEventListener;
import org.hibernate.event.spi.DeleteContext;
import org.hibernate.event.spi.DeleteEvent;
import org.hibernate.event.spi.DeleteEventListener;
import org.hibernate.event.spi.DirtyCheckEvent;
import org.hibernate.event.spi.DirtyCheckEventListener;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.EvictEvent;
import org.hibernate.event.spi.EvictEventListener;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreLoadEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Hibernate Event listener which wraps the event to fire the appropriate event handler on the global and/or entity event listener.
 *
 * @since 1.0.0
 */
public class EventListener
    implements Integrator, PreInsertEventListener, PostInsertEventListener, PreDeleteEventListener, PostDeleteEventListener,
    DeleteEventListener, PreUpdateEventListener, PostUpdateEventListener, PreLoadEventListener, PostLoadEventListener,
    FlushEventListener, AutoFlushEventListener, ClearEventListener, DirtyCheckEventListener, EvictEventListener {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger			logger;

	/**
	 * Shared dispatcher that resolves and invokes the global event-handler class (and entity methods).
	 */
	private ORMEventDispatcher		dispatcher;

	/**
	 * Constructor
	 *
	 * @param dispatcher The shared event dispatcher wrapping the global event-handler class (may wrap {@code null}).
	 */
	EventListener( ORMEventDispatcher dispatcher ) {
		this.logger		= runtime.getLoggingService().getLogger( "orm" );
		this.dispatcher	= dispatcher;
	}

	@Override
	public void integrate( Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory ) {
		EventListenerRegistry eventListenerRegistry = sessionFactory.getServiceRegistry().getService( EventListenerRegistry.class );

		eventListenerRegistry.prependListeners( EventType.PRE_INSERT, this );
		eventListenerRegistry.prependListeners( EventType.POST_INSERT, this );

		eventListenerRegistry.prependListeners( EventType.PRE_DELETE, this );
		eventListenerRegistry.prependListeners( EventType.POST_DELETE, this );
		eventListenerRegistry.prependListeners( EventType.DELETE, this );

		eventListenerRegistry.prependListeners( EventType.PRE_UPDATE, this );
		eventListenerRegistry.prependListeners( EventType.POST_UPDATE, this );

		eventListenerRegistry.prependListeners( EventType.PRE_LOAD, this );
		eventListenerRegistry.prependListeners( EventType.POST_LOAD, this );

		eventListenerRegistry.prependListeners( EventType.AUTO_FLUSH, this );
		eventListenerRegistry.prependListeners( EventType.FLUSH, this );

		eventListenerRegistry.prependListeners( EventType.EVICT, this );
		eventListenerRegistry.prependListeners( EventType.CLEAR, this );

		eventListenerRegistry.prependListeners( EventType.DIRTY_CHECK, this );
	}

	@Override
	public void disintegrate( SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry ) {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean requiresPostCommitHandling( EntityPersister persister ) {
		return false;
	}

	@Override
	public void onEvict( EvictEvent event ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onEvict, event, args );
	}

	@Override
	public void onDirtyCheck( DirtyCheckEvent event ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onDirtyCheck, event, args );
	}

	@Override
	public void onClear( ClearEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onClear, event, args );
	}

	@Override
	public void onAutoFlush( AutoFlushEvent event ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onAutoFlush, event, args );
	}

	@Override
	public void onFlush( FlushEvent event ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onFlush, event, args );
	}

	@Override
	public void onPostLoad( PostLoadEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() )
		);
		announceGlobalEvent( ORMKeys.postLoad, event, args );
		announceEntityEvent( ORMKeys.postLoad, FacadeSupport.unwrap( event.getEntity() ), args );
	}

	@Override
	public void onPreLoad( PreLoadEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() )
		);
		announceGlobalEvent( ORMKeys.preLoad, event, args );
		announceEntityEvent( ORMKeys.preLoad, FacadeSupport.unwrap( event.getEntity() ), args );
	}

	@Override
	public void onPostUpdate( PostUpdateEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() )
		);
		announceGlobalEvent( ORMKeys.postUpdate, event, args );
		announceEntityEvent( ORMKeys.postUpdate, FacadeSupport.unwrap( event.getEntity() ), args );
		PostCommitQueue.record( event.getSession(), this.dispatcher, FacadeSupport.unwrap( event.getEntity() ),
		    ORMErrors.entityName( event.getPersister().getEntityName() ), "update" );
	}

	@Override
	public boolean onPreUpdate( PreUpdateEvent event ) {
		IStruct		oldData			= new Struct();
		String[]	propertyNames	= event.getPersister().getPropertyNames();
		Object[]	oldState		= event.getOldState();
		if ( oldState != null ) {
			for ( int i = 0; i < propertyNames.length; i++ ) {
				oldData.put( propertyNames[ i ], oldState[ i ] );
			}
		}
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() ),
		    ORMKeys.oldData, oldData
		);
		// A handler that returns false cancels the update (Hibernate skips the SQL; the entity keeps its changes).
		if ( announceVetoable( ORMKeys.preUpdate, event, FacadeSupport.unwrap( event.getEntity() ), args ) ) {
			return true;
		}
		// Update state so that changes made in the event are persisted
		updateEntityEventState( event.getState(), event.getPersister().getPropertyNames(), event.getPersister().getPropertyTypes(),
		    event.getPersister().isVersioned() ? event.getPersister().getVersionPropertyIndex() : -1,
		    FacadeSupport.unwrap( event.getEntity() ) );
		return false;
	}

	@Override
	public void onDelete( DeleteEvent event ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onDelete, event, args );
	}

	@Override
	public void onDelete( DeleteEvent event, DeleteContext transientEntities ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onDelete, event, args );
	}

	@Override
	public void onPostDelete( PostDeleteEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() )
		);
		announceGlobalEvent( ORMKeys.postDelete, event, args );
		announceEntityEvent( ORMKeys.postDelete, FacadeSupport.unwrap( event.getEntity() ), args );
		PostCommitQueue.record( event.getSession(), this.dispatcher, FacadeSupport.unwrap( event.getEntity() ),
		    ORMErrors.entityName( event.getPersister().getEntityName() ), "delete" );
	}

	@Override
	public boolean onPreDelete( PreDeleteEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() )
		);
		// A handler that returns false cancels the delete.
		return announceVetoable( ORMKeys.preDelete, event, FacadeSupport.unwrap( event.getEntity() ), args );
	}

	@Override
	public void onPostInsert( PostInsertEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, FacadeSupport.unwrap( event.getEntity() )
		);
		announceGlobalEvent( ORMKeys.postInsert, event, args );
		announceEntityEvent( ORMKeys.postInsert, FacadeSupport.unwrap( event.getEntity() ), args );
		PostCommitQueue.record( event.getSession(), this.dispatcher, FacadeSupport.unwrap( event.getEntity() ),
		    ORMErrors.entityName( event.getPersister().getEntityName() ), "insert" );
	}

	@Override
	public boolean onPreInsert( PreInsertEvent event ) {
		IClassRunnable	entity	= FacadeSupport.unwrap( event.getEntity() );
		IStruct			args	= Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, entity
		);
		// A handler that returns false cancels the insert.
		if ( announceVetoable( ORMKeys.preInsert, event, entity, args ) ) {
			if ( event.getId() == null ) {
				// The id comes from the database (identity), so the INSERT is the only way to get one: Hibernate cannot
				// skip it and fails with a bare "null identifier" assertion. Say so plainly instead.
				String name = ORMErrors.entityName( event.getPersister().getEntityName() );
				throw new ORMException(
				    ORMErrorType.EVENT_VETO,
				    "The preInsert handler of [" + name + "] returned false, but [" + name
				        + "] gets its id from the database (generator=\"identity\"), so its insert cannot be skipped.",
				    "Decide before calling entitySave() instead, or throw an error from preInsert to abort the save. Returning false from preInsert works for entities whose id is known before the insert (assigned, increment, uuid, sequence, ...).",
				    Struct.of( "entityName", name, "event", "preInsert" ),
				    null );
			}
			return true;
		}
		// update our entity state to ensure changes persist
		updateEntityEventState( event.getState(), event.getPersister().getPropertyNames(), event.getPersister().getPropertyTypes(),
		    event.getPersister().isVersioned() ? event.getPersister().getVersionPropertyIndex() : -1, ( IClassRunnable ) entity );
		return false;
	}

	/**
	 * Fire an event on the global event handler.
	 *
	 * @param eventType The event name (the handler method to call).
	 * @param event     The Hibernate event (unused; kept for symmetry and future use).
	 * @param args      The arguments passed to the handler method.
	 *
	 * @return What the handler returned, or null when there is no handler or method.
	 */
	private Object announceGlobalEvent( Key eventType, Object event, IStruct args ) {
		return this.dispatcher.announceGlobal( eventType, args );
	}

	/**
	 * Fire an event on the entity's own method of the same name.
	 *
	 * @param eventType The event name (the entity method to call).
	 * @param entity    The entity.
	 * @param args      The arguments passed to the method.
	 *
	 * @return What the method returned, or null when the entity has no such method.
	 */
	private Object announceEntityEvent( Key eventType, IClassRunnable entity, IStruct args ) {
		return ORMEventDispatcher.announceEntity( entity, eventType, args );
	}

	/**
	 * Fire a pre-operation event on the global handler and on the entity, and report whether either vetoed it by
	 * returning {@code false}. Both are always called, so each sees the event even when the other vetoes.
	 *
	 * @param eventType The event name ({@code preInsert}, {@code preUpdate} or {@code preDelete}).
	 * @param event     The Hibernate event.
	 * @param entity    The entity.
	 * @param args      The arguments passed to the handlers.
	 *
	 * @return True when the operation must be cancelled.
	 */
	private boolean announceVetoable( Key eventType, Object event, IClassRunnable entity, IStruct args ) {
		boolean	globalVeto	= ORMEventDispatcher.isVeto( announceGlobalEvent( eventType, event, args ) );
		boolean	entityVeto	= ORMEventDispatcher.isVeto( announceEntityEvent( eventType, entity, args ) );
		if ( globalVeto || entityVeto ) {
			logger.debug( "ORM {} of [{}] vetoed by the {} event handler", eventType.getName(), entity.bxGetName().getName(),
			    globalVeto ? "global" : "entity" );
			return true;
		}
		return false;
	}

	/**
	 * Sync the any internal changes to the entity back to the event state array so that they are persisted.
	 *
	 * See http://anshuiitk.blogspot.com/2010/11/hibernate-pre-database-opertaion-event.html
	 *
	 * @param state             The entity state to persist
	 * @param persistProperties Array of properties to update
	 * @param entity            The entity to test for altered values.
	 */
	private void updateEntityEventState( Object[] state, String[] persistProperties, org.hibernate.type.Type[] propertyTypes, int versionPropertyIndex,
	    IClassRunnable entity ) {
		if ( logger.isTraceEnabled() ) {
			logger.trace( String.format( "Updating state changes on state properties %s", Arrays.toString( persistProperties ) ) );
		}
		for ( int i = 0; i < persistProperties.length; i++ ) {
			// Never write association or collection state from the BoxLang scope back into Hibernate's event state array.
			// The scope holds a facade / FacadeCollectionView that differs from Hibernate's own state entry for that slot,
			// so overwriting it corrupts Hibernate's association/collection tracking and provokes a spurious second UPDATE -
			// which double-fires preUpdate/postUpdate. Event handlers change basic property values, not associations, so
			// this only ever needs to sync basic slots.
			if ( propertyTypes != null && i < propertyTypes.length && propertyTypes[ i ] != null
			    && ( propertyTypes[ i ].isAssociationType() || propertyTypes[ i ].isCollectionType() ) ) {
				continue;
			}
			// Never write the optimistic-lock <version> slot back either. Hibernate seeds it on insert and increments it on
			// update inside its own state array; the BoxLang scope still holds the pre-increment value, so overwriting the
			// slot reverts Hibernate's version and breaks the version check (OptimisticLockException / "Unexpected row count").
			if ( i == versionPropertyIndex ) {
				continue;
			}
			Key		propertyName	= Key.of( persistProperties[ i ] );
			Object	propertyValue	= entity.getVariablesScope().get( propertyName );
			Object	oldValue		= state[ i ];
			if ( Objects.equals( oldValue, propertyValue ) ) {
				if ( logger.isTraceEnabled() ) {
					logger.trace( String.format( " - No change on property %s, value remains %s", propertyName, oldValue ) );
				}
				// no change
				continue;
			}
			state[ i ] = propertyValue;
			if ( logger.isTraceEnabled() ) {
				logger.trace( String.format( " - Updated property %s from %s to value %s", propertyName, oldValue, propertyValue ) );
			}
		}
	}

}
