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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.metadata.ClassMetadata;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.GenericCaster;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class ORMApp {

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger						logger;

	/**
	 * Runtime
	 */
	private static final BoxRuntime				runtime	= BoxRuntime.getInstance();

	/**
	 * A map of session factories, keyed by name.
	 */
	private Map<Key, SessionFactory>			sessionFactories;

	/**
	 * The boxlang context used to create this ORM application.
	 */
	private RequestBoxContext					context;

	/**
	 * The ORM configuration.
	 */
	private ORMConfig							config;

	/**
	 * A unique name for this ORM application.
	 */
	private Key									name;

	/**
	 * The default session factory for this ORM application.
	 * <p>
	 * In other words, the session factory for the default datasource.
	 */
	private SessionFactory						defaultSessionFactory;

	/**
	 * The default datasource for this ORM application - created from the datasource named in the ORM configuration.
	 */
	private DataSource							defaultDatasource;

	/**
	 * Array of configured datasources for this ORM application.
	 */
	private List<DataSource>					datasources;

	/**
	 * A map of entities discovered for this ORM application, keyed by datasource name.
	 */
	private Map<DataSource, List<EntityRecord>>	entityMap;

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Static Helpers
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Get a unique name for this context's ORM Application.
	 *
	 * Used to ensure we can tell the various ORM apps apart.
	 *
	 * @return a unique key for the given context's application.
	 */
	public static Key getUniqueAppName( IBoxContext context ) {
		ApplicationBoxContext appContext = context.getApplicationContext();
		return getUniqueAppName( appContext.getApplication(), appContext.getApplication().getStartingListener().getSettings() );
	}

	/**
	 * Get a unique name for this context's ORM Application.
	 *
	 * Used to ensure we can tell the various ORM apps apart.
	 *
	 * @return a unique key for the given context's application.
	 */
	public static Key getUniqueAppName( Application application, IStruct appConfig ) {
		return Key.of( application.getName() + "_" + appConfig.hashCode() );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Constructor(s)
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Constructor for ORMApp.
	 *
	 * @param context The BoxLang Request context for the application.
	 * @param config  The ORM configuration for the application.
	 */
	public ORMApp( RequestBoxContext context, ORMConfig config ) {
		// @TODO: Consider only storing the ApplicationBoxContext, as that's the parent, and the RequestBoxContext will obviously age out pretty quickly.
		this.context	= context;
		this.logger		= runtime.getLoggingService().getLogger( "orm" );

		if ( context.getParentOfType( ApplicationBoxContext.class ) == null ) {
			logger.error( "ORMApp created with a context that is not inside an application context; aborting." );
			return;
		}

		this.config				= config;
		this.sessionFactories	= new ConcurrentHashMap<>();
		this.datasources		= new ArrayList<>();
		this.name				= ORMApp.getUniqueAppName( context );

		ConnectionManager connectionManager = context.getConnectionManager();
		this.defaultDatasource = config.datasource == null
		    ? connectionManager.getDefaultDatasourceOrThrow()
		    : connectionManager.getDatasourceOrThrow( Key.of( config.datasource ) );
	}

	/**
	 * Start up the ORM application, creating session factories for all discovered entities and their datasources.
	 */
	public void startup() {
		this.entityMap = MappingGenerator.discoverEntities( context, config );

		if ( logger.isDebugEnabled() )
			logger.debug( "Discovered [{}] entities", this.entityMap.size() );

		this.entityMap.forEach( ( datasource, entities ) -> {
			if ( logger.isDebugEnabled() )
				logger.debug( "Creating session factory for datasource: {}", datasource.getOriginalName() );

			this.datasources.add( datasource );

			SessionFactoryBuilder	builder	= new SessionFactoryBuilder( context, datasource, config, entities );
			SessionFactory			factory	= builder.build();

			logger.info( "Registering new Hibernate session factory for name: {}", builder.getUniqueName() );

			this.sessionFactories.put( datasource.getUniqueName(), factory );

			if ( datasource.equals( this.defaultDatasource ) ) {
				if ( logger.isDebugEnabled() )
					logger.debug( "Setting the default datasource: {}", datasource );

				this.defaultSessionFactory = factory;
			}
		} );
	}

	/**
	 * Get a unique name for this context's ORM Application.
	 *
	 * Used to ensure we can tell the various ORM apps apart.
	 *
	 * @return a unique key for the given context's application.
	 */
	public Key getUniqueName() {
		return this.name;
	}

	/**
	 * Get ALL discovered entities/entity meta info for this ORM application.
	 */
	public List<EntityRecord> getEntityRecords() {
		return this.entityMap.values().stream().flatMap( List::stream ).toList();
	}

	/**
	 *
	 * Get ALL discovered entities/entity meta for this ORM application which are associated with the given datasource.
	 *
	 * @param datasource The datasource for which to get entities. Will filter the result to entities with a `datasource="myDatasourceName"`
	 *                   annotation.
	 */
	public List<EntityRecord> getEntityRecords( DataSource datasource ) {
		if ( !this.entityMap.containsKey( datasource ) ) {
			throw new BoxRuntimeException( "No entities found for datasource: " + datasource );
		}
		return this.entityMap.get( datasource );
	}

	/**
	 * Lookup the BoxLang EntityRecord object containing known entity information for a given entity name.
	 *
	 * @param entityName The entity name to look up
	 * @param fail       Whether to throw an exception if the entity is not found.
	 *
	 * @return
	 */
	public EntityRecord lookupEntity( String entityName, Boolean fail ) {
		var entityFromDefault = getEntityRecords( this.defaultDatasource ).stream()
		    .filter( ( entity ) -> entity.getEntityName().equalsIgnoreCase( entityName ) )
		    .findFirst();

		if ( entityFromDefault.isPresent() ) {
			return entityFromDefault.get();
		}

		for ( DataSource datasource : this.datasources ) {
			if ( !datasource.equals( this.defaultDatasource ) ) {
				var entityFromDatasource = getEntityRecords( datasource ).stream()
				    .filter( ( entity ) -> entity.getEntityName().equalsIgnoreCase( entityName ) )
				    .findFirst();

				if ( entityFromDatasource.isPresent() ) {
					return entityFromDatasource.get();
				}
			}
		}
		return null;
	}

	public IClassRunnable loadEntityById( RequestBoxContext context, String entityName, Object keyValue ) {
		EntityRecord	entityRecord	= this.lookupEntity( entityName, true );
		Session			session			= ORMRequestContext.getForContext( context ).getSession( entityRecord.getDatasource() );

		// @TODO: Support composite keys.
		String			keyType			= getKeyJavaType( session, entityName ).getSimpleName();
		Serializable	id				= ( Serializable ) GenericCaster.cast( context, keyValue, keyType );
		var				entity			= session.get( entityName, id );
		// @TODO: announce postLoad event
		return ( IClassRunnable ) entity;
	}

	/**
	 * TODO: Remove once we figure out how to get the JPA metamodel working.
	 */
	private Class<?> getKeyJavaType( Session session, String entityName ) {
		ClassMetadata metadata = session.getSessionFactory().getClassMetadata( entityName );
		return metadata.getIdentifierType().getReturnedClass();
	}

	/**
	 * TODO: Get this JPA metamodel stuff working. We're currently unable to get the class name for dynamic boxlang classes; this may change in the
	 * future.
	 * private Class<?> getKeyJavaType( Session session, Class entityClassType ) {
	 * Metamodel metamodel = session
	 * .getEntityManagerFactory()
	 * .getMetamodel();
	 * 
	 * EntityType<?> entityType = metamodel.entity( entityClassType );
	 * SingularAttribute<?, ?> idAttribute = entityType.getId( entityType.getIdType().getJavaType() );
	 * return idAttribute.getJavaType();
	 * }
	 */

	/**
	 * Get the entity map for this ORM application, where the key is the configured datasource name and the value is a list of EntityRecords.
	 */
	public Map<DataSource, List<EntityRecord>> getEntityMap() {
		return this.entityMap;
	}

	/**
	 * Get the ORM configuration for this ORM application.
	 */
	public ORMConfig getConfig() {
		return this.config;
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 *
	 * @param datasourceName Datasource name to look up the session factory for.
	 */
	public SessionFactory getSessionFactoryOrThrow( Key datasourceName ) {
		return getSessionFactoryOrThrow( ( ( IJDBCCapableContext ) context ).getConnectionManager().getDatasourceOrThrow( datasourceName ) );
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 *
	 * @param datasource The datasource for which to get the session factory.
	 */
	public SessionFactory getSessionFactoryOrThrow( DataSource datasource ) {
		if ( !this.sessionFactories.containsKey( datasource.getUniqueName() ) ) {
			throw new BoxRuntimeException( "No session factory found for datasource: " + datasource.getUniqueName() );
		}

		return this.sessionFactories.get( datasource.getUniqueName() );
	}

	/**
	 * Get the default session factory for this ORM application.
	 */
	public SessionFactory getDefaultSessionFactoryOrThrow() {
		if ( this.defaultSessionFactory == null ) {
			throw new BoxRuntimeException( "No default session factory set for ORM application" );
		}
		return this.defaultSessionFactory;
	}

	/**
	 * Get the datasource for a given name, falling back to the default datasource if the name is null.
	 *
	 * Will throw a BoxRuntimeException if the datasource is not found.
	 */
	public DataSource getDatasourceForNameOrDefault( IBoxContext context, Key datasourceName ) {
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		return datasourceName != null ? connectionManager.getDatasourceOrThrow( datasourceName )
		    : connectionManager.getDefaultDatasourceOrThrow();
	}

	/**
	 * Get the list of datasources configured for this ORM application.
	 */
	public List<DataSource> getDatasources() {
		return this.datasources;
	}

	/**
	 * Shut down the ORM application, including shutting down all Hibernate resources - session factories, open sessions and connections, etc.
	 */
	public void shutdown() {
		logger.info( "ORMApp shutdown" );
		// @TODO: "It is the responsibility of the application to ensure that there are
		// no open sessions before calling this method as the impact on those
		// sessions is indeterminate."
		for ( SessionFactory sessionFactory : this.sessionFactories.values() ) {
			sessionFactory.close();
		}
		this.sessionFactories.clear();
	}
}
