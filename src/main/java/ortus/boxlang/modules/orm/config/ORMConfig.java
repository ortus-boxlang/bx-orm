package ortus.boxlang.modules.orm.config;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.naming.BoxLangClassNamingStrategy;
import ortus.boxlang.modules.orm.config.naming.MacroCaseNamingStrategy;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IBoxRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.ResolvedFilePath;

public class ORMConfig {

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger	logger		= LoggerFactory.getLogger( ORMConfig.class );

	/**
	 * Specifies whether ColdFusion should automatically generate entity mappings
	 * for the persistent CFCs. If autogenmap=false, the mapping should be
	 * provided in the form of <code>orm.xml</code> files.
	 */
	public boolean				autoGenMap	= true;

	/**
	 * Allows the engine to manage the Hibernate session. It is recommended not to
	 * let the engine manage it for you.
	 *
	 * Use transaction blocks in order to demarcate your regions that should start,
	 * flush and end a transaction.
	 */
	public boolean				autoManageSession;

	/**
	 * Specify a string path to the secondary cache configuration file. This configuration file must be formatted to the specification of the jCache
	 * provider specified in the `cacheProvider` setting.
	 */
	public String				cacheConfig;

	/**
	 * Specify the alias name OR full class path of a jCache provider to use for the second-level cache. Must be one of the following:
	 * <ul>
	 * <li><code>ehcache</code> - use the EHCache jCache implementation bundled with the BoxLang ORM module</li>
	 * <li><code>com.foo.MyJCacheProvider</code> - String path to a custom jCache provider loaded into your BoxLang application.</li>
	 * </ul>
	 */
	public String				cacheProvider;

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
	public String[]				entityPaths;

	/**
	 * Define the data source to be utilized by the ORM. If not used,
	 * defaults to the this.datasource in the Application.cfc.
	 */
	public String				datasource;

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
	public String				dbcreate;

	/**
	 * The dialect to use for your database. By default Hibernate will introspect
	 * the datasource and try to figure it out. See the dialects section below.
	 *
	 * You can also use the fully Java qualified name of the class.
	 */
	public String				dialect;

	/**
	 * If true, then it enables the ORM event callbacks in entities and globally via
	 * the `eventHandler` property.
	 */
	public boolean				eventHandling;

	/**
	 * The CFC path of the CFC that will manage the global ORM events.
	 */
	public String				eventHandler;

	/**
	 * Specifies if an orm flush should be called automatically at the end of a
	 * request. In our opinion this SHOULD never be true. Database persistence
	 * should be done via transaction tags and good transaction demarcation.
	 */
	public boolean				flushAtRequestEnd;

	/**
	 * Specifies if the SQL queries should be logged to the console.
	 */
	public boolean				logSQL;

	/**
	 * Defines the naming convention to use on table and column names.
	 *
	 * - default : Uses the table or column names as is
	 * - smart : This strategy changes the logical table or column name to
	 * uppercase.
	 * - CFC PATH : Use your own CFC to determine naming
	 */
	public String				namingStrategy;

	/**
	 * The path to a custom Hibernate configuration file:
	 *
	 * - hibernate.properties
	 * - hibernate.cfc.xml
	 */
	public String				ormConfig;

	/**
	 * If enabled, the ORM will create the Hibernate mapping XML (*.hbmxml) files
	 * alongside the entities. This is great for debugging your entities and
	 * relationships.
	 *
	 */
	public boolean				saveMapping;

	/**
	 * The default database schema to use for database connections. This can be
	 * overriden at the datasource level, as well as on each entity.
	 */
	public String				schema;

	/**
	 * Specifies the default Database Catalog that ORM should use. This can be
	 * overriden at the datasource level, as well as on each entity.
	 */
	public String				catalog;

	/**
	 * Enable or disable the secondary cache.
	 */
	public boolean				secondaryCacheEnabled;

	/**
	 * If true, then the ORM startup will ignore CFCs that have compile time errors
	 * in them.
	 * If `false`, exceptions will be thrown during the ORM startup for any class that could not be converted to a mapping.
	 * 
	 * @TODO: Rename to `strictParsing` or similar.
	 */
	public boolean				skipCFCWithError;

	/**
	 * Path to a SQL script file that will be executed after the ORM is initialized.
	 * Only used if dbcreate is set to <code>dropcreate</code>.
	 */
	public String				sqlScript;

	/**
	 * Specifies whether the database has to be inspected to identify the missing
	 * information required to generate the Hibernate mapping.
	 *
	 * The database is inspected to get the column data type, primary key and
	 * foreign key information.
	 */
	public boolean				useDBForMapping;

	/**
	 * Constructor
	 */
	public ORMConfig( IStruct properties ) {
		process( properties );
	}

	/**
	 * Process the ORM configuration properties and set the private config fields
	 * accordingly.
	 */
	private void process( IStruct properties ) {
		if ( properties == null ) {
			return;
		}
		if ( !properties.containsKey( ORMKeys.datasource ) || properties.get( ORMKeys.datasource ) == null ) {
			throw new BoxRuntimeException(
			    "ORM configuration is missing 'datasource' key. Default datasources will be supported in a future iteration." );
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

		if ( properties.containsKey( ORMKeys.flushAtRequestEnd ) && properties.get( ORMKeys.flushAtRequestEnd ) != null ) {
			flushAtRequestEnd = BooleanCaster.cast( properties.get( ORMKeys.flushAtRequestEnd ) );
		}

		if ( properties.containsKey( ORMKeys.logSQL ) && properties.get( ORMKeys.logSQL ) != null ) {
			logSQL = BooleanCaster.cast( properties.get( ORMKeys.logSQL ) );
		}

		// String properties: Check key existence, check for null, and check for empty
		// or blank (whitespace-only) strings
		if ( properties.containsKey( ORMKeys.cacheConfig ) && properties.get( ORMKeys.cacheConfig ) != null ) {
			cacheConfig = properties.getAsString( ORMKeys.cacheConfig );
		}
		if ( properties.containsKey( ORMKeys.cacheProvider ) && properties.get( ORMKeys.cacheProvider ) != null
		    && !properties.getAsString( ORMKeys.cacheProvider ).isBlank() ) {
			cacheProvider = properties.getAsString( ORMKeys.cacheProvider );
		}

		if ( properties.containsKey( ORMKeys.entityPaths ) && properties.get( ORMKeys.entityPaths ) != null ) {
			setEntityPaths( properties.get( ORMKeys.entityPaths ) );
		} else {
			// CFML-compatible `cfcLocation` configuration support
			if ( properties.containsKey( ORMKeys.cfclocation ) ) {
				setEntityPaths( properties.get( ORMKeys.cfclocation ) );
			} else {
				setEntityPaths( null );
			}
		}

		if ( properties.containsKey( ORMKeys.datasource ) && properties.get( ORMKeys.datasource ) != null ) {
			Object datasourceProperty = properties.get( ORMKeys.datasource );
			if ( datasourceProperty instanceof String datasourceName ) {
				datasource = datasourceName;
			} else if ( datasourceProperty instanceof IStruct datasourceStruct ) {
				// @TODO: Implement this!
				// datasourceStruct = datasourceStruct;
			}
		}

		if ( properties.containsKey( ORMKeys.dbcreate ) && properties.get( ORMKeys.dbcreate ) != null
		    && !properties.getAsString( ORMKeys.dbcreate ).isBlank() ) {
			dbcreate = properties.getAsString( ORMKeys.dbcreate );
		}

		if ( properties.containsKey( ORMKeys.dialect ) && properties.get( ORMKeys.dialect ) != null
		    && !properties.getAsString( ORMKeys.dialect ).isBlank() ) {
			logger.warn(
			    "Setting 'dialect' in Hibernate 6.0+ is unnecessary on all Hibernate-supported databases. Ignoring 'dialect' configuration for now." );
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
	 * Populate and return a Hibernate configuration object using the constructed
	 * properties.
	 */
	public Configuration toHibernateConfig() {
		Configuration configuration = new Configuration();

		if ( this.dbcreate != null ) {
			switch ( dbcreate ) {
				case "dropcreate" :
					this.dbcreate = "drop-and-create";
					break;
				default :
					break;
			}
			configuration.setProperty( AvailableSettings.HBM2DDL_AUTO, Action.interpretHbm2ddlSetting( dbcreate ).getExternalHbm2ddlName() );
		}

		if ( this.namingStrategy != null ) {
			PhysicalNamingStrategy loadedNamingStrategy = getNamingStrategyForName(
			    this.namingStrategy );
			if ( namingStrategy != null ) {
				configuration.setPhysicalNamingStrategy( loadedNamingStrategy );
			}
		}

		if ( this.dialect != null ) {
			// @TODO: Until we implement a method to resolve dialect short names, like
			// `MYSQL` to full
			// class names, Hibernate will throw errors on startup as 90% of dialect names
			// will not be found. Should we drop `dialect` support entirely, or should we
			// add full support for a mostly unnecessary feature?
			// See warning at
			// https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/cfg/JdbcSettings.html#DIALECT
			// configuration.setProperty(AvailableSettings.DIALECT, dialect);
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
		// - cacheConfig
		// - cacheProvider
		// - eventHandling
		// - eventHandler
		// - flushAtRequestEnd
		// - logSQL
		// - secondaryCacheEnabled
		// - ormConfig

		// These properties are only used in the SessionFactoryBuilder, and do not need
		// copying into the Hibernate configuration:
		// - skipCFCWithError
		// - useDBForMapping
		// - saveMapping

		return configuration;
	}

	private PhysicalNamingStrategy getNamingStrategyForName( String name ) {
		// @TODO: Use an enum for the naming strategies
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
			 * The "cfc" naming strategy allows apps to define their own naming strategy by
			 * providing a full CFC path.
			 */
			default -> new BoxLangClassNamingStrategy( loadBoxlangClassByPath( name ) );
		};
	}

	/**
	 * Load and instantiate the Boxlang class by its string path. Useful for using a
	 * .bx class as a custom naming strategy.
	 *
	 * @param classPath The path to the Boxlang class, either slash or dot
	 *                  delimited.
	 */
	private Class<IBoxRunnable> loadBoxlangClassByPath( String classPath ) {
		String packageName = Paths.get( classPath ).getParent().toString().replace( "/", "." );
		return RunnableLoader.getInstance().loadClass( ResolvedFilePath.of( Paths.get( classPath ) ),
		    BoxRuntime.getInstance().getRuntimeContext() );
	}

	/**
	 * Get the `cacheProvider` setting as a path to a JCache provider.
	 */
	public String getJCacheProviderClassPath() {
		String upperAliasName = cacheProvider.toUpperCase();
		return JCacheProvider.contains( upperAliasName ) ? JCacheProvider.valueOf( upperAliasName ).toClassPath()
		    : cacheProvider;
	}

	/**
	 * Enumeration of possible values for the `cacheProvider` configuration setting.
	 * <p>
	 * Each value must be a JCache provider.
	 */
	private enum JCacheProvider {

		EHCACHE;

		public String toClassPath() {
			return switch ( this ) {
				case EHCACHE -> "org.ehcache.jsr107.EhcacheCachingProvider";
			};
		}

		/**
		 * Check if the provided alias is a valid JCache provider.
		 *
		 * @param alias String alias name to look for in the JCacheProvider enum.
		 */
		public static boolean contains( String alias ) {
			return Arrays.stream( JCacheProvider.values() ).anyMatch( provider -> provider.name().equals( alias ) );
		}
	}
}
