package ortus.boxlang.modules.orm.interceptors;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.events.InterceptorPool;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM transaction lifecycle management.
 * <p>
 * Listens to boxlang transaction events to manage the Hibernate transaction lifecycles (start,end,commit,rollback, etc.)
 */
public class TransactionManager extends BaseInterceptor {

	private static final Logger	logger	= LoggerFactory.getLogger( TransactionManager.class );

	/**
	 * Boxlang request context for this transaction manager.
	 */
	private RequestBoxContext	context;

	/**
	 * ORM application for this transaction manager.
	 */
	private ORMApp				ormApp;

	/**
	 * ORM configuration.
	 */
	private ORMConfig			config;

	/**
	 * Interceptor pool used for listening to transaction events.
	 */
	private InterceptorPool		interceptorPool;

	public TransactionManager( RequestBoxContext context, ORMConfig config ) {
		super();
		this.context			= context;
		this.config				= config;
		this.ormApp				= ORMService.getInstance().getORMApp( context );
		this.interceptorPool	= context.getApplicationListener().getInterceptorPool();
	}

	/**
	 * Register self with the context attachments, and register interception methods with the interceptor pool.
	 */
	public void selfRegister() {
		logger.debug( "Registering ORM transaction manager" );
		this.context.putAttachment( ORMKeys.TransactionManager, this );
		this.interceptorPool.register( this );
	}

	/**
	 * Delete self from the context attachments, and unregister from the interceptor pool.
	 */
	public void selfDestruct() {
		logger.debug( "Unregistering ORM transaction manager" );
		this.context.removeAttachment( ORMKeys.TransactionManager );
		this.interceptorPool.unregister( this );
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
	public void onTransactionBegin( IStruct args ) {
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormApp.getSession( context, datasource );
			logger.debug( "Starting ORM transaction on session {}", ormSession );
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct args ) {
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormApp.getSession( context, datasource );
			logger.debug( "Ending ORM transaction on session {}", ormSession );
			ormSession.getTransaction().commit();
		} );
	}
}
