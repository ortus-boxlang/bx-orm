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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.hibernate.EntityMode;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMConnectionProvider;
import ortus.boxlang.modules.orm.hibernate.EntityTuplizer;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Configures and starts up Hibernate - specifically, configures and starts up a session factory specific to a single datasource.
 */
public class SessionFactoryBuilder {

	public static final String		BOXLANG_APPLICATION_CONTEXT	= "BOXLANG_APPLICATION_CONTEXT";
	public static final String		BOXLANG_CONTEXT				= "BOXLANG_CONTEXT";
	public static final String		BOXLANG_ENTITY_MAP			= "BOXLANG_ENTITY_MAP";
	public static final String		BOXLANG_EVENT_LISTENER		= "BOXLANG_EVENT_LISTENER";

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime						= BoxRuntime.getInstance();

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private BoxLangLogger			logger;

	/**
	 * The ORM datasource name which this session factory should be tied to.
	 */
	private Key						datasourceName;

	/**
	 * The ORM configuration for this session factory.
	 */
	private ORMConfig				ormConfig;

	/**
	 * The discovered entities for this session factory.
	 */
	private List<EntityRecord>		entities;

	/**
	 * The BoxLang context for this session factory.
	 */
	private IJDBCCapableContext		context;

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Static Helpers
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Get the BoxLang context tied to this Hibernate session factory.
	 *
	 * @param sessionFactory The Hibernate session factory
	 */
	public static IBoxContext getRequestContext( SessionFactory sessionFactory ) {
		return ( IBoxContext ) sessionFactory.getProperties().get( BOXLANG_CONTEXT );
	}

	/**
	 * Get a unique key for the given context/datasource combination.
	 * 
	 * @param context        The BoxLang context for this session factory.
	 * @param datasourceName The ORM datasource for this session factory.
	 *
	 * @return a unique key for the given context/datasource combination.
	 */
	public static Key getUniqueName( IBoxContext context, Key datasourceName ) {
		return Key.of( ORMService.getAppNameFromContext( context ) + "_" + datasourceName.getName() );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Constructor(s)
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Constructor
	 *
	 * @param context        The BoxLang context for this session factory.
	 * @param datasourceName The ORM datasource for this session factory.
	 * @param ormConfig      The ORM configuration for this session factory.
	 * @param entities       The discovered entities for this session factory.
	 */
	public SessionFactoryBuilder( IJDBCCapableContext context, Key datasourceName, ORMConfig ormConfig, List<EntityRecord> entities ) {
		this.ormConfig		= ormConfig;
		this.context		= context;
		this.datasourceName	= datasourceName;
		this.entities		= entities;
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
	}

	/**
	 * Get a unique name for this session factory.
	 *
	 * @return a unique name for this session factory, based on the application name and datasource name.
	 */
	public Key getUniqueName() {
		return SessionFactoryBuilder.getUniqueName( this.context, datasourceName );
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
		ClassLoader		oldClassLoader	= Thread.currentThread().getContextClassLoader();
		ModuleRecord	moduleRecord	= runtime.getModuleService().getModuleRecord( Key.of( "orm" ) );
		Thread.currentThread().setContextClassLoader( moduleRecord.classLoader );

		// Make sure we clean up the classloader when we're done.
		SessionFactory	factory;
		Configuration	configuration;
		try {
			System.out.println( "Context class loader: " + Thread.currentThread().getContextClassLoader().getName() );
			configuration = buildConfiguration();
			System.out.println( "After buildConfiguration Context class loader: " + Thread.currentThread().getContextClassLoader().getName() );
			factory = configuration.buildSessionFactory();
		} finally {
			Thread.currentThread().setContextClassLoader( oldClassLoader );
		}

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
		Configuration			configuration	= ormConfig.toHibernateConfig();
		Properties				properties		= new Properties();
		Collection<ClassLoader>	classLoaders	= new ArrayList<>();
		classLoaders.add( runtime.getModuleService().getModuleRecord( Key.of( "orm" ) ).classLoader );

		// @TODO: Any configuration which needs a specific java type (such as the
		// connection provider instance) goes here
		properties.put( AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider( this.datasourceName ) );
		properties.put( AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread" );
		properties.put( AvailableSettings.CLASSLOADERS, classLoaders );
		properties.put( AvailableSettings.TC_CLASSLOADER, "org.hibernate.boot.registry.classloading.internal.AggregatedClassLoader" );
		properties.put( BOXLANG_CONTEXT, context );

		// properties.put( AvailableSettings.SESSION_FACTORY_NAME, getAppName().toString() );

		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.MAP, EntityTuplizer.class );
		configuration.getEntityTuplizerFactory().registerDefaultTuplizerClass( EntityMode.POJO, EntityTuplizer.class );

		// Don't pretend our BL entities are POJOs.
		configuration.setProperty( AvailableSettings.DEFAULT_ENTITY_MODE, "dynamic-map" );

		Map<String, EntityRecord> entityMap = this.entities
		    .stream()
		    .collect( java.util.stream.Collectors.toMap( entity -> entity.getEntityName().toLowerCase().trim(), entity -> entity ) );
		properties.put( BOXLANG_ENTITY_MAP, entityMap );

		entityMap.values()
		    .stream()
		    .map( EntityRecord::getXmlFilePath )
		    .map( Path::toString )
		    .forEach( configuration::addFile );

		configuration.addProperties( properties );

		return configuration;
	}
}
