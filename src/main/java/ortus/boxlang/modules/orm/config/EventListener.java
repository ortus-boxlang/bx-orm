package ortus.boxlang.modules.orm.config;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class EventListener
    implements Integrator, PreInsertEventListener, PostInsertEventListener, PreDeleteEventListener, PostDeleteEventListener,
    DeleteEventListener, PreUpdateEventListener, PostUpdateEventListener, PreLoadEventListener, PostLoadEventListener,
    FlushEventListener, AutoFlushEventListener, ClearEventListener, DirtyCheckEventListener, EvictEventListener {

	private Logger			logger	= LoggerFactory.getLogger( EventListener.class );

	/**
	 * Global listener for this event handler.
	 */
	private IClassRunnable	globalListener;

	EventListener( IClassRunnable globalListener ) {
		this.globalListener = globalListener;
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
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPostLoad, event, args );
		announceEntityEvent( ORMKeys.onPostLoad, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public void onPreLoad( PreLoadEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPreLoad, event, args );
		announceEntityEvent( ORMKeys.onPreLoad, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public void onPostUpdate( PostUpdateEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPostUpdate, event, args );
		announceEntityEvent( ORMKeys.onPostUpdate, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public boolean onPreUpdate( PreUpdateEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity(),
		    // @TODO: Convert the state data to a struct.
		    ORMKeys.oldData, event.getOldState()
		);
		announceGlobalEvent( ORMKeys.onPreUpdate, event, args );
		announceEntityEvent( ORMKeys.onPreUpdate, ( IClassRunnable ) event.getEntity(), args );
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
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPostDelete, event, args );
		announceEntityEvent( ORMKeys.onPostDelete, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public boolean onPreDelete( PreDeleteEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPreDelete, event, args );
		announceEntityEvent( ORMKeys.onPreDelete, ( IClassRunnable ) event.getEntity(), args );
		// @TODO: Allow the event to be vetoed from EITHER the global or the entity-specific event listener.
		return false;
	}

	@Override
	public void onPostInsert( PostInsertEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPostInsert, event, args );
		announceEntityEvent( ORMKeys.onPostInsert, ( IClassRunnable ) event.getEntity(), args );
	}

	@Override
	public boolean onPreInsert( PreInsertEvent event ) {
		IStruct args = Struct.of(
		    ORMKeys.event, event,
		    ORMKeys.entity, ( IClassRunnable ) event.getEntity()
		);
		announceGlobalEvent( ORMKeys.onPreInsert, event, args );
		announceEntityEvent( ORMKeys.onPreInsert, ( IClassRunnable ) event.getEntity(), args );
		// @TODO: Allow the event to be vetoed from EITHER the global or the entity-specific event listener.
		return false;
	}

	private void announceGlobalEvent( Key eventType, AbstractEvent event, IStruct args ) {
		if ( globalListener != null && globalListener.containsKey( ORMKeys.onPreLoad ) ) {
			logger.debug( "Ready to invoke EventHandler.onPreLoad() with args {}", args.toString() );
		}
	}

	private void announceEntityEvent( Key eventType, IClassRunnable entity, IStruct args ) {
		if ( entity.containsKey( ORMKeys.onPreLoad ) ) {
			logger.debug( "Ready to invoke entity.onPreLoad() with args {}", args.toString() );
			// entity.dereferenceAndInvoke( context, ORMKeys.onPreLoad, args, false );
		}
	}
}
