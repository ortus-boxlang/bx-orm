package ortus.boxlang.modules.interceptors;

import ortus.boxlang.runtime.config.segments.DatasourceConfig;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.jdbc.drivers.IJDBCDriver;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class DatasourceInterceptor extends BaseInterceptor {

	private static final Key STRING_TYPE_KEY = Key.of( "stringtype" );

	@InterceptionPoint
	public void onDatasourceStartup( IStruct interceptData ) {
		Object configItem = interceptData.get( Key.config );
		if ( configItem != null && configItem instanceof DatasourceConfig config ) {
			try {
				IJDBCDriver	driver		= config.getDriver();
				String		driverName	= driver.getName().getName();
				if ( driverName.toLowerCase().contains( "postgresql" ) ) {
					// If a postgresql driver we add the custom property "stringtype" with value "unspecified" to prevent issues with UUID type bindings errors
					IStruct	dsnProperties	= config.getProperties();
					IStruct	custom			= dsnProperties.getAsStruct( Key.custom );

					if ( custom == null ) {
						custom = new Struct();
						dsnProperties.put( Key.custom, custom );
					}

					if ( !custom.containsKey( STRING_TYPE_KEY ) ) {
						custom.put( STRING_TYPE_KEY, "unspecified" );
					}
				}

			} catch ( Exception e ) {
				logger.warn( "Error in bx-orm datasource startup interception: ", e );
			}
		} else {
			logger.warn( "Datasource startup interception received invalid config data: {}", configItem.getClass().getName() );
		}
	}

}
