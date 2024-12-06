package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * BoxLang request listener used to construct and tear down ORM request contexts.
 * <p>
 * Listens to request events (start and end) in order to construct and tear down ORM request contexts.
 */
public class RequestListener extends BaseListener {

	private static final Logger logger = LoggerFactory.getLogger( RequestListener.class );

	// no-arg constructor for IInterceptor
	public RequestListener() {
		super();
	}

	@InterceptionPoint
	public void onRequestStart( IStruct args ) {
		logger.debug( "onRequestEnd - Starting up ORM request" );
		// RequestBoxContext context = args.getAs( RequestBoxContext.class, Key.context );
		// ORMConfig config = getORMConfig( context );
		// if ( config == null ) {
		// logger.debug( "ORM not enabled for this request." );
		// return;
		// }
		// if ( context.hasAttachment( ORMKeys.ORMRequestContext ) ) {
		// logger.warn( "ORM request already started." );
		// return;
		// }
		// logger.debug( "onRequestStart - Starting ORM request" );
		// context.putAttachment( ORMKeys.ORMRequestContext, new ORMRequestContext( context, config ) );
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct args ) {
		logger.debug( "onRequestEnd - Shutting down ORM request" );
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
}
