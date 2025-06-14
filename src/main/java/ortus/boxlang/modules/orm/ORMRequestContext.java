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

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Transient context for ORM requests.
 * <p>
 * Tracks Hibernate sessions and transactions for the lifetime of a single Boxlang request.
 * 
 * @since 1.0.0
 */
public class ORMRequestContext {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime		= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger			logger;

	/**
	 * ORM service.
	 */
	private ORMService				ormService;

	private ORMApp					ormApp;

	private RequestBoxContext		context;

	private ORMConfig				config;

	/**
	 * Map of Hibernate sessions for this request, keyed by datasource name.
	 */
	private Map<Key, Session>		sessions	= new ConcurrentHashMap<>();

	/**
	 * Retrieve the ORMRequestContext for the given context.
	 *
	 * @param context The context to retrieve the ORMRequestContext for.
	 *
	 * @return The ORMRequestContext for the given context.
	 */
	public static ORMRequestContext getForContext( RequestBoxContext context ) {
		IStruct appSettings = ( IStruct ) context.getConfigItem( Key.applicationSettings );

		if ( !BooleanCaster.cast( appSettings.getOrDefault( ORMKeys.ORMEnabled, false ) ) ) {
			throw new BoxRuntimeException( "Could not acquire ORM context; ORMEnabled is false or not specified. Is this application ORM-enabled?" );
		}

		return context.computeAttachmentIfAbsent( ORMKeys.ORMRequestContext, key -> {
			// logger.debug( "Initializing ORM context" );
			return new ORMRequestContext(
			    context,
			    new ORMConfig( appSettings.getAsStruct( ORMKeys.ORMSettings ), context )
			);
		} );
	}

	/**
	 * Constructor.
	 *
	 * @param context The request context.
	 * @param config  The ORM configuration.
	 */
	public ORMRequestContext( RequestBoxContext context, ORMConfig config ) {
		this.context	= context;
		this.config		= config;
		this.ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );
		this.ormApp		= this.ormService.getORMAppByContext( context );
		this.logger		= runtime.getLoggingService().getLogger( "orm" );
	}

	/**
	 * Check if this request context has an ORM application loaded, or none found.
	 */
	public boolean hasORMApp() {
		return this.ormApp != null;
	}

	/**
	 * Retrieve the initialized ORM application for this request context.
	 */
	public ORMApp getORMApp() {
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
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		return getSession( connectionManager.getDefaultDatasourceOrThrow() );
	}

	/**
	 * Get a Hibernate session for the given datasource, opening one if it does not already exist.
	 *
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( Key datasource ) {
		return getSession( this.ormApp.getDatasourceForNameOrDefault( context, datasource ) );
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

			SessionFactory sessionFactory = this.ormApp.getSessionFactoryOrThrow( datasource );
			return sessionFactory.openSession();
		} );
	}

	/**
	 * Getter for this ORM request context's ORM configuration.
	 *
	 * @return
	 */
	public ORMConfig getConfig() {
		return this.config;
	}

	/**
	 * Shut down this ORM request context.
	 * <p>
	 * Will close all Hibernate sessions and unregister the transaction manager.
	 */
	public ORMRequestContext shutdown() {
		// Auto-flush all sessions at the end of the request
		if ( this.config.flushAtRequestEnd && this.config.autoManageSession ) {
			logger.debug( "'flushAtRequestEnd' is enabled; Flushing all ORM sessions for this request" );
			this.sessions.forEach( ( key, session ) -> session.flush() );
		}

		// Close all ORM sessions
		logger.debug( "onRequestEnd - closing ORM sessions" );
		this.closeAllSessions();

		return this;
	}

	/**
	 * Close all open Hibernate sessions for this request context.
	 * <p>
	 * Attempts a transaction commit prior to closing the sessions, if an active transaction is present.
	 */
	public ORMRequestContext closeAllSessions() {
		this.sessions.forEach( ( key, session ) -> {
			logger.debug( "Closing session on datasource {}", key );
			try {
				closeSessionAndTransaction( session );
				this.sessions.remove( key );
			} catch ( Exception e ) {
				logger.error( "Error closing session or session factory on datasource {}", key.getName(), e );
				// ensure we continue to close other sessions
			}
		} );
		this.sessions.clear();
		return this;
	}

	/**
	 * Close the Hibernate session on the given datasource, and ensure it is removed from the session map.
	 */
	public ORMRequestContext closeSession( Key datasourceName ) {
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
	 */
	private ORMRequestContext closeSessionAndTransaction( Session session ) {
		var tx = session.getTransaction();
		if ( tx.isActive() ) {
			logger.warn( "Session has an active transaction; committing before flushing" );
			try {
				tx.commit();
			} catch ( Exception e ) {
				logger.error( "Error committing transaction on session", e );
				tx.rollback();
			}
		}
		session.close();
		return this;
	}
}
