/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Application event listener for ORM application creation and shutdown.
 * <p>
 * This interceptor uses application startup and shutdown events to construct and destroy the ORM service. (Which is itself responsible for
 * constructing the ORM session factories, etc.)
 */
public class ApplicationListener extends BaseInterceptor {

	private static final Logger	logger		= LoggerFactory.getLogger( ApplicationListener.class );

	private static BoxRuntime	instance	= BoxRuntime.getInstance();

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

	/**
	 * Listen for application startup and construct a Hibernate session factory if
	 * ORM configuration is present in the application config.
	 */
	@InterceptionPoint
	public void afterApplicationListenerLoad( IStruct args ) {
		logger.info(
		    "afterApplicationListenerLoad fired; checking for ORM configuration in the application context config" );

		RequestBoxContext	context	= ( RequestBoxContext ) args.get( "context" );
		ORMConfig			config	= ORMConfig.loadFromContext( context );
		if ( config != null ) {
			logger.info( "ORMEnabled is true and ORM settings are specified - Firing ORM application startup." );
			System.out.println( "ORMService is registered:" + instance.hasGlobalService( ORMKeys.ORMService ) );
			ORMService ormService = ( ( ORMService ) instance.getGlobalService( ORMKeys.ORMService ) );
			ormService.startupApp( context, config );
		}
	}

	/**
	 * Listen for application shutdown and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationEnd( IStruct args ) {
		if ( !instance.hasGlobalService( ORMKeys.ORMService ) ) {
			logger.error( "No global service found for ORMService; unable to shut down ORM application" );
		}
		logger.info( "onApplicationEnd fired; Shutting down ORM application" );
		Application	application	= ( Application ) args.get( "application" );
		ORMService	ormService	= ( ORMService ) instance.getGlobalService( ORMKeys.ORMService );
		ormService.shutdownApp( ORMApp.getUniqueAppName( application, application.getStartingListener().getSettings() ) );
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
