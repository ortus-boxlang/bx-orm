package ortus.boxlang.modules.orm.interceptors;

import org.hibernate.Session;
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

	/**
	 * ORM service.
	 */
	private ORMService			ormService;

	public TransactionManager( RequestBoxContext context, ORMConfig config ) {
		super();
		this.context			= context;
		this.config				= config;
		this.ormService			= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		this.ormApp				= ormService.getORMApp( context );
		this.interceptorPool	= context.getApplicationListener().getInterceptorPool();
		this.interceptorPool.register( this );
	}

	/**
	 * Unregister from the interceptor pool.
	 */
	public TransactionManager shutdown() {
		this.interceptorPool.unregister( this );
		return this;
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
		logger.debug( "onTransactionBegin fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormApp.getSession( context, datasource );
			logger.debug( "Starting ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionCommit( IStruct args ) {
		logger.debug( "onTransactionCommit fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormApp.getSession( context, datasource );
			logger.debug( "Commiting ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().commit();
			logger.debug( "Beginning new ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionRollback( IStruct args ) {
		logger.debug( "onTransactionRollback fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormApp.getSession( context, datasource );
			logger.debug( "Rolling back ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().rollback();
			logger.debug( "Beginning new ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct args ) {
		logger.debug( "onTransactionEnd fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormApp.getSession( context, datasource );
			logger.debug( "Ending ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().commit();
		} );
	}
}
