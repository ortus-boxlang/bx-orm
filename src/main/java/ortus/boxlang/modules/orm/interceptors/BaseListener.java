package ortus.boxlang.modules.orm.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.RequestBoxContext;
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
		    || Boolean.FALSE.equals( appSettings.getAsBoolean( ORMKeys.ORMEnabled ) ) ) {
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
