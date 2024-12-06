package ortus.boxlang.modules.orm.interceptors;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM transaction lifecycle management.
 * <p>
 * Listens to boxlang transaction events to manage the Hibernate transaction lifecycles (start,end,commit,rollback, etc.)
 */
public class TransactionManager extends BaseListener {

	private static final Logger	logger	= LoggerFactory.getLogger( TransactionManager.class );

	/**
	 * The ORM service.
	 */
	private ORMService			ormService;

	/**
	 * Constructor, used when initializing the interceptor at module load time.
	 */
	public TransactionManager() {
		super();

		this.ormService = ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
	}

	@InterceptionPoint
	public void onTransactionBegin( IStruct args ) {
		logger.debug( "onTransactionBegin fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMApp( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

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

	@InterceptionPoint
	public void onTransactionCommit( IStruct args ) {
		logger.debug( "onTransactionCommit fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMApp( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			logger.debug( "Committing ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().commit();
			logger.debug( "Beginning new ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionRollback( IStruct args ) {
		logger.debug( "onTransactionRollback fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMApp( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			logger.debug( "Rolling back ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().rollback();
			logger.debug( "Beginning new ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct args ) {
		logger.debug( "onTransactionEnd fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMApp( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			logger.debug( "Ending ORM transaction on session {} for datasource", ormSession, datasource.getUniqueName() );
			ormSession.getTransaction().commit();
		} );
	}
}
