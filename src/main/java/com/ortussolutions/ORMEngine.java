package com.ortussolutions;

import java.util.Map;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.loader.DynamicClassLoader;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Java class responsible for constructing and managing the Hibernate ORM
 * engine.
 *
 * Constructs and stores Hibernate session factories.
 */
public class ORMEngine {

	/**
	 * The singleton instance of the ORMEngine.
	 */
	private static ORMEngine			instance;

	/**
	 * The logger for the ORMEngine.
	 */
	private static final Logger			logger	= LoggerFactory.getLogger( ORMEngine.class );

	/**
	 * A map of session factories, keyed by name.
	 *
	 * Each web application will have its own session factory which you can look up
	 * by name using {@link #getSessionFactoryForName(Key)}
	 */
	private Map<Key, SessionFactory>	sessionFactories;

	/**
	 * Private constructor for the ORMEngine. Use the getInstance method to get an
	 * instance of the ORMEngine.
	 */
	private ORMEngine() {
		this.sessionFactories = new java.util.HashMap<>();
		setupCustomLogLevels();
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

	/**
	 * Register a new Hibernate session factory with the ORM engine.
	 *
	 * @param name           The name of the session factory - in most cases this
	 *                       will be the web application name.
	 * @param sessionFactory The Hibernate session factory, constructed via the
	 *                       {@link SessionFactoryBuilder}.
	 */
	public void setSessionFactoryForName( Key name, SessionFactory sessionFactory ) {
		logger.info( "Registering new Hibernate session factory for name: {}", name );
		sessionFactories.put( name, sessionFactory );
	}

	/**
	 * Get a Hibernate session factory by name.
	 *
	 * @param name The name of the session factory.
	 *
	 * @return The Hibernate session factory.
	 */
	public SessionFactory getSessionFactoryForName( Key name ) {
		return sessionFactories.get( name );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context The context for which to get a session.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSessionForContext( IBoxContext context ) {
		Key				applicationName	= context.getParentOfType( ortus.boxlang.runtime.context.ApplicationBoxContext.class )
		    .getApplication().getName();
		SessionFactory	sessionFactory	= getSessionFactoryForName( applicationName );

		// Method One: Using Hibernate's session context tracker
		return sessionFactory.getCurrentSession();

		// Method Two: using Boxlang context attachments:
		// Get the nearest JDBC capable context. This can be either a thread or a request.
		// IJDBCCapableContext jdbcContext = context.getParentOfType( IJDBCCapableContext.class );
		// if ( jdbcContext.hasAttachment( ORMKeys.ORMSession ) ) {
		// return ( Session ) jdbcContext.getAttachment( ORMKeys.ORMSession );
		// }
		// return ( Session ) jdbcContext.putAttachment( ORMKeys.ORMSession, sessionFactory.openSession() );
	}

	/**
	 * Shut down the ORM engine, closing all Hibernate session factories and
	 * sessions, and releasing all resources.
	 */
	public void shutdown() {
		// @TODO: "It is the responsibility of the application to ensure that there are
		// no open sessions before calling this method as the impact on those
		// sessions is indeterminate."
		sessionFactories.forEach( ( key, sessionFactory ) -> {
			sessionFactory.close();
		} );
	}

	/**
	 * Set up custom log levels for the ORM engine.
	 *
	 * We can use this method or similar to adjust Hibernate logging levels and pipe them to a destination (log file) of choice.
	 */
	private void setupCustomLogLevels() {
		// How can we put this graciously: the class loader logs are just too much.
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( DynamicClassLoader.class ) ).setLevel( Level.WARN );
	}
}
