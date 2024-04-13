package com.ortussolutions;

import java.util.Properties;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

import com.ortussolutions.config.ORMKeys;
import com.ortussolutions.config.ORMConnectionProvider;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Java class responsible for constructing and managing the Hibernate ORM engine.
 */
public class ORMEngine {

	private static ORMEngine			instance;

	private static final Logger			logger	= LoggerFactory.getLogger( ORMEngine.class );

	private Map<Key, SessionFactory>	sessionFactories;

	/**
	 * Private constructor for the ORMEngine. Use the getInstance method to get an instance of the ORMEngine.
	 */
	private ORMEngine() {
	}

	/**
	 * Get an instance of the ORMEngine.
	 *
	 * @return An instance of the ORMEngine.
	 */
	public static synchronized ORMEngine getInstance() {
		if ( instance == null ) {
			instance = new ORMEngine();
		}
		return instance;
	}

	public void setSessionFactoryForName( Key name, SessionFactory sessionFactory ) {
		logger.info( "Registering new Hibernate session factory for name: {}", name );
		sessionFactories.put( name, sessionFactory );
	}

	public SessionFactory getSessionFactoryForName( Key name ) {
		return sessionFactories.get( name );
	}

	/**
	 * Shut down the ORM engine, closing all Hibernate session factories and sessions, and releasing all resources.
	 */
	public void shutdown() {
		// @TODO: "It is the responsibility of the application to ensure that there are no open sessions before calling this method as the impact on those
		// sessions is indeterminate."
		sessionFactories.forEach( ( key, sessionFactory ) -> {
			sessionFactory.close();
		} );
	}
}
