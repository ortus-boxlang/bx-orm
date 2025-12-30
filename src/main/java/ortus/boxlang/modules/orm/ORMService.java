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
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.hibernate.metadata.ClassMetadata;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.hibernate.BoxProxy;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.BaseApplicationListener;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.BaseService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Java class responsible for constructing and managing the Hibernate ORM
 * engine.
 *
 * Constructs and stores Hibernate session factories.
 *
 * @since 1.0.0
 */
public class ORMService extends BaseService {

	public static final String	BX_CLASS_SUFFIX			= "$bx";
	public static final String	CFC_CLASS_SUFFIX		= "$cfc";
	public static final String	COMPILED_CLASS_PREFIX	= "boxgenerated.class.";

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
		getLogger().trace( "ORMService built" );

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
			hibernateLogger.setLevel( logger.isDebugEnabled() ? Level.DEBUG : Level.INFO );
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
		getLogger().debug( "+ ORMService started" );
	}

	/**
	 * Shut down the ORM service, including all ORM applications.
	 */
	@Override
	public void onShutdown( Boolean force ) {
		getLogger().debug( "+ ORMService shutdown requested" );
		this.ormApps.forEach( ( key, ormApp ) -> ormApp.shutdown() );
		this.ormApps.clear();
	}

	/**
	 * --------------------------------------------------------------------------
	 * ORM Application Methods
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Stringify the value for ORM configuration uniqueness detection.
	 *
	 * @param value The value to stringify. Arrays, structs, and string-castable values are supported.
	 */
	public static String stringify( Object value ) {
		if ( value instanceof Array valArray ) {
			return valArray.stream()
			    .map( StringCaster::cast )
			    .collect( Collectors.joining( "," ) );
		}
		if ( value instanceof IStruct valStruct ) {
			return valStruct.entrySet().stream()
			    .sorted( Map.Entry.comparingByKey() )
			    .map( entry -> StringCaster.cast( entry.getKey().getName() ) + "=" + stringify( entry.getValue() ) )
			    .collect( Collectors.joining( "," ) );
		}
		return StringCaster.cast( value );
	}

	/**
	 * Helper method to construct ORM based application names, which rely on the unique combination
	 * of an application name and the application's configuration.
	 *
	 * @param appName The application name.
	 * @param config  The application configuration.
	 *
	 * @return A unique key for the given application name and configuration.
	 */
	public static Key buildUniqueAppName( Key appName, IStruct config ) {
		List<Key>	hashableKeys	= List.of( ORMKeys.ORMEnabled, ORMKeys.ORMSettings, Key.datasource );
		String		ormSettingKey	= hashableKeys.stream().map( key -> {
										return stringify( config.containsKey( key ) ? config.get( key ) : "" );
									} )
		    .collect( Collectors.joining( "_" ) );
		return Key.of( new StringBuilder( appName.getNameNoCase() ).append( "_" ).append( ormSettingKey.hashCode() ).toString() );
	}

	/**
	 * Build a unique application name from the given context.
	 *
	 * @param context The IBoxContext for the application.
	 *
	 * @return A unique key for the given application name and configuration.
	 */
	public static Key getAppNameFromContext( IBoxContext context ) {
		RequestBoxContext requestContext = context.getRequestContext();
		if ( requestContext == null ) {
			throw new BoxRuntimeException( "No request context available to build unique ORM application name." );
		}
		IStruct settings = requestContext.getApplicationListener().getSettings();
		return buildUniqueAppName( context.getApplicationContext().getApplication().getName(), settings );
	}

	/**
	 * Start up a new ORM application with the given context and ORM configuration.
	 * Please note that a new ORM application will be constructed if the following conditions are met:
	 * - An application listener name changes
	 * - The application.bx configuration changes
	 *
	 * @param context          The IBoxContext for the application.
	 * @param config           The ORM configuration - parsed from the application settings.
	 * @param startingListener The listener that is starting the ORM application.
	 *
	 * @return A new or existing ORM application if started already.
	 */
	public ORMApp startupApp( RequestBoxContext context, ORMConfig config, BaseApplicationListener startingListener ) {
		Key appName = ORMService.buildUniqueAppName( startingListener.getAppName(), startingListener.getSettings() );
		// Atomically create or get the ORMApp for the given context.
		return this.ormApps.computeIfAbsent(
		    appName,
		    key -> new ORMApp( context, config, appName ).startup()
		);
	}

	/**
	 * Retrieve the entity name for the given entity object
	 *
	 * @param entity
	 *
	 * @return
	 */
	public static String getEntityName( Object entity ) {
		if ( entity instanceof BoxProxy proxyEntity ) {
			return proxyEntity.getHibernateLazyInitializer().getEntityName();
		} else if ( entity instanceof IClassRunnable boxClass ) {
			return getEntityName( boxClass );
		} else {
			if ( entity instanceof String entityString ) {
				throw new BoxRuntimeException(
				    "The string provided " + entityString + " is not a valid BoxLang ORM entity or proxy." );
			} else {

				throw new BoxRuntimeException(
				    "The entity instance of " + entity.getClass().getName() + " is not a valid BoxLang ORM entity or proxy." );
			}
		}
	}

	/**
	 * Retrieve the entity name for the given entity class.
	 *
	 * @param entity Instance of IClassRunnable, aka the compiled/parsed entity.
	 */
	public static String getEntityName( IClassRunnable entity ) {
		// @TODO: Should we look up the EntityRecord and use that to grab the class name?
		IStruct annotations = entity.getAnnotations();
		if ( annotations.containsKey( ORMKeys.entity ) && !annotations.getAsString( ORMKeys.entity ).isBlank() ) {
			return annotations.getAsString( ORMKeys.entity );
		} else if ( annotations.containsKey( ORMKeys.entityName ) && !annotations.getAsString( ORMKeys.entityName ).isBlank() ) {
			return annotations.getAsString( ORMKeys.entityName );
		} else {
			return getClassNameFromFQN( entity.bxGetName().getName() );
		}
	}

	/**
	 * Retrieve the last portion of the FQN as the class name.
	 *
	 * @param fqn Boxlang class FQN, like models.orm.foo
	 */
	public static String getClassNameFromFQN( String fqn ) {
		return fqn.substring( fqn.lastIndexOf( '.' ) + 1 );
	}

	/**
	 * Retrieve the primary key value for the given entity instance.
	 *
	 * @param entity Instance of IClassRunnable, aka the compiled/parsed entity.
	 *
	 * @return The primary key value for the given entity instance.
	 */
	public static Object getEntityIdentifier( IClassRunnable entity ) {
		IBoxContext context = RequestBoxContext.getCurrent();
		if ( context == null ) {
			throw new BoxRuntimeException( "No current request context available to retrieve entity identifier." );
		}
		return getEntityIdentifier( entity, context );
	}

	/**
	 * Retrieve the primary key value for the given entity instance.
	 *
	 * @param entity  Instance of IClassRunnable, aka the compiled/parsed entity.
	 * @param context The IBoxContext for the application.
	 *
	 * @return The primary key value for the given entity instance.
	 */
	public static Object getEntityIdentifier( IClassRunnable entity, IBoxContext context ) {
		IBoxContext		jdbcContext		= context.getParentOfType( IJDBCCapableContext.class );
		ORMContext		ormContext		= ORMContext.getForContext( jdbcContext );
		ORMApp			ormApp			= ormContext.getORMApp();
		String			entityName		= getEntityName( entity );
		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ormContext.getSession( entityRecord.getDatasource() );
		ClassMetadata	metadata		= session.getSessionFactory().getClassMetadata( entityRecord.getEntityName() );
		return metadata.getIdentifier( entity );
	}

	/**
	 * Shut down a particular ORM application by request context.
	 * <p>
	 * Will retrieve and close all session factories associated with the provided context.
	 *
	 * @param context The IBoxContext for the application.
	 */
	public void shutdownApp( IBoxContext context ) {
		this.shutdownApp( ORMService.getAppNameFromContext( context ) );
		context.removeAttachment( ORMKeys.ORMContext );
	}

	/**
	 * Shut down an ORM application by unique name
	 * <p>
	 * Will retrieve and close all session factories associated with the provided context.
	 *
	 * @param uniqueAppName The unique name of the ORM application to shut down.
	 */
	public void shutdownApp( Key uniqueAppName ) {
		// We remove it first to prevent further access to the ORMApp
		ORMApp app = this.ormApps.remove( uniqueAppName );
		if ( app != null ) {
			logger.debug( "Shutting down ORMApp for unique name [{}]", uniqueAppName );
			app.shutdown();
		}
		IBoxContext context = RequestBoxContext.getCurrent();
		if ( context == null ) {
			return; // No context to remove from
		}
		RequestBoxContext requestContext = context.getRequestContext();
		if ( requestContext != null && requestContext.hasAttachment( ORMKeys.ORMContext ) ) {
			requestContext.removeAttachment( ORMKeys.ORMContext );
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
		RequestBoxContext requestContext = context instanceof RequestBoxContext castedContext ? castedContext : context.getRequestContext();
		if ( requestContext == null ) {
			throw new BoxRuntimeException( "No request context available to reload ORM application." );
		}
		shutdownApp( requestContext );
		return startupApp(
		    requestContext,
		    ORMConfig.loadFromContext( requestContext ),
		    requestContext.getApplicationListener()
		);
	}

	/**
	 * Retrieve the ORM application configured for the given context.
	 * We build the application name by searching the context for an application context, and getting the name of the application.
	 *
	 * @param context The IBoxContext for the current request. The parent application context is used for the ORM application lookup.
	 *
	 * @return The ORM application for the given context or null if not found
	 */
	public ORMApp getORMAppByContext( IBoxContext context ) {
		RequestBoxContext requestContext = context.getRequestContext();
		if ( requestContext == null ) {
			throw new BoxRuntimeException( "No request context available to get ORM application." );
		}
		IStruct	settings	= requestContext.getApplicationListener().getSettings();
		Key		appName		= ORMService.buildUniqueAppName( context.getApplicationContext().getApplication().getName(), settings );
		return getORMApp( appName );
	}

	/**
	 * Get the ORM by unique name.
	 *
	 * @param appName The unique name of the ORM application.
	 *
	 * @return The ORM application, if it exists, or null.
	 */
	public ORMApp getORMApp( Key appName ) {
		return this.ormApps.containsKey( appName ) ? this.ormApps.get( appName ) : null;
	}

	/**
	 * Do we have an ORM application for the name
	 *
	 * @param appName The unique name of the ORM application.
	 *
	 * @return True if the ORM application exists, false otherwise.
	 */
	public boolean hasORMApp( Key appName ) {
		return this.ormApps.containsKey( appName );
	}

	/**
	 * How many ORM apps are currently running?
	 *
	 * @return The number of ORM applications currently running.
	 */
	public int getORMAppCount() {
		return this.ormApps.size();
	}

	/**
	 * Get an array list of all the ORM applications currently running.
	 *
	 * @return An array list of all the ORM applications currently running.
	 */
	public List<String> getORMAppNames() {
		return List.copyOf( this.ormApps.keySet().stream().map( Key::getName ).toList() );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Helper methods
	 * --------------------------------------------------------------------------
	 */

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
