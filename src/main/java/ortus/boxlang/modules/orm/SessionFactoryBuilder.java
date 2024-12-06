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
package ortus.boxlang.modules.orm;

import java.nio.file.Path;
import java.util.List;
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
	public static final String		BOXLANG_EVENT_LISTENER		= "BOXLANG_EVENT_LISTENER";

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
	 * The discovered entities for this session factory.
	 */
	private List<EntityRecord>		entities;

	private IJDBCCapableContext		context;
	private ApplicationBoxContext	applicationContext;

	/**
	 * Lookup the BoxLang EntityRecord object containing known entity information for a given entity name.
	 * 
	 * @TODO: Move this into our Session wrapper class.
	 * 
	 * @param session    The Hibernate session
	 * @param entityName The entity name to look up
	 * 
	 * @return
	 */
	public static EntityRecord lookupEntity( Session session, String entityName ) {
		return lookupEntity( session.getSessionFactory(), entityName );
	}

	/**
	 * Lookup the BoxLang EntityRecord object containing known entity information for a given entity name.
	 * 
	 * @TODO: Move this into our SessionFactory wrapper class.
	 * 
	 * @param sessionFactory The Hibernate session factory
	 * @param entityName     The entity name to look up
	 * 
	 * @return The BoxLang entityRecord defining the entity name, filepath, FQN, and mapping xml file path
	 */
	public static EntityRecord lookupEntity( SessionFactory sessionFactory, String entityName ) {
		String						lookup		= entityName.trim().toLowerCase();
		@SuppressWarnings( "unchecked" )
		Map<String, EntityRecord>	entityMap	= ( Map<String, EntityRecord> ) sessionFactory.getProperties().get( BOXLANG_ENTITY_MAP );
		if ( !entityMap.containsKey( lookup ) ) {
			logger.warn( "Entity {} not found in entity map.", entityName );
			// @TODO: Catch this in calling code and silence if ormConfig.skipParseErrors is true.
			throw new BoxRuntimeException( "Entity " + entityName + " not found in entity map." );
		}

		return entityMap.get( lookup );
	}

	/**
	 * Lookup the BoxLang EntityRecord object containing known entity information for a given CLASS name.
	 * 
	 * @TODO: Move this into our SessionFactory wrapper class.
	 * 
	 * @param sessionFactory The Hibernate session factory
	 * @param className      The class name to look up
	 * 
	 * @return The BoxLang entityRecord defining the entity name, filepath, FQN, and mapping xml file path
	 */
	@SuppressWarnings( "unchecked" )
	public static EntityRecord lookupEntityByClassName( SessionFactory sessionFactory, String className ) {
		String						lookup		= className.trim().toLowerCase();
		Map<String, EntityRecord>	entityMap	= ( Map<String, EntityRecord> ) sessionFactory.getProperties().get( BOXLANG_ENTITY_MAP );

		return entityMap.values().stream()
		    .filter( ( entity ) -> entity.getClassName().equalsIgnoreCase( className ) )
		    .findFirst()
		    .orElseThrow( () -> {
			    String message = String.format( "Entity '%s' not found in entity map.", lookup );
			    logger.warn( message );
			    // @TODO: Catch this in calling code and silence if ormConfig.skipParseErrors is true.
			    return new BoxRuntimeException( message );
		    } );
	}

	/**
	 * Get the entity map for this Hibernate session factory.
	 * 
	 * @TODO: Move this into our SessionFactory wrapper class.
	 * 
	 * @param sessionFactory The Hibernate session factory
	 */
	@SuppressWarnings( "unchecked" )
	public static Map<String, EntityRecord> getEntityMap( SessionFactory sessionFactory ) {
		return ( Map<String, EntityRecord> ) sessionFactory.getProperties().get( BOXLANG_ENTITY_MAP );
	}

	/**
	 * Get the application context tied to this Hibernate session factory.
	 * 
	 * @TODO: Move this into our SessionFactory wrapper class.
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
	 * Get a unique name for this context's ORM Application.
	 * 
	 * Used to ensure we can tell the various ORM apps apart.
	 * 
	 * @return a unique key for the given context's application.
	 */
	public static String getUniqueAppName( IBoxContext context ) {
		ApplicationBoxContext appContext = ( ApplicationBoxContext ) context.getParentOfType( ApplicationBoxContext.class );
		return appContext.getApplication().getName() + "_" + context.getConfig().hashCode();
	}

	/**
	 * Get a unique key for the given context/datasource combination.
	 * 
	 * @return a unique key for the given context/datasource combination.
	 */
	public static Key getUniqueName( IBoxContext context, DataSource datasource ) {
		return Key.of( SessionFactoryBuilder.getUniqueAppName( context ) + "_" + datasource.getUniqueName().getName() );
	}

	public SessionFactoryBuilder( IJDBCCapableContext context, DataSource datasource, ORMConfig ormConfig, List<EntityRecord> entities ) {
		this.ormConfig			= ormConfig;
		this.context			= context;
		this.datasource			= datasource;
		this.entities			= entities;
		this.applicationContext	= ( ( IBoxContext ) context ).getParentOfType( ApplicationBoxContext.class );
	}

	/**
	 * Get a unique name for this session factory.
	 * 
	 * @return a unique name for this session factory, based on the application name and datasource name.
	 */
	public Key getUniqueName() {
		return SessionFactoryBuilder.getUniqueName( ( IBoxContext ) this.context, datasource );
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
		// Sadly, JAXB hardcodes the context classloader, so we have to temporarily set the context classloader to whatever classloader is used to load this
		// module's dependencies.
		ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader( Configuration.class.getClassLoader() );
		Configuration	configuration	= buildConfiguration();

		SessionFactory	factory			= configuration.buildSessionFactory();
		Thread.currentThread().setContextClassLoader( oldClassLoader );

		factory.getProperties().put( BOXLANG_APPLICATION_CONTEXT, configuration.getProperties().get( BOXLANG_APPLICATION_CONTEXT ) );
		factory.getProperties().put( BOXLANG_CONTEXT, configuration.getProperties().get( BOXLANG_CONTEXT ) );
		factory.getProperties().put( BOXLANG_ENTITY_MAP, configuration.getProperties().get( BOXLANG_ENTITY_MAP ) );
		factory.getProperties().put( BOXLANG_EVENT_LISTENER, this.ormConfig.eventHandler );

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

		Map<String, EntityRecord> entityMap = this.entities.stream()
		    .collect( java.util.stream.Collectors.toMap( ( entity ) -> entity.getEntityName().toLowerCase().trim(), ( entity ) -> entity ) );
		properties.put( BOXLANG_ENTITY_MAP, entityMap );

		entityMap.values()
		    .stream()
		    .map( EntityRecord::getXmlFilePath )
		    .map( Path::toString )
		    .forEach( ( path ) -> {
			    configuration.addFile( path );
		    } );

		configuration.addProperties( properties );

		return configuration;
	}
}
