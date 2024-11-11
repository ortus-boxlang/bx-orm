package ortus.boxlang.modules.orm.interceptors;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Incubating;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.InterceptorPool;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM transaction lifecycle management.
 * <p>
 * Listens to boxlang transaction events to manage the Hibernate transaction lifecycles (start,end,commit,rollback, etc.)
 */
public class TransactionManager {

	private static final Logger	logger	= LoggerFactory.getLogger( TransactionManager.class );

	/**
	 * Boxlang request context for this transaction manager.
	 */
	private RequestBoxContext	context;

	/**
	 * ORM request context.
	 */
	private ORMRequestContext	ormRequestContext;

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

	public TransactionManager( RequestBoxContext context, ORMRequestContext ormRequestContext, ORMConfig config ) {
		super();
		this.context			= context;
		this.config				= config;
		this.ormService			= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		this.ormApp				= ormService.getORMApp( context );
		this.ormRequestContext	= ormRequestContext;
		this.interceptorPool	= context.getApplicationListener().getInterceptorPool();

		// custom registration.
		// this.interceptorPool.register( DynamicObject.of( this ),
		// Key.onTransactionBegin, Key.onTransactionCommit, Key.onTransactionRollback, Key.onTransactionEnd );
		this.interceptorPool.register( DynamicObject.of( this ), getListenerMethods().toArray( new Key[ 0 ] ) );
	}

	private Set<Key> getListenerMethods() {
		// Discover all @Incubating methods and build into an array of Keys to register
		DynamicObject target = DynamicObject.of( this );
		return target.getMethodsAsStream( true )
		    // filter only the methods that have the @Incubating annotation
		    .filter( method -> method.isAnnotationPresent( Incubating.class ) )
		    // map it to the method name
		    .map( method -> Key.of( method.getName() ) )
		    // Collect to the states set to register
		    .collect( Collectors.toSet() );
	}

	@Incubating
	public void onTransactionBegin( IStruct args ) {
		logger.debug( "onTransactionBegin fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			if ( config.autoManageSession ) {
				logger.debug( "'autoManageSession' is enabled; flushing ORM session {} for datasource prior to transaction begin.", ormSession,
				    datasource.getUniqueName() );
				ormSession.flush();
			}

			logger.debug( "Starting ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			if ( ormSession.isJoinedToTransaction() ) {
				// May want to put this behind some kind of compatibility flag...
				logger.debug( "Session {} is already joined to a transaction, closing transaction and beginning anew", ormSession );
				ormSession.getTransaction().commit();
			}
			ormSession.beginTransaction();
		} );
	}

	@Incubating
	public void onTransactionCommit( IStruct args ) {
		logger.debug( "onTransactionCommit fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			logger.debug( "Committing ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().commit();
			logger.debug( "Beginning new ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@Incubating
	public void onTransactionRollback( IStruct args ) {
		logger.debug( "onTransactionRollback fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			logger.debug( "Rolling back ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().rollback();
			logger.debug( "Beginning new ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@Incubating
	public void onTransactionEnd( IStruct args ) {
		logger.debug( "onTransactionEnd fired" );
		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			logger.debug( "Ending ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().commit();
		} );
	}

	/**
	 * Unregister from the interceptor pool.
	 */
	public TransactionManager shutdown() {
		this.interceptorPool.unregister( DynamicObject.of( this ), getListenerMethods().toArray( new Key[ 0 ] ) );
		return this;
	}
}
