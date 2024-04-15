package com.ortussolutions.config;

import ortus.boxlang.runtime.types.IStruct;

public class ORMConfig {

	/**
	 * Specifies whether ColdFusion should automatically generate entity mappings
	 * for the persistent CFCs. If autogenmap=false, the mapping should be
	 * provided in the form of <code>hbm.xml</code> files.
	 */
	private boolean autoGenMap;

	/**
	 * Allows the engine to manage the Hibernate session. It is recommended not to
	 * let the engine manage it for you.
	 *
	 * Use transaction blocks in order to demarcate your regions that should start,
	 * flush and end a transaction.
	 */
	private boolean autoManageSession;

	/**
	 * Specifies the location of the configuration file that the secondary cache
	 * provider should use. This setting is used only when
	 * secondaryCacheEnabled=true
	 */
	private boolean cacheConfig;

	/**
	 * Specifies the cache provider that ORM should use as a secondary cache. The
	 * values can be:
	 *
	 * Ehcache
	 * ConcurrentHashMap
	 *
	 * The fully qualified name of the class for any other cache provider
	 */
	private String cacheProvider;

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
	private String cfclocation;

	/**
	 * This setting defines the data source to be utilized by the ORM. If not used,
	 * defaults to the this.datasource in the Application.cfc.
	 */
	private String datasource;

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
	private String dbcreate;

	/**
	 * The dialect to use for your database. By default Hibernate will introspect
	 * the datasource and try to figure it out. See the dialects section below.
	 *
	 * You can also use the fully Java qualified name of the class.
	 */
	private String dialect;

	/**
	 * If true, then it enables the ORM event callbacks in entities and globally via
	 * the `eventHandler` property.
	 */
	private boolean eventHandling;

	/**
	 * The CFC path of the CFC that will manage the global ORM events.
	 */
	private String eventHandler;

	/**
	 * Specifies if an orm flush should be called automatically at the end of a
	 * request. In our opinion this SHOULD never be true. Database persistence
	 * should be done via transaction tags and good transaction demarcation.
	 */
	private boolean flushAtRequestEnd;

	/**
	 * Specifies if the SQL queries should be logged to the console.
	 */
	private boolean logSQL;

	/**
	 * Defines the naming convention to use on table and column names.
	 *
	 * - default : Uses the table or column names as is
	 * - smart : This strategy changes the logical table or column name to
	 * uppercase.
	 * - CFC PATH : Use your own CFC to determine naming
	 */
	private String namingStrategy;

	/**
	 * The path to a custom Hibernate configuration file:
	 *
	 * - hibernate.properties
	 * - hibernate.cfc.xml
	 */
	private String ormConfig;

	/**
	 * If enabled, the ORM will create the Hibernate mapping XML (*.hbmxml) files
	 * alongside the entities. This is great for debugging your entities and
	 * relationships.
	 *
	 */
	private boolean saveMapping;

	/**
	 * The default database schema to use for database connections. This can be
	 * overriden at the datasource level.
	 */
	private String schema;

	/**
	 * Specifies the default Database Catalog that ORM should use. This can be
	 * overriden at the datasource level.
	 */
	private String catalog;

	/**
	 * Enable the secondary cache or not.
	 */
	private boolean secondaryCacheEnabled;

	/**
	 * If true, then the ORM will ignore CFCs that have compile time errors in them.
	 * Use false to throw exceptions.
	 */
	private boolean skipCFCWithError;

	/**
	 * Path to a SQL script file that will be executed after the ORM is initialized.
	 * Only used if dbcreate is set to <code>dropcreate</code>.
	 */
	private String sqlScript;

	/**
	 * Specifies whether the database has to be inspected to identify the missing
	 * information required to generate the Hibernate mapping.
	 *
	 * The database is inspected to get the column data type, primary key and
	 * foreign key information.
	 */
	private boolean useDBForMapping;

	/**
	 * Constructor
	 */
	public ORMConfig(IStruct properties) {
		process(properties);
	}

	/**
	 * Process the properties
	 */
	private void process(IStruct properties) {
		if (properties == null) {
			return;
		}
		if (properties.containsKey(ORMKeys.autoGenMap)) {
			autoGenMap = properties.getAsBoolean(ORMKeys.autoGenMap);
		}
	}
}
