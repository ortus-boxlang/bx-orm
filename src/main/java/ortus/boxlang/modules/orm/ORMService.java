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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMEntityWatcher;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.hibernate.BoxProxy;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.BaseApplicationListener;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
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
import ortus.boxlang.runtime.util.EncryptionUtil;

/**
 * Java class responsible for constructing and managing the Hibernate ORM
 * engine.
 *
 * Constructs and stores Hibernate session factories.
 *
 * @since 1.0.0
 */
public class ORMService extends BaseService {

	public static final String			BX_CLASS_SUFFIX			= "$bx";
	public static final String			CFC_CLASS_SUFFIX		= "$cfc";
	public static final String			COMPILED_CLASS_PREFIX	= "boxgenerated.class.";

	/**
	 * The logger for the ORMEngine.
	 */
	private BoxLangLogger				logger;

	/**
	 * A map of ORM applications, keyed by the unique name of the ORM application.
	 */
	private Map<Key, ORMApp>			ormApps					= new ConcurrentHashMap<>();

	/**
	 * The last startup failure per application (cleared on a successful start). Lets every later ORM call explain why the
	 * ORM is not available instead of failing with a null pointer.
	 */
	private final Map<Key, BootFailure>	bootFailures			= new ConcurrentHashMap<>();

	/**
	 * A recorded ORM startup failure.
	 *
	 * @param at    When the startup failed.
	 * @param error The (translated) startup error.
	 */
	public record BootFailure( java.time.Instant at, RuntimeException error ) {
	}

	/**
	 * Auto-mode entity watchers, keyed by ORM application name. Owned here (not by {@link ORMApp}) so a single watcher
	 * survives reloads - a reload swaps the {@link ORMApp} but the entity paths do not change - and is stopped only on a
	 * real application shutdown.
	 */
	private Map<Key, ORMEntityWatcher>	entityWatchers			= new ConcurrentHashMap<>();

	/**
	 * Auto-mode: application names flagged for reload by their entity watcher. The reload itself happens on the next
	 * request that resolves the app (which has the request/JDBC context a reload requires); the watcher thread does not.
	 */
	private java.util.Set<Key>			dirtyApps				= ConcurrentHashMap.newKeySet();

	/**
	 * Interception points for the ORM service.
	 */
	private static final Key[]			ORM_INTERCEPTION_POINTS	= List.of(
	    ORMKeys.EVENT_POST_NEW,
	    ORMKeys.EVENT_POST_LOAD ).toArray( new Key[ 0 ] );

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

		// TODO: Make this configurable. For now, log debug so it can assist us in
		// debugging.
		LoggerContext	loggerContext		= runtime.getLoggingService().getLoggerContext();
		for ( String category : hibernateCategories ) {
			Logger					hibernateLogger	= loggerContext.getLogger( category );
			Appender<ILoggingEvent>	ormAppender		= getLogger().getAppender( "orm" );
			if ( ormAppender != null ) {
				hibernateLogger.addAppender( ormAppender );
			}
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
	 * The configuration load event is fired when the runtime loads the
	 * configuration
	 */
	@Override
	public void onConfigurationLoad() {
		// Not used by the service, since those are only for core services
	}

	/**
	 * Start up the ORM service. Unless and until ORM is supported at the runtime
	 * level, this method is essentially a no-op.
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
	 * Helper method to construct ORM based application names, which rely on the
	 * unique combination
	 * of an application name and the application's configuration.
	 * <p>
	 * Builds a canonical string representation of the relevant config keys and
	 * hashes it.
	 * No streams or lambdas are used; struct keys are sorted via TreeMap to
	 * guarantee
	 * a deterministic result regardless of insertion order.
	 *
	 * @deprecated This method is no longer used to build application names, unless
	 *             we ever allow it.
	 *
	 * @param appName The application name.
	 * @param config  The application configuration.
	 *
	 * @return A deterministic, unique key for the given application name and
	 *         configuration.
	 */
	public static Key buildUniqueAppName( Key appName, IStruct config ) {
		StringBuilder sb = new StringBuilder( 64 );
		canonicalize( config.containsKey( ORMKeys.ORMEnabled ) ? config.get( ORMKeys.ORMEnabled ) : "", sb );
		sb.append( '|' );
		canonicalize( config.containsKey( Key.datasource ) ? config.get( Key.datasource ) : "", sb );
		sb.append( '|' );
		canonicalize( config.containsKey( ORMKeys.ORMSettings ) ? config.get( ORMKeys.ORMSettings ) : "", sb );
		return Key.of( appName.getNameNoCase().trim() + "_" + EncryptionUtil.generate64BitHash( sb.toString() ) );
	}

	/**
	 * Writes a canonical, order-independent representation of {@code value} into
	 * {@code sb}.
	 * <p>
	 * Handles nested {@link IStruct} (keys sorted case-insensitively via TreeMap),
	 * {@link Array}, and any scalar value via {@link StringCaster}.
	 * No streams, collectors, or lambdas are used.
	 *
	 * @param value The value to canonicalize (IStruct, Array, or scalar).
	 * @param sb    The StringBuilder to append into.
	 */
	private static void canonicalize( Object value, StringBuilder sb ) {
		if ( value instanceof IStruct valStruct ) {
			TreeMap<String, Object> sorted = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
			for ( Map.Entry<Key, Object> entry : valStruct.entrySet() ) {
				sorted.put( entry.getKey().getNameNoCase(), entry.getValue() );
			}
			sb.append( '{' );
			boolean first = true;
			for ( Map.Entry<String, Object> entry : sorted.entrySet() ) {
				if ( !first ) {
					sb.append( ',' );
				}
				first = false;
				sb.append( entry.getKey() ).append( '=' );
				canonicalize( entry.getValue(), sb );
			}
			sb.append( '}' );
		} else if ( value instanceof Array valArray ) {
			sb.append( '[' );
			for ( int i = 0, len = valArray.size(); i < len; i++ ) {
				if ( i > 0 )
					sb.append( ',' );
				canonicalize( valArray.get( i ), sb );
			}
			sb.append( ']' );
		} else if ( value != null ) {
			sb.append( StringCaster.cast( value ) );
		} else {
			sb.append( "null" );
		}
	}

	/**
	 * Build a unique application name from the given context.
	 *
	 * @param context The IBoxContext for the application.
	 *
	 * @return A unique key for the given application name and configuration.
	 */
	public static Key getAppNameFromContext( IBoxContext context ) {
		ApplicationBoxContext appContext = context.getApplicationContext();
		if ( appContext == null ) {
			throw new BoxRuntimeException( "No application context available to retrieve ORM application." );
		}
		return appContext.getApplication().getName();
	}

	/**
	 * Start up a new ORM application with the given context and ORM configuration.
	 * Please note that a new ORM application will be constructed if the following
	 * conditions are met:
	 * - An application listener name changes
	 * - The application.bx configuration changes
	 *
	 * @param context          The IBoxContext for the application.
	 * @param config           The ORM configuration - parsed from the application
	 *                         settings.
	 * @param startingListener The listener that is starting the ORM application.
	 *
	 * @return A new or existing ORM application if started already.
	 */
	public ORMApp startupApp( RequestBoxContext context, ORMConfig config, BaseApplicationListener startingListener ) {
		// Derive the key the same way getORMAppByContext() does so lookups always hit.
		Key appName = ORMService.getAppNameFromContext( context );
		// Atomically create or get the ORMApp for the given context.
		try {
			ORMApp app = this.ormApps.computeIfAbsent(
			    appName,
			    key -> new ORMApp( config, appName ).startup( context ) );
			this.bootFailures.remove( appName );
			return app;
		} catch ( Throwable e ) {
			throw recordBootFailure( appName, e );
		}
	}

	/**
	 * Translate a startup error, remember it for later ORM calls and {@code ormDiagnostics()}, log it, and return it for
	 * throwing. A failure that is not an ORM exception becomes {@code orm.config} with its original message.
	 *
	 * @param appName The application whose ORM failed to start.
	 * @param error   The startup error.
	 *
	 * @return The translated error to throw.
	 */
	private RuntimeException recordBootFailure( Key appName, Throwable error ) {
		RuntimeException translated = ortus.boxlang.modules.orm.errors.ORMErrors.translate( error,
		    ortus.boxlang.modules.orm.errors.ORMErrors.Context.of( "ORM startup" ) );
		if ( ! ( translated instanceof ortus.boxlang.modules.orm.errors.ORMException ) ) {
			// Anything else that stops the ORM from starting (a bad entity path, field type or datasource, found by bx-orm or
			// BoxLang) is a configuration problem: keep its message, give it the orm.config type.
			String message = String.valueOf( translated.getMessage() );
			translated = new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.CONFIG,
			    "The ORM could not start: " + message,
			    "Check this.ormSettings in Application.bx and the entity files named above.", null, error );
		}
		this.bootFailures.put( appName, new BootFailure( java.time.Instant.now(), translated ) );
		logger.error( "ORM application [{}] failed to start: {}", appName.getName(), translated.getMessage(), error );
		return translated;
	}

	/**
	 * The last startup failure for an application.
	 *
	 * @param appName The application name.
	 *
	 * @return The failure, or null when the last startup succeeded (or none failed).
	 */
	public BootFailure getBootFailure( Key appName ) {
		return this.bootFailures.get( appName );
	}

	/**
	 * The ORM application for a context, or a clear {@code orm.notEnabled} / {@code orm.notReady} error explaining why
	 * there is none. Every BIF uses this instead of a null check.
	 *
	 * @param context The current context.
	 *
	 * @return The running ORM application.
	 */
	public ORMApp requireORMApp( IBoxContext context ) {
		ORMApp app = null;
		try {
			app = getORMAppByContext( context );
		} catch ( ortus.boxlang.modules.orm.errors.ORMException e ) {
			throw e;
		} catch ( BoxRuntimeException e ) {
			// No application context at all: the code is not running inside an ORM-enabled application.
			throw notReadyError( context );
		}
		if ( app == null ) {
			throw notReadyError( context );
		}
		return app;
	}

	/**
	 * Explain why there is no ORM application for a context: ORM not enabled, startup failed (with the original reason),
	 * or not started yet.
	 *
	 * @param context The current context (may be null).
	 *
	 * @return An {@code orm.notEnabled} or {@code orm.notReady} error to throw.
	 */
	public ortus.boxlang.modules.orm.errors.ORMException notReadyError( IBoxContext context ) {
		ApplicationBoxContext appContext = context == null ? null : context.getApplicationContext();
		if ( appContext == null ) {
			return new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.NOT_ENABLED,
			    "No ORM application is available here: this code is not running inside an application, or its application failed to start.",
			    "Run it from an app whose Application.bx sets this.ormEnabled = true. If the app failed to start, fix the startup error first." );
		}
		Key appName = appContext.getApplication().getName();
		if ( ORMConfig.loadFromContext( context ) == null ) {
			return new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.NOT_ENABLED,
			    String.format( "The application [%s] is not ORM-enabled.", appName.getName() ),
			    "Set this.ormEnabled = true (and this.ormSettings) in Application.bx." );
		}
		BootFailure failure = this.bootFailures.get( appName );
		if ( failure != null ) {
			IStruct info = new ortus.boxlang.runtime.types.Struct();
			info.put( Key.of( "failedAt" ), failure.at().toString() );
			info.put( Key.of( "startupError" ), String.valueOf( failure.error().getMessage() ) );
			return new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.NOT_READY,
			    String.format( "The ORM for application [%s] failed to start at %s: %s", appName.getName(), failure.at(),
			        failure.error().getMessage() ),
			    "Fix the startup error, then call ormReload() or restart the application. ormDiagnostics() shows the details.",
			    info, failure.error() );
		}
		return new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.NOT_READY,
		    String.format( "The ORM for application [%s] has not started.", appName.getName() ),
		    "It starts with the application (onApplicationStart). Call ormReload() to start it now." );
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
		} else if ( entity instanceof ortus.boxlang.modules.orm.hibernate.facade.BoxEntityFacade ) {
			// Facade (POJO) mode: resolve the entity name via the backing BoxLang instance.
			return getEntityName( ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrap( entity ) );
		} else if ( entity instanceof IClassRunnable boxClass ) {
			return getEntityName( boxClass );
		} else {
			if ( entity instanceof String entityString ) {
				throw new BoxRuntimeException(
				    "The string provided " + entityString + " is not a valid BoxLang ORM entity or proxy." );
			} else {

				throw new BoxRuntimeException(
				    "The entity instance of " + entity.getClass().getName()
				        + " is not a valid BoxLang ORM entity or proxy." );
			}
		}
	}

	/**
	 * Retrieve the entity name for the given entity class.
	 *
	 * @param entity Instance of IClassRunnable, aka the compiled/parsed entity.
	 */
	public static String getEntityName( IClassRunnable entity ) {
		// @TODO: Should we look up the EntityRecord and use that to grab the class
		// name?
		IStruct annotations = entity.getAnnotations();
		if ( annotations.containsKey( ORMKeys.entity ) && !annotations.getAsString( ORMKeys.entity ).isBlank() ) {
			return annotations.getAsString( ORMKeys.entity );
		} else if ( annotations.containsKey( ORMKeys.entityName )
		    && !annotations.getAsString( ORMKeys.entityName ).isBlank() ) {
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
		return ormApp.getEntityPersister( session, entityName ).getIdentifier( entity, ( SharedSessionContractImplementor ) session );
	}

	/**
	 * Shut down a particular ORM application by request context.
	 * <p>
	 * Will retrieve and close all session factories associated with the provided
	 * context.
	 *
	 * @param context The IBoxContext for the application.
	 */
	public void shutdownApp( IBoxContext context ) {
		// Close all open Hibernate sessions BEFORE tearing down the SessionFactories.
		// Skipping this causes open Sessions to hold a strong reference to the old
		// SessionFactory (and its entire QuerySpacesImpl / LoadPlanImpl tree),
		// preventing
		// GC after every ORMReload() — the primary cause of the heap leak seen in the
		// dump.
		IBoxContext jdbcContext = context.getParentOfType( IJDBCCapableContext.class );
		if ( jdbcContext == null ) {
			jdbcContext = context;
		}
		if ( jdbcContext.hasAttachment( ORMKeys.ORMContext ) ) {
			ORMContext ormContext = ( ORMContext ) jdbcContext.getAttachment( ORMKeys.ORMContext );
			try {
				ormContext.shutdown();
			} catch ( Exception e ) {
				logger.warn( "Error closing ORM sessions before app shutdown: {}", e.getMessage(), e );
			}
			jdbcContext.removeAttachment( ORMKeys.ORMContext );
		}
		// Now it is safe to close the SessionFactories and datasources.
		this.shutdownApp( ORMService.getAppNameFromContext( context ) );
	}

	/**
	 * Shut down an ORM application by unique name
	 * <p>
	 * Will retrieve and close all session factories associated with the provided
	 * context.
	 *
	 * @param uniqueAppName The unique name of the ORM application to shut down.
	 */
	public void shutdownApp( Key uniqueAppName ) {
		// Stop the auto-mode entity watcher (if any) on a real shutdown; reloads intentionally keep it running.
		stopEntityWatcher( uniqueAppName );

		// We remove it first to prevent further access to the ORMApp
		ORMApp app = this.ormApps.remove( uniqueAppName );

		// Shutdown the app if it exists, which will close all session factories and
		// datasources associated with the app.
		if ( app != null ) {
			logger.debug( "Shutting down ORMApp for unique name [{}]", uniqueAppName );
			app.shutdown();
		}

		// Try to get the current thread context and remove any ORMContext attachments,
		// just in case there are any lingering references to the old app that
		// could cause issues if the same app name is restarted.
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
		RequestBoxContext requestContext = context instanceof RequestBoxContext castedContext ? castedContext
		    : context.getRequestContext();
		if ( requestContext == null ) {
			throw new BoxRuntimeException( "No request context available to reload ORM application." );
		}

		// Step 1: Close any open Hibernate sessions BEFORE rebuilding factories.
		// This must happen first so sessions don't hold stale factory references.
		IBoxContext jdbcContext = requestContext.getParentOfType( IJDBCCapableContext.class );
		if ( jdbcContext == null ) {
			jdbcContext = requestContext;
		}
		if ( jdbcContext.hasAttachment( ORMKeys.ORMContext ) ) {
			ORMContext ormContext = ( ORMContext ) jdbcContext.getAttachment( ORMKeys.ORMContext );
			try {
				ormContext.shutdown();
			} catch ( Exception e ) {
				logger.warn( "Error closing ORM sessions during reload: {}", e.getMessage(), e );
			}
			jdbcContext.removeAttachment( ORMKeys.ORMContext );
		}

		// Step 2: Build the new ORMApp BEFORE touching the map so there is never a
		// window where getORMAppByContext() returns null (which breaks concurrent
		// callers such as cborm module activation running in a parallel thread).
		Key		appName	= ORMService.getAppNameFromContext( requestContext );
		ORMApp	newApp;
		try {
			newApp = new ORMApp( ORMConfig.loadFromContext( requestContext ), appName ).startup( context );
			this.bootFailures.remove( appName );
		} catch ( Throwable e ) {
			throw recordBootFailure( appName, e );
		}

		// Step 3: Atomically swap — put the new app into the map and retrieve the old
		// one.
		ORMApp oldApp = this.ormApps.put( appName, newApp );

		// Step 4: Shut down the old app's session factories AFTER the new one is live,
		// minimising the disruption window for any requests still using the old
		// factory.
		if ( oldApp != null ) {
			try {
				oldApp.shutdown();
			} catch ( Exception e ) {
				logger.warn( "Error shutting down old ORMApp during reload: {}", e.getMessage(), e );
			}
		}

		// Step 5: Eagerly install a fresh ORMContext for the reloading request's JDBC
		// context.
		// The old one was removed in Step 1. Without this, the first ORM call after
		// reload
		// would lazily create a new ORMContext which is fine, but doing it here ensures
		// the
		// calling code (e.g. ORMReload BIF) immediately sees the new app — no null
		// window.
		try {
			ORMContext.getForContext( jdbcContext );
		} catch ( Exception e ) {
			logger.warn( "Could not eagerly initialize new ORMContext after reload: {}", e.getMessage(), e );
		}

		return newApp;
	}

	/**
	 * Retrieve the ORM application configured for the given context.
	 * We build the application name by searching the context for an application
	 * context, and getting the name of the application.
	 *
	 * @param context The IBoxContext for the current request. The parent
	 *                application context is used for the ORM application lookup.
	 *
	 * @return The ORM application for the given context or null if not found
	 */
	public ORMApp getORMAppByContext( IBoxContext context ) {
		ApplicationBoxContext appContext = context.getApplicationContext();
		if ( appContext == null ) {
			throw new BoxRuntimeException( "No application context available to retrieve ORM application." );
		}
		Key appName = appContext.getApplication().getName();
		// Auto-mode live reload: if the entity watcher flagged this app, reload now - we are on a request thread with the
		// context a reload needs. remove() returns true for a single caller under concurrency, so only one reload runs; a
		// freshly reloaded app is not dirty, so the eager ORMContext init inside reloadApp cannot recurse here.
		if ( this.dirtyApps.remove( appName ) ) {
			try {
				return reloadApp( context );
			} catch ( Exception e ) {
				logger.warn( "ORM auto-mode reload failed: {}", e.getMessage(), e );
			}
		}
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
	 * Ensure a single auto-mode entity watcher exists for an application (idempotent across reloads). On a source change
	 * the watcher reloads the application via {@link #reloadApp(IBoxContext)}, run inside a request context obtained with
	 * {@link RequestBoxContext#runInContext} (the watcher fires on a background thread that has none). Best-effort: a
	 * runtime without a watcher service just leaves live reload disabled.
	 *
	 * @param appName The ORM application name.
	 * @param config  The ORM configuration (entity paths to watch).
	 * @param context The boot context, whose application context anchors the reload's request context.
	 */
	public void ensureEntityWatcher( Key appName, ORMConfig config, IBoxContext context ) {
		if ( this.entityWatchers.containsKey( appName ) ) {
			return;
		}
		try {
			// The watcher runs on a background thread with no request context, so it only flags the app dirty; the reload
			// runs later on a request thread (see getORMAppByContext) which has the context a reload needs.
			ORMEntityWatcher watcher = ORMEntityWatcher.startFor( appName, config, context, () -> this.dirtyApps.add( appName ), getLogger() );
			if ( watcher != null ) {
				this.entityWatchers.put( appName, watcher );
			}
		} catch ( Throwable t ) {
			getLogger().warn( "ORM auto-mode entity watcher unavailable for [{}], live reload disabled: {}", appName.getName(), t.getMessage() );
		}
	}

	/**
	 * Stop and remove the auto-mode entity watcher for an application, if any. Called on a real application shutdown (not
	 * on reload, which keeps the watcher running).
	 *
	 * @param appName The ORM application name.
	 */
	public void stopEntityWatcher( Key appName ) {
		this.dirtyApps.remove( appName );
		ORMEntityWatcher watcher = this.entityWatchers.remove( appName );
		if ( watcher != null ) {
			watcher.stop();
		}
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
