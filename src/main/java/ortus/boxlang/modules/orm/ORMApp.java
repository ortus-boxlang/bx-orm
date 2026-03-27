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
import java.util.stream.Collectors;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.metadata.ClassMetadata;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.hibernate.BoxProxy;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.GenericCaster;
import ortus.boxlang.runtime.dynamic.casters.KeyCaster;
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
 *
 * @since 1.0.0
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
	 * @param name    A unique name for this ORM application, typically derived from the BoxLang context.
	 */
	public ORMApp( RequestBoxContext context, ORMConfig config, Key name ) {
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
	 *
	 * @param context The BoxLang context for this ORM application.
	 *
	 * @return The ORMApp instance, with session factories built and ready for use.
	 */
	public ORMApp startup( IBoxContext context ) {
		IJDBCCapableContext jdbcContext = context.getParentOfType( IJDBCCapableContext.class );

		// Guard against startup without a JDBC-capable context, which is required to build session factories and use the ORM application at all.
		if ( jdbcContext == null ) {
			throw new BoxRuntimeException( "No JDBC-capable context found for ORMApp startup" );
		}

		// Discover entities for this application and group them by datasource.
		this.entityMap = MappingGenerator.discoverEntities( jdbcContext, this.config );
		if ( logger.isDebugEnabled() ) {
			logger.debug( "Discovered entities on [{}] datasources", this.entityMap.size() );
		}

		// For each datasource with discovered entities, create a session factory and add it to the map. Also track the datasource names in an array for easy
		// access.
		this.entityMap.forEach( ( datasource, entities ) -> {
			if ( logger.isDebugEnabled() ) {
				logger.debug( "Creating session factory for datasource: {}", datasource );
			}

			this.datasources.add( datasource );
			SessionFactory factory = buildSessionFactoryForDatasource( datasource, jdbcContext );
			this.sessionFactories.put( datasource, factory );

			if ( datasource.equals( this.defaultDataSource ) ) {
				if ( logger.isDebugEnabled() ) {
					logger.debug( "Setting the default session factory to the default datasource: {}", datasource );
				}
				this.defaultSessionFactory = factory;
			}
		} );

		// If no entities were discovered for the default datasource, we still need to create a session factory for it so that the ORM application can
		// function at all. It will just be an empty session factory with no mapped entities.
		if ( this.defaultSessionFactory == null ) {
			this.defaultSessionFactory = buildSessionFactoryForDatasource( this.defaultDataSource, jdbcContext );
			this.sessionFactories.put( this.defaultDataSource, this.defaultSessionFactory );
		}

		// Configure logging according to the ORM configuration, after all session factories are built.
		// This ensures that any logging during session factory construction is not affected by the new configuration, which could cause confusion or issues
		// if the new configuration is invalid.
		configureLoggingPerORMConfig();

		return this;
	}

	/**
	 * Build a session factory for the given datasource using the provided JDBC context.
	 *
	 * @param datasource The datasource for which to build the session factory.
	 * @param context    The JDBC context to use for building the session factory.
	 *
	 * @return A new SessionFactory instance for the given datasource.
	 */
	private SessionFactory buildSessionFactoryForDatasource( Key datasource, IJDBCCapableContext context ) {
		SessionFactoryBuilder builder = new SessionFactoryBuilder( context, datasource, config, entityMap.getOrDefault( datasource, new ArrayList<>() ) );
		return builder.build();
	}

	/**
	 * Enable SQL logging for this ORM application if the ORM configuration specifies it.
	 */
	private void configureLoggingPerORMConfig() {
		if ( this.config.logSQL ) {
			LoggerContext loggerContext = runtime.getLoggingService().getLoggerContext();
			loggerContext.getLogger( "org.hibernate.SQL" ).setLevel( logger.isDebugEnabled() ? Level.TRACE : Level.DEBUG );
			loggerContext.getLogger( "org.hibernate.type.descriptor.sql" ).setLevel( logger.isDebugEnabled() ? Level.TRACE : Level.DEBUG );
		}
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
			String entityNames = getEntityRecords().stream().map( er -> er.getEntityName() ).collect( Collectors.joining( ", " ) );
			throw new BoxRuntimeException( "Entity not found: " + entityName + "; configured entities are [" + entityNames + "]" );
		}
		return null;
	}

	/**
	 * Load an entity by its primary key.
	 *
	 * @param context    Boxlang JDBC context
	 * @param entityName The name of the entity to load
	 * @param keyValue   The primary key value to load the entity by. This can be a single value such as a string or integer, or a struct for composite
	 *                   keys.
	 */
	public IClassRunnable loadEntityById( IBoxContext context, String entityName, Object keyValue ) {
		EntityRecord	entityRecord	= this.lookupEntity( entityName, true );
		Session			session			= ORMContext.getForContext( context ).getSession( entityRecord.getDatasource() );

		// @TODO: Support composite keys.
		String			keyType			= getKeyJavaType( session, entityName ).getSimpleName();
		Serializable	id				= ( Serializable ) GenericCaster.cast( context, keyValue, keyType );
		var				entity			= session.get( entityRecord.getEntityName(), id );
		if ( entity instanceof BoxProxy castProxy ) {
			return castProxy.getRunnable();
		} else {
			return ( IClassRunnable ) entity;
		}
	}

	/**
	 * Load an array of entities by filter criteria.
	 *
	 * @param context    JDBC-capable context in which the BIF was invoked.
	 * @param entityName The name of the entity to load.
	 * @param filter     Struct of filter criteria.
	 * @param options    Struct of options, including maxResults, offset, order, etc.
	 */
	public Array loadEntitiesByFilter( IBoxContext context, String entityName, IStruct filter, IStruct options ) {
		EntityRecord			entityRecord	= this.lookupEntity( entityName, true );
		Session					session			= ORMContext.getForContext( context ).getSession( entityRecord.getDatasource() );
		org.hibernate.Criteria	criteria		= session.createCriteria( entityRecord.getEntityName() );

		if ( filter != null ) {

			Array properties = entityRecord.getEntityMeta().getPropertyNamesArray();

			// Ensure that all filter keys are valid properties of the entity or its parent
			filter.keySet()
			    .stream()
			    .filter( key -> !properties.contains( key ) )
			    .findFirst()
			    .ifPresent( key -> {
				    throw new BoxRuntimeException(
				        "No persistent filter property found with the name of '" + key.getName() + "' in entity '" + entityName + "'" );
			    } );

			for ( Key entryKey : filter.keySet() ) {
				int		propertyIndex	= properties.indexOf( KeyCaster.cast( entryKey ) );
				Object	propertyValue	= filter.get( entryKey );
				criteria.add(
				    propertyValue != null
				        ? org.hibernate.criterion.Restrictions.eq(
				            KeyCaster.cast( properties.get( propertyIndex ) ).getName(),
				            propertyValue
				        )
				        : org.hibernate.criterion.Restrictions.isNull(
				            KeyCaster.cast( properties.get( propertyIndex ) ).getName()
				        )
				);
			}
		}

		return Array.of(
		    executeCriteriaQuery( criteria, options )
		        .stream()
		        .map( entity -> ( IClassRunnable ) entity )
		        .toArray()
		);
	}

	/**
	 * Execute a Criteria query with various options.
	 *
	 * @param criteria The criteria to execute.
	 * @param options  Struct of options, including maxResults, offset, order, etc.
	 */
	public List executeCriteriaQuery( Criteria criteria, IStruct options ) {
		if ( options.containsKey( ORMKeys.cacheable ) ) {
			criteria.setCacheable( BooleanCaster.cast( options.get( ORMKeys.cacheable ) ) );
		}
		if ( options.containsKey( Key.timeout ) ) {
			Integer timeout = options.getAsInteger( Key.timeout );
			if ( timeout != null ) {
				criteria.setTimeout( timeout );
			}
		}
		if ( options.containsKey( ORMKeys.maxResults ) ) {
			Integer maxResults = options.getAsInteger( ORMKeys.maxResults );
			if ( maxResults != null ) {
				criteria.setMaxResults( maxResults );
			}
		}
		if ( options.containsKey( Key.offset ) ) {
			Integer offset = options.getAsInteger( Key.offset );
			if ( offset != null && offset > 0 ) {
				criteria.setFirstResult( offset );
			}
		}

		if ( options.containsKey( ORMKeys.orderBy ) ) {
			options.getAsArray( ORMKeys.orderBy ).forEach( ( item ) -> {
				IStruct	order		= ( IStruct ) item;
				String	orderColumn	= order.getAsString( ORMKeys.property );
				if ( order.getAsBoolean( ORMKeys.ascending ) ) {
					criteria.addOrder( Order.asc( orderColumn ) );
				} else {
					criteria.addOrder( Order.desc( orderColumn ) );
				}
			} );
		}
		return criteria.list();
	}

	/**
	 * Get the java type for the primary key of an entity.
	 *
	 * TODO: We're using Hibernate's deprecated metamodel. Refactor to use JPA metamodel.
	 */
	public Class<?> getKeyJavaType( Session session, String entityName ) {
		EntityRecord	entityRecord	= this.lookupEntity( entityName, true );
		ClassMetadata	metadata		= session.getSessionFactory().getClassMetadata( entityRecord.getEntityName() );
		return metadata.getIdentifierType().getReturnedClass();
	}

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
	 * @param context        The BoxLang context to use for looking up the session factory, which will be used to get the datasource if needed.
	 *
	 * @throws BoxRuntimeException if no session factory is found for the given datasource name.
	 *
	 * @return the SessionFactory for the given datasource name.
	 */
	public SessionFactory getSessionFactoryOrThrow( String datasourceName, IBoxContext context ) {
		return getSessionFactoryOrThrow( Key.of( datasourceName ), context );
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 *
	 * @param datasourceName Datasource name to look up the session factory for.
	 * @param context        The BoxLang context to use for looking up the session factory, which will be used to get the datasource if needed.
	 *
	 * @throws BoxRuntimeException if no session factory is found for the given datasource name.
	 *
	 * @return the SessionFactory for the given datasource name.
	 */
	public SessionFactory getSessionFactoryOrThrow( Key datasourceName, IBoxContext context ) {
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		return getSessionFactoryOrThrow(
		    connectionManager.getDatasourceOrThrow( datasourceName )
		);
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 *
	 * @param datasource The datasource for which to get the session factory.
	 *
	 * @throws BoxRuntimeException if no session factory is found for the given datasource.
	 *
	 * @return the SessionFactory for the given datasource.
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
		return ( datasourceName != null ) ? connectionManager.getDatasourceOrThrow( datasourceName )
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
		logger.debug( "Shutting down ORM App: " + this.name );

		// Close all session factories, which should also close any open sessions and connections.
		// Log each close for visibility into shutdown progress, since it can
		for ( Map.Entry<Key, SessionFactory> entry : this.sessionFactories.entrySet() ) {
			try {
				logger.debug( "ORMApp.shutdown: Closing session factory: {}", entry.getKey() );
				entry.getValue().close();
			} catch ( Exception e ) {
				logger.error( "ORMApp.shutdown: Error closing session factory [{}]: {}", entry.getKey(), e.getMessage() );
			}
		}

		// Clear the map and null the default reference; no second close() call needed
		// since defaultSessionFactory is always in sessionFactories.values() already.
		this.sessionFactories.clear();
		this.defaultSessionFactory = null;
		this.datasources.clear();
		this.defaultDataSource	= null;
		this.config				= null;
		this.entityMap.clear();
	}
}
