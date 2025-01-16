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

import java.net.URI;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.application.BaseApplicationListener;
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

	// The properties to configure the interceptor with
	private static BoxRuntime	runtime	= BoxRuntime.getInstance();
	private ORMService			ormService;

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
	 * Remember that when this fires, there is NO APPLICATION scope or APPLICATION loaded yet.
	 */
	@InterceptionPoint
	public void beforeApplicationListenerLoad( IStruct args ) {
		this.logger.debug(
		    "beforeApplicationListenerLoad fired; checking for ORM configuration"
		);
		// @TODO: This is a hacky hack; please remove.
		if ( this.ormService == null ) {
			this.ormService = ( ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService ) );
		}

		RequestBoxContext		context				= ( RequestBoxContext ) args.get( "context" );
		BaseApplicationListener	startingListener	= ( BaseApplicationListener ) args.get( "listener" );
		URI						startingTemplate	= ( URI ) args.get( "template" );
		ORMConfig				config				= ORMConfig.loadFromContext( context );

		// If the starting template is null, it means, the listener is not linked to a template and no Application.bx, so ignore it.
		if ( startingTemplate != null && config != null ) {
			this.logger.info( "ORMEnabled, starting up ORM app for [{}]", startingListener.getAppName() );
			this.ormService.startupApp( context, config, startingListener );
		}
	}

	/**
	 * Listen for application shutdown and clean up application-specific Hibernate resources.
	 */
	@InterceptionPoint
	public void onApplicationEnd( IStruct args ) {
		// @TODO: This is a hacky hack; please remove.
		if ( this.ormService == null ) {
			this.ormService = ( ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService ) );
		}
		this.logger.info( "onApplicationEnd fired; Shutting down ORM application" );
		Application application = ( Application ) args.get( "application" );
		// If the orm app doesn't exist, this is a no-op
		this.ormService.shutdownApp( ORMService.buildUniqueAppName( application.getName(), application.getStartingListener().getSettings() ) );
	}

}
