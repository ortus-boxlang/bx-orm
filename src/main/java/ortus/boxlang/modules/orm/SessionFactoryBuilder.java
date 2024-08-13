package ortus.boxlang.modules.orm;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hibernate.EntityMode;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMConnectionProvider;
import ortus.boxlang.modules.orm.hibernate.EntityTuplizer;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

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

	public static String lookupBoxLangClass( SessionFactory sessionFactory, String entityName ) {
		Map<String, EntityRecord> entityMap = ( Map<String, EntityRecord> ) sessionFactory.getProperties().get( BOXLANG_ENTITY_MAP );

		return entityMap.get( entityName ).classFQN();
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

	/**
	 * Configure the Hibernate session factory with the ORM configuration, entity mappings, etc.
	 * 
	 * @return a populated Hibernate configuration object
	 */
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

		if ( ormConfig.secondaryCacheEnabled ) {
			properties.put( AvailableSettings.USE_SECOND_LEVEL_CACHE, true );
			properties.put( AvailableSettings.USE_QUERY_CACHE, true );
			properties.put( AvailableSettings.CACHE_REGION_FACTORY, "jcache" );
			properties.put( "hibernate.javax.cache.provider", ormConfig.getJCacheProviderClassPath() );
			if ( ormConfig.cacheConfig != null && !ormConfig.cacheConfig.isEmpty() ) {
				properties.put( "hibernate.javax.cache.uri", ormConfig.cacheConfig );
			}
		}

		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.MAP, EntityTuplizer.class );
		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.POJO, EntityTuplizer.class );

		Map<String, EntityRecord> entities = getEntityMap();
		properties.put( BOXLANG_ENTITY_MAP, entities );

		entities.values()
		    .stream()
		    .map( EntityRecord::mappingFile )
		    .map( Path::toString )
		    .forEach( ( path ) -> {
			    logger.error( "Adding entity mapping file: {}", path );
			    configuration.addFile( path );
		    } );

		configuration.addProperties( properties );

		return configuration;
	}

	/**
	 * Read or generate entity mappings and return a map of entity names to entity file paths
	 * 
	 * @return
	 */
	private Map<String, EntityRecord> getEntityMap() {
		if ( !ormConfig.autoGenMap ) {
			// Skip mapping generation and load the pre-generated mappings from `ormConfig.cfcLocation`
			throw new BoxRuntimeException( "ORMConfiguration setting `autoGenMap=false` is currently unsupported." );
		} else {
			// @TODO: Here we generate entity mappings and populate the temp directory (aka
			// xmlMappingLocation) with the generated files.
			// If `ormConfig.getAsBoolean(ORMKeys.savemapping)` is true, we should save the
			// generated files to `ormConfig.getAsString(ORMKeys.cfcLocation)`. Else, we
			// should save them to the temp directory.
			return new MappingGenerator( ( IBoxContext ) context, ormConfig )
			    .generateMappings()
			    .getEntityMap();
		}
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
