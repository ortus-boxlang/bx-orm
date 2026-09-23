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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.Query;

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
	 * @param config The ORM configuration for the application.
	 * @param name   A unique name for this ORM application, typically derived from the BoxLang context.
	 */
	public ORMApp( ORMConfig config, Key name ) {
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

		// Derive this application's facade namespace from its (unique) application name, so its generated entity facades
		// are segregated from any other application's same-named entities sharing this JVM. Set before mapping generation
		// and facade generation, both of which read it.
		this.config.facadeNamespace = ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming
		    .sanitizeNamespace( ORMService.getAppNameFromContext( context ).getName() );

		// Resolve entities for this application and group them by datasource. In `trust` manifest mode this loads a
		// pre-generated .bxorm/manifest.json with zero discovery/parsing/mapping-generation; otherwise it discovers
		// normally (and, in `auto` mode, rewrites the manifest so it stays current).
		long discoverStart = System.currentTimeMillis();
		this.entityMap = resolveEntityMap( context );
		if ( logger.isDebugEnabled() ) {
			logger.debug( "Discovered entities on [{}] datasources", this.entityMap.size() );
			logger.debug( "ORM startup metric - total entity discovery, parsing and meta collection: {}ms", System.currentTimeMillis() - discoverStart,
			    this.entityMap.size() );
		}

		// For each datasource with discovered entities, create a session factory and add it to the map. Also track the datasource names in an array for easy
		// access.
		this.entityMap.forEach( ( datasource, entities ) -> {
			if ( logger.isDebugEnabled() ) {
				logger.debug( "Creating session factory for datasource: {}", datasource );
			}

			this.datasources.add( datasource );
			long			sfBuildStart	= System.currentTimeMillis();
			SessionFactory	factory			= buildSessionFactoryForDatasource( datasource, jdbcContext );
			logger.debug( "ORM startup metric - Hibernate SessionFactory build time [{}]: {}ms", datasource, System.currentTimeMillis() - sfBuildStart );
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
			long sfBuildStart = System.currentTimeMillis();
			this.defaultSessionFactory = buildSessionFactoryForDatasource( this.defaultDataSource, jdbcContext );
			logger.debug( "ORM startup metric - Hibernate SessionFactory build time [{}]: {}ms", this.defaultDataSource,
			    System.currentTimeMillis() - sfBuildStart );
			this.sessionFactories.put( this.defaultDataSource, this.defaultSessionFactory );
		}

		// In auto manifest mode, persist the just-generated facade bytecode to .bxorm/facades.jar so a later trust-mode boot
		// can inject those classes instead of re-running ByteBuddy. Best-effort; a failure never breaks boot.
		if ( "auto".equals( this.config.ormManifest ) ) {
			try {
				java.nio.file.Path jar = ortus.boxlang.modules.orm.mapping.manifest.ManifestService.resolveFolder( context.getRequestContext() )
				    .resolve( ortus.boxlang.modules.orm.mapping.manifest.ManifestService.FACADES_JAR );
				ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeFactory.writeFacadeJar( jar, this.config.facadeNamespace );
			} catch ( RuntimeException e ) {
				logger.warn( "ORM manifest [auto] mode: failed to write facades.jar (continuing normally): {}", e.getMessage() );
			}

			// Ensure a single source watcher over the entity paths so edits trigger an automatic ORM reload. Owned by the
			// ORMService (idempotent across reloads), so a reload does not stop the watcher that triggered it.
			( ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService ) ).ensureEntityWatcher( this.name, this.config, context );
		}

		// Configure logging according to the ORM configuration, after all session factories are built.
		// This ensures that any logging during session factory construction is not affected by the new configuration, which could cause confusion or issues
		// if the new configuration is invalid.
		configureLoggingPerORMConfig();

		return this;
	}

	/**
	 * Resolve the entity map for this application, honoring the {@code ormManifest} mode.
	 * <ul>
	 * <li>{@code trust} - load {@code .bxorm/manifest.json} (integrity-checked, fail-closed) and rehydrate entities with no
	 * discovery, parsing or mapping generation.</li>
	 * <li>{@code auto} - discover normally, then (best-effort) rewrite the manifest so it stays current for shipping.</li>
	 * <li>{@code off} - discover normally (default, unchanged behavior).</li>
	 * </ul>
	 *
	 * @param context The BoxLang context for this ORM application.
	 *
	 * @return The entity map keyed by datasource.
	 */
	private Map<Key, List<EntityRecord>> resolveEntityMap( IBoxContext context ) {
		String mode = this.config.ormManifest == null ? "off" : this.config.ormManifest;

		if ( "trust".equals( mode ) ) {
			java.nio.file.Path										folder		= ortus.boxlang.modules.orm.mapping.manifest.ManifestService
			    .resolveFolder( context.getRequestContext() );
			ortus.boxlang.modules.orm.mapping.manifest.OrmManifest	manifest	= ortus.boxlang.modules.orm.mapping.manifest.ManifestService
			    .read( folder, true );
			// Load pre-generated facade bytecode (if a facades.jar was shipped) so the session factory build injects those
			// classes instead of re-running ByteBuddy. Best-effort: a missing jar just means facades are regenerated.
			ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeFactory
			    .loadFacadeJar( folder.resolve( ortus.boxlang.modules.orm.mapping.manifest.ManifestService.FACADES_JAR ) );
			logger.info( "ORM manifest [trust] mode: booting from [{}] with {} entities; discovery/parsing/generation skipped.", folder,
			    manifest.getEntities().size() );
			return ortus.boxlang.modules.orm.mapping.manifest.ManifestService.toEntityMap( manifest );
		}

		Map<Key, List<EntityRecord>> map = MappingGenerator.discoverEntities( context.getRequestContext(), this.config );

		if ( "auto".equals( mode ) ) {
			try {
				java.nio.file.Path folder = ortus.boxlang.modules.orm.mapping.manifest.ManifestService.resolveFolder( context.getRequestContext() );
				ortus.boxlang.modules.orm.mapping.manifest.ManifestService.write(
				    ortus.boxlang.modules.orm.mapping.manifest.ManifestService.build( map, this.config, moduleVersion() ), folder );
				if ( logger.isDebugEnabled() ) {
					logger.debug( "ORM manifest [auto] mode: wrote manifest to [{}]", folder );
				}
			} catch ( RuntimeException e ) {
				logger.warn( "ORM manifest [auto] mode: failed to write manifest (continuing normally): {}", e.getMessage() );
			}
		}

		return map;
	}

	/**
	 * The ORM module version stamp recorded in a manifest. Falls back to {@code "dev"} when no packaged version is present.
	 */
	private String moduleVersion() {
		String version = getClass().getPackage().getImplementationVersion();
		return version == null ? "dev" : version;
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
			loggerContext.getLogger( "org.hibernate.SQL" ).setLevel( logger.isDebugEnabled() ? Level.DEBUG : Level.INFO );
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

		Class<?>		keyClass		= getKeyJavaType( session, entityName );
		Object			id;

		boolean			isCompositeKey	= entityRecord.getEntityMeta() != null && entityRecord.getEntityMeta().getIdProperties().size() > 1;

		if ( isCompositeKey && !java.util.Map.class.isAssignableFrom( keyClass ) ) {
			// Facade (POJO) mode composite key: the entity uses an embedded (non-aggregated) composite id, so its id
			// representation is the entity's own facade class rather than a Map. Build a facade instance carrying the key
			// property values and let Hibernate read the key off it.
			if ( ! ( keyValue instanceof IStruct compositeStruct ) ) {
				throw new BoxRuntimeException(
				    String.format(
				        "Entity '%s' has a composite primary key. Pass a struct of { propertyName: value } pairs to entityLoadByPK().",
				        entityName
				    ) );
			}
			Object			idFacade	= ( ( SessionFactoryImplementor ) session.getSessionFactory() )
			    .getMappingMetamodel()
			    .getEntityDescriptor( hibernateEntityName( session, entityRecord.getEntityName() ) )
			    .getRepresentationStrategy()
			    .getInstantiator()
			    .instantiate();
			IClassRunnable	idInstance	= ( IClassRunnable ) ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( idFacade );
			for ( Key k : compositeStruct.keySet() ) {
				idInstance.getVariablesScope().put( k, compositeStruct.get( k ) );
				idInstance.getThisScope().put( k, compositeStruct.get( k ) );
			}
			id = idFacade;
		} else if ( java.util.Map.class.isAssignableFrom( keyClass ) ) {
			// Composite key: Hibernate expects a HashMap<String, Object> with String keys (not Key objects)
			if ( ! ( keyValue instanceof IStruct compositeStruct ) ) {
				throw new BoxRuntimeException(
				    String.format(
				        "Entity '%s' has a composite primary key. Pass a struct of { propertyName: value } pairs to entityLoadByPK().",
				        entityName
				    ) );
			}
			HashMap<String, Object> compositeId = new HashMap<>();
			for ( Key k : compositeStruct.keySet() ) {
				compositeId.put( k.getName(), compositeStruct.get( k ) );
			}
			id = compositeId;
		} else {
			id = GenericCaster.cast( context, keyValue, keyClass.getSimpleName() );
		}
		var entity = session.get( hibernateEntityName( session, entityRecord.getEntityName() ), id );
		if ( entity instanceof BoxProxy castProxy ) {
			return castProxy.getRunnable();
		} else {
			// Hibernate returns a POJO facade; unwrap it to the BoxLang instance.
			return ( IClassRunnable ) ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( entity );
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
		EntityRecord		entityRecord	= this.lookupEntity( entityName, true );
		Session				session			= ORMContext.getForContext( context ).getSession( entityRecord.getDatasource() );
		StringBuilder		hql				= new StringBuilder( "select e from " ).append( entityRecord.getEntityName() ).append( " e" );
		Map<String, Object>	params			= new HashMap<>();
		Array				properties		= entityRecord.getEntityMeta().getPropertyNamesArray();

		if ( filter != null && !filter.isEmpty() ) {

			// Ensure that all filter keys are valid properties of the entity or its parent
			filter.keySet()
			    .stream()
			    .filter( key -> !properties.contains( key ) )
			    .findFirst()
			    .ifPresent( key -> {
				    throw new BoxRuntimeException(
				        "No persistent filter property found with the name of '" + key.getName() + "' in entity '" + entityName + "'" );
			    } );

			hql.append( " where" );
			int index = 0;
			for ( Key entryKey : filter.keySet() ) {
				int		propertyIndex	= properties.indexOf( KeyCaster.cast( entryKey ) );
				String	propertyName	= KeyCaster.cast( properties.get( propertyIndex ) ).getName();
				Object	propertyValue	= filter.get( entryKey );
				if ( index > 0 ) {
					hql.append( " and" );
				}
				if ( propertyValue != null ) {
					String paramName = "p" + index;
					hql.append( " e." ).append( propertyName ).append( " = :" ).append( paramName );
					params.put( paramName, propertyValue );
				} else {
					hql.append( " e." ).append( propertyName ).append( " is null" );
				}
				index++;
			}
		}

		if ( options.containsKey( ORMKeys.orderBy ) ) {
			List<String> orderClauses = new ArrayList<>();
			options.getAsArray( ORMKeys.orderBy ).forEach( ( item ) -> {
				IStruct	order			= ( IStruct ) item;
				// The property name is interpolated into the HQL, so an unvalidated caller value would allow HQL
				// injection. Validate + canonicalize it against the entity's persistent properties, same as filter keys.
				int		orderPropIndex	= properties.indexOf( Key.of( order.getAsString( ORMKeys.property ) ) );
				if ( orderPropIndex < 0 ) {
					throw new BoxRuntimeException(
					    "No persistent order-by property found with the name of '" + order.getAsString( ORMKeys.property )
					        + "' in entity '" + entityName + "'" );
				}
				String orderProp = KeyCaster.cast( properties.get( orderPropIndex ) ).getName();
				orderClauses.add( "e." + orderProp + ( order.getAsBoolean( ORMKeys.ascending ) ? " asc" : " desc" ) );
			} );
			if ( !orderClauses.isEmpty() ) {
				hql.append( " order by " ).append( String.join( ", ", orderClauses ) );
			}
		}

		Query<?>			query			= session.createQuery( hql.toString(), Object.class );
		// A to-one association filter may be supplied as a primary key (for example { manufacturer : 1 }).
		// Hibernate 7 rejects a raw scalar for an entity-typed parameter, so resolve those to a managed
		// reference first - the same conversion HQLQuery applies to ORM queries - keeping the Hibernate 5 behavior.
		Map<String, String>	entityParams	= entityParameterTargets( query );
		params.forEach( ( name, value ) -> query.setParameter(
		    name,
		    entityParams.containsKey( name ) ? resolveEntityReference( session, entityParams.get( name ), value ) : value ) );

		return Array.of(
		    executeFilterQuery( query, options )
		        .stream()
		        // Hibernate returns POJO facades; unwrap each to its BoxLang instance.
		        .map( entity -> ( IClassRunnable ) ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( entity ) )
		        .toArray()
		);
	}

	/**
	 * Apply common query options (cacheable, timeout, maxResults, offset) and execute the query.
	 *
	 * @param query   The query to execute.
	 * @param options Struct of options, including maxResults, offset, etc.
	 */
	public List<?> executeFilterQuery( Query<?> query, IStruct options ) {
		if ( options.containsKey( ORMKeys.cacheable ) ) {
			query.setCacheable( BooleanCaster.cast( options.get( ORMKeys.cacheable ) ) );
		}
		if ( options.containsKey( Key.timeout ) ) {
			Integer timeout = options.getAsInteger( Key.timeout );
			if ( timeout != null ) {
				query.setTimeout( timeout );
			}
		}
		if ( options.containsKey( ORMKeys.maxResults ) ) {
			Integer maxResults = options.getAsInteger( ORMKeys.maxResults );
			if ( maxResults != null ) {
				query.setMaxResults( maxResults );
			}
		}
		if ( options.containsKey( Key.offset ) ) {
			Integer offset = options.getAsInteger( Key.offset );
			if ( offset != null && offset > 0 ) {
				query.setFirstResult( offset );
			}
		}
		return query.list();
	}

	/**
	 * Get the java type for the primary key of an entity.
	 */
	public Class<?> getKeyJavaType( Session session, String entityName ) {
		return getEntityPersister( session, entityName ).getIdentifierType().getReturnedClass();
	}

	/**
	 * Get the Hibernate runtime descriptor (persister) for an entity.
	 *
	 * @param session    A Hibernate session bound to the entity's datasource.
	 * @param entityName The name of the entity.
	 */
	public EntityPersister getEntityPersister( Session session, String entityName ) {
		EntityRecord entityRecord = this.lookupEntity( entityName, true );
		return ( ( SessionFactoryImplementor ) session.getSessionFactory() ).getMappingMetamodel()
		    .getEntityDescriptor( hibernateEntityName( session, entityRecord.getEntityName() ) );
	}

	/**
	 * Resolve the Hibernate entity-name for a BoxLang entity name, for calls into the Hibernate Session/metamodel APIs by
	 * name. With the modern {@code mapping.xml} format a facade entity's Hibernate entity-name is its generated facade
	 * class and the BoxLang name is only the JPA/HQL import. {@code getImportedName()} maps the BoxLang import name to
	 * that entity-name (returning the input unchanged when it is already the entity-name).
	 *
	 * @param sf         The session factory.
	 * @param entityName The BoxLang entity name.
	 *
	 * @return The Hibernate entity-name.
	 */
	public static String hibernateEntityName( SessionFactoryImplementor sf, String entityName ) {
		if ( entityName == null ) {
			return null;
		}
		String imported = sf.getMappingMetamodel().getImportedName( entityName );
		return imported != null ? imported : entityName;
	}

	/**
	 * @param session    A Hibernate session.
	 * @param entityName The BoxLang entity name.
	 *
	 * @return The Hibernate entity-name (see {@link #hibernateEntityName(SessionFactoryImplementor, String)}).
	 */
	public static String hibernateEntityName( Session session, String entityName ) {
		return hibernateEntityName( ( SessionFactoryImplementor ) session.getSessionFactory(), entityName );
	}

	/**
	 * Resolve a caller-supplied value into the managed entity Hibernate expects for an association.
	 * <p>
	 * Hibernate 5 accepted either a raw primary key or an entity instance wherever an association was expected, silently
	 * resolving a key to its entity. Hibernate 7's stricter type layer rejects both unless the value is already the managed
	 * entity. bx-orm is the ORM abstraction, so we preserve the Hibernate 5 behavior by resolving here:
	 * <ul>
	 * <li>a primary key (any non-entity scalar) becomes a {@code getReference()} handle to that row;</li>
	 * <li>an entity instance already tracked by the session is returned as-is;</li>
	 * <li>a detached entity instance is resolved to a managed reference by its identifier;</li>
	 * <li>a transient instance with no identifier, or a {@code null}, is passed through unchanged.</li>
	 * </ul>
	 *
	 * @param session    A Hibernate session bound to the entity's datasource.
	 * @param entityName The Hibernate entity name the association targets.
	 * @param value      The caller-supplied value: a primary key or an entity instance.
	 *
	 * @return The managed entity/reference to bind, or the original value when it cannot be resolved to one.
	 */
	public Object resolveEntityReference( Session session, String entityName, Object value ) {
		if ( value == null ) {
			return null;
		}
		// Already an entity instance (live or detached); BoxProxy implements IClassRunnable too.
		if ( value instanceof IClassRunnable runnable ) {
			// Hibernate tracks the facade, so operate on that representation.
			Object managed = ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.wrap( this.config.facadeNamespace, entityName, runnable );
			if ( session.contains( hibernateEntityName( session, entityName ), managed ) ) {
				return managed;
			}
			Object id = getEntityPersister( session, entityName ).getIdentifier( managed, ( SharedSessionContractImplementor ) session );
			// Transient (no id yet): let Hibernate handle it rather than fabricate a reference.
			return id == null ? managed : referenceFor( session, entityName, id );
		}
		// A raw primary key value.
		return referenceFor( session, entityName, value );
	}

	/**
	 * Resolve a managed reference for an entity by id. A lazy {@code BoxProxy} is not assignable to the entity's generated
	 * facade class (which Hibernate's query-parameter type check requires), so fetch the managed facade itself.
	 *
	 * @param session    The Hibernate session.
	 * @param entityName The entity name.
	 * @param id         The identifier.
	 *
	 * @return A managed reference assignable to the entity's mapped representation.
	 */
	private Object referenceFor( Session session, String entityName, Object id ) {
		return session.get( hibernateEntityName( session, entityName ), id );
	}

	/**
	 * Inspect a built query's SQM tree and map each named parameter that targets an entity association to the
	 * Hibernate entity name it references. Used to resolve association filter values (a primary key or entity
	 * instance) to a managed reference before binding, as Hibernate 7 rejects a raw scalar for an entity-typed parameter.
	 *
	 * @param query The query to inspect.
	 *
	 * @return A map of parameter name to the Hibernate entity name it targets (empty when there are none).
	 */
	private Map<String, String> entityParameterTargets( Query<?> query ) {
		Map<String, String> targets = new HashMap<>();
		if ( query instanceof org.hibernate.query.spi.SqmQuery<?> sqmQuery ) {
			for ( org.hibernate.query.sqm.tree.expression.SqmParameter<?> sqmParam : sqmQuery.getSqmStatement().getSqmParameters() ) {
				if ( sqmParam.getName() != null
				    && sqmParam.getAnticipatedType() instanceof org.hibernate.metamodel.model.domain.EntityDomainType<?> entityType ) {
					targets.put( sqmParam.getName(), entityType.getHibernateEntityName() );
				}
			}
		}
		return targets;
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
	 * 
	 * @deprecated Use {@link ORMContext#getDatasource(IBoxContext, Key)} instead, which requires the context to look up the datasource and is more
	 *             consistent with how other methods in this class work. This method will be removed in a future release.
	 */
	@Deprecated( since = "1.6.3", forRemoval = true )
	public DataSource getDatasourceForNameOrDefault( IBoxContext context, Key datasourceName ) {
		return ORMContext.getForContext( context ).getDatasource( datasourceName );
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
