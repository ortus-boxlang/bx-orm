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
package ortus.boxlang.modules.orm.config;

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.async.watchers.WatcherContext;
import ortus.boxlang.runtime.async.watchers.WatcherEvent;
import ortus.boxlang.runtime.async.watchers.WatcherInstance;
import ortus.boxlang.runtime.async.watchers.listeners.IWatcherListener;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.util.FileSystemUtil;

/**
 * Auto-mode entity watcher: watches an application's ORM entity paths and, on any relevant source change, flags the
 * application for reload.
 * <p>
 * A file-change event fires on the {@code WatcherService}'s background thread, which has no request/JDBC context, and an
 * ORM reload requires a fully-initialized request context (application binding + a thread-valid datasource) that a
 * background thread cannot reproduce. So the listener does not reload directly: it invokes a lightweight callback that
 * marks the application dirty, and the reload happens on the next request that resolves the ORM app - which has exactly
 * that context. The watcher is owned by the {@code ORMService} (one per application) and is not stopped by a reload, so
 * it keeps detecting changes across reloads.
 * <p>
 * This class is only referenced from the {@code auto} manifest-mode branch of ORM startup and is deliberately isolated
 * so its BoxLang watcher-service dependencies are not loaded in {@code off}/{@code trust} modes or on runtimes that
 * predate the watcher service.
 *
 * @since 2.0.0
 */
public final class ORMEntityWatcher {

	/** Debounce window (ms): collapses rapid save bursts (e.g. editor atomic writes) into a single reload. */
	private static final long		DEBOUNCE_MS	= 500L;

	private final WatcherInstance	watcher;

	private ORMEntityWatcher( WatcherInstance watcher ) {
		this.watcher = watcher;
	}

	/**
	 * Build and start a watcher over an application's entity paths.
	 *
	 * @param appName  The ORM application name (used to key the watcher uniquely).
	 * @param config   The ORM configuration (source of the entity paths).
	 * @param context  The boot context (used to expand relative entity paths and as the watcher's parent context).
	 * @param onChange Invoked on each relevant source change; marks the application for reload (no context needed).
	 * @param logger   The ORM logger.
	 *
	 * @return A started watcher, or {@code null} if there are no existing paths to watch.
	 */
	public static ORMEntityWatcher startFor( Key appName, ORMConfig config, IBoxContext context, Runnable onChange, BoxLangLogger logger ) {
		List<String> paths = resolvePaths( config, context );
		if ( paths.isEmpty() ) {
			logger.debug( "ORM auto-mode watcher: no existing entity paths to watch for [{}]; live reload disabled.", appName.getName() );
			return null;
		}

		Key				watcherName	= Key.of( "orm-entities-" + appName.getName() );
		WatcherInstance	instance	= WatcherInstance.builder( watcherName )
		    .paths( paths.toArray( new String[ 0 ] ) )
		    .recursive( true )
		    .debounce( DEBOUNCE_MS )
		    .atomicWrites( true )
		    .parentContext( context )
		    .listener( new EntityChangeListener( onChange, logger ) )
		    .build();

		BoxRuntime.getInstance().getWatcherService().registerAndStart( instance, true );
		logger.info( "ORM auto-mode watcher started for [{}] over {} (reload on next request after a change).", appName.getName(), paths );
		return new ORMEntityWatcher( instance );
	}

	/** Stop and deregister the watcher. Best-effort; never throws. */
	public void stop() {
		try {
			BoxRuntime.getInstance().getWatcherService().removeWatcher( this.watcher.getName() );
		} catch ( Exception ignored ) {
			// best-effort teardown
		}
	}

	/** Expand the configured entity paths to absolute, existing directories (same expansion discovery uses). */
	private static List<String> resolvePaths( ORMConfig config, IBoxContext context ) {
		List<String> resolved = new ArrayList<>();
		if ( config.entityPaths == null ) {
			return resolved;
		}
		for ( String entityPath : config.entityPaths ) {
			if ( entityPath == null || entityPath.isBlank() ) {
				continue;
			}
			try {
				String abs = FileSystemUtil.expandPath( context, entityPath ).absolutePath().toString();
				if ( java.nio.file.Files.isDirectory( java.nio.file.Path.of( abs ) ) ) {
					resolved.add( abs );
				}
			} catch ( RuntimeException e ) {
				// A path that cannot be expanded is simply not watched.
			}
		}
		return resolved;
	}

	/**
	 * Listener that filters watcher events down to real entity-source changes and fires {@code onChange}. Generated
	 * mapping files ({@code *.orm.xml}) and the {@code .bxorm/} boot cache are ignored so a reload's own writes do not
	 * retrigger the watcher (which would loop).
	 */
	private static final class EntityChangeListener implements IWatcherListener {

		private final Runnable		onChange;
		private final BoxLangLogger	logger;

		EntityChangeListener( Runnable onChange, BoxLangLogger logger ) {
			this.onChange	= onChange;
			this.logger		= logger;
		}

		@Override
		public void onEvent( WatcherEvent event, WatcherContext context ) {
			if ( !isRelevant( event ) ) {
				return;
			}
			logger.debug( "ORM auto-mode watcher: [{}] {} -> flagging ORM app for reload", event.getKind(), event.getPath() );
			try {
				onChange.run();
			} catch ( RuntimeException e ) {
				logger.warn( "ORM auto-mode watcher: failed to flag app for reload: {}", e.getMessage() );
			}
		}

		@Override
		public void onError( Exception e, WatcherContext context ) {
			logger.warn( "ORM auto-mode watcher error: {}", e.getMessage() );
		}

		/** A change is relevant when it targets a BoxLang class source and is not a generated/cache artifact. */
		private boolean isRelevant( WatcherEvent event ) {
			if ( event.getPath() == null ) {
				return false;
			}
			String path = event.getPath().toString().replace( '\\', '/' ).toLowerCase();
			if ( path.contains( "/.bxorm/" ) || path.endsWith( ".orm.xml" ) ) {
				return false;
			}
			return path.endsWith( ".bx" ) || path.endsWith( ".cfc" );
		}
	}
}
