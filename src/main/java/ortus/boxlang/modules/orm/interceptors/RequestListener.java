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

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * BoxLang request listener used to construct and tear down ORM request contexts.
 * <p>
 * Listens to request events (start and end) in order to construct and tear down ORM request contexts.
 */
public class RequestListener extends BaseInterceptor {

	/**
	 * This method is called by the BoxLang runtime to configure the interceptor
	 * with a Struct of properties
	 *
	 * @param properties The properties to configure the interceptor with (if any)
	 */
	@Override
	public void configure( IStruct properties ) {
		this.properties	= properties;
		this.logger		= getRuntime().getLoggingService().getLogger( "orm" );
	}

	@InterceptionPoint
	public void onRequestStart( IStruct args ) {
		logger.debug( "onRequestStart - Starting up ORM request" );
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct args ) {
		logger.debug( "onRequestEnd - Shutting down ORM request" );
		RequestBoxContext	context	= args.getAs( RequestBoxContext.class, Key.context );
		ORMConfig			config	= ORMConfig.loadFromContext( context );

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
