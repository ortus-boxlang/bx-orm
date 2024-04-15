package com.ortussolutions;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ortussolutions.config.ORMConfig;
import com.ortussolutions.config.ORMConnectionProvider;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class SessionFactoryBuilder {

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private static final Logger logger = LoggerFactory.getLogger(SessionFactoryBuilder.class);

	/**
	 * The ORM datasource for this session factory.
	 */
	private DataSource datasource;

	/**
	 * The ORM configuration for this session factory.
	 */
	private ORMConfig ormConfig;

	/**
	 * The application name for this session factory. Used as an identifier in
	 * hash maps.
	 */
	private Key appName;

	public SessionFactoryBuilder(Key appName, IStruct properties) {
		this.appName = appName;
		this.ormConfig = new ORMConfig(properties);
		this.datasource = getORMDataSource();
	}

	public SessionFactory build() {
		Configuration configuration = buildConfiguration();
		getORMMappingFiles().forEach(configuration::addFile);

		return configuration.buildSessionFactory();
	}

	/**
	 * Get the ORM datasource from the ORM configuration.
	 * We currently throw a BoxRuntimeException if no datasource is found in the ORM
	 * configuration, but eventually we will support a default datasource.
	 */
	private DataSource getORMDataSource() {
		String ormDatasource = this.ormConfig.getDatasourceName();
		if (ormDatasource != null) {
			return BoxRuntime.getInstance().getDataSourceService().get(Key.of(ormDatasource));
		}
		throw new BoxRuntimeException(
				"ORM configuration is missing 'datasource' key. Default datasources will be supported in a future iteration.");
		// @TODO: Implement this. the hard part is knowing the context.
		// logger.warn( "ORM configuration is missing 'datasource' key; falling back to
		// default datasource" );
		// return currentContext.getConnectionManager().getDefaultDatasourceOrThrow();
	}

	private List<File> getORMMappingFiles() {
		// @TODO: Should we use the application name, or the ORM configuration hash?
		String xmlMappingLocation = FileSystemUtil.getTempDirectory() + "/orm_mappings/" + getAppName().getName();
		if (!ormConfig.isAutoGenMap()) {
			// Skip mapping generation and load the pre-generated mappings from this
			// location.
			xmlMappingLocation = ormConfig.getCFCLocation();
			throw new BoxRuntimeException("ORMConfiguration setting `autoGenMap=false` is currently unsupported.");
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
		Configuration configuration = ormConfig.toHibernateConfig();

		Properties properties = new Properties();
		// @TODO: Any configuration which needs a specific java type (such as the
		// connection provider instance) goes here
		properties.put(AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider(this.datasource));
		configuration.addProperties(properties);
		return configuration;
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
