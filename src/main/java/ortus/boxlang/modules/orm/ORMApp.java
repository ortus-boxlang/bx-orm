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
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.GenericCaster;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Manages a single ORM application and persists the lifetime of the boxlang application.
 * 
 * Stores ORM configuration, datasources, session factories, and other until the ORM application is shut down or reloaded.
 */
public class ORMApp {

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger					logger;

	/**
	 * Runtime
	 */
	private static final BoxRuntime			runtime				= BoxRuntime.getInstance();

	/**
	 * A map of session factories, keyed by name.
	 */
	private Map<Key, SessionFactory>		sessionFactories	= new ConcurrentHashMap<>();

	/**
	 * The boxlang context used to create this ORM application.
	 */
	private RequestBoxContext				context;

	/**
	 * The ORM configuration.
	 */
	private ORMConfig						config;

	/**
	 * A unique name for this ORM application.
	 */
	private Key								name;

	/**
	 * The default session factory for this ORM application.
	 * <p>
	 * In other words, the session factory for the default datasource.
	 */
	private SessionFactory					defaultSessionFactory;

	/**
	 * The default datasource for this ORM application - created from the datasource named in the ORM configuration.
	 */
	private Key								defaultDataSource;

	/**
	 * Array of configured datasource names for this ORM application.
	 */
	private List<Key>						datasources			= new ArrayList<>();

	/**
	 * A map of entities discovered for this ORM application, keyed by datasource name.
	 */
	private Map<Key, List<EntityRecord>>	entityMap;

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
	public ORMApp( RequestBoxContext context, ORMConfig config, Key name ) {
		this.context			= context;
		this.logger				= runtime.getLoggingService().getLogger( "orm" );
		this.config				= config;
		this.name				= name;
		this.defaultDataSource	= this.config.datasource;

		this.logger.debug( "ORMApp created for application: [{}]", name );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * App Methods
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Start up the ORM application, creating session factories for all discovered entities and their datasources.
	 */
	public ORMApp startup() {
		this.entityMap = MappingGenerator.discoverEntities( this.context, this.config );

		if ( logger.isDebugEnabled() )
			logger.debug( "Discovered entities on [{}] datasources", this.entityMap.size() );

		this.entityMap.forEach( ( datasource, entities ) -> {
			if ( logger.isDebugEnabled() )
				logger.debug( "Creating session factory for datasource: {}", datasource );

			this.datasources.add( datasource );

			SessionFactoryBuilder	builder	= new SessionFactoryBuilder( context, datasource, config, entities );
			SessionFactory			factory	= builder.build();
			this.sessionFactories.put( Key.of( datasource ), factory );

			if ( datasource.equals( this.defaultDataSource ) ) {
				if ( logger.isDebugEnabled() )
					logger.debug( "Setting the default session factory to the default datasource: {}", datasource );
				this.defaultSessionFactory = factory;
			}
		} );

		return this;
	}

	/**
	 * Get a unique name for this context's ORM Application.
	 *
	 * Used to ensure we can tell the various ORM apps apart.
	 *
	 * @return a unique key for the given context's application.
	 */
	public Key getName() {
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
	public List<EntityRecord> getEntityRecords( Key datasource ) {
		if ( !this.entityMap.containsKey( datasource ) ) {
			throw new BoxRuntimeException( "No entities found for datasource: " + datasource.getOriginalValue() );
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
		var entityFromDefault = getEntityRecords( this.defaultDataSource ).stream()
		    .filter( ( entity ) -> entity.getEntityName().equalsIgnoreCase( entityName ) )
		    .findFirst();

		if ( entityFromDefault.isPresent() ) {
			return entityFromDefault.get();
		}

		for ( Key datasourceName : this.datasources ) {
			if ( !datasourceName.equals( this.defaultDataSource ) ) {
				var entityFromDatasource = getEntityRecords( datasourceName ).stream()
				    .filter( ( entity ) -> entity.getEntityName().equalsIgnoreCase( entityName ) )
				    .findFirst();

				if ( entityFromDatasource.isPresent() ) {
					return entityFromDatasource.get();
				}
			}
		}
		if ( fail ) {
			String entityNames = getEntityRecords().stream().map( er -> er.getEntityName() ).toString();
			throw new BoxRuntimeException( "Entity not found: " + entityName + "; configured entities are [" + entityNames + "]" );
		}
		return null;
	}

	/**
	 * Load an entity by its primary key.
	 *
	 * @param context    Boxlang Request context
	 * @param entityName The name of the entity to load
	 * @param keyValue   The primary key value to load the entity by. This can be a single value such as a string or integer, or a struct for composite
	 *                   keys.
	 */
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
	 * Load an array of entities by filter criteria.
	 * 
	 * @param context    Context in which the BIF was invoked.
	 * @param entityName The name of the entity to load.
	 * @param filter     Struct of filter criteria.
	 */
	public Array loadEntitiesByFilter( RequestBoxContext context, String entityName, IStruct filter ) {
		EntityRecord			entityRecord	= this.lookupEntity( entityName, true );
		Session					session			= ORMRequestContext.getForContext( context ).getSession( entityRecord.getDatasource() );

		org.hibernate.Criteria	criteria		= session.createCriteria( entityName );

		if ( filter != null ) {
			for ( Map.Entry<Key, Object> entry : filter.entrySet() ) {
				criteria.add( org.hibernate.criterion.Restrictions.eq( entry.getKey().getName(), entry.getValue() ) );
			}
		}

		return Array.of(
		    criteria.list()
		        .stream()
		        .map( entity -> ( IClassRunnable ) entity )
		        .toArray()
		);
	}

	/**
	 * Get the java type for the primary key of an entity.
	 *
	 * TODO: We're using Hibernate's deprecated metamodel. Refactor to use JPA metamodel.
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
	public Map<Key, List<EntityRecord>> getEntityMap() {
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
	public SessionFactory getSessionFactoryOrThrow( String datasourceName ) {
		return getSessionFactoryOrThrow( Key.of( datasourceName ) );
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 *
	 * @param datasourceName Datasource name to look up the session factory for.
	 */
	public SessionFactory getSessionFactoryOrThrow( Key datasourceName ) {
		IJDBCCapableContext jdbcContext = context.getParentOfType( IJDBCCapableContext.class );
		return getSessionFactoryOrThrow( jdbcContext.getConnectionManager().getDatasourceOrThrow( datasourceName ) );
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 *
	 * @param datasource The datasource for which to get the session factory.
	 */
	public SessionFactory getSessionFactoryOrThrow( DataSource datasource ) {
		if ( !this.sessionFactories.containsKey( Key.of( datasource.getOriginalName() ) ) ) {
			throw new BoxRuntimeException( "No session factory found for datasource: " + datasource.getOriginalName() );
		}

		return this.sessionFactories.get( Key.of( datasource.getOriginalName() ) );
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
	public List<Key> getDatasources() {
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
			logger.debug( "ORMApp.shutdown: Closing session factory: {}", sessionFactory );
			sessionFactory.close();
		}
		// This SHOULD be a redundant call, and SHOULD no-op if it was closed above.
		logger.debug( "ORMApp.shutdown: Closing default session factory: {}", this.defaultSessionFactory );
		this.defaultSessionFactory.close();
		this.sessionFactories.clear();

		// shouldn't this be performed by BoxLang Core?
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		this.datasources.forEach( ( datasource ) -> {
			DataSource ormDatasource = connectionManager.getDatasource( datasource );
			logger.debug( "ORMApp.shutdown: Shutting down and removing datasource: {}", ormDatasource.getOriginalName() );
			runtime.getDataSourceService().remove( ormDatasource.getUniqueName() );
			// ormDatasource.shutdown();
		} );
		this.datasources.clear();

		DataSource defaultDataSource = connectionManager.getDatasource( this.defaultDataSource );
		logger.debug( "ORMApp.shutdown: Shutting down and removing default datasource: {}", defaultDataSource.getOriginalName() );
		runtime.getDataSourceService().remove( defaultDataSource.getUniqueName() );
		this.defaultDataSource = null;

	}
}
