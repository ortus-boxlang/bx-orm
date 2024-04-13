package com.ortussolutions;

import java.nio.file.Paths;
import java.util.Properties;

import org.hibernate.SessionFactory;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

import com.ortussolutions.config.ORMConnectionProvider;

public class SessionFactoryBuilder {

	private IStruct ormConfig;

	public SessionFactoryBuilder( IStruct ormConfig ) {
		this.ormConfig = ormConfig;
	}

	public SessionFactory build() {

		DataSource	dataSource	= getORMDataSource();

		// build Hibernate ORM configuration
		Properties	properties	= new Properties();
		properties.put( AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider( dataSource ) );

		Configuration configuration = new Configuration();

		// getORMMappingFiles( ormConfig ).forEach( file -> configuration.addFile( file ) );

		return configuration
		    .setProperties( properties )
		    .addFile( Paths.get( "src/test/resources/bx/models/MyEntity.xml" ).toString() )
		    .buildSessionFactory();
	}

	/**
	 * Get the ORM datasource from the ORM configuration.
	 * We currently throw a BoxRuntimeException if no datasource is found in the ORM configuration, but eventually we will support a default datasource.
	 */
	DataSource getORMDataSource() {
		Key ormDatasource = ormConfig.getAsKey( Key.datasource );
		if ( ormDatasource != null ) {
			return BoxRuntime.getInstance().getDataSourceService().get( ormDatasource );
		}
		throw new BoxRuntimeException( "ORM configuration is missing 'datasource' key. Default datasources will be supported in a future iteration." );
		// @TODO: Implement this. the hard part is knowing the context.
		// logger.warn( "ORM configuration is missing 'datasource' key; falling back to default datasource" );
		// return currentContext.getConnectionManager().getDefaultDatasourceOrThrow();
	}

	// List<File> getORMMappingFiles( IStruct ormConfig ) {
	// if ( !ormConfig.getAsBoolean( ORMConfigKeys.autoGenMap ) ) {
	// // @TODO: Here we turn off the automatic generation of entity mappings and instead scan the codebase for orm.xml files.
	// return ( ( java.util.List ) ormConfig.getAsArray( ORMConfigKeys.cfclocation ) )
	// .stream()
	// .flatMap( path -> {
	// try {
	// return Files.walk( Paths.get( path ), 1 );
	// } catch ( IOException e ) {
	// throw new BoxRuntimeException( "Error walking cfclcation path: " + path.toString(), e );
	// }
	// } )
	// // filter to valid orm.xml files
	// .filter( filePath -> FilePath.endsWith( ".orm.xml" ) )
	// @TODO: I'm unsure, but we may need to convert the string path to a File object to work around JPA's classpath limitations.
	// // We should first try this without the conversion and see if it works.
	// .map( filePath -> new File( filePath ) )
	// .toArray();
	// } else {
	// // @TODO: Here we generate entity mappings and return an array of the generated files.
	// }
	// }
}
