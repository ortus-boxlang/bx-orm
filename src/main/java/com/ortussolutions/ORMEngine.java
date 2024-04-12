package com.ortussolutions;

import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

import com.ortussolutions.config.ORMConfigKeys;
import com.ortussolutions.config.ORMConnectionProvider;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Java class responsible for constructing and managing the Hibernate ORM engine.
 */
public class ORMEngine {

	private static final Logger logger = LoggerFactory.getLogger( ORMEngine.class );

	/**
	 * Constructor for the ORMEngine. Self-registers as a global runtime service.
	 *
	 * @param runtime The BoxRuntime instance to which this ORMEngine will attach itself.
	 */
	public ORMEngine( BoxRuntime runtime ) {
		// runtime.setGlobalService( ORMConfigKeys.ORM, this );
	}

	public void onStartup( IBoxContext context ) {
		// @TODO: This is proof-of-concept code; move it to a more logical location like a new HibernateConfigurator() or something.
		// grab the ormConfig struct from the runtime config
		IStruct		ormConfig	= (IStruct) context.getConfigItem( ORMConfigKeys.ormConfig );
		if ( ormConfig == null ) {
			// silent fail?
			logger.info( "No ORM configuration found in runtime configuration" );
			return;
		}
		DataSource	dataSource	= getORMDataSource( ormConfig );

		// build Hibernate ORM configuration
		Properties	properties	= new Properties();
		properties.put( AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider( dataSource ) );
		SessionFactory sessionFactory = buildSessionFactory( properties );
		System.out.println( "Session factory created! " + sessionFactory.toString());
	}

	DataSource getORMDataSource( IStruct ormConfig ) {
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

	SessionFactory buildSessionFactory( Properties properties ) {
		Configuration configuration = new Configuration();

		// getORMMappingFiles( ormConfig ).forEach( file -> configuration.addFile( file ) );

		return configuration
		    .setProperties( properties )
		    .addFile( Paths.get( "src/test/resources/bx/models/MyEntity.xml" ).toString() )
		    .buildSessionFactory();
	}
}
