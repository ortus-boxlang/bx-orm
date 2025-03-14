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
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractEvent;
import org.hibernate.event.spi.AutoFlushEvent;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.ClearEvent;
import org.hibernate.event.spi.ClearEventListener;
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
import org.hibernate.tuple.entity.EntityMetamodel;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Hibernate Event listener which wraps the event to fire the appropriate event handler on the global and/or entity event listener.
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
	 * Global listener for this event handler.
	 */
	private DynamicObject			globalListener;

	/**
	 * Constructor
	 *
	 * @param globalListener The global event listener to fire on each event.
	 */
	EventListener( DynamicObject globalListener ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.globalListener	= globalListener;
	}

	@Override
	public void integrate( Metadata metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry ) {
		EventListenerRegistry eventListenerRegistry = serviceRegistry.getService( EventListenerRegistry.class );

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
	public boolean requiresPostCommitHanding( EntityPersister persister ) {
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
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.postLoad, event, args );
		announceEntityEvent( ORMKeys.postLoad, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public void onPreLoad( PreLoadEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.preLoad, event, args );
		announceEntityEvent( ORMKeys.preLoad, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public void onPostUpdate( PostUpdateEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.postUpdate, event, args );
		announceEntityEvent( ORMKeys.postUpdate, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public boolean onPreUpdate( PreUpdateEvent event ) {
		IStruct			oldData			= new Struct();
		EntityMetamodel	entityMetamodel	= event.getPersister().getEntityMetamodel();
		Arrays.stream( entityMetamodel.getPropertyNames() ).forEach( propertyName -> {
			oldData.put( propertyName, event.getOldState()[ entityMetamodel.getPropertyIndex( propertyName ) ] );
		} );
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity(),
		    ORMKeys.oldData, oldData
		);
		announceGlobalEvent( ORMKeys.preUpdate, event, args );
		announceEntityEvent( ORMKeys.preUpdate, ( IClassRunnable ) event.getEntity(), args );
		// @TODO: Allow the event to be vetoed from EITHER the global or the entity-specific event listener.
		return false;
	}

	@Override
	public void onDelete( DeleteEvent event ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onDelete, event, args );
	}

	@SuppressWarnings( "rawtypes" )
	@Override
	public void onDelete( DeleteEvent event, Set transientEntities ) throws HibernateException {
		IStruct args = Struct.of(
		    ORMKeys.event, event
		);
		announceGlobalEvent( ORMKeys.onDelete, event, args );
	}

	@Override
	public void onPostDelete( PostDeleteEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.postDelete, event, args );
		announceEntityEvent( ORMKeys.postDelete, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public boolean onPreDelete( PreDeleteEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.preDelete, event, args );
		announceEntityEvent( ORMKeys.preDelete, ( IClassRunnable ) event.getEntity(), args );
		// @TODO: Allow the event to be vetoed from EITHER the global or the entity-specific event listener.
		return false;
	}

	@Override
	public void onPostInsert( PostInsertEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.postInsert, event, args );
		announceEntityEvent( ORMKeys.postInsert, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public boolean onPreInsert( PreInsertEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, event.getEntity()
		);
		announceGlobalEvent( ORMKeys.preInsert, event, args );
		announceEntityEvent( ORMKeys.preInsert, ( IClassRunnable ) event.getEntity(), args );
		// @TODO: Allow the event to be vetoed from EITHER the global or the entity-specific event listener.
		return false;
	}

	private void announceGlobalEvent( Key eventType, AbstractEvent event, IStruct args ) {
		if ( globalListener == null ) {
			return;
		}
		boolean hasMethod = false;

		if ( IClassRunnable.class.isAssignableFrom( globalListener.getTargetClass() ) ) {
			hasMethod = ( ( IClassRunnable ) globalListener.unWrapBoxLangClass() ).getThisScope().containsKey( eventType );
		} else {
			hasMethod = globalListener.hasMethodNoCase( eventType.getNameNoCase() );
		}

		if ( hasMethod ) {
			if ( logger.isDebugEnabled() ) {
				logger.debug( "Ready to invoke {} on global EventHandler with args {}", eventType.getName(), args.toString() );
			}
			// Fire the method on the global event handler
			this.globalListener.dereferenceAndInvoke( getCurrentContext(), eventType, args, false );
		}
	}

	private void announceEntityEvent( Key eventType, IClassRunnable entity, IStruct args ) {
		if ( entity.containsKey( eventType ) ) {
			if ( logger.isDebugEnabled() ) {
				logger.debug( "Ready to invoke {} on entity with args {}", eventType.getName(), args.toString() );
			}

			// Fire the method on the entity itself
			entity.dereferenceAndInvoke( getCurrentContext(), eventType, args, false );
		}
	}

	/**
	 * Get the current RequestBoxContext, constructing one if necessary.
	 */
	private RequestBoxContext getCurrentContext() {
		RequestBoxContext context = RequestBoxContext.getCurrent();
		if ( context == null ) {
			context = new ScriptingRequestBoxContext( BoxRuntime.getInstance().getRuntimeContext() );
		}
		return context;
	}
}
