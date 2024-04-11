package com.ortussolutions;

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.config.segments.DatasourceConfig;
import ortus.boxlang.runtime.config.segments.RuntimeConfig;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class ORMEngine {

	private static final Logger logger = LoggerFactory.getLogger( ORMEngine.class );

	public ORMEngine() {
		// do stuff?
	}

	// @TODO: This is proof-of-concept code; feel free to move it to a more logical location.
	public void onStartup() {
		// grab the ormConfig struct from the runtime config
		// @TODO: This only checks the runtime config - how do we tweak this to check the web application config as well?
		BoxRuntime		runtime			= BoxRuntime.getInstance();
		RuntimeConfig	runtimeConfig	= ( RuntimeConfig ) runtime.getConfiguration().asStruct().get( Key.runtime );
		IStruct			ormConfig		= runtimeConfig.asStruct().getAsStruct( Key.of( "ormConfig" ) );
		if ( ormConfig == null ) {
			// silent fail?
			logger.info( "No ORM configuration found in runtime configuration" );
		} else {
			// grab configured datasource from the runtime config
			// @TODO: This only checks the runtime config - how do we tweak this to check the web application config as well?
			Key		ormDatasource			= ormConfig.getAsKey( Key.datasource );
			IStruct	availableDatasources	= runtimeConfig.asStruct().getAsStruct( Key.datasources );
			if ( !availableDatasources.containsKey( ormDatasource ) ) {
				throw new BoxRuntimeException( "Datasource '{}' not found in runtime configuration", ormDatasource );
			}

			IStruct				datasourceStruct	= availableDatasources.getAsStruct( ormDatasource );
			DatasourceConfig	datasourceConfig	= new DatasourceConfig( ormDatasource, datasourceStruct.getAsKey( Key.driver ), datasourceStruct );
			DataSource			dataSource			= new DataSource( datasourceConfig );
			ConnectionProvider	connectionProvider	= new ORMConnectionProvider( dataSource );

			// Now that we have a connection provider so Hibernate can talk to the DB, build Hibernate ORM configuration
		}
	}
}
