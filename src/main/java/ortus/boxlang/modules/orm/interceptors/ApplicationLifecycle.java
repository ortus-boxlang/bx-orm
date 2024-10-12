package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.application.BaseApplicationListener;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM application lifecycle management.
 * <p>
 * This interceptor uses application startup and shutdown events to construct and destroy the ORM service. (Which is itself responsible for
 * constructing the ORM session factories, etc.)
 */
public class ApplicationLifecycle extends BaseInterceptor {

	/**
	 * This method is called by the BoxLang runtime to configure the interceptor
	 * with a Struct of properties
	 *
	 * @param properties The properties to configure the interceptor with (if any)
	 */
	@Override
	public void configure( IStruct properties ) {
		this.properties	= properties;
		this.logger		= LoggerFactory.getLogger( this.getClass() );
	}

	/**
	 * Listen for application startup and construct a Hibernate session factory if
	 * ORM configuration is present in the application config.
	 */
	@InterceptionPoint
	public void afterApplicationListenerLoad( IStruct args ) {
		logger.info(
		    "afterApplicationListenerLoad fired; checking for ORM configuration in the application context config" );

		BaseApplicationListener	listener	= ( BaseApplicationListener ) args.get( "listener" );
		RequestBoxContext		context		= ( RequestBoxContext ) args.get( "context" );

		IStruct					appSettings	= ( IStruct ) context.getConfigItem( Key.applicationSettings );
		if ( !appSettings.containsKey( ORMKeys.ORMEnabled )
		    || Boolean.FALSE.equals( appSettings.getAsBoolean( ORMKeys.ORMEnabled ) ) ) {
			logger.info( "ORMEnabled is false or not specified; Refusing to start ORM Service for this application." );
			return;
		}
		if ( !appSettings.containsKey( ORMKeys.ORMSettings )
		    || appSettings.get( ORMKeys.ORMSettings ) == null ) {
			logger.info( "No ORM configuration found in application configuration; Refusing to start ORM Service for this application." );
			return;
		}
		ORMConfig config = getORMConfig( context );
		if ( config != null ) {
			logger.info( "ORMEnabled is true and ORM settings are specified - Firing ORM application startup." );
			ORMService.getInstance().startupApp( context, listener.getAppName(), config );
		}
	}

	/**
	 * Construct an ORMConfig object from the application settings.
	 * 
	 * @param context The IBoxContext object for the current request.
	 * 
	 * @return ORMConfig object or null if ORM is not enabled or no ORM settings are present in the application settings.
	 */
	private ORMConfig getORMConfig( RequestBoxContext context ) {
		IStruct appSettings = ( IStruct ) context.getConfigItem( Key.applicationSettings );
		if ( !appSettings.containsKey( ORMKeys.ORMEnabled )
		    || Boolean.FALSE.equals( appSettings.getAsBoolean( ORMKeys.ORMEnabled ) ) ) {
			logger.info( "ORMEnabled is false or not specified; Refusing to start ORM Service for this application." );
			return null;
		}
		if ( !appSettings.containsKey( ORMKeys.ORMSettings )
		    || appSettings.get( ORMKeys.ORMSettings ) == null ) {
			logger.info( "No ORM configuration found in application configuration; Refusing to start ORM Service for this application." );
			return null;
		}
		return new ORMConfig( ( IStruct ) appSettings.get( ORMKeys.ORMSettings ) );
	}

	/**
	 * Listen for application shutdown and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationEnd( IStruct args ) {
		logger.info( "onApplicationEnd fired; cleaning up ORM resources for this application context" );
		ORMService.getInstance().shutdownApp( ( ( BaseApplicationListener ) args.get( "listener" ) ).getAppName() );
		// @TODO: Switch to:
		// ORMService.getInstance().shutdownApp( ( RequestBoxContext ) args.get( "context" ) );
	}

	/**
	 * Listen for application restart and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationRestart( IStruct args ) {
		logger.info( "onApplicationRestart fired; cleaning up ORM resources for this application context" );
		// @TODO: clean up Hibernate resources
	}
}
