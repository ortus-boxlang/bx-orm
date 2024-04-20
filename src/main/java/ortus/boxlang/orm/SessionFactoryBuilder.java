package ortus.boxlang.orm;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.hibernate.EntityMode;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.orm.config.ORMConfig;
import ortus.boxlang.orm.config.ORMConnectionProvider;
import ortus.boxlang.orm.hibernate.EntityTuplizer;
import ortus.boxlang.orm.mapping.EntityRecord;
import ortus.boxlang.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class SessionFactoryBuilder {

	public static final String		BOXLANG_APPLICATION_ENTITYMAPPING	= "BOXLANG_APPLICATION_ENTITYMAPPING";
	public static final String		BOXLANG_APPLICATION_CONTEXT			= "BOXLANG_APPLICATION_CONTEXT";
	public static final String		BOXLANG_CONTEXT						= "BOXLANG_CONTEXT";
	public static final String		BOXLANG_ENTITY_MAP					= "BOXLANG_ENTITY_MAP";

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger		logger								= LoggerFactory.getLogger( SessionFactoryBuilder.class );

	/**
	 * The ORM datasource for this session factory.
	 */
	private DataSource				datasource;

	/**
	 * The ORM configuration for this session factory.
	 */
	private ORMConfig				ormConfig;

	/**
	 * The application name for this session factory. Used as an identifier in
	 * hash maps.
	 */
	private Key						appName;

	/**
	 * The context-level connection manager, used for retrieving datasources that we
	 * can be sure have been registered with the datasource manager.
	 */
	private ConnectionManager		connectionManager;

	private IJDBCCapableContext		context;
	private ApplicationBoxContext	applicationContext;

	public static Class<IClassRunnable> lookupBoxLangClass( SessionFactory sessionFactory, String entityName ) {
		Map<String, EntityRecord> entityMap = ( Map<String, EntityRecord> ) sessionFactory.getProperties().get( BOXLANG_ENTITY_MAP );

		return entityMap.get( entityName ).entityClass();
	}

	public static ApplicationBoxContext getApplicationContext( SessionFactory sessionFactory ) {
		return ( ApplicationBoxContext ) sessionFactory.getProperties().get( BOXLANG_APPLICATION_CONTEXT );
	}

	public static IBoxContext getContext( SessionFactory sessionFactory ) {
		return ( IBoxContext ) sessionFactory.getProperties().get( BOXLANG_CONTEXT );
	}

	public SessionFactoryBuilder( IJDBCCapableContext context, Key appName, IStruct properties ) {
		this.appName			= appName;
		this.connectionManager	= context.getConnectionManager();
		this.ormConfig			= new ORMConfig( properties );
		this.datasource			= getORMDataSource();
		this.context			= context;
		this.applicationContext	= ( ( IBoxContext ) context ).getParentOfType( ApplicationBoxContext.class );
	}

	public SessionFactory build() {
		Configuration	configuration	= buildConfiguration();
		// getORMMappingFiles().forEach( configuration::addFile );

		SessionFactory	factory			= configuration.buildSessionFactory();

		factory.getProperties().put( BOXLANG_APPLICATION_CONTEXT, configuration.getProperties().get( BOXLANG_APPLICATION_CONTEXT ) );
		factory.getProperties().put( BOXLANG_CONTEXT, configuration.getProperties().get( BOXLANG_CONTEXT ) );
		factory.getProperties().put( BOXLANG_ENTITY_MAP, configuration.getProperties().get( BOXLANG_ENTITY_MAP ) );

		return factory;
	}

	/**
	 * Get the ORM datasource from the ORM configuration.
	 * We currently throw a BoxRuntimeException if no datasource is found in the ORM
	 * configuration, but eventually we will support a default datasource.
	 */
	private DataSource getORMDataSource() {
		Object ormDatasource = this.ormConfig.datasource;
		if ( ormDatasource != null ) {
			if ( ormDatasource instanceof IStruct datasourceStruct ) {
				return connectionManager.getOnTheFlyDataSource( datasourceStruct );
			}
			return connectionManager.getDatasourceOrThrow( Key.of( ormDatasource ) );
		}
		throw new BoxRuntimeException(
		    "ORM configuration is missing 'datasource' key, or named datasource is not found. Default datasources will be supported in a future iteration." );
		// @TODO: Implement this. the hard part is knowing the context.
		// logger.warn( "ORM configuration is missing 'datasource' key; falling back to
		// default datasource" );
		// return currentContext.getConnectionManager().getDefaultDatasourceOrThrow();
	}

	private List<File> getORMMappingFiles() {
		// @TODO: Should we use the application name, or the ORM configuration hash?
		String xmlMappingLocation = Path.of( FileSystemUtil.getTempDirectory(), "orm_mappings", getAppName().getName() ).toString();
		if ( !ormConfig.autoGenMap ) {
			// Skip mapping generation and load the pre-generated mappings from this
			// location.
			// xmlMappingLocation = ormConfig.cfcLocation;
			throw new BoxRuntimeException( "ORMConfiguration setting `autoGenMap=false` is currently unsupported." );
		} else {
			// @TODO: Here we generate entity mappings and populate the temp directory (aka
			// xmlMappingLocation) with the generated files.
			// If `ormConfig.getAsBoolean(ORMKeys.savemapping)` is true, we should save the
			// generated files to `ormConfig.getAsString(ORMKeys.cfclocation)`. Else, we
			// should save them to the temp directory.
			// @TODO: Also remember to use the
			// `ormConfig.getAsBoolean(ORMKeys.skipCFCWithError)` setting to determine
			// whether to throw exceptions on compile-time errors.
		}
		// Regardless of the autoGenMap configuration value, we now have a directory
		// populated with JPA orm.xml mapping files.
		// We now need to load these files into the Hibernate configuration.
		// return Arrays.stream(xmlMappingLocation.split(","))
		// .flatMap(path -> {
		// try {
		// return Files.walk(Paths.get(path), 1);
		// } catch (IOException e) {
		// throw new BoxRuntimeException("Error walking cfclcation path: " +
		// path.toString(), e);
		// }
		// })
		// // filter to valid orm.xml files
		// .filter(filePath -> FilePath.endsWith(".orm.xml"))
		// // @TODO: I'm unsure, but we may need to convert the string path to a File
		// // object to work around JPA's classpath limitations.
		// // We should first try this without the conversion and see if it works.
		// .map(filePath -> filePath.toFile())
		// .toList();

		Map<String, EntityRecord>	entities	= new MappingGenerator( xmlMappingLocation ).mapEntities( ( IBoxContext ) context, ormConfig.cfcLocation );

		// Alternative test implementation
		List<File>					files		= new java.util.ArrayList<>();
		// Dummy file for testing
		files.add( Paths.get( "src/test/resources/app/models/Event.hbm.xml" ).toFile() );
		files.add( Paths.get( "src/test/resources/app/models/Developer.hbm.xml" ).toFile() );
		return files;
	}

	private Configuration buildConfiguration() {
		Configuration	configuration	= ormConfig.toHibernateConfig();

		Properties		properties		= new Properties();
		// @TODO: Any configuration which needs a specific java type (such as the
		// connection provider instance) goes here
		properties.put( AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider( this.datasource ) );
		properties.put( AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread" );
		properties.put( BOXLANG_APPLICATION_ENTITYMAPPING, new HashMap<String, String>() );
		properties.put( BOXLANG_APPLICATION_CONTEXT, applicationContext );
		properties.put( BOXLANG_CONTEXT, context );

		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.MAP, EntityTuplizer.class );
		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.POJO, EntityTuplizer.class );

		String						xmlMappingLocation	= Path.of( FileSystemUtil.getTempDirectory(), "orm_mappings", getAppName().getName() ).toString();
		Map<String, EntityRecord>	entities			= new MappingGenerator( xmlMappingLocation ).mapEntities( ( IBoxContext ) context,
		    ormConfig.cfcLocation );
		properties.put( BOXLANG_ENTITY_MAP, entities );

		entities.values()
		    .stream()
		    .map( EntityRecord::mappingFile )
		    .map( Path::toString )
		    .forEach( configuration::addFile );

		configuration.addProperties( properties );

		return configuration;
	}

	/**
	 * Get the application name for this session factory.
	 * /**
	 *
	 * Get the application name for this session factory. Used as an identifier in
	 * hash maps.
	 */
	private Key getAppName() {
		return appName;
	}
}
