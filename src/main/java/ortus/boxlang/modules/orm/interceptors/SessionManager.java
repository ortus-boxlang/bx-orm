package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.events.InterceptorPool;
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
	 * The interceptor pool used to listen for request events.
	 */
	private InterceptorPool		interceptorPool;

	public SessionManager( ORMConfig config, InterceptorPool interceptorPool ) {
		super();
		this.config				= config;
		this.interceptorPool	= interceptorPool;
		this.interceptorPool.register( this );
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
		RequestBoxContext context = args.getAs( RequestBoxContext.class, Key.context );
		if ( context.hasAttachment( ORMKeys.ORMRequestContext ) ) {
			logger.warn( "ORM request already started." );
			return;
		}
		logger.debug( "onRequestStart - Starting ORM request" );
		context.putAttachment( ORMKeys.ORMRequestContext, new ORMRequestContext( context, this.config ) );
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct args ) {
		RequestBoxContext	context				= args.getAs( RequestBoxContext.class, Key.context );
		ORMRequestContext	ormRequestContext	= context.getAttachment( ORMKeys.ORMRequestContext );
		ormRequestContext.shutdown();
		context.removeAttachment( ORMKeys.ORMRequestContext );
	}

	/**
	 * Initialize a new SessionManager, store it as a context attachment, and return it.
	 * 
	 * @param context The context to register the SessionManager with.
	 * @param config  The ORM configuration to use.
	 */
	public static SessionManager selfRegister( IBoxContext context, ORMConfig config ) {
		// just in case of double registration
		if ( context.hasAttachment( ORMKeys.SessionManager ) ) {
			logger.warn( "Session manager already registered" );
			return context.getAttachment( ORMKeys.SessionManager );
		}

		// Register our ORM Session Manager
		// The application listener seems to get blown away on every request, so listening here is pretty dang fragile.
		//
		// return new SessionManager( config, context.getApplicationListener().getInterceptorPool() );
		return new SessionManager( config, BoxRuntime.getInstance().getInterceptorService() );
	}

	/**
	 * Unregister this SessionManager from the interceptor pool.
	 */
	public SessionManager selfDestruct() {
		this.interceptorPool.unregister( this );
		return this;
	}
}
