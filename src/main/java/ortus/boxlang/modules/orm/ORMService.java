package ortus.boxlang.modules.orm;

import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.loader.DynamicClassLoader;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.IService;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Java class responsible for constructing and managing the Hibernate ORM
 * engine.
 *
 * Constructs and stores Hibernate session factories.
 */
public class ORMService implements IService {

	/**
	 * The singleton instance of the ORMEngine.
	 */
	private static ORMService			instance;

	/**
	 * The logger for the ORMEngine.
	 */
	private static final Logger			logger	= LoggerFactory.getLogger( ORMService.class );

	/**
	 * A map of session factories, keyed by name.
	 *
	 * Each web application will have its own session factory which you can look up
	 * by name using {@link #getSessionFactoryForName(Key)}
	 */
	private Map<Key, SessionFactory>	sessionFactories;

	private Map<Key, List<Key>>			sessionFactoriesByApp;

	/**
	 * Private constructor for the ORMEngine. Use the getInstance method to get an
	 * instance of the ORMEngine.
	 */
	private ORMService() {
		this.sessionFactories		= new java.util.HashMap<>();
		this.sessionFactoriesByApp	= new java.util.HashMap<>();
		setupCustomLogLevels();
	}

	/**
	 * Retrieve the keyed name of this service.
	 */
	public Key getName() {
		return ORMKeys.ORMService;
	}

	/**
	 * Start up the ORM service. Unless and until ORM is supported at the runtime level, this method is essentially a no-op.
	 */
	public void onStartup() {
		logger.info( "ORMService started" );
	}

	/**
	 * Get an instance of the ORMEngine.
	 *
	 * @return An instance of the ORMEngine.
	 */
	public static synchronized ORMService getInstance() {
		if ( instance == null ) {
			instance = new ORMService();
		}
		return instance;
	}

	/**
	 * Register a new Hibernate session factory with the ORM engine.
	 *
	 * @param name           The name of the session factory - in most cases this
	 *                       will be the web application name plus datasource name.
	 * @param sessionFactory The Hibernate session factory, constructed via the
	 *                       {@link SessionFactoryBuilder}.
	 */
	public void setSessionFactoryForName( Key name, SessionFactory sessionFactory ) {
		logger.info( "Registering new Hibernate session factory for name: {}", name );
		sessionFactories.put( name, sessionFactory );
	}

	/**
	 * Start up a new ORM application with the given context and ORM configuration.
	 * 
	 * @param context The IBoxContext for the application.
	 * @param config  The ORM configuration - parsed from the application settings.
	 */
	public void startupApp( RequestBoxContext context, ORMConfig config ) {
		if ( context.getParentOfType( ApplicationBoxContext.class ) == null ) {
			logger.error( "ORMService startupApp called with a context that is not inside an application context; aborting." );
			return;
		}
		Key								appName				= Key.of( SessionFactoryBuilder.getUniqueAppName( context ) );

		Map<String, List<EntityRecord>>	entityDatasources	= discoverEntities( context, config );
		logger.debug( "Discovered entities on {} datasources:", entityDatasources.size() );

		entityDatasources.forEach( ( datasourceName, entities ) -> {
			logger.debug( "Creating session factory for datasource: {}", datasourceName );
			DataSource				datasource	= context.getConnectionManager().getDatasourceOrThrow( Key.of( datasourceName ) );

			SessionFactoryBuilder	builder		= new SessionFactoryBuilder( context, datasource, config, entities );
			SessionFactory			factory		= builder.build();

			setSessionFactoryForName( builder.getUniqueName(), factory );
			logger.info( "Session factory created! {}", factory );

			sessionFactoriesByApp.putIfAbsent( appName, new java.util.ArrayList<>() );
			this.sessionFactoriesByApp.get( appName ).add( builder.getUniqueName() );
		} );
	}

	/**
	 * Shut down a particular ORM application.
	 * <p>
	 * Will retrieve and close all session factories associated with the provided context.
	 * 
	 * @param context The IBoxContext for the application.
	 */
	public void shutdownApp( IBoxContext context ) {
		Key			appName					= Key.of( SessionFactoryBuilder.getUniqueAppName( context ) );
		List<Key>	sessionFactoriesToClose	= sessionFactoriesByApp.get( appName );
		if ( sessionFactoriesToClose != null ) {
			sessionFactoriesToClose.forEach( ( name ) -> {
				SessionFactory sessionFactory = sessionFactories.get( name );
				sessionFactory.close();
				sessionFactories.remove( name );
			} );
		}
	}

	/**
	 * Get a Hibernate session factory by unique key.
	 *
	 * @param name The unique key for this session factory - usually the application name plus datasource name.
	 *
	 * @return The Hibernate session factory.
	 */
	public SessionFactory getSessionFactoryForName( Key name ) {
		return sessionFactories.get( name );
	}

	/**
	 * Get a Hibernate session factory for the given context and datasource combo.
	 * 
	 * @param context    The IBoxContext. MUST have a parent ApplicationBoxContext.
	 * @param datasource The datasource this session factory acts upon
	 * 
	 * @return
	 */
	public SessionFactory getSessionFactoryForContextAndDataSource( IBoxContext context, DataSource datasource ) {
		return getSessionFactoryForName( SessionFactoryBuilder.getUniqueName( context, datasource ) );
	}

	/**
	 * Get a Hibernate session factory for a given Boxlang context. Will return the session factory matching the default datasource.
	 *
	 * @param context The context for which to get a session.
	 *
	 * @return The Hibernate session.
	 */
	public SessionFactory getSessionFactoryForContext( IBoxContext context ) {
		return getSessionFactoryForContext( context, null );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context    The context for which to get a session.
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public SessionFactory getSessionFactoryForContext( IBoxContext context, Key datasource ) {
		return getSessionFactoryForContextAndDataSource( context, getDatasourceForNameOrDefault( context, datasource ) );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context The context for which to get a session.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSessionForContext( IBoxContext context ) {
		return getSessionForContext( context, null );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context    The context for which to get a session.
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSessionForContext( IBoxContext context, Key datasource ) {
		IBoxContext	jdbcContext		= ( IBoxContext ) context.getParentOfType( IJDBCCapableContext.class );
		String		datasourceName	= getDatasourceForNameOrDefault( context, datasource ).getUniqueName().getName();
		Key			sessionKey		= Key.of( "session_" + datasourceName );

		if ( jdbcContext.hasAttachment( sessionKey ) ) {
			return jdbcContext.getAttachment( sessionKey );
		}

		SessionFactory sessionFactory = getSessionFactoryForContext( context, datasource );
		jdbcContext.putAttachment( sessionKey, sessionFactory.openSession() );
		return jdbcContext.getAttachment( sessionKey );
	}

	/**
	 * Shut down the ORM service. Will perform cleanup on all Hibernate resources - session factories, open sessions and connections, etc.
	 */
	public void onShutdown( Boolean force ) {
		logger.info( "ORMService shutdown" );
		// @TODO: "It is the responsibility of the application to ensure that there are
		// no open sessions before calling this method as the impact on those
		// sessions is indeterminate."
		sessionFactories.forEach( ( key, sessionFactory ) -> {
			sessionFactory.close();
		} );
		sessionFactoriesByApp.clear();
	}

	/**
	 * Retrieve the entity map for this session factory, constructing them if necessary.
	 * 
	 * @return a map of datasource UNIQUE names to a list of EntityRecords.
	 */
	public static Map<String, List<EntityRecord>> discoverEntities( IBoxContext context, ORMConfig ormConfig ) {
		if ( !ormConfig.autoGenMap ) {
			// Skip mapping generation and load the pre-generated mappings from `ormConfig.entityPaths`
			throw new BoxRuntimeException( "ORMConfiguration setting `autoGenMap=false` is currently unsupported." );
		} else {
			// generate xml mappings on the fly, saving them either to a temp directory or alongside the entity class files if `ormConfig.saveMapping` is true.
			return new MappingGenerator( context, ormConfig )
			    .generateMappings()
			    .getEntityDatasourceMap();
		}
	}

	private DataSource getDatasourceForNameOrDefault( IBoxContext context, Key datasourceName ) {
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		return datasourceName != null ? connectionManager.getDatasourceOrThrow( datasourceName )
		    : connectionManager.getDefaultDatasourceOrThrow();
	}

	/**
	 * Set up custom log levels for the ORM engine.
	 *
	 * @TODO: use this method or similar to adjust Hibernate logging levels and pipe them to a destination (log file) of choice.
	 */
	private void setupCustomLogLevels() {
		ch.qos.logback.classic.Level ORMModuleLevel = ch.qos.logback.classic.Level.DEBUG;
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( "ortus.boxlang.modules.orm" ) ).setLevel( ORMModuleLevel );

		ch.qos.logback.classic.Level customLogLevel = ch.qos.logback.classic.Level.WARN;
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( "com.zaxxer.hikari" ) ).setLevel( customLogLevel );
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( "org.hibernate" ) ).setLevel( customLogLevel );
		// How can we put this graciously: the class loader logs are just too much.
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( DynamicClassLoader.class ) ).setLevel( customLogLevel );
	}
}
