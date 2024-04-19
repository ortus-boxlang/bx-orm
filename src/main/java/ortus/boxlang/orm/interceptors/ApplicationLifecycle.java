package ortus.boxlang.orm.interceptors;

import org.slf4j.LoggerFactory;

import ortus.boxlang.orm.ORMEngine;
import ortus.boxlang.orm.SessionFactoryBuilder;
import ortus.boxlang.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.ApplicationListener;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
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
		if ( !appSettings.containsKey( ORMKeys.ORMSettings )
		    || !appSettings.containsKey( ORMKeys.ORMEnabled )
		    || Boolean.FALSE.equals( appSettings.getAsBoolean( ORMKeys.ORMEnabled ) )
		    || appSettings.get( ORMKeys.ORMSettings ) == null ) {
			// silent fail?
			logger.info( "No ORM configuration found in application configuration" );
			return;
		}
		IStruct		ormSettings	= ( IStruct ) appSettings.get( ORMKeys.ORMSettings );
		ORMEngine	ormEngine	= ORMEngine.getInstance();

		ormEngine.setSessionFactoryForName( listener.getAppName(),
		    new SessionFactoryBuilder( ( IJDBCCapableContext ) context, listener.getAppName(), ormSettings ).build() );
		this.logger.info( "Session factory created! {}", ormEngine.getSessionFactoryForName( listener.getAppName() ) );
	}

	/**
	 * Listen for runtime shutdown and shut down the ORM engine; mainly cleaning up
	 * Hibernate session factories.
	 */
	@InterceptionPoint
	public void onRuntimeShutdown( BoxRuntime runtime, Boolean force ) {
		logger.info( "onRuntimeShutdown fired; cleaning up Hibernate session factories" );
		ORMEngine.getInstance().shutdown();
	}

}
