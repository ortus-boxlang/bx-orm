package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.events.InterceptorPool;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * BoxLang request listener used to construct and tear down ORM request contexts.
 * <p>
 * Listens to request events (start and end) in order to construct and tear down ORM request contexts.
 */
public class RequestListener extends BaseListener {

	private static final Logger	logger	= LoggerFactory.getLogger( RequestListener.class );

	/**
	 * The interceptor pool used to listen for request events.
	 */
	private InterceptorPool		interceptorPool;

	// no-arg constructor for IInterceptor
	public RequestListener() {
		this( ( InterceptorPool ) BoxRuntime.getInstance().getInterceptorService() );
	}

	public RequestListener( InterceptorPool interceptorPool ) {
		super();
		this.interceptorPool = interceptorPool;
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
		RequestBoxContext	context	= args.getAs( RequestBoxContext.class, Key.context );
		ORMConfig			config	= getORMConfig( context );
		if ( config == null ) {
			logger.debug( "ORM not enabled for this request." );
			return;
		}
		if ( context.hasAttachment( ORMKeys.ORMRequestContext ) ) {
			logger.warn( "ORM request already started." );
			return;
		}
		logger.debug( "onRequestStart - Starting ORM request" );
		context.putAttachment( ORMKeys.ORMRequestContext, new ORMRequestContext( context, config ) );
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct args ) {
		RequestBoxContext	context	= args.getAs( RequestBoxContext.class, Key.context );
		ORMConfig			config	= getORMConfig( context );
		if ( config == null ) {
			logger.debug( "ORM not enabled for this request." );
			return;
		}
		if ( !context.hasAttachment( ORMKeys.ORMRequestContext ) ) {
			logger.warn( "No ORM request context; did the request startup fail for some reason?" );
			return;
		}
		ORMRequestContext ormRequestContext = context.getAttachment( ORMKeys.ORMRequestContext );
		ormRequestContext.shutdown();
		context.removeAttachment( ORMKeys.ORMRequestContext );
	}

	/**
	 * Initialize a new RequestListener, store it as a context attachment, and return it.
	 * 
	 * @param context The context to register the RequestListener with.
	 * @param config  The ORM configuration to use.
	 */
	public static RequestListener selfRegister( IBoxContext context, ORMConfig config ) {
		// just in case of double registration
		if ( context.hasAttachment( ORMKeys.RequestListener ) ) {
			logger.warn( "Session manager already registered" );
			return context.getAttachment( ORMKeys.RequestListener );
		}

		// Register our ORM Session Manager
		// The application listener seems to get blown away on every request, so listening here is pretty dang fragile.
		//
		// return new RequestListener( config, context.getApplicationListener().getInterceptorPool() );
		return new RequestListener( BoxRuntime.getInstance().getInterceptorService() );
	}

	/**
	 * Unregister this RequestListener from the interceptor pool.
	 */
	public RequestListener selfDestruct() {
		this.interceptorPool.unregister( this );
		return this;
	}
}
