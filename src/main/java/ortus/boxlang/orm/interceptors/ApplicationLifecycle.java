package ortus.boxlang.orm.interceptors;

import org.slf4j.LoggerFactory;

import ortus.boxlang.orm.ORMService;
import ortus.boxlang.orm.SessionFactoryBuilder;
import ortus.boxlang.orm.config.ORMKeys;
import ortus.boxlang.runtime.application.ApplicationListener;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

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

		ApplicationListener	listener	= ( ApplicationListener ) args.get( "listener" );
		RequestBoxContext	context		= ( RequestBoxContext ) args.get( "context" );

		// grab the ORMSettings struct from the application config
		IStruct				appSettings	= ( IStruct ) context.getConfigItem( Key.applicationSettings );
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
		IStruct		ormSettings	= ( IStruct ) appSettings.get( ORMKeys.ORMSettings );
		ORMService	ormService	= ORMService.getInstance();

		ormService.setSessionFactoryForName( listener.getAppName(),
		    new SessionFactoryBuilder( context, listener.getAppName(), ormSettings ).build() );
		this.logger.info( "Session factory created! {}", ormService.getSessionFactoryForName( listener.getAppName() ) );
	}

	/**
	 * Listen for application shutdown and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationEnd( IStruct args ) {
		logger.info( "onApplicationEnd fired; cleaning up ORM resources for this application context" );
		// @TODO: clean up Hibernate resources
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
