package ortus.boxlang.modules.orm;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.hibernate.EntityMode;
import org.hibernate.Session;
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
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class SessionFactoryBuilder {

	public static final String		BOXLANG_APPLICATION_CONTEXT	= "BOXLANG_APPLICATION_CONTEXT";
	public static final String		BOXLANG_CONTEXT				= "BOXLANG_CONTEXT";
	public static final String		BOXLANG_ENTITY_MAP			= "BOXLANG_ENTITY_MAP";

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger		logger						= LoggerFactory.getLogger( SessionFactoryBuilder.class );

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

	private IJDBCCapableContext		context;
	private ApplicationBoxContext	applicationContext;

	/**
	 * Lookup the BoxLang class FQN for a given entity name.
	 * 
	 * @param sessionFactory The Hibernate session factory
	 * @param entityName     The entity name to look up
	 * 
	 * @return The BoxLang entityRecord defining the entity name, filepath, FQN, and mapping xml file path
	 */
	public static EntityRecord lookupEntity( SessionFactory sessionFactory, String entityName ) {
		Map<String, EntityRecord> entityMap = ( Map<String, EntityRecord> ) sessionFactory.getProperties().get( BOXLANG_ENTITY_MAP );

		return entityMap.get( entityName );
	}

	public static EntityRecord lookupEntity( Session session, String entityName ) {
		return lookupEntity( session.getSessionFactory(), entityName );
	}

	/**
	 * Get the application context tied to this Hibernate session factory.
	 * 
	 * @param sessionFactory The Hibernate session factory
	 */
	public static ApplicationBoxContext getApplicationContext( SessionFactory sessionFactory ) {
		return ( ApplicationBoxContext ) sessionFactory.getProperties().get( BOXLANG_APPLICATION_CONTEXT );
	}

	/**
	 * Get the BoxLang context tied to this Hibernate session factory.
	 * 
	 * @param sessionFactory The Hibernate session factory
	 */
	public static IBoxContext getContext( SessionFactory sessionFactory ) {
		return ( IBoxContext ) sessionFactory.getProperties().get( BOXLANG_CONTEXT );
	}

	/**
	 * Get a unique name for this session factory.
	 * 
	 * @return a unique name for this session factory, based on the application name and datasource name.
	 */
	public static Key getUniqueName( Key appName, DataSource datasource ) {
		return Key.of( appName.getName() + "_" + datasource.getUniqueName().getName() );
	}

	public SessionFactoryBuilder( IJDBCCapableContext context, Key appName, DataSource datasource, ORMConfig ormConfig ) {
		this.appName			= appName;
		this.ormConfig			= ormConfig;
		this.context			= context;
		this.datasource			= datasource;
		this.applicationContext	= ( ( IBoxContext ) context ).getParentOfType( ApplicationBoxContext.class );
	}

	/**
	 * Get a unique name for this session factory.
	 * 
	 * @return a unique name for this session factory, based on the application name and datasource name.
	 */
	public Key getUniqueName() {
		return SessionFactoryBuilder.getUniqueName( getAppName(), datasource );
	}

	/**
	 * Build the Hibernate session factory.
	 * <p>
	 * This method will generate entity mappings if `ormConfig.autoGenMap` is true, as well as parse the ORM configuration and set up the Hibernate
	 * configuration.
	 * 
	 * @return a Hibernate session factory ready for use.
	 */
	public SessionFactory build() {
		Configuration	configuration	= buildConfiguration();

		SessionFactory	factory			= configuration.buildSessionFactory();

		factory.getProperties().put( BOXLANG_APPLICATION_CONTEXT, configuration.getProperties().get( BOXLANG_APPLICATION_CONTEXT ) );
		factory.getProperties().put( BOXLANG_CONTEXT, configuration.getProperties().get( BOXLANG_CONTEXT ) );
		factory.getProperties().put( BOXLANG_ENTITY_MAP, configuration.getProperties().get( BOXLANG_ENTITY_MAP ) );

		return factory;
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
		properties.put( BOXLANG_APPLICATION_CONTEXT, applicationContext );
		properties.put( BOXLANG_CONTEXT, context );
		// properties.put( AvailableSettings.SESSION_FACTORY_NAME, getAppName().toString() );

		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.MAP, EntityTuplizer.class );
		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.POJO, EntityTuplizer.class );

		// Don't pretend our BL entities are POJOs.
		configuration.setProperty( AvailableSettings.DEFAULT_ENTITY_MODE, "dynamic-map" );

		Map<String, EntityRecord> entities = getEntityMap();
		properties.put( BOXLANG_ENTITY_MAP, entities );

		entities.values()
		    .stream()
		    .map( EntityRecord::getXmlFilePath )
		    .map( Path::toString )
		    .forEach( ( path ) -> {
			    configuration.addFile( path );
		    } );

		configuration.addProperties( properties );

		return configuration;
	}

	/**
	 * Retrieve the entity map for this session factory, constructing them if necessary.
	 * 
	 * @return a map of entity names to entity file paths
	 */
	private Map<String, EntityRecord> getEntityMap() {
		if ( !ormConfig.autoGenMap ) {
			// Skip mapping generation and load the pre-generated mappings from `ormConfig.entityPaths`
			throw new BoxRuntimeException( "ORMConfiguration setting `autoGenMap=false` is currently unsupported." );
		} else {
			// generate xml mappings on the fly, saving them either to a temp directory or alongside the entity class files if `ormConfig.saveMapping` is true.
			return new MappingGenerator( ( IBoxContext ) context, ormConfig )
			    .generateMappings()
			    .getEntityMap();
		}
	}

	/**
	 * Get the application name for this session factory. Used as an identifier in
	 * hash maps.
	 */
	private Key getAppName() {
		return appName;
	}
}
