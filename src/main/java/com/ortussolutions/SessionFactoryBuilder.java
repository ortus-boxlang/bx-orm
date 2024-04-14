package com.ortussolutions;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

import com.ortussolutions.config.ORMConnectionProvider;
import com.ortussolutions.config.ORMKeys;
import com.ortussolutions.config.naming.MacroCaseNamingStrategy;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class SessionFactoryBuilder {

	private IStruct ormConfig;

	public SessionFactoryBuilder(IStruct ormConfig) {
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
		if (Boolean.FALSE.equals(ormConfig.getAsBoolean(ORMKeys.autoGenMap))) {
			// Here we turn off the automatic generation of entity mappings and
			// instead scan the codebase for orm.xml files.
			// return Arrays.stream(ormConfig.getAsArray(ORMKeys.cfclocation))
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
			throw new BoxRuntimeException(
					"ORMKeys.autoGenMap: the `false` setting value is currently unsupported.");
		} else {
			List<File> files = new java.util.ArrayList<>();
			// Dummy file for testing
			files.add(Paths.get("src/test/resources/bx/models/MyEntity.xml").toFile());
			// @TODO: Here we generate entity mappings and return an array of the
			// generated files.
			return files;
		}
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

		Properties properties = new Properties();
		// @TODO: Any configuration which needs a specific java type (such as the
		// connection provider instance) goes here
		properties.put(AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider(getORMDataSource()));
		configuration.setProperties(properties);
		return configuration;
	}

	private PhysicalNamingStrategy getNamingStrategyForName(String name) {
		return switch (name) {
			/**
			 * Historically, the "smart" naming strategy simply converts camelCase to
			 * MACRO_CASE.
			 */
			case "smart" -> new MacroCaseNamingStrategy();
			/**
			 * Allows apps to define their own naming strategy by providing a ful CFC path.
			 */
			case "CFC_Path" -> throw new BoxRuntimeException("CFC_Path naming strategy is not yet supported.");
			/**
			 * The "default" naming strategy is essentially a no-op, and simply returns the
			 * identifier value unmodified.
			 */
			case "default" -> null;
			default -> null;
		};
	}
}
