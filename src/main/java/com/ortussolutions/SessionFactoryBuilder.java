package com.ortussolutions;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ortussolutions.config.ORMConnectionProvider;
import com.ortussolutions.config.ORMKeys;
import com.ortussolutions.config.naming.BoxLangClassNamingStrategy;
import com.ortussolutions.config.naming.MacroCaseNamingStrategy;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class SessionFactoryBuilder {

	private IStruct ormConfig;

	/**
	 * The application name for this session factory. Used as an identifier in
	 * hash maps.
	 */
	private Key appName;

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger logger = LoggerFactory.getLogger(SessionFactoryBuilder.class);

	public SessionFactoryBuilder(Key appName, IStruct ormConfig) {
		this.appName = appName;
		this.ormConfig = ormConfig;
	}

	public SessionFactory build() {
		Configuration configuration = buildConfiguration();
		getORMMappingFiles(ormConfig).forEach(configuration::addFile);

		return configuration.buildSessionFactory();
	}

	/**
	 * Get the ORM datasource from the ORM configuration.
	 * We currently throw a BoxRuntimeException if no datasource is found in the ORM
	 * configuration, but eventually we will support a default datasource.
	 */
	private DataSource getORMDataSource() {
		Key ormDatasource = ormConfig.getAsKey(Key.datasource);
		if (ormDatasource != null) {
			return BoxRuntime.getInstance().getDataSourceService().get(ormDatasource);
		}
		throw new BoxRuntimeException(
				"ORM configuration is missing 'datasource' key. Default datasources will be supported in a future iteration.");
		// @TODO: Implement this. the hard part is knowing the context.
		// logger.warn( "ORM configuration is missing 'datasource' key; falling back to
		// default datasource" );
		// return currentContext.getConnectionManager().getDefaultDatasourceOrThrow();
	}

	private List<File> getORMMappingFiles(IStruct ormConfig) {
		// @TODO: Should we use the application name, or the ORM configuration hash?
		String xmlMappingLocation = FileSystemUtil.getTempDirectory() + "/orm_mappings/" + getAppName().getName();
		if (Boolean.FALSE.equals(ormConfig.getAsBoolean(ORMKeys.autoGenMap))) {
			// Skip mapping generation and load the pre-generated mappings from this
			// location.
			xmlMappingLocation = ormConfig.getAsString(ORMKeys.cfclocation);
			throw new BoxRuntimeException("ORMKeys.autoGenMap: the `false` setting value is currently unsupported.");
		} else {
			// @TODO: Here we generate entity mappings and populate the temp directory (aka
			// xmlMappingLocation) with the generated files.
			// If `ormConfig.getAsBoolean(ORMKeys.savemapping)` is true, we should save the
			// generated files to `ormConfig.getAsString(ORMKeys.cfclocation)`. Else, we
			// should save them to the temp directory.
			// @TODO: Also remember to use the
			// `ormConfig.getAsBoolean(ORMKeys.skipCFCWithError)` setting to determine
			// whether to throw exceptions on compile-time errors.
		}
		// Regardless of the autoGenMap configuration value, we now have a directory
		// populated with JPA orm.xml mapping files.
		// We now need to load these files into the Hibernate configuration.
		// return Arrays.stream(xmlMappingLocation.split(","))
		// .flatMap(path -> {
		// try {
		// return Files.walk(Paths.get(path), 1);
		// } catch (IOException e) {
		// throw new BoxRuntimeException("Error walking cfclcation path: " +
		// path.toString(), e);
		// }
		// })
		// // filter to valid orm.xml files
		// .filter(filePath -> FilePath.endsWith(".orm.xml"))
		// // @TODO: I'm unsure, but we may need to convert the string path to a File
		// // object to work around JPA's classpath limitations.
		// // We should first try this without the conversion and see if it works.
		// .map(filePath -> filePath.toFile())
		// .toList();

		// Alternative test implementation
		List<File> files = new java.util.ArrayList<>();
		// Dummy file for testing
		files.add(Paths.get("src/test/resources/bx/models/MyEntity.xml").toFile());
		return files;
	}

	private Configuration buildConfiguration() {
		Configuration configuration = new Configuration();

		// @TODO: generic config goes here
		if (ormConfig.containsKey(ORMKeys.namingStrategy)) {
			PhysicalNamingStrategy namingStrategy = getNamingStrategyForName(
					ormConfig.getAsString(ORMKeys.namingStrategy));
			if (namingStrategy != null) {
				configuration.setPhysicalNamingStrategy(namingStrategy);
			}
		}

		if (ormConfig.containsKey(ORMKeys.dialect)) {
			String dialect = ormConfig.getAsString(ORMKeys.dialect);
			if (dialect != null && !dialect.isBlank()) {
				logger.warn(
						"Setting 'dialect' in Hibernate 6.0+ is unnecessary on all Hibernate-supported databases. Ignoring 'dialect' configuration for now.");
				// @TODO: Until we implement a method to resolve dialect short names, like
				// `MYSQL` to full
				// class names, Hibernate will throw errors on startup as 90% of dialect names
				// will not be found. Should we drop `dialect` support entirely, or should we
				// add full support for a mostly unnecessary feature?
				// See warning at
				// https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/cfg/JdbcSettings.html#DIALECT
				// configuration.setProperty(AvailableSettings.DIALECT, dialect);
			}
		}

		if (ormConfig.containsKey(ORMKeys.schema)) {
			String schema = ormConfig.getAsString(ORMKeys.schema);
			if (schema != null && !schema.isBlank()) {
				configuration.setProperty(AvailableSettings.DEFAULT_SCHEMA, schema);
			}
		}

		if (ormConfig.containsKey(ORMKeys.catalog)) {
			String catalog = ormConfig.getAsString(ORMKeys.catalog);
			if (catalog != null && !catalog.isBlank()) {
				configuration.setProperty(AvailableSettings.DEFAULT_CATALOG, catalog);
			}
		}

		if (ormConfig.containsKey(ORMKeys.sqlScript)) {
			String sqlScript = ormConfig.getAsString(ORMKeys.sqlScript);
			if (sqlScript != null && !sqlScript.isBlank()
					&& ormConfig.getAsString(ORMKeys.dbcreate).equals("dropcreate")) {
				if (new File(sqlScript).exists()) {
					// @TODO: We could possibly upgrade this to use the JPA setting:
					// `JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE`, but we'd have to test to see if that
					// script executes *after* the schema generation (correct behavior), or *in
					// place of* the schema generation (incorrect behavior).
					configuration.setProperty(AvailableSettings.HBM2DDL_IMPORT_FILES, sqlScript);
				} else {
					logger.error("ORM Configuration `sqlScript` file not found: {}", sqlScript);
				}
			}
		}

		Properties properties = new Properties();
		// @TODO: Any configuration which needs a specific java type (such as the
		// connection provider instance) goes here
		properties.put(AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider(getORMDataSource()));
		configuration.setProperties(properties);
		return configuration;
	}

	private PhysicalNamingStrategy getNamingStrategyForName(String name) {
		// @TODO: Use an enum for the naming strategies
		return switch (name.toLowerCase()) {
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
			default -> new BoxLangClassNamingStrategy(loadBoxlangClassByPath(name));
		};
	}

	/**
	 * Load and instantiate the Boxlang class by its string path. Useful for using a
	 * .bx class as a custom naming strategy.
	 *
	 * @param classPath The path to the Boxlang class, either slash or dot
	 *                  delimited.
	 */
	private Class<IClassRunnable> loadBoxlangClassByPath(String classPath) {
		String packageName = Paths.get(classPath).getParent().toString().replace("/", ".");
		return RunnableLoader.getInstance().loadClass(Paths.get(classPath), packageName,
				BoxRuntime.getInstance().getRuntimeContext());
	}

	/**
	 * Get the application name for this session factory.
	 * /**
	 *
	 * Get the application name for this session factory. Used as an identifier in
	 * hash maps.
	 */
	private Key getAppName() {
		return appName;
	}
}
