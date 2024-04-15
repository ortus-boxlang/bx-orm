package com.ortussolutions.config;

import java.io.File;
import java.nio.file.Paths;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ortussolutions.config.naming.BoxLangClassNamingStrategy;
import com.ortussolutions.config.naming.MacroCaseNamingStrategy;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.types.IStruct;

public class ORMConfig {

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger	logger	= LoggerFactory.getLogger( ORMConfig.class );

	/**
	 * Underlying storage for all ORM configuration properties.
	 */
	private IStruct				properties;

	/**
	 * Specifies whether ColdFusion should automatically generate entity mappings
	 * for the persistent CFCs. If autogenmap=false, the mapping should be
	 * provided in the form of <code>hbm.xml</code> files.
	 */
	private boolean				autoGenMap;

	/**
	 * Allows the engine to manage the Hibernate session. It is recommended not to
	 * let the engine manage it for you.
	 *
	 * Use transaction blocks in order to demarcate your regions that should start,
	 * flush and end a transaction.
	 */
	private boolean				autoManageSession;

	/**
	 * Specifies the location of the configuration file that the secondary cache
	 * provider should use. This setting is used only when
	 * secondaryCacheEnabled=true
	 */
	private boolean				cacheConfig;

	/**
	 * Specifies the cache provider that ORM should use as a secondary cache. The
	 * values can be:
	 *
	 * Ehcache
	 * ConcurrentHashMap
	 *
	 * The fully qualified name of the class for any other cache provider
	 */
	private String				cacheProvider;

	/**
	 * Specifies the directory (or array of directories) that should be used to
	 * search for persistent CFCs to generate the mapping.
	 * <p>
	 * Always specify it or pay a performance startup price.
	 * <p>
	 * <strong>Important:</strong> If it is not set, the extension looks at the
	 * application directory, its sub-directories, and its mapped directories to
	 * search for persistent CFCs.
	 */
	private String				cfclocation;

	/**
	 * This setting defines the data source to be utilized by the ORM. If not used,
	 * defaults to the this.datasource in the Application.cfc.
	 */
	private String				datasource;

	/**
	 * <ul>
	 * <li><code>update</code> : Creates the database according to your ORM model.
	 * It only does incremental updates. It will never remove tables, indexes,
	 * etc.</li>
	 * <li><code>dropcreate</code> : Same as above but it destroys the database if
	 * it has ny content and recreates it every time the ORM is reloaded.</li>
	 * <li><code>none</code> : Does not change the database schema at all.</li>
	 * </ul>
	 */
	private String				dbcreate;

	/**
	 * The dialect to use for your database. By default Hibernate will introspect
	 * the datasource and try to figure it out. See the dialects section below.
	 *
	 * You can also use the fully Java qualified name of the class.
	 */
	private String				dialect;

	/**
	 * If true, then it enables the ORM event callbacks in entities and globally via
	 * the `eventHandler` property.
	 */
	private boolean				eventHandling;

	/**
	 * The CFC path of the CFC that will manage the global ORM events.
	 */
	private String				eventHandler;

	/**
	 * Specifies if an orm flush should be called automatically at the end of a
	 * request. In our opinion this SHOULD never be true. Database persistence
	 * should be done via transaction tags and good transaction demarcation.
	 */
	private boolean				flushAtRequestEnd;

	/**
	 * Specifies if the SQL queries should be logged to the console.
	 */
	private boolean				logSQL;

	/**
	 * Defines the naming convention to use on table and column names.
	 *
	 * - default : Uses the table or column names as is
	 * - smart : This strategy changes the logical table or column name to
	 * uppercase.
	 * - CFC PATH : Use your own CFC to determine naming
	 */
	private String				namingStrategy;

	/**
	 * The path to a custom Hibernate configuration file:
	 *
	 * - hibernate.properties
	 * - hibernate.cfc.xml
	 */
	private String				ormConfig;

	/**
	 * If enabled, the ORM will create the Hibernate mapping XML (*.hbmxml) files
	 * alongside the entities. This is great for debugging your entities and
	 * relationships.
	 *
	 */
	private boolean				saveMapping;

	/**
	 * The default database schema to use for database connections. This can be
	 * overriden at the datasource level.
	 */
	private String				schema;

	/**
	 * Specifies the default Database Catalog that ORM should use. This can be
	 * overriden at the datasource level.
	 */
	private String				catalog;

	/**
	 * Enable the secondary cache or not.
	 */
	private boolean				secondaryCacheEnabled;

	/**
	 * If true, then the ORM will ignore CFCs that have compile time errors in them.
	 * Use false to throw exceptions.
	 */
	private boolean				skipCFCWithError;

	/**
	 * Path to a SQL script file that will be executed after the ORM is initialized.
	 * Only used if dbcreate is set to <code>dropcreate</code>.
	 */
	private String				sqlScript;

	/**
	 * Specifies whether the database has to be inspected to identify the missing
	 * information required to generate the Hibernate mapping.
	 *
	 * The database is inspected to get the column data type, primary key and
	 * foreign key information.
	 */
	private boolean				useDBForMapping;

	/**
	 * Constructor
	 */
	public ORMConfig( IStruct properties ) {
		this.properties = properties;
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

		/**
		 * Boolean properties: Check key existence, check for null, then cast to
		 * boolean.
		 */
		if ( properties.containsKey( ORMKeys.autoGenMap ) && properties.get( ORMKeys.autoGenMap ) != null ) {
			autoGenMap = properties.getAsBoolean( ORMKeys.autoGenMap );
		}

		if ( properties.containsKey( ORMKeys.autoManageSession ) && properties.get( ORMKeys.autoManageSession ) != null ) {
			autoManageSession = properties.getAsBoolean( ORMKeys.autoManageSession );
		}

		if ( properties.containsKey( ORMKeys.cacheConfig ) && properties.get( ORMKeys.cacheConfig ) != null ) {
			cacheConfig = properties.getAsBoolean( ORMKeys.cacheConfig );
		}

		if ( properties.containsKey( ORMKeys.eventHandling ) && properties.get( ORMKeys.eventHandling ) != null ) {
			eventHandling = properties.getAsBoolean( ORMKeys.eventHandling );
		}

		if ( properties.containsKey( ORMKeys.flushAtRequestEnd ) && properties.get( ORMKeys.flushAtRequestEnd ) != null ) {
			flushAtRequestEnd = properties.getAsBoolean( ORMKeys.flushAtRequestEnd );
		}

		if ( properties.containsKey( ORMKeys.logSQL ) && properties.get( ORMKeys.logSQL ) != null ) {
			logSQL = properties.getAsBoolean( ORMKeys.logSQL );
		}

		// String properties: Check key existence, check for null, and check for empty
		// or blank (whitespace-only) strings
		if ( properties.containsKey( ORMKeys.cacheProvider ) && properties.get( ORMKeys.cacheProvider ) != null
		    && !properties.getAsString( ORMKeys.cacheProvider ).isBlank() ) {
			cacheProvider = properties.getAsString( ORMKeys.cacheProvider );
		}

		if ( properties.containsKey( ORMKeys.cfclocation ) && properties.get( ORMKeys.cfclocation ) != null
		    && !properties.getAsString( ORMKeys.cfclocation ).isBlank() ) {
			cfclocation = properties.getAsString( ORMKeys.cfclocation );
		}

		if ( properties.containsKey( ORMKeys.datasource ) && properties.get( ORMKeys.datasource ) != null
		    && !properties.getAsString( ORMKeys.datasource ).isBlank() ) {
			datasource = properties.getAsString( ORMKeys.datasource );
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
			saveMapping = properties.getAsBoolean( ORMKeys.saveMapping );
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
	 * Populate and return a Hibernate configuration object using the constructed
	 * properties.
	 */
	public Configuration toHibernateConfig() {
		Configuration configuration = new Configuration();

		if ( this.dbcreate != null ) {
			// @TODO: Use an enum for the dbcreate values, because `create` should be
			// coerced to `create-only`, and a few others need manipulation as well.
			configuration.setProperty( AvailableSettings.HBM2DDL_AUTO, this.dbcreate.toString() );
		}

		if ( this.namingStrategy != null ) {
			PhysicalNamingStrategy namingStrategy = getNamingStrategyForName(
			    this.namingStrategy );
			if ( namingStrategy != null ) {
				configuration.setPhysicalNamingStrategy( namingStrategy );
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
	private Class<IClassRunnable> loadBoxlangClassByPath( String classPath ) {
		String packageName = Paths.get( classPath ).getParent().toString().replace( "/", "." );
		return RunnableLoader.getInstance().loadClass( Paths.get( classPath ), packageName,
		    BoxRuntime.getInstance().getRuntimeContext() );
	}

	public Action toHibernateDBCreate() {
		return DBCreate.valueOf( dbcreate.toUpperCase() ).toHibernateSetting();
	}

	private enum DBCreate {

	    // Standard settings in CFML:
		/**
		 * Update existing schema on Hibernate startup.
		 */
		UPDATE,

		/**
		 * Drop and recreate database schema on Hibernate startup.
		 *
		 * @see https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/tool/schema/Action.html#CREATE
		 */
		DROPCREATE,

		/**
		 * Do no schema generation on Hibernate startup. Default if no `dbcreate`
		 * configuration is specified.
		 *
		 * @see https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/tool/schema/Action.html#NONE
		 */
		NONE,

		/**
		 * "create-only" Hibernate setting. Create schema on Hibernate
		 * startup if it does not exist, but do not drop or update if it already exists.
		 * <p>
		 * NEW for BoxLang, not supported in Adobe or Lucee CFML engines.
		 *
		 * @see https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/tool/schema/Action.html#CREATE_ONLY
		 */
		CREATE,

		/**
		 * Drop schema on hibernate startup, create the schema, then drop the schema on
		 * hibernate shutdown.
		 * <p>
		 * NEW for BoxLang, not supported in Adobe or Lucee CFML engines.
		 *
		 * @see https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/tool/schema/Action.html#CREATE_DROP
		 */
		DROPCREATEDROP,

		/**
		 * Validate schema on Hibernate startup.
		 * <p>
		 * NEW for BoxLang, not supported in Adobe or Lucee CFML engines.
		 *
		 * @see https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/tool/schema/Action.html#VALIDATE
		 */
		VALIDATE,

		/**
		 * Truncate tables on Hibernate startup.
		 * <p>
		 * NEW for BoxLang, not supported in Adobe or Lucee CFML engines.
		 *
		 * @see https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/tool/schema/Action.html#TRUNCATE
		 */
		TRUNCATE;

		public Action toHibernateSetting() {
			switch ( this ) {
				case CREATE :
					return Action.CREATE_ONLY;
				case UPDATE :
					return Action.UPDATE;
				case DROPCREATE :
					return Action.CREATE;
				case NONE :
					return Action.NONE;
				default :
					return Action.NONE;
			}
		}
	}
}
