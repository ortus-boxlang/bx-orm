package ortus.boxlang.modules.orm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.interceptors.TransactionManager;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;

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
		this.transactionManager = new TransactionManager( context, this.config );
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
				this.ormApp.getSession( context, datasource ).flush();
			} );
		}

		logger.debug( "onRequestEnd - closing ORM sessions" );
		this.ormApp.getDatasources().forEach( datasource -> {
			this.ormApp.getSession( context, datasource ).close();
		} );
		return this;
	}
}
