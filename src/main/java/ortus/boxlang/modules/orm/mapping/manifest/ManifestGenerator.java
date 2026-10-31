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
package ortus.boxlang.modules.orm.mapping.manifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeFactory;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeClassLoader;
import ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.ApplicationClassListener;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IBoxContext.ScopeSearchResult;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.BaseScope;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.ApplicationService.ApplicationDescriptorTemplate;

/**
 * Builds an application's {@code .bxorm/} boot cache from the command line, with no web server, no database and no
 * application start: {@code boxlang --bx-config <runtime boxlang.json> module:orm generate [--dir=<appRoot>]}.
 * <p>
 * It loads the application's {@code Application.bx} (or {@code Application.cfc}) the way BoxLang does for a request,
 * which runs its pseudo-constructor ({@code this.name}, {@code this.mappings}, {@code this.ormSettings}, ...), but it
 * never defines or starts the application: neither the ORM's {@code onApplicationStart} interceptor nor the
 * application's own {@code onApplicationStart()} runs. It then discovers the entities and writes
 * {@code manifest-{app}.json} exactly as an {@code auto}-mode boot would.
 * <p>
 * Runtime-level settings (mappings, datasources and modules declared in {@code boxlang.json} rather than in the
 * Application class) come from the BoxLang runtime config, so run it with the same {@code --bx-config} the server uses.
 * <p>
 * It also writes the entity facade classes to {@code facades-{app}.jar}: they are generated from entity metadata
 * alone, so this needs no Hibernate session factory and no database.
 *
 * @since 2.0.0
 */
public final class ManifestGenerator {

	/** The descriptor file names, in the order BoxLang looks them up. */
	private static final List<String>	DESCRIPTORS	= List.of( "Application.bx", "Application.cfc" );

	/** The CGI scope name; core BoxLang declares no constant for it (the web runtimes provide the scope). */
	private static final Key			CGI			= Key.of( "cgi" );

	/**
	 * The request context the descriptor loads in: a scripting context that also answers the web scopes ({@code cgi},
	 * {@code url}, {@code form}, {@code cookie}), empty, since Application classes commonly read them in the
	 * pseudo-constructor (for example {@code cgi.path_info} or {@code url.reinit}) and the CLI has no web request.
	 */
	private static final class GenerateContext extends ScriptingRequestBoxContext {

		private final Map<Key, IScope> webScopes = Map.of(
		    CGI, new EmptyCgiScope(),
		    Key.of( "url" ), new BaseScope( Key.of( "url" ) ),
		    Key.of( "form" ), new BaseScope( Key.of( "form" ) ),
		    Key.of( "cookie" ), new BaseScope( Key.of( "cookie" ) ) );

		GenerateContext( IBoxContext parent ) {
			super( parent, false );
		}

		@Override
		public IScope getScope( Key name ) {
			IScope scope = this.webScopes.get( name );
			return scope != null ? scope : super.getScope( name );
		}

		@Override
		public boolean isKeyVisibleScope( Key key, boolean nearby, boolean shallow ) {
			return this.webScopes.containsKey( key ) || super.isKeyVisibleScope( key, nearby, shallow );
		}

		@Override
		public ScopeSearchResult scopeFind( Key key, IScope defaultScope, boolean forAssign ) {
			// An unscoped web scope name resolves to the scope itself, as it does on a web request.
			IScope scope = this.webScopes.get( key );
			if ( scope != null ) {
				return new ScopeSearchResult( scope, scope, key, true );
			}
			return super.scopeFind( key, defaultScope, forAssign );
		}
	}

	/** A CGI scope with no request behind it: every variable reads as an empty string, as on a web request. */
	private static final class EmptyCgiScope extends BaseScope {

		EmptyCgiScope() {
			super( CGI );
		}

		@Override
		public Object dereference( IBoxContext context, Key key, Boolean safe ) {
			Object value = super.get( key );
			return value == null ? "" : value;
		}
	}

	/**
	 * What a generation produced.
	 *
	 * @param manifest The manifest file written.
	 * @param facades  The facade jar written.
	 * @param appName  The application name ({@code this.name}).
	 * @param entities The number of entities in the manifest.
	 * @param millis   How long discovery and writing took.
	 */
	public record Result( Path manifest, Path facades, String appName, int entities, long millis ) {
	}

	private ManifestGenerator() {
	}

	/**
	 * BoxLang-friendly entry point for {@code ModuleConfig.main()}: generates the cache and maps the outcome to a CLI
	 * message and exit code.
	 *
	 * @param appRootPath   The application root (the folder holding {@code Application.bx} or {@code Application.cfc}).
	 * @param moduleVersion The running ORM module version, recorded in the manifest.
	 *
	 * @return The CLI result: 0 on success, 1 on failure.
	 */
	public static ManifestCli.CliResult run( String appRootPath, String moduleVersion ) {
		try {
			Result result = generate( Path.of( appRootPath ).toAbsolutePath().normalize(), moduleVersion );
			return new ManifestCli.CliResult(
			    "✅ Wrote the ORM boot cache for [" + result.appName() + "]: " + result.entities() + " entities in " + result.millis()
			        + "ms\n   " + result.manifest() + "\n   " + result.facades(),
			    0 );
		} catch ( RuntimeException e ) {
			return new ManifestCli.CliResult( "❌ Could not generate the ORM boot cache: " + e.getMessage(), 1 );
		}
	}

	/**
	 * Load the application descriptor without starting the application, discover its entities and write the manifest.
	 *
	 * @param appRoot       The application root.
	 * @param moduleVersion The running ORM module version, recorded in the manifest.
	 *
	 * @return What was written.
	 */
	public static Result generate( Path appRoot, String moduleVersion ) {
		long						start		= System.currentTimeMillis();
		Path						descriptor	= findDescriptor( appRoot );
		BoxRuntime					runtime		= BoxRuntime.getInstance();
		// No application descriptor yet: loading one through the constructor would define and start the application.
		ScriptingRequestBoxContext	context		= new GenerateContext( runtime.getRuntimeContext() );
		RequestBoxContext.setCurrent( context );
		try {
			// Runs the pseudo-constructor only. Attaching the listener makes this.* (ormSettings, mappings, ...) visible
			// through the request config; defineApplication() is deliberately not called.
			ApplicationDescriptorTemplate loaded = runtime.getApplicationService().loadDescriptorTemplate( context, descriptor.toUri() );
			if ( loaded.runnable() == null ) {
				throw new IllegalStateException( "[" + descriptor + "] is not an Application class; generate needs this.ormSettings" );
			}
			ApplicationClassListener listener = new ApplicationClassListener( loaded.runnable(), context, loaded.path() );
			context.setApplicationListener( listener );

			ORMConfig config = ORMConfig.loadFromContext( context );
			if ( config == null ) {
				throw new IllegalStateException( "ORM is not enabled in [" + descriptor + "] (this.ormEnabled and this.ormSettings)" );
			}
			// Same derivation as ORMApp.startup(), so the manifest name and settings fingerprint match the running app.
			config.appName				= StringCaster.cast( listener.getSettings().get( Key._NAME ) );
			config.facadeNamespace		= EntityFacadeNaming.sanitizeNamespace( config.appName );

			// The facades go into a fresh loader, as each server build does, so their bytecode can be captured below.
			config.facadeClassLoader	= new FacadeClassLoader( ORMApp.moduleClassLoader() );

			Map<Key, List<EntityRecord>>	entityMap	= MappingGenerator.discoverEntities( context, config );
			Path							folder		= resolveFolder( appRoot, config.manifestLocation );
			OrmManifest						manifest	= ManifestService.build( entityMap, config, moduleVersion,
			    ManifestService.portablePaths( context, config ) );
			ManifestService.write( manifest, folder );

			// The facades depend only on entity metadata, never on Hibernate or the database: generate them exactly as a
			// session factory build does, then write their bytecode for trust mode to inject instead of regenerating.
			List<EntityRecord> allEntities = entityMap.values().stream().flatMap( List::stream ).toList();
			SessionFactoryBuilder.generateEntityFacades( config, allEntities );
			Path jar = ManifestService.facadesJar( folder, config.appName );
			EntityFacadeFactory.writeFacadeJar( jar, config.facadeNamespace );

			return new Result( ManifestService.manifestFile( folder, config.appName ), jar, config.appName, manifest.getEntities().size(),
			    System.currentTimeMillis() - start );
		} finally {
			RequestBoxContext.removeCurrent();
		}
	}

	/**
	 * The application's descriptor file: {@code Application.bx}, else {@code Application.cfc}.
	 */
	private static Path findDescriptor( Path appRoot ) {
		for ( String name : DESCRIPTORS ) {
			Path candidate = appRoot.resolve( name );
			if ( Files.isRegularFile( candidate ) ) {
				return candidate;
			}
		}
		throw new IllegalStateException( "No Application.bx or Application.cfc in [" + appRoot + "]; pass the application root with --dir" );
	}

	/**
	 * The {@code .bxorm/} folder, resolved against the application root the way a request from the root resolves it:
	 * blank means the root, a relative {@code ormManifestLocation} is under the root, an absolute one is used as is.
	 */
	private static Path resolveFolder( Path appRoot, String location ) {
		if ( location == null || location.isBlank() ) {
			return appRoot.resolve( ManifestService.FOLDER_NAME );
		}
		Path base = Path.of( location.trim() );
		return ( base.isAbsolute() ? base : appRoot.resolve( base ) ).normalize().resolve( ManifestService.FOLDER_NAME );
	}
}
