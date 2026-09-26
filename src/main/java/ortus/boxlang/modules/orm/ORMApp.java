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
import ortus.boxlang.runtime.types.Struct;
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
		this.config.facadeNamespace		= ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming
		    .sanitizeNamespace( ORMService.getAppNameFromContext( context ).getName() );
		// Each build (first boot and every reload) generates its facades into a fresh classloader, so a reload with a
		// changed entity defines new facade classes instead of reusing the previous build's (a loader can only define a
		// given class name once). Trust mode replaces this with a loader carrying the pre-generated facades.jar bytecode.
		this.config.facadeClassLoader	= new ortus.boxlang.modules.orm.hibernate.facade.FacadeClassLoader( moduleClassLoader() );

		// Resolve entities for this application and group them by datasource. In `trust` manifest mode this loads a
		// pre-generated .bxorm/manifest.json with zero discovery/parsing/mapping-generation; otherwise it discovers
		// normally (and, in `auto` mode, rewrites the manifest so it stays current).
		long discoverStart = System.currentTimeMillis();
		this.entityMap = resolveEntityMap( context );
		// Catch mistakes Hibernate would only report with internal names (or not at all) before it starts.
		validateEntities();
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
			SessionFactory	factory			= buildSessionFactoryWithHints( datasource, jdbcContext );
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
			this.defaultSessionFactory = buildSessionFactoryWithHints( this.defaultDataSource, jdbcContext );
			logger.debug( "ORM startup metric - Hibernate SessionFactory build time [{}]: {}ms", this.defaultDataSource,
			    System.currentTimeMillis() - sfBuildStart );
			this.sessionFactories.put( this.defaultDataSource, this.defaultSessionFactory );
		}

		// In auto manifest mode, persist the just-generated facade bytecode to .bxorm/facades.jar so a later trust-mode boot
		// can inject those classes instead of re-running ByteBuddy. Best-effort; a failure never breaks boot.
		if ( "auto".equals( this.config.ormManifest ) ) {
			try {
				java.nio.file.Path jar = ortus.boxlang.modules.orm.mapping.manifest.ManifestService
				    .resolveFolder( context.getRequestContext(), this.config.manifestLocation )
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
	 * The ORM module classloader: the parent of each build's facade classloader.
	 *
	 * @return The module classloader.
	 */
	private static ClassLoader moduleClassLoader() {
		return runtime.getModuleService().getModuleRecord( Key.of( "orm" ) ).classLoader;
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
			    .resolveFolder( context.getRequestContext(), this.config.manifestLocation );
			ortus.boxlang.modules.orm.mapping.manifest.OrmManifest	manifest	= ortus.boxlang.modules.orm.mapping.manifest.ManifestService
			    .read( folder, true );
			// Fail closed on a stale manifest: booting old mappings against changed entities or settings would silently
			// persist to the wrong columns/tables. Regenerate it with an auto-mode boot.
			List<String>											stale		= ortus.boxlang.modules.orm.mapping.manifest.ManifestService
			    .verify( manifest, this.config, moduleVersion() );
			if ( !stale.isEmpty() ) {
				throw new BoxRuntimeException( "ORM manifest mode is [trust] but the manifest at [" + folder + "] is stale: "
				    + String.join( "; ", stale )
				    + ". Regenerate it by booting once with ormManifest=\"auto\", then switch back to [trust]." );
			}
			// Load pre-generated facade bytecode (if a facades.jar was shipped) so the session factory build injects those
			// classes instead of re-running ByteBuddy. Best-effort: a missing jar just means facades are regenerated.
			this.config.facadeClassLoader = new ortus.boxlang.modules.orm.hibernate.facade.FacadeClassLoader( moduleClassLoader(),
			    ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeFactory
			        .readFacadeJar( folder.resolve( ortus.boxlang.modules.orm.mapping.manifest.ManifestService.FACADES_JAR ) ) );
			logger.info( "ORM manifest [trust] mode: booting from [{}] with {} entities; discovery/parsing/generation skipped.", folder,
			    manifest.getEntities().size() );
			return ortus.boxlang.modules.orm.mapping.manifest.ManifestService.toEntityMap( manifest );
		}

		Map<Key, List<EntityRecord>> map = MappingGenerator.discoverEntities( context.getRequestContext(), this.config );

		if ( "auto".equals( mode ) ) {
			try {
				java.nio.file.Path folder = ortus.boxlang.modules.orm.mapping.manifest.ManifestService
				    .resolveFolder( context.getRequestContext(), this.config.manifestLocation );
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
	 * The ORM module version stamp recorded in a manifest (the module record's version, as the CLI reports it). Falls back
	 * to {@code "dev"} when the module record carries no version.
	 */
	private static String moduleVersion() {
		ortus.boxlang.runtime.modules.ModuleRecord record = runtime.getModuleService().getModuleRecord( Key.of( "orm" ) );
		return record == null || record.version == null || record.version.isBlank() ? "dev" : record.version;
	}

	/**
	 * Build a datasource's session factory; if Hibernate fails and an ormtype looked wrong, say so in the error (the raw
	 * Hibernate message is usually about SQL type codes, not the property that caused it).
	 *
	 * @param datasource  The datasource to build the session factory for.
	 * @param jdbcContext The JDBC-capable context used to build it.
	 *
	 * @return The session factory.
	 *
	 * @throws ortus.boxlang.modules.orm.errors.ORMException ({@code orm.config}) naming the unknown ormtypes, when there are any.
	 */
	private SessionFactory buildSessionFactoryWithHints( Key datasource, IJDBCCapableContext jdbcContext ) {
		try {
			return buildSessionFactoryForDatasource( datasource, jdbcContext );
		} catch ( RuntimeException e ) {
			if ( this.unknownOrmTypes.isEmpty() ) {
				throw e;
			}
			RuntimeException translated = ortus.boxlang.modules.orm.errors.ORMErrors.translate( e,
			    ortus.boxlang.modules.orm.errors.ORMErrors.Context.of( "ORM startup" ) );
			throw new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.CONFIG,
			    "The ORM could not start. Likely cause: " + String.join( " ", this.unknownOrmTypes ),
			    "Use a valid ormtype such as string, integer, long, boolean, timestamp, text or bigdecimal. Hibernate said: "
			        + translated.getMessage(),
			    null, e );
		}
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
	 * Every ormtype value Hibernate resolves once normalized by {@link MappingXMLWriter#toHibernateType(String)}. An
	 * ormtype outside this set is reported as a warning (it may still be a valid Hibernate or Java type name), and named in
	 * the startup error if Hibernate then fails to start.
	 */
	private static final java.util.Set<String>	KNOWN_ORM_TYPES	= java.util.Set.of( "string", "character", "integer", "long", "short",
	    "byte", "float", "double", "boolean", "yes_no", "true_false", "bigdecimal", "biginteger", "timestamp", "time", "text", "binary",
	    "uuid", "serializable", "locale", "timezone", "currency", "class", "url", "duration", "instant", "zoneddatetime", "offsetdatetime",
	    "localdate", "localdatetime", "localtime", "calendar", "calendar_date", "calendar_time", "object", "any", "json" );

	/** ormtype values not in {@link #KNOWN_ORM_TYPES}, as "Entity.property has ormtype=..." sentences. */
	private final List<String>					unknownOrmTypes	= new ArrayList<>();

	/**
	 * Validate the discovered entities before Hibernate starts: duplicate entity names on one datasource are an error
	 * (Hibernate would fail with an internal "Duplicate key" message); unknown ormtype values are logged as warnings and
	 * remembered, so a later Hibernate startup failure can name them.
	 *
	 * @throws ortus.boxlang.modules.orm.errors.ORMException ({@code orm.config}) listing every duplicate entity name.
	 */
	private void validateEntities() {
		List<String> problems = new ArrayList<>();
		this.entityMap.forEach( ( datasource, records ) -> {
			Map<String, List<EntityRecord>> byName = new java.util.LinkedHashMap<>();
			for ( EntityRecord record : records ) {
				byName.computeIfAbsent( record.getEntityName().toLowerCase(), k -> new ArrayList<>() ).add( record );
			}
			byName.values().stream().filter( list -> list.size() > 1 ).forEach( list -> problems.add( String.format(
			    "%d entities are named [%s] on datasource [%s]: %s. Give each a unique entityName.", list.size(),
			    list.get( 0 ).getEntityName(), datasource.getName(),
			    list.stream().map( EntityRecord::getClassFQN ).collect( Collectors.joining( ", " ) ) ) ) );
		} );
		for ( EntityRecord record : getEntityRecords() ) {
			if ( record.getEntityMeta() == null ) {
				continue;
			}
			for ( var prop : record.getEntityMeta().getAllPersistentProperties() ) {
				String ormType = prop.getORMType();
				if ( ormType == null || ormType.isBlank() ) {
					continue;
				}
				String normalized = ortus.boxlang.modules.orm.mapping.MappingXMLWriter.toHibernateType( ormType );
				if ( !KNOWN_ORM_TYPES.contains( normalized ) && !ormType.contains( "." ) ) {
					String sentence = String.format( "%s.%s has ormtype=\"%s\", which is not a known type.%s", record.getEntityName(),
					    prop.getName(), ormType, ortus.boxlang.modules.orm.errors.ORMErrors.suggestion( normalized, KNOWN_ORM_TYPES ) );
					this.unknownOrmTypes.add( sentence );
					logger.warn( "ORM: {}", sentence );
				}
			}
		}
		if ( !problems.isEmpty() ) {
			throw new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.CONFIG,
			    "The ORM could not start: " + String.join( " ", problems ), "Each entity name must be unique per datasource." );
		}
	}

	/** Metadata structs for {@code entityGetMetadata()}, built once per entity and kept for this application's life. */
	private final Map<String, IStruct> metadataCache = new ConcurrentHashMap<>();

	/**
	 * The metadata struct of an entity (see {@code entityGetMetadata()}). Built once per entity and cached for the life of
	 * this ORM application (an {@code ormReload()} creates a new application and so a fresh cache). Each call returns a
	 * deep copy, so callers may change it freely.
	 *
	 * @param entityName The entity name (any casing).
	 *
	 * @return A copy of the entity's metadata struct.
	 *
	 * @throws ortus.boxlang.modules.orm.errors.ORMException ({@code orm.entity.notFound}) for an unknown entity.
	 */
	public IStruct getEntityMetadata( String entityName ) {
		EntityRecord	record	= lookupEntity( entityName, true );
		IStruct			cached	= this.metadataCache.computeIfAbsent( record.getEntityName().toLowerCase(),
		    key -> EntityInspector.buildMetadata( this, record ) );
		return ortus.boxlang.runtime.util.DuplicationUtil.duplicateStruct( cached, true );
	}

	/**
	 * Unknown ormtype warnings found at startup (see {@link #validateEntities()}).
	 *
	 * @return One sentence per unknown ormtype; empty when all ormtypes are known.
	 */
	public List<String> getOrmTypeWarnings() {
		return List.copyOf( this.unknownOrmTypes );
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
	 * Lookup the BoxLang EntityRecord object containing known entity information for a given entity name. The default
	 * datasource is searched first, then every other datasource; datasources without entities are skipped.
	 *
	 * @param entityName The entity name to look up (case-insensitive).
	 * @param fail       Whether to throw an exception if the entity is not found.
	 *
	 * @return The entity record, or null when not found and {@code fail} is false.
	 *
	 * @throws ortus.boxlang.modules.orm.errors.ORMException ({@code orm.entity.notFound}, with a suggestion) when not found
	 *                                                       and {@code fail} is true.
	 */
	public EntityRecord lookupEntity( String entityName, Boolean fail ) {
		// The default datasource first, then the others. A datasource with no entities (including a default datasource when
		// every entity names another datasource) is simply skipped.
		java.util.function.Function<Key, java.util.Optional<EntityRecord>>	find				= datasourceName -> this.entityMap
		    .getOrDefault( datasourceName, List.of() )
		    .stream()
		    .filter( ( entity ) -> entity.getEntityName().equalsIgnoreCase( entityName ) )
		    .findFirst();

		var																	entityFromDefault	= this.defaultDataSource == null
		    ? java.util.Optional.<EntityRecord>empty()
		    : find.apply( this.defaultDataSource );
		if ( entityFromDefault.isPresent() ) {
			return entityFromDefault.get();
		}
		for ( Key datasourceName : this.entityMap.keySet() ) {
			var entityFromDatasource = find.apply( datasourceName );
			if ( entityFromDatasource.isPresent() ) {
				return entityFromDatasource.get();
			}
		}
		if ( fail ) {
			throw ortus.boxlang.modules.orm.errors.ORMErrors.entityNotFound( entityName, getEntityNames(), null );
		}
		return null;
	}

	/**
	 * Every entity name in this ORM application, sorted case-insensitively.
	 *
	 * @return The entity names.
	 */
	public List<String> getEntityNames() {
		return getEntityRecords().stream().map( EntityRecord::getEntityName ).sorted( String.CASE_INSENSITIVE_ORDER ).toList();
	}

	/**
	 * Every property name of an entity (ids, version, columns and associations). Used for "did you mean" suggestions.
	 *
	 * @param entityName The entity name.
	 *
	 * @return The property names, or an empty list when the entity or its metadata is unknown.
	 */
	public List<String> getPropertyNames( String entityName ) {
		EntityRecord record = lookupEntity( entityName, false );
		if ( record == null || record.getEntityMeta() == null ) {
			return List.of();
		}
		var				meta	= record.getEntityMeta();
		List<String>	names	= new ArrayList<>();
		meta.getIdProperties().forEach( p -> names.add( p.getName() ) );
		if ( meta.getVersionProperty() != null ) {
			names.add( meta.getVersionProperty().getName() );
		}
		meta.getProperties().forEach( p -> names.add( p.getName() ) );
		meta.getAssociations().forEach( p -> names.add( p.getName() ) );
		return names.stream().distinct().toList();
	}

	/**
	 * An error context for {@link ortus.boxlang.modules.orm.errors.ORMErrors#translate} that knows this application's
	 * entity and property names (for "did you mean" suggestions).
	 *
	 * @param operation The BIF or operation name.
	 *
	 * @return The error context.
	 */
	public ortus.boxlang.modules.orm.errors.ORMErrors.Context errorContext( String operation ) {
		return ortus.boxlang.modules.orm.errors.ORMErrors.Context.of( operation ).withNames( getEntityNames(), this::getPropertyNames );
	}

	/**
	 * Load an entity by its primary key.
	 *
	 * @param context    Boxlang JDBC context
	 * @param entityName The name of the entity to load
	 * @param keyValue   The primary key value to load the entity by. This can be a single value such as a string or integer, or a struct for composite
	 *                   keys.
	 *
	 * @return The entity, or null when no row has that id.
	 */
	public IClassRunnable loadEntityById( IBoxContext context, String entityName, Object keyValue ) {
		return loadEntityById( context, entityName, keyValue, null );
	}

	/**
	 * Load an entity by its primary key, optionally locked or read-only.
	 *
	 * @param context    Boxlang JDBC context
	 * @param entityName The name of the entity to load
	 * @param keyValue   The primary key value, or a struct for composite keys.
	 * @param options    Load options, may be null: {@code lock} (read, write or force), {@code timeout} (lock wait in seconds),
	 *                   {@code skipLocked}, {@code readOnly}.
	 *
	 * @return The entity, or null when no row has that id.
	 */
	public IClassRunnable loadEntityById( IBoxContext context, String entityName, Object keyValue, IStruct options ) {
		EntityRecord							entityRecord	= this.lookupEntity( entityName, true );
		Session									session			= ORMContext.getForContext( context ).getSession( entityRecord.getDatasource() );
		Object									id				= toIdentifier( context, session, entityRecord, keyValue );
		String									hbName			= hibernateEntityName( session, entityRecord.getEntityName() );

		List<jakarta.persistence.FindOption>	findOptions		= new ArrayList<>();
		if ( options != null && options.get( ORMKeys.lock ) != null && !options.get( ORMKeys.lock ).toString().isBlank() ) {
			org.hibernate.LockMode mode = EntityLocking.mode( options.get( ORMKeys.lock ), "entityLoadByPK" );
			EntityLocking.requireTransaction( ORMContext.getForContext( context ), "entityLoadByPK" );
			EntityLocking.checkForce( mode, getEntityPersister( session, entityRecord.getEntityName() ), entityRecord.getEntityName(),
			    "entityLoadByPK" );
			findOptions.addAll( EntityLocking.findOptions( mode, options ) );
		}
		if ( options != null && BooleanCaster.cast( options.getOrDefault( ORMKeys.readOnly, false ) ) ) {
			findOptions.add( org.hibernate.ReadOnlyMode.READ_ONLY );
		}
		var entity = findOptions.isEmpty()
		    ? session.get( hbName, id )
		    : session.find( hbName, id, findOptions.toArray( new jakarta.persistence.FindOption[ 0 ] ) );
		if ( entity instanceof BoxProxy castProxy ) {
			return castProxy.getRunnable();
		} else {
			// Hibernate returns a POJO facade; unwrap it to the BoxLang instance.
			return ( IClassRunnable ) ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( entity );
		}
	}

	/**
	 * A reference to an entity by id, without loading it. The reference is a lazy proxy: nothing is read from the database
	 * until one of its properties or methods is used, which then loads the row (and fails with {@code orm.notFound}-style
	 * Hibernate errors if it does not exist).
	 *
	 * @param context    Boxlang JDBC context
	 * @param entityName The name of the entity.
	 * @param keyValue   The primary key value, or a struct for composite keys.
	 *
	 * @return The reference (an uninitialized proxy, or the managed entity when the session already holds it).
	 */
	public IClassRunnable getEntityReference( IBoxContext context, String entityName, Object keyValue ) {
		EntityRecord	entityRecord	= this.lookupEntity( entityName, true );
		Session			session			= ORMContext.getForContext( context ).getSession( entityRecord.getDatasource() );
		Object			id				= toIdentifier( context, session, entityRecord, keyValue );
		Object			reference		= session.getReference( hibernateEntityName( session, entityRecord.getEntityName() ), id );
		if ( reference instanceof IClassRunnable runnable ) {
			// An uninitialized BoxProxy: return it as is so nothing loads.
			return runnable;
		}
		return ( IClassRunnable ) ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( reference );
	}

	/**
	 * Load at most one entity by id or by a filter struct.
	 *
	 * @param context    Boxlang JDBC context
	 * @param entityName The name of the entity.
	 * @param idOrFilter A primary key value, or a struct of property values to match.
	 * @param operation  The BIF name, for the error when a filter matches several rows.
	 *
	 * @return The entity, or null when none matched.
	 *
	 * @throws ortus.boxlang.modules.orm.errors.ORMException {@code orm.query.nonUnique} when a filter matches several rows.
	 */
	public IClassRunnable loadOne( IBoxContext context, String entityName, Object idOrFilter, String operation ) {
		if ( idOrFilter instanceof IStruct filter && !isCompositeId( entityName, filter ) ) {
			Array results = loadEntitiesByFilter( context, entityName, filter, Struct.of( ORMKeys.maxResults, 2 ) );
			if ( results.size() > 1 ) {
				throw ortus.boxlang.modules.orm.errors.ORMErrors.nonUniqueResult( 0, operation, null );
			}
			return results.isEmpty() ? null : ( IClassRunnable ) results.getFirst();
		}
		return loadEntityById( context, entityName, idOrFilter );
	}

	/**
	 * Whether a struct is exactly an entity's composite key (every key property and nothing else), so it is loaded by id
	 * rather than used as a filter.
	 *
	 * @param entityName The entity name.
	 * @param value      The struct.
	 *
	 * @return True for a composite key struct.
	 */
	private boolean isCompositeId( String entityName, IStruct value ) {
		EntityRecord record = this.lookupEntity( entityName, true );
		if ( record.getEntityMeta() == null || record.getEntityMeta().getIdProperties().size() < 2
		    || record.getEntityMeta().getIdProperties().size() != value.size() ) {
			return false;
		}
		return record.getEntityMeta().getIdProperties().stream().allMatch( p -> value.containsKey( Key.of( p.getName() ) ) );
	}

	/**
	 * Convert a BoxLang id value to the identifier Hibernate expects for an entity: the key's Java type for a simple key, a
	 * map or an id facade for a composite key.
	 *
	 * @param context      Boxlang context, for casting.
	 * @param session      The entity's session.
	 * @param entityRecord The entity.
	 * @param keyValue     The id value, or a struct of key property values for a composite key.
	 *
	 * @return The Hibernate identifier.
	 */
	private Object toIdentifier( IBoxContext context, Session session, EntityRecord entityRecord, Object keyValue ) {
		String		entityName		= entityRecord.getEntityName();
		Class<?>	keyClass		= getKeyJavaType( session, entityName );
		Object		id;

		boolean		isCompositeKey	= entityRecord.getEntityMeta() != null && entityRecord.getEntityMeta().getIdProperties().size() > 1;

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
			try {
				id = GenericCaster.cast( context, keyValue, keyClass.getSimpleName() );
			} catch ( ortus.boxlang.runtime.types.exceptions.BoxCastException e ) {
				IStruct info = new ortus.boxlang.runtime.types.Struct();
				info.put( Key.of( "entityName" ), entityRecord.getEntityName() );
				if ( keyValue != null ) {
					info.put( Key.of( "id" ), keyValue );
				}
				String given = keyValue instanceof String str && str.isEmpty() ? "an empty string" : "[" + keyValue + "]";
				throw new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.ARGUMENT,
				    String.format( "%s is not a valid id for %s, whose id is of type %s.", given, entityRecord.getEntityName(),
				        keyClass.getSimpleName() ),
				    "Pass the entity's primary key value.", info, e );
			}
		}
		return id;
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
		EntityRecord	entityRecord	= this.lookupEntity( entityName, true );
		ORMContext		ormContext		= ORMContext.getForContext( context );
		Session			session			= ormContext.getSession( entityRecord.getDatasource() );
		// Read-your-writes: flush pending ORM writes when inside a BoxLang transaction so this query sees them.
		ormContext.flushForQuery( session );
		StringBuilder		hql			= new StringBuilder( "select e from " ).append( entityRecord.getEntityName() ).append( " e" );
		Map<String, Object>	params		= new HashMap<>();
		Array				properties	= entityRecord.getEntityMeta().getPropertyNamesArray();

		if ( filter != null && !filter.isEmpty() ) {

			// Ensure that all filter keys are valid properties of the entity or its parent
			filter.keySet()
			    .stream()
			    .filter( key -> !properties.contains( key ) )
			    .findFirst()
			    .ifPresent( key -> {
				    throw ortus.boxlang.modules.orm.errors.ORMErrors.propertyNotFound( entityRecord.getEntityName(), key.getName(),
				        getPropertyNames( entityRecord.getEntityName() ), "entityLoad" );
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
					throw ortus.boxlang.modules.orm.errors.ORMErrors.propertyNotFound( entityRecord.getEntityName(),
					    order.getAsString( ORMKeys.property ), getPropertyNames( entityRecord.getEntityName() ), "entityLoad (sort order)" );
				}
				String	orderProp	= KeyCaster.cast( properties.get( orderPropIndex ) ).getName();
				// ignorecase: sort text properties case-insensitively. Non-text properties are sorted as-is (lower() on a
				// number or date is an error on some databases).
				boolean	ignoreCase	= BooleanCaster.cast( options.getOrDefault( Key.of( "ignorecase" ), false ) )
				    && isTextProperty( entityRecord, orderProp );
				String	sortExpr	= ignoreCase ? "lower(e." + orderProp + ")" : "e." + orderProp;
				orderClauses.add( sortExpr + ( order.getAsBoolean( ORMKeys.ascending ) ? " asc" : " desc" ) );
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
		params.forEach( ( name, value ) -> {
			try {
				query.setParameter(
				    name,
				    entityParams.containsKey( name ) ? resolveEntityReference( session, entityParams.get( name ), value ) : value );
			} catch ( org.hibernate.query.QueryArgumentException e ) {
				throw filterValueError( entityRecord.getEntityName(), filterPropertyFor( filter, properties, name ), value, e );
			}
		} );

		return Array.of(
		    executeFilterQuery( query, options )
		        .stream()
		        // Hibernate returns POJO facades; unwrap each to its BoxLang instance.
		        .map( entity -> ( IClassRunnable ) ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( entity ) )
		        .toArray()
		);
	}

	/**
	 * Whether a property holds text (ormtype string, text, character or unset, which defaults to string).
	 *
	 * @param record   The entity record.
	 * @param property The property name.
	 *
	 * @return True for a text property; false for other types or an unknown property.
	 */
	public static boolean isTextProperty( EntityRecord record, String property ) {
		if ( record.getEntityMeta() == null ) {
			return false;
		}
		for ( var prop : record.getEntityMeta().getAllPersistentProperties() ) {
			if ( prop.getName().equalsIgnoreCase( property ) ) {
				if ( prop.isAssociationType() ) {
					return false;
				}
				String type = prop.getORMType() == null || prop.getORMType().isBlank() ? "string"
				    : ortus.boxlang.modules.orm.mapping.MappingXMLWriter.toHibernateType( prop.getORMType() );
				return type.equals( "string" ) || type.equals( "text" ) || type.equals( "character" );
			}
		}
		return false;
	}

	/**
	 * The filter property bound to a generated parameter name ({@code p0}, {@code p1}, ...). Parameter indexes count every
	 * filter key, including null values (which become {@code is null} and bind no parameter).
	 *
	 * @param filter     The entityLoad filter struct.
	 * @param properties The entity's property names (canonical casing).
	 * @param paramName  The generated parameter name.
	 *
	 * @return The property name, or the parameter name when it cannot be matched.
	 */
	private static String filterPropertyFor( IStruct filter, Array properties, String paramName ) {
		int	index	= Integer.parseInt( paramName.substring( 1 ) );
		int	i		= 0;
		for ( Key entryKey : filter.keySet() ) {
			if ( filter.get( entryKey ) != null ) {
				if ( i == index ) {
					int propertyIndex = properties.indexOf( KeyCaster.cast( entryKey ) );
					return KeyCaster.cast( properties.get( propertyIndex ) ).getName();
				}
			}
			i++;
		}
		return paramName;
	}

	/**
	 * A clear error for a filter value that cannot be used for its property's type.
	 *
	 * @param entityName The entity name.
	 * @param property   The filter property.
	 * @param value      The value that was passed.
	 * @param cause      Hibernate's argument exception (gives the expected type).
	 *
	 * @return An {@code orm.query.parameter} error to throw.
	 */
	private static ortus.boxlang.modules.orm.errors.ORMException filterValueError( String entityName, String property, Object value,
	    org.hibernate.query.QueryArgumentException cause ) {
		IStruct info = new ortus.boxlang.runtime.types.Struct();
		info.put( Key.of( "entityName" ), entityName );
		info.put( Key.of( "property" ), property );
		if ( value != null ) {
			info.put( Key.of( "argument" ), value );
		}
		String type = cause.getParameterType() == null ? "its type" : cause.getParameterType().getSimpleName();
		if ( value instanceof String str && str.isEmpty() ) {
			return new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.QUERY_PARAMETER,
			    String.format( "The filter for %s.%s is an empty string, which cannot be used as %s.", entityName, property, type ),
			    "Pass null to match rows with no value, or leave the property out of the filter.", info, cause );
		}
		return new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.QUERY_PARAMETER,
		    String.format( "The filter value [%s] for %s.%s cannot be used as %s.", value, entityName, property, type ),
		    "Check the value you pass for this property.", info, cause );
	}

	/**
	 * Apply common query options (cacheable, timeout, maxResults, offset) and execute the query.
	 *
	 * @param query   The query to execute.
	 * @param options Struct of options, including maxResults, offset, etc.
	 */
	public List<?> executeFilterQuery( Query<?> query, IStruct options ) {
		// cacheable, cachename (the second-level cache region) and timeout, the same way ormExecuteQuery applies them.
		HQLQuery.applyCacheAndTimeout( query, options );
		if ( BooleanCaster.cast( options.getOrDefault( ORMKeys.readOnly, false ) ) ) {
			query.setReadOnly( true );
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
	 * @param entityName The entity the association targets: its BoxLang name or its Hibernate (facade class) name.
	 * @param value      The caller-supplied value: a primary key or an entity instance.
	 *
	 * @return The managed entity/reference to bind, or the original value when it cannot be resolved to one.
	 */
	public Object resolveEntityReference( Session session, String entityName, Object value ) {
		if ( value == null ) {
			return null;
		}
		// Query parameter metadata names the association target by its Hibernate entity name, which is the generated facade
		// class name. Everything below works with BoxLang entity names, so translate it back first.
		String boxName = ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.entityNameForFacade( entityName );
		if ( boxName != null ) {
			entityName = boxName;
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
		Object reference = session.get( hibernateEntityName( session, entityName ), id );
		// When the session already holds a lazy proxy for this row (e.g. read through an association), get() returns that
		// proxy, which is not assignable to the facade class a query parameter needs. Use the entity behind it.
		if ( reference instanceof org.hibernate.proxy.HibernateProxy proxy ) {
			return proxy.getHibernateLazyInitializer().getImplementation();
		}
		return reference;
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
