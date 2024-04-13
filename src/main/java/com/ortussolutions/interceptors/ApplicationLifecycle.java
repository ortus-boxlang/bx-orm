package com.ortussolutions.interceptors;

import java.net.URI;

import org.slf4j.LoggerFactory;

import com.ortussolutions.ORMEngine;
import com.ortussolutions.SessionFactoryBuilder;
import com.ortussolutions.config.ORMKeys;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.ApplicationListener;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;

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
	 * Listen for application startup and construct a Hibernate session factory if ORM configuration is present in the application config.
	 */
	@InterceptionPoint
	public void afterApplicationListenerLoad( ApplicationListener listener, RequestBoxContext context, URI template ) {
		logger.info( "afterApplicationListenerLoad fired; checking for ORM configuration in the application context config" );

		ORMEngine	ormEngine	= ORMEngine.getInstance();

		// grab the ORMSettings struct from the application config
		IStruct		ormSettings	= ( IStruct ) context.getConfigItem( ORMKeys.ORMSettings );
		if ( ormSettings == null ) {
			// silent fail?
			logger.info( "No ORM configuration found in application configuration" );
			return;
		}

		ormEngine.setSessionFactoryForName(
		    listener.getAppName(), new SessionFactoryBuilder( ormSettings ).build()
		);
		this.logger.info( "Session factory created! " + ormEngine.getSessionFactoryForName( Key.runtime ) );
	}

	/**
	 * Listen for runtime shutdown and shut down the ORM engine; mainly cleaning up Hibernate session factories.
	 */
	@InterceptionPoint
	public void onRuntimeShutdown( BoxRuntime runtime, Boolean force ) {
		logger.info( "onRuntimeShutdown fired; cleaning up Hibernate session factories" );
		ORMEngine.getInstance().shutdown();
	}

}
