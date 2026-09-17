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
package ortus.boxlang.modules.orm.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.schema.Action;

import ortus.boxlang.modules.orm.config.naming.BoxLangClassNamingStrategy;
import ortus.boxlang.modules.orm.config.naming.MacroCaseNamingStrategy;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.config.segments.CacheConfig;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * ORM configuration manager, normalizer, validator, etc.
 */
public class ORMConfig {

	/**
	 * Class locator for loading boxlang classes.
	 */
	private static final ClassLocator			CLASS_LOCATOR			= BoxRuntime.getInstance().getClassLocator();

	public static final String					DEFAULT_CACHEPROVIDER	= "BoxCacheProvider";

	/**
	 * Runtime
	 */
	private static final BoxRuntime				runtime					= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger						logger;

	/**
	 * Immutable map of normalized legacy Hibernate 5 dialect aliases to their Hibernate 7 dialect class names.
	 */
	private static final Map<String, String>	DIALECT_ALIASES			= buildDialectAliases();

	/**
	 * Tracks legacy dialect aliases already warned about, so the deprecation warning is logged only once per alias.
	 */
	private static final Set<String>			WARNED_DIALECTS			= ConcurrentHashMap.newKeySet();

	/**
	 * Holds the BootstrapServiceRegistry built by {@link #toHibernateConfig()} so that
	 * {@link ortus.boxlang.modules.orm.SessionFactoryBuilder} can close it on the
	 * <em>failure</em> path (i.e., when {@code buildSessionFactory()} throws).
	 * <p>
	 * On success this registry must remain open: it is the root of the Hibernate service
	 * hierarchy and is closed transitively via {@code SessionFactory.close()}.
	 * Closing it early destroys the registry chain and causes {@code UnknownServiceException}
	 * on the next {@code openSession()} call.
	 */
	private BootstrapServiceRegistry			bootstrapRegistry;

	/**
	 * Specifies whether ColdFusion should automatically generate entity mappings
	 * for the persistent CFCs. If generateMappings=false, the mapping should be
	 * provided in the form of <code>hbm.xml</code> files stored ALONGSIDE the persistent CFCs. If true, the ORM will generate the mapping XML files on
	 * the fly based on the structure of the persistent CFCs and their properties.
	 */
	public boolean								generateMappings		= true;

	/**
	 * Backwards-compatible alias for `generateMappings`. {@link #generateMappings}
	 *
	 * @deprecated Use `generateMappings` instead of this property. This property will be removed in a future release.
	 */
	@Deprecated( since = "1.4.1", forRemoval = true )
	public boolean								autoGenMap				= true;

	/**
	 * Allows the engine to manage the Hibernate session. It is recommended not to
	 * let the engine manage it for you.
	 *
	 * Use transaction blocks in order to demarcate your regions that should start,
	 * flush and end a transaction.
	 */
	public boolean								autoManageSession		= false;

	/**
	 * Specify a string path to the secondary cache configuration file. This configuration file must be formatted to the specification of the jCache
	 * provider specified in the `cacheProvider` setting.
	 */
	public String								cacheConfigFile;

	/**
	 * A structure of properties to configure the secondary cache provider
	 */
	public IStruct								cacheConfigProperties	= CacheConfig.DEFAULTS;

	/**
	 * Specify the alias name OR full class path of a jCache provider to use for the second-level cache. Must be one of the following:
	 * <ul>
	 * <li><code>ehcache</code> - use the EHCache jCache implementation bundled with the BoxLang ORM module</li>
	 * <li><code>com.foo.MyJCacheProvider</code> - String path to a custom jCache provider loaded into your BoxLang application.</li>
	 * </ul>
	 */
	public String								cacheProvider			= DEFAULT_CACHEPROVIDER;

	/**
	 * Specifies the directory (or array of directories) that should be used to
	 * search for persistent CFCs to generate the mapping.
	 * <p>
	 * Always specify it or pay a performance startup price.
	 * <p>
	 * <strong>Important:</strong> If it is not set, the extension looks at the
	 * application directory, its sub-directories, and its mapped directories to
	 * search for persistent CFCs.
	 * <p>
	 * Aliased as `cfclocation` for Adobe and Lucee CFML compatibility.
	 */
	public String[]								entityPaths;

	/**
	 * Define the data source to be utilized by the ORM. If not used,
	 * defaults to the this.datasource in the Application.cfc.
	 */
	public Key									datasource;

	/**
	 * <ul>
	 * <li><code>update</code> : Creates the database according to your ORM model.
	 * It only does incremental updates. It will never remove tables, indexes,
	 * etc.</li>
	 * <li><code>dropcreate</code> : Same as above but it destroys the database if
	 * it has ny content and recreates it every time the ORM is reloaded.</li>
	 * <li><code>none</code> : Does not change the database schema at all.</li>
	 * <li><code>create</code> : Create the database schema, but do not drop if it
	 * already exists. <strong>**New for BoxLang.</strong></li>
	 * <li><code>dropcreatedrop</code> : Drop and recreate database schema on
	 * startup, then drop it on shutdown. <strong>**New for BoxLang.</strong></li>
	 * <li><code>validate</code> : Validate the schema on startup. <strong>**New for
	 * BoxLang.</strong></li>
	 * <li><code>truncate</code> : Truncate tables on startup. <strong>**New for
	 * BoxLang.</strong></li>
	 * </ul>
	 */
	public String								dbcreate;

	/**
	 * The dialect to use for your database. By default Hibernate will introspect
	 * the datasource and try to figure it out. See the dialects section below.
	 *
	 * You can also use the fully Java qualified name of the class.
	 */
	public String								dialect;

	/**
	 * If true, then it enables the ORM event callbacks in entities and globally via
	 * the `eventHandler` property.
	 */
	public boolean								eventHandling;

	/**
	 * The CFC path of the CFC that will manage the global ORM events.
	 */
	public String								eventHandler;

	/**
	 * Specifies if an orm flush should be called automatically at the end of a
	 * request. In our opinion this SHOULD never be true. Database persistence
	 * should be done via transaction tags and good transaction demarcation.
	 */
	public boolean								flushAtRequestEnd		= false;

	/**
	 * Specifies if the SQL queries should be logged to the console.
	 */
	public boolean								logSQL					= false;

	/**
	 * Defines the naming convention to use on table and column names.
	 *
	 * - default : Uses the table or column names as is
	 * - smart : This strategy changes the logical table or column name to
	 * uppercase.
	 * - CFC PATH : Use your own CFC to determine naming. Must implement `orm.models.INamingStrategy`
	 */
	public String								namingStrategy;

	/**
	 * The path to a custom Hibernate <code>hibernate.properties</code> file. Every key/value pair in the file is applied to the Hibernate
	 * {@link Configuration} via {@code setProperty()}, giving application developers a way to set arbitrary Hibernate settings (e.g.
	 * {@code hibernate.connection.release_mode}) without bx-orm having to special-case each one.
	 * <p>
	 * <strong>Note:</strong> only the flat {@code key=value} properties file format is supported. The {@code hibernate.cfg.xml} format is not yet
	 * implemented.
	 * <p>
	 * Applied before {@link #hibernateProperties}, so a matching key in {@link #hibernateProperties} takes precedence.
	 * <p>
	 * A handful of settings that are load-bearing for bx-orm's own correctness (the connection provider, classloaders, session context class,
	 * identifier quoting, and entity mode) are re-applied by {@code SessionFactoryBuilder} <em>after</em> {@link #toHibernateConfig()} runs, so they
	 * cannot be overridden via this file.
	 */
	public String								ormConfig;

	/**
	 * A flat struct of raw Hibernate property name/value pairs (e.g. <code>{ "hibernate.connection.release_mode" : "on_close" }</code>), applied to
	 * the Hibernate {@link Configuration} via {@code setProperty()} for every entry.
	 * <p>
	 * This is the simplest way for application developers to tune arbitrary Hibernate settings without a custom Hibernate config file. Applied last
	 * in {@link #toHibernateConfig()}, so it takes precedence over both bx-orm's own hardcoded defaults and any settings loaded from
	 * {@link #ormConfig}.
	 * <p>
	 * A handful of settings that are load-bearing for bx-orm's own correctness (the connection provider, classloaders, session context class,
	 * identifier quoting, and entity mode) are re-applied by {@code SessionFactoryBuilder} <em>after</em> {@link #toHibernateConfig()} runs, so they
	 * cannot be overridden via this struct.
	 */
	public IStruct								hibernateProperties;

	/**
	 * If enabled, the ORM will create the Hibernate mapping XML (*.hbmxml) files
	 * alongside the entities. This is great for debugging your entities and
	 * relationships.
	 *
	 */
	public boolean								saveMapping				= false;

	/**
	 * The default database schema to use for database connections. This can be
	 * overriden at the datasource level, as well as on each entity.
	 */
	public String								schema;

	/**
	 * Specifies the default Database Catalog that ORM should use. This can be
	 * overriden at the datasource level, as well as on each entity.
	 */
	public String								catalog;

	/**
	 * Enable or disable the secondary cache.
	 */
	public boolean								secondaryCacheEnabled	= false;

	/**
	 * If true, then the ORM startup will ignore CFCs that have compile time errors
	 * in them.
	 * If `false`, exceptions will be thrown during the ORM startup for any class that could not be converted to a mapping.
	 * <p>
	 * Aliased as `skipCFCWithError` for Adobe and Lucee CFML compatibility.
	 */
	public boolean								ignoreParseErrors		= false;

	/**
	 * Path to a SQL script file that will be executed after the ORM is initialized.
	 * Only used if dbcreate is set to <code>dropcreate</code>.
	 */
	public String								sqlScript;

	/**
	 * Specifies whether the database has to be inspected to identify the missing
	 * information required to generate the Hibernate mapping.
	 *
	 * The database is inspected to get the column data type, primary key and
	 * foreign key information.
	 */
	public boolean								useDBForMapping			= false;

	/**
	 * Whether to quote identifiers. If turned off column and table names with reserved words will fail to be created/updated
	 */
	public boolean								quoteIdentifiers		= false;

	/**
	 * Enable or disable the use of threading for mapping multiple ORM entities concurrently.
	 */
	public boolean								enableThreadedMapping	= true;

	/**
	 * Default batch size for hibernate fetching
	 */
	public static int							defaultBatchSize		= 16;

	/**
	 * Whether to use proxy-based lazy loading for entities.
	 */
	public boolean								proxyLazyLoading		= false;

	/**
	 * Whether to emit Hibernate 7's modern {@code mapping.xml} format (root {@code <entity-mappings>}, namespace
	 * {@code http://www.hibernate.org/xsd/orm/mapping}, version {@code 7.0}) instead of the legacy {@code hbm.xml} DTD format.
	 * <p>
	 * This is Hibernate-8 readiness: {@code hbm.xml} is deprecated-for-removal. Defaults to {@code false} so production stays on the HBM writer until a
	 * later release flips the default. The BoxLang-facing behavior of the ORM is identical either way.
	 */
	public boolean								ormXmlMapping			= false;

	/**
	 * Whether to map BoxLang entities to Hibernate as real per-entity POJO "facade" classes (see the
	 * {@code ortus.boxlang.modules.orm.hibernate.facade} package) instead of class-less dynamic MAP entities.
	 * <p>
	 * A facade is a generated real Java class whose accessors delegate their state to the BoxLang instance, so Hibernate
	 * sees a real class and a real id member (which unlocks {@code uuid} and other id generation strategies unavailable
	 * to MAP entities), while BoxLang developers still only ever handle the BoxLang class. Defaults to {@code false}: with
	 * the flag off the representation is byte-for-byte the original MAP behavior.
	 */
	public boolean								entityFacades			= false;

	/**
	 * The instantiated naming strategy object.
	 */
	private PhysicalNamingStrategy				instantiatedNamingStrategy;

	/**
	 * Constructor
	 *
	 * @param properties Struct of ORM configuration properties.
	 */
	public ORMConfig( IStruct properties, IBoxContext context ) {
		this.logger = runtime.getLoggingService().getLogger( "orm" );

		if ( properties == null ) {
			properties = new Struct();
		}
		final IStruct		finalProperties		= properties;
		InterceptorService	interceptorService	= runtime.getInterceptorService();

		if ( interceptorService.hasState( ORMKeys.EVENT_ORM_PRE_CONFIG_LOAD ) ) {
			interceptorService.announce(
			    ORMKeys.EVENT_ORM_PRE_CONFIG_LOAD,
			    () -> Struct.of(
			        Key.properties, finalProperties,
			        Key.context, context
			    )
			);
		}

		process( properties, context );

		if ( interceptorService.hasState( ORMKeys.EVENT_ORM_POST_CONFIG_LOAD ) ) {
			interceptorService.announce(
			    ORMKeys.EVENT_ORM_POST_CONFIG_LOAD,
			    () -> Struct.of(
			        Key.properties, finalProperties,
			        Key.context, context
			    )
			);
		}
	}

	/**
	 * Construct an ORMConfig object from the application settings.
	 *
	 * @param context The IBoxContext object for the current request.
	 *
	 * @return ORMConfig object or null if ORM is not enabled or no ORM settings are present in the application settings.
	 */
	public static ORMConfig loadFromContext( IBoxContext context ) {
		RequestBoxContext requestContext = context.getRequestContext();
		if ( requestContext == null || !isORMEnabled( context ) ) {
			return null;
		}

		IStruct appSettings = ( IStruct ) requestContext.getConfigItem( Key.applicationSettings );
		return new ORMConfig( appSettings.getAsStruct( ORMKeys.ORMSettings ), context );
	}

	/**
	 * Check whether ORM is enabled for the given context.
	 *
	 * Enabled means:
	 * 1. There's a RequestBoxContext available
	 * 2. `ORMEnabled` is true
	 * 3. `ORMSettings` is not empty
	 *
	 * @param context The IBoxContext to check for ORM enablement.
	 */
	public static boolean isORMEnabled( IBoxContext context ) {
		if ( context == null ) {
			return false;
		}
		RequestBoxContext requestContext = context.getRequestContext();
		if ( requestContext == null ) {
			return false;
		}
		IStruct appSettings = ( IStruct ) requestContext.getConfigItem( Key.applicationSettings );

		return appSettings.containsKey( ORMKeys.ORMEnabled )
		    && appSettings.containsKey( ORMKeys.ORMSettings )
		    && BooleanCaster.cast( appSettings.getOrDefault( ORMKeys.ORMEnabled, false ) )
		    && appSettings.get( ORMKeys.ORMSettings ) != null;
	}

	/**
	 * Process the ORM configuration properties and set the private config fields
	 * accordingly.
	 *
	 * @param properties Struct of ORM configuration properties.
	 * @param context    The IBoxContext object for the current request.
	 */
	private void process( IStruct properties, IBoxContext context ) {
		if ( properties == null ) {
			return;
		}

		/**
		 * Boolean properties: Check key existence, check for null, then cast to
		 * boolean.
		 */
		if ( properties.containsKey( ORMKeys.autoGenMap ) && properties.get( ORMKeys.autoGenMap ) != null ) {
			// Backwards-compat alias for generateMappings
			generateMappings	= BooleanCaster.cast( properties.get( ORMKeys.autoGenMap ) );
			autoGenMap			= generateMappings;
		}
		if ( properties.containsKey( ORMKeys.generateMappings ) && properties.get( ORMKeys.generateMappings ) != null ) {
			// note that generateMappings takes precedence over autoGenMap if both are specified
			generateMappings	= BooleanCaster.cast( properties.get( ORMKeys.generateMappings ) );
			autoGenMap			= generateMappings;
		}
		if ( properties.containsKey( ORMKeys.autoManageSession ) && properties.get( ORMKeys.autoManageSession ) != null ) {
			autoManageSession = BooleanCaster.cast( properties.get( ORMKeys.autoManageSession ) );
		}
		if ( properties.containsKey( ORMKeys.eventHandling ) && properties.get( ORMKeys.eventHandling ) != null ) {
			eventHandling = BooleanCaster.cast( properties.get( ORMKeys.eventHandling ) );
		}
		if ( properties.containsKey( ORMKeys.quoteIdentifiers ) ) {
			quoteIdentifiers = BooleanCaster.cast( properties.getOrDefault( ORMKeys.quoteIdentifiers, false ) );
		}
		if ( properties.containsKey( ORMKeys.flushAtRequestEnd ) && properties.get( ORMKeys.flushAtRequestEnd ) != null ) {
			flushAtRequestEnd = BooleanCaster.cast( properties.get( ORMKeys.flushAtRequestEnd ) );
		}
		if ( properties.containsKey( ORMKeys.logSQL ) && properties.get( ORMKeys.logSQL ) != null ) {
			logSQL = BooleanCaster.cast( properties.get( ORMKeys.logSQL ) );
		}
		if ( properties.containsKey( ORMKeys.secondaryCacheEnabled ) && properties.get( ORMKeys.secondaryCacheEnabled ) != null ) {
			secondaryCacheEnabled = BooleanCaster.cast( properties.get( ORMKeys.secondaryCacheEnabled ) );
		}
		if ( properties.containsKey( ORMKeys.ignoreParseErrors ) && properties.get( ORMKeys.ignoreParseErrors ) != null ) {
			ignoreParseErrors = BooleanCaster.cast( properties.get( ORMKeys.ignoreParseErrors ) );
		}
		if ( properties.containsKey( ORMKeys.enableThreadedMapping ) && properties.get( ORMKeys.enableThreadedMapping ) != null ) {
			enableThreadedMapping = BooleanCaster.cast( properties.get( ORMKeys.enableThreadedMapping ) );
		}

		// String properties: Check key existence, check for null, and check for empty
		// or blank (whitespace-only) strings
		if ( properties.containsKey( ORMKeys.cacheConfig ) && properties.get( ORMKeys.cacheConfig ) != null
		    && properties.get( ORMKeys.cacheConfig ) instanceof String ) {
			cacheConfigFile = properties.getAsString( ORMKeys.cacheConfig );
		} else if ( properties.containsKey( ORMKeys.cacheConfig ) && properties.get( ORMKeys.cacheConfig ) != null
		    && properties.get( ORMKeys.cacheConfig ) instanceof IStruct configStruct ) {
			cacheConfigProperties = configStruct;
		}

		if ( properties.containsKey( ORMKeys.cacheProvider ) && properties.get( ORMKeys.cacheProvider ) != null
		    && !properties.getAsString( ORMKeys.cacheProvider ).isBlank() ) {
			cacheProvider = properties.getAsString( ORMKeys.cacheProvider );
		}

		if ( properties.containsKey( ORMKeys.entityPaths ) && properties.get( ORMKeys.entityPaths ) != null ) {
			setEntityPaths( properties.get( ORMKeys.entityPaths ) );
		} else {
			setEntityPaths( null );
		}

		if ( properties.containsKey( ORMKeys.datasource ) && properties.get( ORMKeys.datasource ) != null ) {
			Object datasourceProperty = properties.get( ORMKeys.datasource );
			if ( datasourceProperty instanceof String datasourceName ) {
				datasource = Key.of( datasourceName );
			} else if ( datasourceProperty instanceof IStruct datasourceStruct ) {
				// @TODO: Implement this!
				// datasourceStruct = datasourceStruct;
			}
		}
		if ( datasource == null || datasource.equals( Key.EMPTY ) ) {
			datasource = getAppDefaultDatasource( context );
		}

		if ( properties.containsKey( ORMKeys.dbcreate ) && properties.get( ORMKeys.dbcreate ) != null
		    && !properties.getAsString( ORMKeys.dbcreate ).isBlank() ) {
			dbcreate = properties.getAsString( ORMKeys.dbcreate );
		}

		if ( properties.containsKey( ORMKeys.dialect ) && properties.get( ORMKeys.dialect ) != null
		    && !properties.getAsString( ORMKeys.dialect ).isBlank() ) {
			// @TODO: Enable this warning IF and WHEN we migrate to Hibernate 6+.
			// logger.warn(
			// "Setting 'dialect' in Hibernate 6.0+ is unnecessary on all Hibernate-supported databases. Ignoring 'dialect' configuration for now." );
			dialect = properties.getAsString( ORMKeys.dialect );
		}

		if ( properties.containsKey( ORMKeys.eventHandler ) && properties.get( ORMKeys.eventHandler ) != null
		    && !properties.getAsString( ORMKeys.eventHandler ).isBlank() ) {
			eventHandler = properties.getAsString( ORMKeys.eventHandler );
		}
		if ( properties.containsKey( ORMKeys.namingStrategy ) && properties.get( ORMKeys.namingStrategy ) != null
		    && !properties.getAsString( ORMKeys.namingStrategy ).isBlank() ) {
			namingStrategy = properties.getAsString( ORMKeys.namingStrategy );
		}

		if ( properties.containsKey( ORMKeys.ormConfig ) && properties.get( ORMKeys.ormConfig ) != null
		    && !properties.getAsString( ORMKeys.ormConfig ).isBlank() ) {
			ormConfig = properties.getAsString( ORMKeys.ormConfig );
		}

		if ( properties.containsKey( ORMKeys.hibernateProperties )
		    && properties.get( ORMKeys.hibernateProperties ) instanceof IStruct hibernatePropertiesStruct ) {
			hibernateProperties = hibernatePropertiesStruct;
		}

		if ( properties.containsKey( ORMKeys.saveMapping ) && properties.get( ORMKeys.saveMapping ) != null ) {
			saveMapping = BooleanCaster.cast( properties.get( ORMKeys.saveMapping ) );
		}

		if ( properties.containsKey( ORMKeys.schema ) && properties.get( ORMKeys.schema ) != null
		    && !properties.getAsString( ORMKeys.schema ).isBlank() ) {
			schema = properties.getAsString( ORMKeys.schema );
		}

		if ( properties.containsKey( ORMKeys.catalog ) && properties.get( ORMKeys.catalog ) != null
		    && !properties.getAsString( ORMKeys.catalog ).isBlank() ) {
			catalog = properties.getAsString( ORMKeys.catalog );
		}

		if ( properties.containsKey( ORMKeys.defaultBatchSize ) && properties.get( ORMKeys.defaultBatchSize ) != null ) {
			defaultBatchSize = IntegerCaster.cast( properties.get( ORMKeys.defaultBatchSize ) );
		}

		if ( properties.containsKey( ORMKeys.proxyLazyLoading ) && properties.get( ORMKeys.proxyLazyLoading ) != null ) {
			proxyLazyLoading = BooleanCaster.cast( properties.get( ORMKeys.proxyLazyLoading ) );
		}

		if ( properties.containsKey( ORMKeys.ormXmlMapping ) && properties.get( ORMKeys.ormXmlMapping ) != null ) {
			ormXmlMapping = BooleanCaster.cast( properties.get( ORMKeys.ormXmlMapping ) );
		}

		if ( properties.containsKey( ORMKeys.entityFacades ) && properties.get( ORMKeys.entityFacades ) != null ) {
			entityFacades = BooleanCaster.cast( properties.get( ORMKeys.entityFacades ) );
		}

		if ( this.namingStrategy != null ) {
			this.instantiatedNamingStrategy = getNamingStrategyForName( this.namingStrategy );
		}
	}

	/**
	 * Read the default datasource name from application settings.
	 *
	 * @param context The IBoxContext to read the application settings from.
	 *
	 * @throws BoxRuntimeException if a default datasource is not found in the application settings, or if the default datasource specified is not found
	 *                             in the datasources struct of the application settings.
	 *
	 * @return The default datasource name specified in the application settings, or null if not found.
	 */
	private Key getAppDefaultDatasource( IBoxContext context ) {
		Key		defaultDatasource	= Key.of( ( String ) context.getConfigItems( new Key[] { Key.defaultDatasource } ) );
		IStruct	configDatasources	= ( IStruct ) context.getConfigItems( new Key[] { Key.datasources } );
		if ( !defaultDatasource.isEmpty() && configDatasources.containsKey( defaultDatasource ) ) {
			return defaultDatasource;
		} else if ( !defaultDatasource.isEmpty() ) {
			logger.warn( "The datasource [" + defaultDatasource.getName() + "] could not be found in the request configuration.  Datasources found: ["
			    + configDatasources.keySet().stream().map( Key::getName ).collect( Collectors.joining( ", " ) ) + "]" );
			return defaultDatasource;
		} else {
			throw new BoxRuntimeException( "A default datasource could not be found in the current runtime configuration. Available datasources: "
			    + configDatasources.keySet().stream().map( Key::getName ).collect( Collectors.joining( ", " ) ) );
		}
	}

	/**
	 * Encapsulates the logic for setting the `entityPaths` configuration setting based on a string, list of strings, array, or null value.
	 *
	 * @param entityPaths The value of the `entityPaths` or (deprecated) `cfcLocation` configuration setting.
	 */
	private void setEntityPaths( Object entityPaths ) {
		if ( entityPaths == null ) {
			this.entityPaths = new String[] {};
		}
		if ( entityPaths instanceof String entityPathString && !entityPathString.isBlank() ) {
			this.entityPaths = new String[] { entityPathString };
		} else if ( entityPaths instanceof Array pathArray ) {
			Object[] temp = pathArray.toArray();
			this.entityPaths = Arrays.copyOf( temp, temp.length, String[].class );
		}
	}

	/**
	 * Populate and return a Hibernate configuration object using the constructed properties in this ORMConfig object.
	 *
	 * @return Hibernate Configuration object.
	 */
	public Configuration toHibernateConfig() {
		// Load the event handler class if it is specified, else null
		DynamicObject eventHandlerClass = this.eventHandler != null
		    ? loadBoxLangClassByFQN( this.eventHandler )
		    : null;
		// Build the BootstrapServiceRegistry with the event listener integrator if an event handler class was specified. This registry will be closed by
		// SessionFactoryBuilder if session factory construction fails to prevent leaks; on success it remains open as the root of the Hibernate service
		// hierarchy and is closed transitively via SessionFactory.close().
		this.bootstrapRegistry = new BootstrapServiceRegistryBuilder()
		    .applyIntegrator( new EventListener( eventHandlerClass ) )
		    .build();
		Configuration	configuration		= new Configuration( this.bootstrapRegistry );
		var				sysEnvProps			= new Properties();
		Field[]			availableSettings	= AvailableSettings.class.getFields();
		for ( var prop : System.getProperties().entrySet() ) {
			String settingName = ( ( String ) prop.getKey() ).toUpperCase();
			if ( settingName.startsWith( "HIBERNATE_" ) ) {
				Object	value			= prop.getValue();
				Field	foundSetting	= Stream.of( availableSettings ).filter( field -> field.getName().equalsIgnoreCase( settingName ) ).findFirst()
				    .orElse( null );
				try {
					if ( foundSetting != null ) {
						sysEnvProps.put( foundSetting.get( foundSetting ), value );
					}
				} catch ( IllegalAccessException e ) {
					logger.error( "Unable to read or set setting from env var: {}", settingName );
				}
			}
			sysEnvProps.put( prop.getKey(), prop.getValue() );
		}
		configuration.addProperties( sysEnvProps );

		boolean hasExplicitDialect = this.dialect != null && !this.dialect.isBlank();

		// If no dialect is configured, Hibernate must inspect JDBC metadata to resolve it.
		configuration.setProperty( AvailableSettings.ALLOW_METADATA_ON_BOOT, hasExplicitDialect ? "disallow" : "allow" );
		if ( !hasExplicitDialect ) {
			configuration.setProperty( AvailableSettings.DIALECT_RESOLVERS, SQLiteDialectResolver.class.getName() );
		}

		if ( this.dbcreate != null ) {
			switch ( this.dbcreate ) {
				case "dropcreate" :
					this.dbcreate = "drop-and-create";
					break;
				default :
					break;
			}
			configuration.setProperty( AvailableSettings.HBM2DDL_AUTO, Action.interpretHbm2ddlSetting( dbcreate ).getExternalHbm2ddlName() );
		}

		if ( this.instantiatedNamingStrategy != null ) {
			configuration.setPhysicalNamingStrategy( this.instantiatedNamingStrategy );
		}

		// Default batch size for collections
		configuration.setProperty( AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, Integer.toString( ORMConfig.defaultBatchSize ) );

		configuration.setProperty( AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.toString( this.secondaryCacheEnabled ) );
		if ( this.secondaryCacheEnabled ) {
			configuration.setProperty( AvailableSettings.USE_QUERY_CACHE, "true" );
			configuration.setProperty( AvailableSettings.CACHE_REGION_FACTORY, "jcache" );
			configuration.setProperty( "hibernate.javax.cache.missing_cache_strategy", "create" );
			configuration.setProperty( "hibernate.javax.cache.provider", this.getJCacheProviderClassPath() );
			if ( this.cacheConfigFile != null && !this.cacheConfigFile.isEmpty() ) {
				configuration.setProperty( "hibernate.javax.cache.uri", this.cacheConfigFile );
			}
		}

		if ( this.logSQL ) {
			configuration.setProperty( AvailableSettings.SHOW_SQL, "true" );
			configuration.setProperty( AvailableSettings.FORMAT_SQL, "true" );
			configuration.setProperty( AvailableSettings.USE_SQL_COMMENTS, "true" );
			configuration.setProperty( AvailableSettings.GENERATE_STATISTICS, "true" );
			configuration.setProperty( AvailableSettings.LOG_SESSION_METRICS, "true" );
			configuration.setProperty( AvailableSettings.LOG_JDBC_WARNINGS, "true" );
		}

		if ( hasExplicitDialect ) {
			// @TODO: Once we migrate to Hibernate 6+, we should drop dialect configuration entirely.
			// https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/cfg/JdbcSettings.html#DIALECT
			// configuration.setProperty(AvailableSettings.DIALECT, dialect);

			configuration.setProperty( AvailableSettings.DIALECT, toFullHibernateDialectName( dialect ) );
		}

		if ( this.schema != null ) {
			configuration.setProperty( AvailableSettings.DEFAULT_SCHEMA, schema );
		}

		if ( this.catalog != null ) {
			configuration.setProperty( AvailableSettings.DEFAULT_CATALOG, catalog );
		}

		if ( this.sqlScript != null ) {
			if ( Action.CREATE.toString().equals( this.dbcreate ) ) {
				if ( new File( sqlScript ).exists() ) {
					// @TODO: We could possibly upgrade this to use the JPA setting:
					// `JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE`, but we'd have to test to see if that
					// script executes *after* the schema generation (correct behavior), or *in
					// place of* the schema generation (incorrect behavior).
					configuration.setProperty( AvailableSettings.HBM2DDL_IMPORT_FILES, sqlScript );
				} else {
					logger.error( "ORM Configuration `sqlScript` file not found: {}", sqlScript );
				}
			} else {
				logger.trace(
				    "ORM Configuration `sqlScript` is only valid with `dbcreate=dropcreate`. Ignoring for now." );
			}
		}

		// Session and transaction management settings:
		configuration.setProperty( AvailableSettings.FLUSH_BEFORE_COMPLETION, "false" )
		    .setProperty( AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION, "true" )
		    .setProperty( AvailableSettings.AUTO_CLOSE_SESSION, "false" );

		// Apply raw Hibernate properties from a `hibernate.properties`-formatted file (ormConfig), then from the inline `hibernateProperties`
		// struct. Both are applied last so application developers can override any of bx-orm's own defaults above, including per-datasource tuning
		// like `hibernate.connection.release_mode`. `hibernateProperties` is applied after `ormConfig` so it wins on conflicting keys.
		// Note: a handful of settings that are load-bearing for bx-orm's own correctness (connection provider, classloaders, session context class,
		// identifier quoting, entity mode) are re-applied afterward by SessionFactoryBuilder and cannot be overridden here.
		if ( this.ormConfig != null && !this.ormConfig.isBlank() ) {
			applyHibernatePropertiesFile( configuration, this.ormConfig );
		}

		if ( this.hibernateProperties != null ) {
			this.hibernateProperties.entrySet().stream()
			    .filter( entry -> entry.getValue() != null )
			    .forEach( entry -> configuration.setProperty( entry.getKey().getName(), entry.getValue().toString() ) );
		}

		return configuration;
	}

	/**
	 * Load a flat <code>hibernate.properties</code>-formatted file from the given path and apply every entry to the given Hibernate
	 * {@link Configuration} via {@code setProperty()}.
	 * <p>
	 * If the file does not exist or cannot be read, an error is logged and the configuration is left unmodified; it does not throw, matching the
	 * existing `sqlScript` behavior.
	 *
	 * @param configuration The Hibernate configuration to apply properties to.
	 * @param ormConfigPath Path to the `hibernate.properties`-formatted file.
	 */
	private void applyHibernatePropertiesFile( Configuration configuration, String ormConfigPath ) {
		File ormConfigFile = new File( ormConfigPath );
		if ( !ormConfigFile.exists() ) {
			logger.error( "ORM Configuration `ormConfig` file not found: {}", ormConfigPath );
			return;
		}

		Properties fileProperties = new Properties();
		try ( FileInputStream inputStream = new FileInputStream( ormConfigFile ) ) {
			fileProperties.load( inputStream );
		} catch ( IOException | IllegalArgumentException e ) {
			// IllegalArgumentException covers a malformed Unicode escape sequence, which Properties.load() throws
			// in addition to IOException.
			logger.error( "Unable to read ORM Configuration `ormConfig` file [{}]: {}", ormConfigPath, e.getMessage() );
			return;
		}

		fileProperties.forEach( ( key, value ) -> configuration.setProperty( ( String ) key, ( String ) value ) );
	}

	/**
	 * Get the naming strategy for the given name.
	 *
	 * @name The name of the naming strategy.
	 *
	 * @return The naming strategy for the given name.
	 */
	private PhysicalNamingStrategy getNamingStrategyForName( String name ) {
		return switch ( name.toLowerCase() ) {
			/**
			 * Historically, the "smart" naming strategy simply converts camelCase to
			 * MACRO_CASE.
			 */
			case "smart" -> new MacroCaseNamingStrategy();
			/**
			 * The "default" naming strategy is essentially a no-op, and simply returns the
			 * identifier value unmodified. Since this is the default action in Hibernate 6,
			 * we can skip returning a naming strategy.
			 */
			case "default" -> null;
			/**
			 * The "class" naming strategy allows apps to define their own naming strategy by
			 * providing a full box class path.
			 */
			default -> new BoxLangClassNamingStrategy( loadBoxLangClassByFQN( name ) );
		};
	}

	/**
	 * Load a BoxLang class by its fully-qualified name.
	 *
	 * @param fqn The fully-qualified name of the class to load.
	 *
	 * @return The loaded class.
	 */
	private DynamicObject loadBoxLangClassByFQN( String fqn ) {
		return ( DynamicObject ) RequestBoxContext.runInContext( ctx -> {
			return CLASS_LOCATOR.load(
			    ctx,
			    fqn,
			    ClassLocator.BX_PREFIX,
			    true,
			    ctx.getCurrentImports()
			).invokeConstructor( ctx );
		} );
	}

	/**
	 * Close the BootstrapServiceRegistry that was created during {@link #toHibernateConfig()}.
	 * <p>
	 * Called by {@link ortus.boxlang.modules.orm.SessionFactoryBuilder} only on the
	 * <em>failure</em> path (when {@code buildSessionFactory()} throws) to prevent
	 * classloader and integrator leaks from an orphaned registry.
	 * <p>
	 * On the success path this method must NOT be called: the registry remains live as
	 * the top of the Hibernate service hierarchy and is closed transitively when
	 * {@code SessionFactory.close()} is called.
	 */
	public void closeBootstrapRegistry() {
		if ( this.bootstrapRegistry != null ) {
			try {
				this.bootstrapRegistry.close();
			} catch ( Exception ignored ) {
				// Hibernate may have already closed it on success; ignore the error.
			} finally {
				this.bootstrapRegistry = null;
			}
		}
	}

	/**
	 * Get the `cacheProvider` setting as a path to a JCache provider.
	 */
	public String getJCacheProviderClassPath() {
		return "ortus.boxlang.modules.orm.hibernate.cache.BoxHibernateCachingProvider";
	}

	/**
	 * Get the default JCache properties for Hibernate.
	 */
	public Properties getJCacheDefaultProperties() {
		Properties properties = new Properties();
		properties.setProperty( "hibernate.cache.region_prefix", datasource.getName() + "_" );
		properties.setProperty( "hibernate.javax.cache.provider", getJCacheProviderClassPath() );
		return properties;
	}

	/**
	 * Translate a short dialect name like {@code MYSQL} to a Hibernate 7 dialect class name like
	 * {@code org.hibernate.dialect.MySQLDialect}.
	 * <p>
	 * bx-orm is the ORM abstraction: applications configured against Hibernate 5 used short aliases and version-specific dialect
	 * names (for example {@code MySQL57}, {@code Oracle10g}, {@code DerbyTenSeven}). Hibernate 7 removed the version-specific
	 * dialects in favor of a single, version-detecting dialect per database, and moved several databases to the
	 * {@code hibernate-community-dialects} artifact. To keep existing settings booting, every recognized legacy alias is mapped
	 * to its Hibernate 7 equivalent and a one-time deprecation warning is logged for version-specific aliases. Fully-qualified
	 * class names are passed through untouched, and unrecognized short names are returned as-is for Hibernate to resolve.
	 *
	 * @param dialectName Hibernate dialect name, a short alias like {@code MYSQL} or a full class name.
	 *
	 * @return The resolved Hibernate 7 dialect class name, or the original value when it is a class name or unrecognized alias.
	 */
	private String toFullHibernateDialectName( String dialectName ) {
		String raw = dialectName.trim();
		// A fully-qualified class name (contains a dot) is passed through untouched.
		if ( raw.contains( "." ) ) {
			return raw;
		}
		String	key			= raw.toUpperCase().replace( "DIALECT", "" );
		String	resolved	= DIALECT_ALIASES.get( key );
		if ( resolved == null ) {
			// Unrecognized short name: let Hibernate try to resolve it (and produce its own error if it cannot).
			return raw;
		}
		// Warn once for legacy/version-specific aliases that no longer map one-to-one in Hibernate 7.
		String simpleName = resolved.substring( resolved.lastIndexOf( '.' ) + 1 );
		if ( !simpleName.equalsIgnoreCase( raw ) && !simpleName.equalsIgnoreCase( raw + "Dialect" ) && WARNED_DIALECTS.add( key ) ) {
			logger.warn(
			    "ORM dialect [{}] is a legacy Hibernate 5 alias and was resolved to [{}] for Hibernate 7. Configure a current dialect name to silence this warning.",
			    raw, resolved );
		}
		return resolved;
	}

	/**
	 * Build the immutable map of normalized (uppercased, {@code DIALECT}-suffix-stripped) legacy dialect aliases to their
	 * Hibernate 7 dialect class names. Core dialects live under {@code org.hibernate.dialect}; the rest live under
	 * {@code org.hibernate.community.dialect} (the {@code hibernate-community-dialects} artifact).
	 *
	 * @return An unmodifiable alias map.
	 */
	private static Map<String, String> buildDialectAliases() {
		final String		CORE		= "org.hibernate.dialect.";
		final String		COMMUNITY	= "org.hibernate.community.dialect.";
		Map<String, String>	m			= new HashMap<>();

		// DB2 family
		m.put( "DB2", CORE + "DB2Dialect" );
		m.put( "DB297", CORE + "DB2Dialect" );
		m.put( "DB2390", CORE + "DB2zDialect" );
		m.put( "DB2390V8", CORE + "DB2zDialect" );
		m.put( "DB2400", CORE + "DB2iDialect" );
		m.put( "DB2400V7R3", CORE + "DB2iDialect" );

		// H2 / HSQL
		m.put( "H2", CORE + "H2Dialect" );
		m.put( "HSQL", CORE + "HSQLDialect" );

		// HANA
		m.put( "HANA", CORE + "HANADialect" );
		m.put( "HANACLOUDCOLUMNSTORE", CORE + "HANADialect" );
		m.put( "HANACOLUMNSTORE", CORE + "HANADialect" );
		m.put( "HANAROWSTORE", CORE + "HANADialect" );

		// MariaDB
		m.put( "MARIADB", CORE + "MariaDBDialect" );
		m.put( "MARIADB53", CORE + "MariaDBDialect" );
		m.put( "MARIADB10", CORE + "MariaDBDialect" );
		m.put( "MARIADB102", CORE + "MariaDBDialect" );
		m.put( "MARIADB103", CORE + "MariaDBDialect" );

		// MySQL
		m.put( "MYSQL", CORE + "MySQLDialect" );
		m.put( "MYSQL5", CORE + "MySQLDialect" );
		m.put( "MYSQL55", CORE + "MySQLDialect" );
		m.put( "MYSQL57", CORE + "MySQLDialect" );
		m.put( "MYSQL8", CORE + "MySQLDialect" );
		m.put( "MYSQL5INNODB", CORE + "MySQLDialect" );
		m.put( "MYSQL57INNODB", CORE + "MySQLDialect" );
		m.put( "MYSQLINNODB", CORE + "MySQLDialect" );
		m.put( "MYSQLMYISAM", CORE + "MySQLDialect" );

		// Oracle
		m.put( "ORACLE", CORE + "OracleDialect" );
		m.put( "ORACLE8I", CORE + "OracleDialect" );
		m.put( "ORACLE9", CORE + "OracleDialect" );
		m.put( "ORACLE9I", CORE + "OracleDialect" );
		m.put( "ORACLE10G", CORE + "OracleDialect" );
		m.put( "ORACLE12C", CORE + "OracleDialect" );

		// PostgreSQL
		m.put( "POSTGRESQL", CORE + "PostgreSQLDialect" );
		for ( String v : new String[] { "81", "82", "9", "91", "92", "93", "94", "95", "10" } ) {
			m.put( "POSTGRESQL" + v, CORE + "PostgreSQLDialect" );
		}
		m.put( "POSTGRESPLUS", CORE + "PostgresPlusDialect" );

		// SQL Server
		m.put( "SQLSERVER", CORE + "SQLServerDialect" );
		m.put( "SQLSERVER2005", CORE + "SQLServerDialect" );
		m.put( "SQLSERVER2008", CORE + "SQLServerDialect" );
		m.put( "SQLSERVER2012", CORE + "SQLServerDialect" );
		m.put( "MICROSOFTSQLSERVER", CORE + "SQLServerDialect" );

		// Sybase
		m.put( "SYBASE", CORE + "SybaseDialect" );
		m.put( "SYBASE11", CORE + "SybaseASEDialect" );
		m.put( "SYBASEASE15", CORE + "SybaseASEDialect" );
		m.put( "SYBASEASE157", CORE + "SybaseASEDialect" );
		m.put( "SYBASEANYWHERE", COMMUNITY + "SybaseAnywhereDialect" );

		// CockroachDB
		m.put( "COCKROACHDB192", CORE + "CockroachDialect" );
		m.put( "COCKROACHDB201", CORE + "CockroachDialect" );

		// Community dialects
		m.put( "DERBY", COMMUNITY + "DerbyDialect" );
		m.put( "DERBYTENFIVE", COMMUNITY + "DerbyDialect" );
		m.put( "DERBYTENSIX", COMMUNITY + "DerbyDialect" );
		m.put( "DERBYTENSEVEN", COMMUNITY + "DerbyDialect" );
		m.put( "FIREBIRD", COMMUNITY + "FirebirdDialect" );
		m.put( "INFORMIX", COMMUNITY + "InformixDialect" );
		m.put( "INFORMIX10", COMMUNITY + "InformixDialect" );
		m.put( "INGRES", COMMUNITY + "IngresDialect" );
		m.put( "INGRES9", COMMUNITY + "IngresDialect" );
		m.put( "INGRES10", COMMUNITY + "IngresDialect" );
		m.put( "MIMERSQL", COMMUNITY + "MimerSQLDialect" );
		m.put( "CUBRID", COMMUNITY + "CUBRIDDialect" );
		m.put( "CACHE71", COMMUNITY + "CacheDialect" );
		m.put( "TERADATA", COMMUNITY + "TeradataDialect" );
		m.put( "TERADATA14", COMMUNITY + "TeradataDialect" );
		m.put( "TIMESTEN", COMMUNITY + "TimesTenDialect" );
		m.put( "RDMSOS2200", COMMUNITY + "RDMSOS2200Dialect" );
		m.put( "SAPDB", COMMUNITY + "MaxDBDialect" );
		m.put( "SQLITE", COMMUNITY + "SQLiteDialect" );

		return java.util.Collections.unmodifiableMap( m );
	}

	public PhysicalNamingStrategy getNamingStrategyInstance() {
		return instantiatedNamingStrategy;
	}
}
