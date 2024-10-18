package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM session lifecycle management.
 * <p>
 * Am considering other names:
 * <ul>
 * <li>RequestListener</li>
 * <li>RequestORMSessionLifecycle</li>
 * <li>ORMSessionLifecycleManager</li>
 * </ul>
 */
public class SessionLifecycle extends BaseInterceptor {

	private static final Logger	logger	= LoggerFactory.getLogger( SessionLifecycle.class );

	/**
	 * ORM configuration.
	 */
	private ORMConfig			config;

	public SessionLifecycle( ORMConfig config ) {
		super();
		this.config = config;
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
		// @TODO: begin ORM session
		logger.debug( "Starting ORM session" );/**
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
		IBoxContext context = args.getAs( IBoxContext.class, Key.context );
		// @TODO: if ormConfig.flushAtRequestEnd and ormConfig.autoManageSession, flush the current session.
		// @TODO: end ORM session
		logger.debug( "Ending ORM session" );
		if ( config.flushAtRequestEnd ) {
			// @TODO: Add support for walking all sessions across all datasources for the current RequestContext and flushing them then.
			ORMService.getInstance().getORMApp( context ).getSession( context ).flush();
		}
	}
}
