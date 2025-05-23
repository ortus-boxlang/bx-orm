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
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Properties;
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
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
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
	private static final ClassLocator	CLASS_LOCATOR			= BoxRuntime.getInstance().getClassLocator();

	public static final String			DEFAULT_CACHEPROVIDER	= "BoxCacheProvider";

	/**
	 * Runtime
	 */
	private static final BoxRuntime		runtime					= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger				logger;

	/**
	 * Specifies whether ColdFusion should automatically generate entity mappings
	 * for the persistent CFCs. If autogenmap=false, the mapping should be
	 * provided in the form of <code>orm.xml</code> files.
	 */
	public boolean						autoGenMap				= true;

	/**
	 * Allows the engine to manage the Hibernate session. It is recommended not to
	 * let the engine manage it for you.
	 *
	 * Use transaction blocks in order to demarcate your regions that should start,
	 * flush and end a transaction.
	 */
	public boolean						autoManageSession		= false;

	/**
	 * Specify a string path to the secondary cache configuration file. This configuration file must be formatted to the specification of the jCache
	 * provider specified in the `cacheProvider` setting.
	 */
	public String						cacheConfigFile;

	/**
	 * A structure of properties to configure the secondary cache provider
	 */
	public IStruct						cacheConfigProperties	= CacheConfig.DEFAULTS;

	/**
	 * Specify the alias name OR full class path of a jCache provider to use for the second-level cache. Must be one of the following:
	 * <ul>
	 * <li><code>ehcache</code> - use the EHCache jCache implementation bundled with the BoxLang ORM module</li>
	 * <li><code>com.foo.MyJCacheProvider</code> - String path to a custom jCache provider loaded into your BoxLang application.</li>
	 * </ul>
	 */
	public String						cacheProvider			= DEFAULT_CACHEPROVIDER;

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
	public String[]						entityPaths;

	/**
	 * Define the data source to be utilized by the ORM. If not used,
	 * defaults to the this.datasource in the Application.cfc.
	 */
	public Key							datasource;

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
	public String						dbcreate;

	/**
	 * The dialect to use for your database. By default Hibernate will introspect
	 * the datasource and try to figure it out. See the dialects section below.
	 *
	 * You can also use the fully Java qualified name of the class.
	 */
	public String						dialect;

	/**
	 * If true, then it enables the ORM event callbacks in entities and globally via
	 * the `eventHandler` property.
	 */
	public boolean						eventHandling;

	/**
	 * The CFC path of the CFC that will manage the global ORM events.
	 */
	public String						eventHandler;

	/**
	 * Specifies if an orm flush should be called automatically at the end of a
	 * request. In our opinion this SHOULD never be true. Database persistence
	 * should be done via transaction tags and good transaction demarcation.
	 */
	public boolean						flushAtRequestEnd		= false;

	/**
	 * Specifies if the SQL queries should be logged to the console.
	 */
	public boolean						logSQL					= false;

	/**
	 * Defines the naming convention to use on table and column names.
	 *
	 * - default : Uses the table or column names as is
	 * - smart : This strategy changes the logical table or column name to
	 * uppercase.
	 * - CFC PATH : Use your own CFC to determine naming. Must implement `orm.models.INamingStrategy`
	 */
	public String						namingStrategy;

	/**
	 * The path to a custom Hibernate configuration file:
	 *
	 * - hibernate.properties
	 * - hibernate.cfc.xml
	 */
	public String						ormConfig;

	/**
	 * If enabled, the ORM will create the Hibernate mapping XML (*.hbmxml) files
	 * alongside the entities. This is great for debugging your entities and
	 * relationships.
	 *
	 */
	public boolean						saveMapping				= false;

	/**
	 * The default database schema to use for database connections. This can be
	 * overriden at the datasource level, as well as on each entity.
	 */
	public String						schema;

	/**
	 * Specifies the default Database Catalog that ORM should use. This can be
	 * overriden at the datasource level, as well as on each entity.
	 */
	public String						catalog;

	/**
	 * Enable or disable the secondary cache.
	 */
	public boolean						secondaryCacheEnabled	= false;

	/**
	 * If true, then the ORM startup will ignore CFCs that have compile time errors
	 * in them.
	 * If `false`, exceptions will be thrown during the ORM startup for any class that could not be converted to a mapping.
	 * <p>
	 * Aliased as `skipCFCWithError` for Adobe and Lucee CFML compatibility.
	 */
	public boolean						ignoreParseErrors		= false;

	/**
	 * Path to a SQL script file that will be executed after the ORM is initialized.
	 * Only used if dbcreate is set to <code>dropcreate</code>.
	 */
	public String						sqlScript;

	/**
	 * Specifies whether the database has to be inspected to identify the missing
	 * information required to generate the Hibernate mapping.
	 *
	 * The database is inspected to get the column data type, primary key and
	 * foreign key information.
	 */
	public boolean						useDBForMapping			= false;

	/**
	 * Whether to quote identifiers. If turned off column and table names with reserved words will fail to be created/updated
	 */
	public boolean						quoteIdentifiers		= false;

	/**
	 * Enable or disable the use of threading for mapping multiple ORM entities concurrently.
	 */
	public boolean						enableThreadedMapping	= true;

	/**
	 * Application context used for class lookups in naming strategies, event handlers, etc.
	 */
	private RequestBoxContext			requestContext;

	/**
	 * The instantiated naming strategy object.
	 */
	private PhysicalNamingStrategy		instantiatedNamingStrategy;

	/**
	 * Constructor
	 *
	 * @param properties Struct of ORM configuration properties.
	 */
	public ORMConfig( IStruct properties, RequestBoxContext context ) {
		this.logger = runtime.getLoggingService().getLogger( "orm" );

		if ( properties == null ) {
			properties = new Struct();
		}
		this.requestContext = context;

		runtime.getInterceptorService().announce( ORMKeys.EVENT_ORM_PRE_CONFIG_LOAD, Struct.of(
		    Key.properties, properties,
		    "context", context
		) );

		process( properties );

		runtime.getInterceptorService().announce( ORMKeys.EVENT_ORM_POST_CONFIG_LOAD, Struct.of(
		    Key.properties, properties,
		    "context", context
		) );
	}

	/**
	 * Construct an ORMConfig object from the application settings.
	 *
	 * @param context The IBoxContext object for the current request.
	 *
	 * @return ORMConfig object or null if ORM is not enabled or no ORM settings are present in the application settings.
	 */
	public static ORMConfig loadFromContext( RequestBoxContext context ) {
		IStruct appSettings = ( IStruct ) context.getConfigItem( Key.applicationSettings );

		if ( !appSettings.containsKey( ORMKeys.ORMEnabled )
		    || !BooleanCaster.cast( appSettings.getOrDefault( ORMKeys.ORMEnabled, false ) ) ) {
			// logger.info( "ORMEnabled is false or not specified;" );
			return null;
		}

		if ( !appSettings.containsKey( ORMKeys.ORMSettings )
		    || appSettings.get( ORMKeys.ORMSettings ) == null ) {
			// logger.info( "No ORM configuration found in application configuration;" );
			return null;
		}

		return new ORMConfig( appSettings.getAsStruct( ORMKeys.ORMSettings ), context );
	}

	/**
	 * Process the ORM configuration properties and set the private config fields
	 * accordingly.
	 *
	 * @param properties Struct of ORM configuration properties.
	 */
	private void process( IStruct properties ) {
		if ( properties == null ) {
			return;
		}

		/**
		 * Boolean properties: Check key existence, check for null, then cast to
		 * boolean.
		 */
		if ( properties.containsKey( ORMKeys.autoGenMap ) && properties.get( ORMKeys.autoGenMap ) != null ) {
			autoGenMap = BooleanCaster.cast( properties.get( ORMKeys.autoGenMap ) );
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
			datasource = getAppDefaultDatasource();
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

		if ( this.namingStrategy != null ) {
			this.instantiatedNamingStrategy = getNamingStrategyForName( this.namingStrategy );
		}
	}

	/**
	 * Read the default datasource name from application settings.
	 */
	private Key getAppDefaultDatasource() {
		Key		defaultDatasource	= Key.of( ( String ) this.requestContext.getConfigItems( new Key[] { Key.defaultDatasource } ) );
		IStruct	configDatasources	= ( IStruct ) this.requestContext.getConfigItems( new Key[] { Key.datasources } );
		if ( !defaultDatasource.isEmpty() && configDatasources.containsKey( defaultDatasource ) ) {
			return defaultDatasource;
		} else if ( !defaultDatasource.isEmpty() ) {
			logger.warn( "The datasource [" + defaultDatasource + "] could not be found in the request configuration.  Datasources found: ["
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
		DynamicObject				eventHandlerClass	= this.eventHandler != null
		    ? loadBoxLangClassByFQN( this.requestContext, this.eventHandler )
		    : null;
		BootstrapServiceRegistry	bootstrapRegistry	= new BootstrapServiceRegistryBuilder()
		    .applyIntegrator( new EventListener( eventHandlerClass ) )
		    .build();
		Configuration				configuration		= new Configuration( bootstrapRegistry );
		var							sysEnvProps			= new Properties();

		Field[]						availableSettings	= AvailableSettings.class.getFields();
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

		// Performance improvement.
		configuration.setProperty( "hibernate.temp.use_jdbc_metadata_defaults", "false" );

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

		configuration.setProperty( AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.toString( this.secondaryCacheEnabled ) );
		if ( this.secondaryCacheEnabled ) {
			configuration.setProperty( AvailableSettings.USE_QUERY_CACHE, "true" );
			configuration.setProperty( AvailableSettings.CACHE_REGION_FACTORY, "jcache" );
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

		if ( this.dialect != null ) {
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
				logger.warn(
				    "ORM Configuration `sqlScript` is only valid with `dbcreate=dropcreate`. Ignoring for now." );
			}
		}

		// @TODO: Implement the remaining configuration settings:
		// - ormConfig

		// Session and transaction management settings:
		configuration.setProperty( AvailableSettings.FLUSH_BEFORE_COMPLETION, "false" )
		    .setProperty( AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION, "true" )
		    .setProperty( AvailableSettings.AUTO_CLOSE_SESSION, "false" );

		return configuration;
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
			default -> new BoxLangClassNamingStrategy( loadBoxLangClassByFQN( this.requestContext, name ) );
		};
	}

	/**
	 * Load a BoxLang class by its fully-qualified name.
	 *
	 * @param context The current request context.
	 * @param fqn     The fully-qualified name of the class to load.
	 *
	 * @return The loaded class.
	 */
	private DynamicObject loadBoxLangClassByFQN( RequestBoxContext context, String fqn ) {
		return CLASS_LOCATOR.load(
		    context,
		    fqn,
		    ClassLocator.BX_PREFIX,
		    true,
		    context.getCurrentImports()
		).invokeConstructor( context );
	}

	/**
	 * Get the `cacheProvider` setting as a path to a JCache provider.
	 */
	public String getJCacheProviderClassPath() {
		return "ortus.boxlang.modules.orm.hibernate.cache.BoxHibernateCachingProvider";
	}

	public Properties getJCacheDefaultProperties() {
		Properties properties = new Properties();
		properties.setProperty( "hibernate.cache.region_prefix", datasource.getName() + "_" );
		properties.setProperty( "hibernate.javax.cache.provider", getJCacheProviderClassPath() );
		return properties;
	}

	/**
	 * Translate a short dialect name like 'MYSQL' to the full Hibernate dialect class name like 'org.hibernate.dialect.MySQLDialect'.
	 * <p>
	 * Note that this method should be removed once we migrate to Hibernate 6+.
	 *
	 * @param dialectName Hibernate dialect name, either full like 'org.hibernate.dialect.MySQLDialect' or short like 'MYSQL'.
	 *
	 * @return If the dialect passed is recognized as a dialect alias, the full Hibernate dialect class name is returned. Otherwise, the original dialect
	 *         name is returned unmodified.
	 */
	private String toFullHibernateDialectName( String dialectName ) {
		switch ( dialectName.trim().toUpperCase().replace( "DIALECT", "" ) ) {
			case "CACHE71" :
				return "org.hibernate.dialect.Cache71Dialect";
			case "COCKROACHDB192" :
				return "org.hibernate.dialect.CockroachDB192Dialect";
			case "COCKROACHDB201" :
				return "org.hibernate.dialect.CockroachDB201Dialect";
			case "CUBRID" :
				return "org.hibernate.dialect.CUBRIDDialect";
			case "DATADIRECTORACLE9" :
				return "org.hibernate.dialect.DataDirectOracle9Dialect";
			case "DB2390" :
				return "org.hibernate.dialect.DB2390Dialect";
			case "DB2390V8" :
				return "org.hibernate.dialect.DB2390V8Dialect";
			case "DB2400" :
				return "org.hibernate.dialect.DB2400Dialect";
			case "DB2400V7R3" :
				return "org.hibernate.dialect.DB2400V7R3Dialect";
			case "DB297" :
				return "org.hibernate.dialect.DB297Dialect";
			case "DB2" :
				return "org.hibernate.dialect.DB2Dialect";
			case "DERBY" :
				return "org.hibernate.dialect.DerbyDialect";
			case "DERBYTENFIVE" :
				return "org.hibernate.dialect.DerbyTenFiveDialect";
			case "DERBYTENSEVEN" :
				return "org.hibernate.dialect.DerbyTenSevenDialect";
			case "DERBYTENSIX" :
				return "org.hibernate.dialect.DerbyTenSixDialect";
			case "FIREBIRD" :
				return "org.hibernate.dialect.FirebirdDialect";
			case "FRONTBASE" :
				return "org.hibernate.dialect.FrontBaseDialect";
			case "H2" :
				return "org.hibernate.dialect.H2Dialect";
			case "HANACLOUDCOLUMNSTORE" :
				return "org.hibernate.dialect.HANACloudColumnStoreDialect";
			case "HANACOLUMNSTORE" :
				return "org.hibernate.dialect.HANAColumnStoreDialect";
			case "HANAROWSTORE" :
				return "org.hibernate.dialect.HANARowStoreDialect";
			case "HSQL" :
				return "org.hibernate.dialect.HSQLDialect";
			case "INFORMIX10" :
				return "org.hibernate.dialect.Informix10Dialect";
			case "INFORMIX" :
				return "org.hibernate.dialect.InformixDialect";
			case "INGRES10" :
				return "org.hibernate.dialect.Ingres10Dialect";
			case "INGRES9" :
				return "org.hibernate.dialect.Ingres9Dialect";
			case "INGRES" :
				return "org.hibernate.dialect.IngresDialect";
			case "INTERBASE" :
				return "org.hibernate.dialect.InterbaseDialect";
			case "JDATASTORE" :
				return "org.hibernate.dialect.JDataStoreDialect";
			case "MARIADB102" :
				return "org.hibernate.dialect.MariaDB102Dialect";
			case "MARIADB103" :
				return "org.hibernate.dialect.MariaDB103Dialect";
			case "MARIADB10" :
				return "org.hibernate.dialect.MariaDB10Dialect";
			case "MARIADB53" :
				return "org.hibernate.dialect.MariaDB53Dialect";
			case "MARIADB" :
				return "org.hibernate.dialect.MariaDBDialect";
			case "MCKOI" :
				return "org.hibernate.dialect.MckoiDialect";
			case "MICROSOFTSQLSERVER" :
				return "org.hibernate.dialect.SQLServerDialect";
			case "MIMERSQL" :
				return "org.hibernate.dialect.MimerSQLDialect";
			case "MYSQL55" :
				return "org.hibernate.dialect.MySQL55Dialect";
			case "MYSQL57" :
				return "org.hibernate.dialect.MySQL57Dialect";
			case "MYSQL57INNODB" :
				return "org.hibernate.dialect.MySQL57InnoDBDialect";
			case "MYSQL5" :
				return "org.hibernate.dialect.MySQL5Dialect";
			case "MYSQL5INNODB" :
				return "org.hibernate.dialect.MySQL5InnoDBDialect";
			case "MYSQL8" :
			case "MYSQL" :
				return "org.hibernate.dialect.MySQL8Dialect";
			case "MYSQLINNODB" :
				return "org.hibernate.dialect.MySQLInnoDBDialect";
			case "MYSQLMYISAM" :
				return "org.hibernate.dialect.MySQLMyISAMDialect";
			case "ORACLE10G" :
				return "org.hibernate.dialect.Oracle10gDialect";
			case "ORACLE12C" :
				return "org.hibernate.dialect.Oracle12cDialect";
			case "ORACLE8I" :
				return "org.hibernate.dialect.Oracle8iDialect";
			case "ORACLE9" :
				return "org.hibernate.dialect.Oracle9Dialect";
			case "ORACLE9I" :
				return "org.hibernate.dialect.Oracle9iDialect";
			case "ORACLE" :
				return "org.hibernate.dialect.OracleDialect";
			case "POINTBASE" :
				return "org.hibernate.dialect.PointbaseDialect";
			case "POSTGRESPLUS" :
				return "org.hibernate.dialect.PostgresPlusDialect";
			case "POSTGRESQL10" :
				return "org.hibernate.dialect.PostgreSQL10Dialect";
			case "POSTGRESQL81" :
				return "org.hibernate.dialect.PostgreSQL81Dialect";
			case "POSTGRESQL82" :
				return "org.hibernate.dialect.PostgreSQL82Dialect";
			case "POSTGRESQL91" :
				return "org.hibernate.dialect.PostgreSQL91Dialect";
			case "POSTGRESQL92" :
				return "org.hibernate.dialect.PostgreSQL92Dialect";
			case "POSTGRESQL93" :
				return "org.hibernate.dialect.PostgreSQL93Dialect";
			case "POSTGRESQL94" :
				return "org.hibernate.dialect.PostgreSQL94Dialect";
			case "POSTGRESQL95" :
				return "org.hibernate.dialect.PostgreSQL95Dialect";
			case "POSTGRESQL9" :
				return "org.hibernate.dialect.PostgreSQL9Dialect";
			case "POSTGRESQL" :
				return "org.hibernate.dialect.PostgreSQLDialect";
			case "PROGRESS" :
				return "org.hibernate.dialect.ProgressDialect";
			case "RDMSOS2200" :
				return "org.hibernate.dialect.RDMSOS2200Dialect";
			case "SAPDB" :
				return "org.hibernate.dialect.SAPDBDialect";
			case "SQLSERVER2005" :
				return "org.hibernate.dialect.SQLServer2005Dialect";
			case "SQLSERVER2008" :
				return "org.hibernate.dialect.SQLServer2008Dialect";
			case "SQLSERVER2012" :
				return "org.hibernate.dialect.SQLServer2012Dialect";
			case "SQLSERVER" :
				return "org.hibernate.dialect.SQLServerDialect";
			case "SYBASE11" :
				return "org.hibernate.dialect.Sybase11Dialect";
			case "SYBASEASE157" :
				return "org.hibernate.dialect.SybaseASE157Dialect";
			case "SYBASEASE15" :
				return "org.hibernate.dialect.SybaseASE15Dialect";
			case "SYBASEANYWHERE" :
				return "org.hibernate.dialect.SybaseAnywhereDialect";
			case "SYBASE" :
				return "org.hibernate.dialect.SybaseDialect";
			case "TERADATA14" :
				return "org.hibernate.dialect.Teradata14Dialect";
			case "TERADATA" :
				return "org.hibernate.dialect.TeradataDialect";
			case "TIMESTEN" :
				return "org.hibernate.dialect.TimesTenDialect";
			default :
				return dialectName;
		}
	}

	public PhysicalNamingStrategy getNamingStrategyInstance() {
		return instantiatedNamingStrategy;
	}
}
