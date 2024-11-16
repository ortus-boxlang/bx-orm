package ortus.boxlang.modules.orm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.loader.DynamicClassLoader;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.IService;

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
	private static ORMService	instance;

	/**
	 * The logger for the ORMEngine.
	 */
	private static final Logger	logger	= LoggerFactory.getLogger( ORMService.class );

	private Map<Key, ORMApp>	ormApps;

	/**
	 * Private constructor for the ORMEngine. Use the getInstance method to get an
	 * instance of the ORMEngine.
	 */
	private ORMService() {
		this.ormApps = new ConcurrentHashMap<>();
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
	 * Start up a new ORM application with the given context and ORM configuration.
	 * 
	 * @param context The IBoxContext for the application.
	 * @param config  The ORM configuration - parsed from the application settings.
	 */
	public void startupApp( RequestBoxContext context, ORMConfig config ) {
		context.getApplicationContext().computeAttachmentIfAbsent( ORMKeys.ORMApp, ( key ) -> {
			ORMApp newORMApp = new ORMApp( context, config );
			logger.debug( "Starting ORMApp {}", newORMApp.getUniqueName() );
			ormApps.put( newORMApp.getUniqueName(), newORMApp );

			newORMApp.startup();
			return newORMApp;
		} );
	}

	/**
	 * Shut down a particular ORM application by request context.
	 * <p>
	 * Will retrieve and close all session factories associated with the provided context.
	 * 
	 * @param context The IBoxContext for the application.
	 */
	public void shutdownApp( IBoxContext context ) {
		this.shutdownApp( ORMApp.getUniqueAppName( context ) );
	}

	/**
	 * Shut down an ORM application by unique name
	 * <p>
	 * Will retrieve and close all session factories associated with the provided context.
	 * 
	 * @param context The IBoxContext for the application.
	 */
	public void shutdownApp( Key uniqueAppName ) {
		ORMApp app = ormApps.get( uniqueAppName );
		if ( app != null ) {
			app.shutdown();
			ormApps.remove( uniqueAppName );
		}
	}

	/**
	 * Retrieve the ORM application configured for the given context.
	 * 
	 * @param context The IBoxContext for the current request. The parent application context is used for the ORM application lookup.
	 */
	public ORMApp getORMApp( IBoxContext context ) {
		Key name = Key.of( ORMApp.getUniqueAppName( context ) );
		if ( !ormApps.containsKey( name ) ) {
			throw new RuntimeException( "ORMApp not found for context: " + name );
		}
		return ormApps.get( name );
	}

	/**
	 * Shut down the ORM service, including all ORM applications.
	 */
	public void onShutdown( Boolean force ) {
		logger.info( "ORMService shutdown" );
		ormApps.forEach( ( key, ormApp ) -> {
			ormApp.shutdown();
		} );
		ormApps.clear();
	}

	/**
	 * Set up custom log levels for the ORM engine.
	 *
	 * @TODO: use this method or similar to adjust Hibernate logging levels and pipe them to a destination (log file) of choice.
	 */
	private void setupCustomLogLevels() {
		Logger ormLogger = LoggerFactory.getLogger( "ortus.boxlang.modules.orm" );

		if ( ! ( ormLogger instanceof ch.qos.logback.classic.Logger ) ) {
			logger.warn( "Could not adjust log levels; logger is not a LogBack instance." );
			return;
		}

		Level	ORMModuleLevel	= Level.DEBUG;
		Level	customLogLevel	= Level.WARN;

		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( "ortus.boxlang.modules.orm" ) ).setLevel( ORMModuleLevel );

		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( "com.zaxxer.hikari" ) ).setLevel( customLogLevel );
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( "org.hibernate" ) ).setLevel( customLogLevel );
		// How can we put this graciously: the class loader logs are just too much.
		( ( ch.qos.logback.classic.Logger ) LoggerFactory.getLogger( DynamicClassLoader.class ) ).setLevel( customLogLevel );
	}
}
