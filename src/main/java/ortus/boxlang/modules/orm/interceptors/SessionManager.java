package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM session lifecycle management.
 * <p>
 * Listens to request events to manage the lifecycle of ORM sessions (mainly session startup and shutdown).
 */
public class SessionManager extends BaseInterceptor {

	private static final Logger	logger	= LoggerFactory.getLogger( SessionManager.class );

	/**
	 * ORM configuration.
	 */
	private ORMConfig			config;

	/**
	 * ORM service.
	 */
	private ORMService			ormService;

	public SessionManager( ORMConfig config ) {
		super();
		this.config		= config;
		this.ormService	= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
	}

	/**
	 * This method is called by the BoxLang runtime to configure the interceptor
	 * with a Struct of properties
	 *
	 * @param properties The properties to configure the interceptor with (if any)
	 */
	@Override
	public void configure( IStruct properties ) {
		this.properties = properties;
	}

	@InterceptionPoint
	public void onRequestStart( IStruct args ) {
		logger.debug( "onRequestStart - Starting ORM session" );
		RequestBoxContext	context				= args.getAs( RequestBoxContext.class, Key.context );

		TransactionManager	transactionManager	= context.getAttachment( ORMKeys.TransactionManager );
		if ( transactionManager == null ) {
			transactionManager = new TransactionManager( context, config );
			transactionManager.selfRegister();
		}
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

	@InterceptionPoint
	public void onRequestEnd( IStruct args ) {
		RequestBoxContext	context					= args.getAs( RequestBoxContext.class, Key.context );
		ORMApp				ormApp					= this.ormService.getORMApp( context );

		TransactionManager	ORMTransactionManager	= context.getAttachment( ORMKeys.TransactionManager );
		if ( ORMTransactionManager != null ) {
			ORMTransactionManager.selfDestruct();
		}

		if ( config.flushAtRequestEnd && config.autoManageSession ) {
			logger.debug( "'flushAtRequestEnd' is enabled; Flushing all ORM sessions for this request" );
			ormApp.getDatasources().forEach( datasource -> {
				ormApp.getSession( context, datasource ).flush();
			} );
		}

		logger.debug( "onRequestEnd - closing ORM sessions" );
		ormApp.getDatasources().forEach( datasource -> {
			ormApp.getSession( context, datasource ).close();
		} );
	}
}
