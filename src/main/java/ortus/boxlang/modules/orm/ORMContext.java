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
package ortus.boxlang.modules.orm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Transient ORM state tracker; manages ORM state for the lifetime of a BoxLang request or thread context.
 * <p>
 * Say you call `entityNew()` in a request context, then call it inside a thread loop:
 * <code>
 * entityNew( "MyEntity" ); // Request context
 * items.each( item -> {
 * entityNew( "MyEntity" ); // Thread context
 * }, true ); // parallel execution
 * </code>
 * <p>
 * You now have N+1 Hibernate sessions open (one for the request context, and one for each item in the `items` array). Each of these Hibernate
 * sessions is stored in its own ORMContext instance, which is attached to the request or thread context. Each thread will shut down the `ORMContext`
 * upon thread completion, which will close all Hibernate sessions opened as part that thread's execution.
 * The request context's `ORMContext` will be torn down at the end of the request, closing any remaining Hibernate sessions.
 *
 * @since 1.0.0
 */
public class ORMContext {

	/**
	 * Static reference to the BoxLang runtime, for accessing services and logging.
	 */
	private static final BoxRuntime			runtime				= BoxRuntime.getInstance();

	/**
	 * Shutdown listener
	 */
	// @formatter:off
	private static Consumer<IBoxContext>	shutdownListener	= ctx -> {

		// If the context doesn't have an ORMContext attachment, then there's no ORM state to shut down, so we can skip it.
		if ( !ctx.hasAttachment( ORMKeys.ORMContext ) ) {
			return;
		}

		// Log the shutdown and shut down the ORM context, which will close all sessions attached to the context.
		runtime.getLoggingService()
			.getLogger( "orm" )
			.debug( "onRequestEnd - Shutting down ORM request" );
		ORMContext ormRequestContext = ctx.getAttachment( ORMKeys.ORMContext );
		ormRequestContext.shutdown();
		ctx.removeAttachment( ORMKeys.ORMContext );
	};
	// @formatter:on

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger					logger;

	/**
	 * ORM service.
	 */
	private ORMService						ormService;

	/**
	 * The ORM application for this context.
	 */
	private ORMApp							ormApp;

	/**
	 * The BoxLang context for this ORM context; should be a JDBC-capable context (request or thread).
	 */
	private IBoxContext						context;

	/**
	 * The ORM configuration for this context.
	 */
	private ORMConfig						config;

	/**
	 * Map of Hibernate sessions for this request, keyed by datasource name.
	 */
	private Map<Key, Session>				sessions			= new ConcurrentHashMap<>();

	/**
	 * Retrieve the ORMContext for the given boxlang context (whatever JDBC-capable context inside which we are currently executing).
	 *
	 * @param context The context for which to retrieve the ORMContext.
	 *
	 * @return The ORMContext for the given context.
	 */
	public static ORMContext getForContext( IBoxContext context ) {
		if ( context == null ) {
			throw new BoxRuntimeException( "Could not acquire ORM context; context is null." );
		}
		IBoxContext jdbcCapableContext = context.getParentOfType( IJDBCCapableContext.class );
		if ( jdbcCapableContext == null ) {
			throw new BoxRuntimeException( "Could not acquire ORM context; supplied context has no parent context which is request or thread typed." );
		}
		// Fix for "effectively final" lambda capture
		// https://www.baeldung.com/java-lambda-effectively-final-local-variables
		final IBoxContext	finalJDBCContext	= jdbcCapableContext;
		final IStruct		appSettings			= ( IStruct ) finalJDBCContext.getConfigItem( Key.applicationSettings );

		if ( !BooleanCaster.cast( appSettings.getOrDefault( ORMKeys.ORMEnabled, false ) ) ) {
			throw new BoxRuntimeException( "Could not acquire ORM context; ORMEnabled is false or not specified. Is this application ORM-enabled?" );
		}

		return jdbcCapableContext.computeAttachmentIfAbsent( ORMKeys.ORMContext, key -> {
			// This will remove the attachment on context shutdown
			jdbcCapableContext.registerShutdownListener( shutdownListener );

			return new ORMContext(
			    finalJDBCContext,
			    new ORMConfig( appSettings.getAsStruct( ORMKeys.ORMSettings ), finalJDBCContext )
			);
		} );
	}

	/**
	 * Constructor.
	 *
	 * @param context The JDBC-capable context (request or thread).
	 * @param config  The ORM configuration.
	 */
	public ORMContext( IBoxContext context, ORMConfig config ) {
		this.context	= context;
		this.config		= config;
		this.ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );
		this.ormApp		= this.ormService.getORMAppByContext( context );
		this.logger		= this.ormService.getLogger();
		this.logger.debug( "Initializing ORM context on context type: {}", context.getClass().getSimpleName() );
	}

	/**
	 * Check if this request context has an ORM application loaded, or none found.
	 */
	public boolean hasORMApp() {
		return this.ormApp != null;
	}

	/**
	 * Retrieve the initialized ORM application for this request context.
	 * <p>
	 * Returns the cached reference when it is still the live app in the service.
	 * Re-fetches from the service only when the cache is null or stale (i.e. the
	 * service holds a different instance, such as after an {@code ORMReload()} call).
	 */
	public ORMApp getORMApp() {
		if ( !hasORMApp() ) {
			this.ormApp = this.ormService.getORMAppByContext( this.context );
		}
		return this.ormApp;
	}

	/**
	 * Retrieve the Hibernate sessions for this request context, keyed by datasource name.
	 */
	public Map<Key, Session> getSessions() {
		return this.sessions;
	}

	/**
	 * Get the default Hibernate session, opening one if it does not already exist.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession() {
		return getSession( getConnectionManager().getDefaultDatasourceOrThrow() );
	}

	/**
	 * Get a Hibernate session for the given datasource, opening one if it does not already exist.
	 *
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( Key datasource ) {
		return getSession( getDatasource( datasource ) );
	}

	/**
	 * Get a Hibernate session for the given datasource, opening one if it does not already exist.
	 *
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( DataSource datasource ) {
		Key sessionKey = Key.of( datasource.getOriginalName() );
		return this.sessions.computeIfAbsent( sessionKey, ( key ) -> {
			logger.debug( "opening NEW session for key: {}", sessionKey.getName() );

			SessionFactory	sessionFactory	= this.ormApp.getSessionFactory( sessionKey );
			Session			session			= sessionFactory.openSession();
			if ( !config.autoManageSession ) {
				session.setHibernateFlushMode( org.hibernate.FlushMode.MANUAL );
			}
			return session;
		} );
	}

	/**
	 * Get the datasource for a given name, falling back to the default datasource if the name is null.
	 * 
	 * @param datasourceName The name of the datasource to retrieve, or null to retrieve the default datasource.
	 *
	 * @throws BoxRuntimeException if neither the named nor a default datasource could be found.
	 */
	public DataSource getDatasource( Key datasourceName ) {
		ConnectionManager connectionManager = getConnectionManager();
		return ( datasourceName != null ) ? connectionManager.getDatasourceOrThrow( datasourceName )
		    : connectionManager.getDefaultDatasourceOrThrow();
	}

	/**
	 * Getter for this ORM context's ORM configuration.
	 *
	 * @return The ORM configuration for this context.
	 */
	public ORMConfig getConfig() {
		return this.config;
	}

	/**
	 * Shut down this ORM context.
	 * <p>
	 * Will close all Hibernate sessions and unregister the transaction manager.
	 */
	public ORMContext shutdown() {
		// Auto-flush all sessions at the end of the request
		// Should we move this to an onRequestEnd() method in case ORMContext.shutdown is called mid-request?
		if ( this.config.flushAtRequestEnd && this.config.autoManageSession ) {
			this.logger.debug( "'flushAtRequestEnd' is enabled; Flushing all ORM sessions for this request" );
			this.sessions.forEach( ( key, session ) -> session.flush() );
		}

		// Close all ORM sessions
		this.logger.debug( "onRequestEnd - closing ORM sessions" );
		this.closeAllSessions();

		return this;
	}

	/**
	 * Close all open Hibernate sessions for this request context.
	 * <p>
	 * Attempts a transaction commit prior to closing the sessions, if an active transaction is present.
	 */
	public ORMContext closeAllSessions() {
		this.sessions.forEach( ( key, session ) -> {
			this.logger.debug( "Closing session on datasource {}", key );
			try {
				closeSessionAndTransaction( session );
				this.sessions.remove( key );
			} catch ( Exception e ) {
				this.logger.error( "Error closing session or session factory on datasource {}", key.getName(), e );
				// ensure we continue to close other sessions
			}
		} );
		this.sessions.clear();
		return this;
	}

	/**
	 * Close the Hibernate session on the given datasource, and ensure it is removed from the session map.
	 *
	 * @param datasourceName The datasource name of the session to close.
	 *
	 * @return this ORMContext, for chaining.
	 */
	public ORMContext closeSession( Key datasourceName ) {
		Session session = null;
		if ( datasourceName == null ) {
			session			= getSession();
			datasourceName	= this.config.datasource;
		} else {
			session = getSession( Key.of( datasourceName ) );
		}
		closeSessionAndTransaction( session );
		this.sessions.remove( datasourceName );
		return this;
	}

	/**
	 * Close the Hibernate session on the given datasource.
	 * <p>
	 * Attempts a transaction commit prior to closing the session, if an active transaction is present.
	 *
	 * @param session The session to close.
	 *
	 * @return this ORMContext, for chaining.
	 */
	private ORMContext closeSessionAndTransaction( Session session ) {
		var tx = session.getTransaction();
		if ( tx.isActive() ) {
			this.logger.trace( "Session has an active transaction; committing before flushing" );
			try {
				tx.commit();
			} catch ( Exception e ) {
				this.logger.error( "Error committing transaction on session", e );
				tx.rollback();
			}
		}
		session.close();
		return this;
	}

	/**
	 * Retrieve the connection manager from the parent JDBC-capable context.
	 * 
	 * @return BoxLang ConnectionManager instance
	 */
	private ConnectionManager getConnectionManager() {
		return this.context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
	}
}
