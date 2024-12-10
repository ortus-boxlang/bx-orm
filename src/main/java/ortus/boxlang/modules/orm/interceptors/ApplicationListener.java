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

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Application event listener for ORM application creation and shutdown.
 * <p>
 * This interceptor uses application startup and shutdown events to construct and destroy the ORM service. (Which is itself responsible for
 * constructing the ORM session factories, etc.)
 */
public class ApplicationListener extends BaseInterceptor {

	// The properties to configure the interceptor with
	private static BoxRuntime	runtime	= BoxRuntime.getInstance();
	private ORMService			ormService;

	private BoxLangLogger		logger;

	/**
	 * This method is called by the BoxLang runtime to configure the interceptor
	 * with a Struct of properties
	 *
	 * @param properties The properties to configure the interceptor with (if any)
	 */
	@Override
	public void configure( IStruct properties ) {
		this.properties	= properties;
		this.logger		= runtime.getLoggingService().getLogger( "orm" );
		this.ormService	= ( ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService ) );
	}

	/**
	 * Listen for application startup and construct a Hibernate session factory if
	 * ORM configuration is present in the application config.
	 */
	@InterceptionPoint
	public void afterApplicationListenerLoad( IStruct args ) {
		this.logger.debug(
		    "afterApplicationListenerLoad fired; checking for ORM configuration in the application context config"
		);

		RequestBoxContext	context	= ( RequestBoxContext ) args.get( "context" );
		ORMConfig			config	= ORMConfig.loadFromContext( context );
		if ( config != null ) {
			this.logger.info( "ORMEnabled is true and ORM settings are specified - Firing ORM application startup." );
			// System.out.println( "ORMService is registered:" + runtime.hasGlobalService( ORMKeys.ORMService ) );

			this.ormService.startupApp( context, config );
		}
	}

	/**
	 * Listen for application shutdown and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationEnd( IStruct args ) {

		if ( !runtime.hasGlobalService( ORMKeys.ORMService ) ) {
			this.logger.error( "No global service found for ORMService; unable to shut down ORM application" );
		}

		this.logger.info( "onApplicationEnd fired; Shutting down ORM application" );

		Application application = ( Application ) args.get( "application" );
		this.ormService.shutdownApp( ORMApp.getUniqueAppName( application, application.getStartingListener().getSettings() ) );
	}

	/**
	 * Listen for application restart and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationRestart( IStruct args ) {
		this.logger.info( "onApplicationRestart fired; cleaning up ORM resources for this application context" );
		// @TODO: clean up Hibernate resources
	}
}
