/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.BaseService;

/**
 * Java class responsible for constructing and managing the Hibernate ORM
 * engine.
 *
 * Constructs and stores Hibernate session factories.
 */
public class ORMService extends BaseService {

	/**
	 * The logger for the ORMEngine.
	 */
	private BoxLangLogger		logger;

	/**
	 * A map of ORM applications, keyed by the unique name of the ORM application.
	 */
	private Map<Key, ORMApp>	ormApps					= new ConcurrentHashMap<>();

	/**
	 * Interception points for the ORM service.
	 */
	private static final Key[]	ORM_INTERCEPTION_POINTS	= List.of(
	    ORMKeys.EVENT_POST_NEW,
	    ORMKeys.EVENT_POST_LOAD
	).toArray( new Key[ 0 ] );

	/**
	 * --------------------------------------------------------------------------
	 * Constructors
	 * --------------------------------------------------------------------------
	 */

	/**
	 * public no-arg constructor for the ServiceProvider
	 */
	public ORMService() {
		this( BoxRuntime.getInstance() );
	}

	/**
	 * Constructor
	 *
	 * @param runtime The BoxRuntime
	 */
	public ORMService( BoxRuntime runtime ) {
		super( runtime, ORMKeys.ORMService );
		getLogger().debug( "ORMService built" );

		// Attach appender to Hibernate logging categories
		String[]		hibernateCategories	= {
		    "org.hibernate.SQL",
		    "org.hibernate.type.descriptor.sql",
		    "org.hibernate.event",
		    "org.hibernate.cache",
		    "org.hibernate.stat"
		};

		// TODO: Make this configurable. For now, log debug so it can assist us in debugging.
		LoggerContext	loggerContext		= runtime.getLoggingService().getLoggerContext();
		for ( String category : hibernateCategories ) {
			Logger hibernateLogger = loggerContext.getLogger( category );
			// hibernateLogger.addAppender( consoleAppender );
			hibernateLogger.addAppender( getLogger().getAppender( "orm" ) );
			// Add a console appender to the Hibernate logger
			hibernateLogger.setLevel( Level.DEBUG );
			hibernateLogger.setAdditive( false ); // Prevent messages from going to parent loggers
		}

		runtime.getInterceptorService().registerInterceptionPoint( ORM_INTERCEPTION_POINTS );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Runtime Service Event Methods
	 * --------------------------------------------------------------------------
	 */

	/**
	 * The configuration load event is fired when the runtime loads the configuration
	 */
	@Override
	public void onConfigurationLoad() {
		// Not used by the service, since those are only for core services
	}

	/**
	 * Start up the ORM service. Unless and until ORM is supported at the runtime level, this method is essentially a no-op.
	 */
	@Override
	public void onStartup() {
		getLogger().info( "+ ORMService started" );
	}

	/**
	 * Shut down the ORM service, including all ORM applications.
	 */
	@Override
	public void onShutdown( Boolean force ) {
		getLogger().info( "+ ORMService shutdown requested" );
		this.ormApps.forEach( ( key, ormApp ) -> ormApp.shutdown() );
		this.ormApps.clear();
	}

	/**
	 * --------------------------------------------------------------------------
	 * Service Methods
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Start up a new ORM application with the given context and ORM configuration.
	 *
	 * @param context The IBoxContext for the application.
	 * @param config  The ORM configuration - parsed from the application settings.
	 *
	 * @return The ORM application that was started.
	 */
	public ORMApp startupApp( RequestBoxContext context, ORMConfig config ) {
		// We store the new ORM application in the application context, so we can retrieve it later.
		// The BoxLang application can have 0-n ORM applications, so we need to store them in the application context.
		return context.getApplicationContext().computeAttachmentIfAbsent( ORMKeys.ORMApp, key -> {
			ORMApp newORMApp = new ORMApp( context, config );

			if ( getLogger().isDebugEnabled() )
				getLogger().debug( "Starting ORMApp {}", newORMApp.getUniqueName() );

			this.ormApps.put( newORMApp.getUniqueName(), newORMApp );
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
		Key appName = ORMApp.getUniqueAppName( context );
		this.shutdownApp( appName );
		context.getRequestContext().removeAttachment( ORMKeys.ORMRequestContext );
		context.getApplicationContext().removeAttachment( appName );
	}

	/**
	 * Shut down an ORM application by unique name
	 * <p>
	 * Will retrieve and close all session factories associated with the provided context.
	 *
	 * @param uniqueAppName The unique name of the ORM application to shut down.
	 */
	public void shutdownApp( Key uniqueAppName ) {
		ORMApp app = ormApps.get( uniqueAppName );
		if ( app != null ) {
			app.shutdown();
			this.ormApps.remove( uniqueAppName );
		}
	}

	/**
	 * Reload the ORM application for the given context.
	 *
	 * @param context The IBoxContext for the application.
	 *
	 * @return The reloaded ORM application.
	 */
	public ORMApp reloadApp( IBoxContext context ) {
		this.shutdownApp( context );
		return this.startupApp( context.getRequestContext(), ORMConfig.loadFromContext( context.getRequestContext() ) );
	}

	/**
	 * Retrieve the ORM application configured for the given context.
	 *
	 * @param context The IBoxContext for the current request. The parent application context is used for the ORM application lookup.
	 */
	public ORMApp getORMApp( IBoxContext context ) {
		Key name = Key.of( ORMApp.getUniqueAppName( context ) );
		if ( !this.ormApps.containsKey( name ) ) {
			throw new RuntimeException( "ORMApp not found for context: " + name );
		}
		return this.ormApps.get( name );
	}

	/**
	 * Get the ORM logger that logs to the "orm" category.
	 */
	public BoxLangLogger getLogger() {
		if ( this.logger == null ) {
			synchronized ( ORMService.class ) {
				if ( this.logger == null ) {
					this.logger = runtime.getLoggingService().getLogger( "orm" );
				}
			}
		}
		return this.logger;
	}
}
