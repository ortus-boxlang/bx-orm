package ortus.boxlang.modules.orm.config;

import java.io.File;
import java.util.Arrays;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.naming.BoxLangClassNamingStrategy;
import ortus.boxlang.modules.orm.config.naming.MacroCaseNamingStrategy;
import ortus.boxlang.modules.orm.hibernate.BoxClassInstantiator;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

public class ORMConfig {

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger	logger					= LoggerFactory.getLogger( ORMConfig.class );

	/**
	 * Specifies whether ColdFusion should automatically generate entity mappings
	 * for the persistent CFCs. If autogenmap=false, the mapping should be
	 * provided in the form of <code>orm.xml</code> files.
	 */
	public boolean				autoGenMap				= true;

	/**
	 * Allows the engine to manage the Hibernate session. It is recommended not to
	 * let the engine manage it for you.
	 *
	 * Use transaction blocks in order to demarcate your regions that should start,
	 * flush and end a transaction.
	 */
	public boolean				autoManageSession		= false;

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
	public boolean				flushAtRequestEnd		= false;

	/**
	 * Specifies if the SQL queries should be logged to the console.
	 */
	public boolean				logSQL					= false;

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
	public boolean				saveMapping				= false;

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
	public boolean				secondaryCacheEnabled	= false;

	/**
	 * If true, then the ORM startup will ignore CFCs that have compile time errors
	 * in them.
	 * If `false`, exceptions will be thrown during the ORM startup for any class that could not be converted to a mapping.
	 * <p>
	 * Aliased as `skipCFCWithError` for Adobe and Lucee CFML compatibility.
	 */
	public boolean				ignoreParseErrors		= false;

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
	public boolean				useDBForMapping			= false;

	/**
	 * Application context used for class lookups in naming strategies, event handlers, etc.
	 */
	private IBoxContext			appContext;

	/**
	 * Constructor
	 */
	public ORMConfig( IStruct properties ) {
		this.appContext = BoxRuntime.getInstance().getRuntimeContext();
		implementBackwardsCompatibility( properties );
		process( properties );
	}

	/**
	 * Implement backwards compatible with renamed configuration property names by aliasing them in this method.
	 * <p>
	 * Implements backwards-compatibility for the following properties:
	 * <ul>
	 * <li><code>skipCFCWithError</code> -> <code>ignoreParseErrors</code></li>
	 * <li><code>cfclocation</code> -> <code>entityPaths</code></li>
	 * </ul>
	 * 
	 * @param properties Struct of ORM configuration properties.
	 */
	public void implementBackwardsCompatibility( IStruct properties ) {

		// backwards compatibility for `skipCFCWithError`
		// TODO: Handle 'skipCFCWithError' true-by-default setting for backwards compatibility?
		if ( properties.containsKey( ORMKeys.skipCFCWithError ) && properties.get( ORMKeys.skipCFCWithError ) != null ) {
			properties.computeIfAbsent(
			    ORMKeys.ignoreParseErrors,
			    key -> BooleanCaster.cast( properties.get( ORMKeys.skipCFCWithError ) )
			);
		}
		// backwards compatibility for `cfclocation`
		if ( properties.containsKey( ORMKeys.cfclocation ) ) {
			properties.computeIfAbsent(
			    ORMKeys.entityPaths,
			    key -> properties.get( ORMKeys.cfclocation )
			);
		}
		// TODO: Handle 'autoManageSession' true-by-default setting for backwards compatibility?
		// TODO: Handle 'flushAtRequestEnd' true-by-default setting for backwards compatibility?
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
			setEntityPaths( null );
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
		IClassRunnable				eventHandlerClass	= this.eventHandler != null
		    ? BoxClassInstantiator.instantiateByFQN( this.appContext, this.eventHandler )
		    : null;
		BootstrapServiceRegistry	bootstrapRegistry	= new BootstrapServiceRegistryBuilder()
		    .applyIntegrator( new EventListener( eventHandlerClass ) )
		    .build();
		Configuration				configuration		= new Configuration( bootstrapRegistry );

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
		if ( this.secondaryCacheEnabled ) {
			configuration.setProperty( AvailableSettings.USE_SECOND_LEVEL_CACHE, "true" );
			configuration.setProperty( AvailableSettings.USE_QUERY_CACHE, "true" );
			configuration.setProperty( AvailableSettings.CACHE_REGION_FACTORY, "jcache" );
			configuration.setProperty( "hibernate.javax.cache.provider", this.getJCacheProviderClassPath() );
			if ( this.cacheConfig != null && !this.cacheConfig.isEmpty() ) {
				configuration.setProperty( "hibernate.javax.cache.uri", this.cacheConfig );
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
			// configuration.setProperty( AvailableSettings.DIALECT, toFullHibernateDialectName( dialect ) );
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
			default -> new BoxLangClassNamingStrategy( BoxClassInstantiator.instantiateByFQN( this.appContext, name ) );
		};
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
			case "CUBRID" :
				return "org.hibernate.dialect.CUBRIDDialect";
			case "CACHE71" :
				return "org.hibernate.dialect.Cache71Dialect";
			case "COCKROACHDB192" :
				return "org.hibernate.dialect.CockroachDB192Dialect";
			case "COCKROACHDB201" :
				return "org.hibernate.dialect.CockroachDB201Dialect";
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
			case "DATADIRECTORACLE9" :
				return "org.hibernate.dialect.DataDirectOracle9Dialect";
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
			case "POSTGRESPLUS" :
				return "org.hibernate.dialect.PostgresPlusDialect";
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
}
