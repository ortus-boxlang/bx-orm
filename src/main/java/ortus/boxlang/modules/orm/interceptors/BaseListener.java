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

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public class BaseListener extends BaseInterceptor {

	private static final Logger logger = LoggerFactory.getLogger( BaseListener.class );

	/**
	 * Construct an ORMConfig object from the application settings.
	 * 
	 * @param context The IBoxContext object for the current request.
	 * 
	 * @return ORMConfig object or null if ORM is not enabled or no ORM settings are present in the application settings.
	 */
	protected static ORMConfig getORMConfig( RequestBoxContext context ) {
		IStruct appSettings = ( IStruct ) context.getConfigItem( Key.applicationSettings );
		if ( !appSettings.containsKey( ORMKeys.ORMEnabled )
		    || !BooleanCaster.cast( appSettings.getOrDefault( ORMKeys.ORMEnabled, false ) ) ) {
			logger.info( "ORMEnabled is false or not specified;" );
			return null;
		}
		if ( !appSettings.containsKey( ORMKeys.ORMSettings )
		    || appSettings.get( ORMKeys.ORMSettings ) == null ) {
			logger.info( "No ORM configuration found in application configuration;" );
			return null;
		}
		return new ORMConfig( ( IStruct ) appSettings.get( ORMKeys.ORMSettings ) );
	}
}
