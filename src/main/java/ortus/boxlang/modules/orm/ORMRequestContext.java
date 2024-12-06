package ortus.boxlang.modules.orm;

import java.util.HashMap;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Transient context for ORM requests.
 * <p>
 * Tracks Hibernate sessions and transactions for the lifetime of a single Boxlang request.
 */
public class ORMRequestContext {

	private static final Logger		logger		= LoggerFactory.getLogger( ORMRequestContext.class );

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
	private HashMap<Key, Session>	sessions	= new HashMap<>();

	/**
	 * Retrieve the ORMRequestContext for the given context.
	 *
	 * @param context The context to retrieve the ORMRequestContext for.
	 * 
	 * @return The ORMRequestContext for the given context.
	 */
	public static ORMRequestContext getForContext( RequestBoxContext context ) {
		IStruct appSettings = ( IStruct ) context.getConfigItem( Key.applicationSettings );
		if ( !appSettings.containsKey( ORMKeys.ORMEnabled )
		    || !BooleanCaster.cast( appSettings.getOrDefault( ORMKeys.ORMEnabled, false ) ) ) {
			throw new BoxRuntimeException( "Could not acquire ORM context; ORMEnabled is false or not specified. Is this application ORM-enabled?" );
		}
		return context.computeAttachmentIfAbsent( ORMKeys.ORMRequestContext, ( key ) -> {
			logger.debug( "Initializing ORM context" );
			IStruct ormConfig = ( IStruct ) appSettings.get( ORMKeys.ORMSettings );
			return new ORMRequestContext( context, new ORMConfig( ormConfig ) );
		} );
	}

	public ORMRequestContext( RequestBoxContext context, ORMConfig config ) {
		this.context	= context;
		this.config		= config;
		this.ormService	= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		this.ormApp		= this.ormService.getORMApp( context );
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
		Key sessionKey = datasource.getUniqueName();
		if ( this.sessions.containsKey( sessionKey ) ) {
			logger.trace( "returning existing session for key: {}", sessionKey.getName() );
			return this.sessions.get( sessionKey );
		}

		logger.trace( "opening NEW session for key: {}", sessionKey.getName() );

		SessionFactory	sessionFactory	= this.ormApp.getSessionFactoryOrThrow( datasource );
		Session			newSession		= sessionFactory.openSession();
		this.sessions.put( sessionKey, newSession );
		newSession.beginTransaction();
		return newSession;
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
		if ( this.config.flushAtRequestEnd && this.config.autoManageSession ) {
			logger.debug( "'flushAtRequestEnd' is enabled; Flushing all ORM sessions for this request" );
			this.sessions.forEach( ( key, session ) -> {
				session.flush();
			} );
		}

		logger.debug( "onRequestEnd - closing ORM sessions" );
		this.sessions.forEach( ( key, session ) -> {
			session.close();
		} );
		return this;
	}
}
