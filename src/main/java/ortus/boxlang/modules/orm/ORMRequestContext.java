package ortus.boxlang.modules.orm;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.interceptors.TransactionManager;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Transient context for ORM requests.
 * <p>
 * Tracks Hibernate sessions and transactions for the lifetime of a single Boxlang request.
 */
public class ORMRequestContext {

	private static final Logger	logger	= LoggerFactory.getLogger( ORMRequestContext.class );

	/**
	 * ORM service.
	 */
	private ORMService			ormService;

	private ORMApp				ormApp;

	private TransactionManager	transactionManager;

	private RequestBoxContext	context;

	private ORMConfig			config;

	/**
	 * Retrieve the ORMRequestContext for the given context.
	 *
	 * @param context The context to retrieve the ORMRequestContext for.
	 * 
	 * @return The ORMRequestContext for the given context.
	 */
	public static ORMRequestContext getForContext( IBoxContext context ) {
		return context.getAttachment( ORMKeys.ORMRequestContext );
	}

	public ORMRequestContext( RequestBoxContext context, ORMConfig config ) {
		this.context	= context;
		this.config		= config;
		this.ormService	= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		this.ormApp		= this.ormService.getORMApp( context );

		logger.debug( "Registering ORM transaction manager" );
		this.transactionManager = new TransactionManager( context, this, this.config );
		this.context.putAttachment( ORMKeys.TransactionManager, this.transactionManager );
		// @TODO: begin ORM session
		/**
		 * @TODO: Plan out session management, datasource management, and connection management for ORM.
		 * 
		 *        Lucee / Lucee Hibernate will create a new session, then preemptively create a datasource connection for
		 *        each configured datasource.
		 * 
		 *        I don't love this approach. I would prefer to only create the connection at the time of first use, as
		 *        Boxlang core does with JDBC queries.
		 */
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession() {
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		return getSession( connectionManager.getDefaultDatasourceOrThrow() );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context    The context for which to get a session.
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( Key datasource ) {
		return getSession( this.ormApp.getDatasourceForNameOrDefault( context, datasource ) );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context    The context for which to get a session.
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( DataSource datasource ) {
		// @TODO: Ask Luis about synchronizing this method. We have multiple threads potentially trying to create a session on the same datasource.
		IBoxContext	jdbcContext	= ( IBoxContext ) this.context.getParentOfType( IJDBCCapableContext.class );
		Key			sessionKey	= Key.of( "orm_session_" + datasource.getUniqueName().getName() );

		if ( jdbcContext.hasAttachment( sessionKey ) ) {
			logger.trace( "returning existing session for key: {}", sessionKey.getName() );
			return jdbcContext.getAttachment( sessionKey );
		}
		logger.trace( "opening NEW session for key: {}", sessionKey.getName() );

		SessionFactory sessionFactory = this.ormApp.getSessionFactoryOrThrow( datasource );
		jdbcContext.putAttachment( sessionKey, sessionFactory.openSession() );
		return jdbcContext.getAttachment( sessionKey );
	}

	/**
	 * Shut down this ORM request context.
	 * <p>
	 * Will close all Hibernate sessions and unregister the transaction manager.
	 */
	public ORMRequestContext shutdown() {
		if ( this.transactionManager == null ) {
			throw new IllegalStateException( "TransactionManager does not exist in context." );
		}
		this.transactionManager.shutdown();
		logger.debug( "Unregistering ORM transaction manager" );
		this.context.removeAttachment( ORMKeys.TransactionManager );

		if ( this.config.flushAtRequestEnd && this.config.autoManageSession ) {
			logger.debug( "'flushAtRequestEnd' is enabled; Flushing all ORM sessions for this request" );
			this.ormApp.getDatasources().forEach( datasource -> {
				getSession( datasource ).flush();
			} );
		}

		logger.debug( "onRequestEnd - closing ORM sessions" );
		this.ormApp.getDatasources().forEach( datasource -> {
			getSession( datasource ).close();
		} );
		return this;
	}
}
