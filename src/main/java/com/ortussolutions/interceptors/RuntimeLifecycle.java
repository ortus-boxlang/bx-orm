package com.ortussolutions.interceptors;

import org.slf4j.LoggerFactory;

import com.ortussolutions.ORMEngine;
import com.ortussolutions.SessionFactoryBuilder;
import com.ortussolutions.config.ORMKeys;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;

public class RuntimeLifecycle extends BaseInterceptor {

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
	 * Listen for runtime startup and construct a Hibernate session factory if ORM configuration is present in the runtime context config.
	 *
	 */
	@InterceptionPoint
	public void onRuntimeStart( IStruct args ) {
		logger.info( "onRuntimeStart fired; checking for ORM configuration in the runtime context config" );

		BoxRuntime	runtime		= BoxRuntime.getInstance();
		IBoxContext	context		= runtime.getRuntimeContext();
		ORMEngine	ormEngine	= ORMEngine.getInstance();

		// @TODO: Switch this to reading the module configuration for the ORM module.
		// grab the ormSettings struct from the runtime config
		IStruct		ormSettings	= ( IStruct ) context.getConfigItem( ORMKeys.ORMSettings );
		if ( ormSettings == null ) {
			// silent fail?
			logger.info( "No ORM configuration found in runtime configuration" );
			return;
		}

		ormEngine.setSessionFactoryForName(
		    Key.runtime, new SessionFactoryBuilder( ormSettings ).build()
		);
		this.logger.info( "Session factory build successful: {}", ormEngine.getSessionFactoryForName( Key.runtime ) );
	}

	/**
	 * Listen for runtime shutdown and shut down the ORM engine; mainly cleaning up Hibernate session factories.
	 */
	@InterceptionPoint
	public void onRuntimeShutdown( IStruct args ) {
		logger.info( "onRuntimeShutdown fired; cleaning up Hibernate session factories" );
		ORMEngine.getInstance().shutdown();
	}
}
