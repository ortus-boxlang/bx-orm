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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Verb logic for the {@code bxorm} manifest CLI, kept free of any BoxLang runtime dependency so it can be unit-tested
 * directly. The BoxLang {@code ModuleConfig.main()} is a thin arg-parser that resolves the {@code .bxorm/} folder and
 * delegates here.
 * <p>
 * Every verb reads the on-disk {@code .bxorm/} boot cache via {@link ManifestService}; none of them boots Hibernate.
 * Generation is not a CLI verb: auto mode writes the manifest on every boot, which is the supported generation path.
 * <p>
 * One {@code .bxorm/} folder can hold several applications' caches ({@code manifest-{app}.json},
 * {@code facades-{app}.jar}). {@code --app=<name>} picks one; without it the only application in the folder is used,
 * and when there are several, {@code info} and {@code validate} cover them all while the other verbs ask for
 * {@code --app}. {@code clear --all} removes the whole folder.
 *
 * @since 2.0.0
 */
public final class ManifestCli {

	/** The option that picks an application: {@code --app=<name>}. */
	private static final String	APP_OPTION	= "--app=";
	/** The option that makes {@code clear} remove every application's cache. */
	private static final String	ALL_OPTION	= "--all";

	/** The result of a verb: a message to print and a process exit code. */
	public record CliResult( String message, int exitCode ) {
	}

	/**
	 * The parsed command line: the verb, its positional arguments and the options.
	 *
	 * @param verb       The verb, lower-cased; {@code info} when none was given.
	 * @param positional The verb's positional arguments.
	 * @param app        The {@code --app} value, or {@code null}.
	 * @param all        Whether {@code --all} was given.
	 */
	private record Command( String verb, List<String> positional, String app, boolean all ) {
	}

	private ManifestCli() {
	}

	/**
	 * BoxLang-friendly entry point: takes the {@code .bxorm/} folder as a string and the args as a list (a BoxLang array
	 * maps directly to {@link java.util.List}), so {@code ModuleConfig.main()} need not build a Java {@code String[]} or
	 * a {@code Path}. Delegates to {@link #run(Path, String, String...)}.
	 *
	 * @param bxormFolderPath The {@code .bxorm/} folder path.
	 * @param moduleVersion   The current ORM module version.
	 * @param args            The verb, its arguments and options.
	 *
	 * @return The verb result (message + exit code).
	 */
	public static CliResult run( String bxormFolderPath, String moduleVersion, java.util.List<?> args ) {
		String[] arr = args == null
		    ? new String[ 0 ]
		    : args.stream().map( o -> o == null ? "" : o.toString() ).toArray( String[]::new );
		return run( Path.of( bxormFolderPath ), moduleVersion, arr );
	}

	/**
	 * Run a manifest CLI verb against a resolved {@code .bxorm/} folder.
	 *
	 * @param folder        The {@code .bxorm/} folder (already resolved to the application root).
	 * @param moduleVersion The current ORM module version, for the {@code version} verb.
	 * @param args          The verb, its arguments and options ({@code --app=<name>}, {@code --all}); no verb means
	 *                      {@code info}.
	 *
	 * @return The verb result (message + exit code).
	 */
	public static CliResult run( Path folder, String moduleVersion, String... args ) {
		Command command = parse( args );
		try {
			return switch ( command.verb() ) {
				case "help", "-h", "--help" -> new CliResult( usage(), 0 );
				case "version", "-v", "--version" -> version( folder, command, moduleVersion );
				case "info" -> info( folder, command );
				case "validate" -> validate( folder, command );
				case "entities" -> entities( folder, command );
				case "entity" -> entity( folder, command );
				case "mappings" -> mappings( folder, command );
				case "clear" -> clear( folder, command );
				default -> new CliResult( "❌ Unknown verb [" + command.verb() + "].\n\n" + usage(), 1 );
			};
		} catch ( CliError e ) {
			return new CliResult( e.getMessage(), 1 );
		}
	}

	/** A verb-level failure carrying a user-facing message; mapped to exit code 1. */
	private static final class CliError extends RuntimeException {

		CliError( String message ) {
			super( message );
		}
	}

	/**
	 * Split the arguments into the verb, its positional arguments and the {@code --app} / {@code --all} options.
	 *
	 * @param args The raw arguments.
	 *
	 * @return The parsed command.
	 */
	private static Command parse( String[] args ) {
		String					verb		= null;
		String					app			= null;
		boolean					all			= false;
		java.util.List<String>	positional	= new java.util.ArrayList<>();
		for ( String arg : args == null ? new String[ 0 ] : args ) {
			String value = arg == null ? "" : arg.trim();
			if ( value.isEmpty() ) {
				continue;
			}
			if ( value.toLowerCase().startsWith( APP_OPTION ) ) {
				app = value.substring( APP_OPTION.length() ).trim();
			} else if ( value.equalsIgnoreCase( ALL_OPTION ) ) {
				all = true;
			} else if ( verb == null ) {
				verb = value.toLowerCase();
			} else {
				positional.add( value );
			}
		}
		return new Command( verb == null ? "info" : verb, positional, app == null || app.isEmpty() ? null : app, all );
	}

	/**
	 * The application a single-application verb works on: the {@code --app} value, else the only application in the
	 * folder.
	 *
	 * @param folder  The {@code .bxorm/} folder.
	 * @param command The parsed command.
	 *
	 * @return The application key.
	 *
	 * @throws CliError When the folder has no manifest, or several and no {@code --app}.
	 */
	private static String requireApp( Path folder, Command command ) {
		if ( command.app() != null ) {
			return ManifestService.appKey( command.app() );
		}
		List<String> apps = ManifestService.listApps( folder );
		if ( apps.isEmpty() ) {
			throw new CliError( "❌ " + noManifestMessage( folder ) );
		}
		if ( apps.size() > 1 ) {
			throw new CliError( "❌ " + severalAppsMessage( folder, apps ) );
		}
		return apps.get( 0 );
	}

	/**
	 * The applications a multi-application verb ({@code info}, {@code validate}) covers: the {@code --app} value, else
	 * every application in the folder.
	 */
	private static List<String> selectedApps( Path folder, Command command ) {
		return command.app() != null ? List.of( ManifestService.appKey( command.app() ) ) : ManifestService.listApps( folder );
	}

	private static CliResult version( Path folder, Command command, String moduleVersion ) {
		StringBuilder sb = new StringBuilder();
		sb.append( "🏷️  bx-orm module : " ).append( moduleVersion == null ? "unknown" : moduleVersion ).append( '\n' );
		sb.append( "manifest format expected : " ).append( OrmManifest.FORMAT_VERSION );
		for ( String app : selectedApps( folder, command ) ) {
			OrmManifest manifest = ManifestService.read( folder, app, false );
			if ( manifest != null ) {
				sb.append( '\n' ).append( "[" ).append( app ).append( "] manifest format on disk : " ).append( manifest.getFormatVersion() );
				sb.append( '\n' ).append( "[" ).append( app ).append( "] manifest built by ORM   : " ).append( manifest.getOrmVersion() );
			}
		}
		return new CliResult( sb.toString(), 0 );
	}

	private static CliResult info( Path folder, Command command ) {
		List<String> apps = selectedApps( folder, command );
		if ( apps.isEmpty() || ( command.app() != null && !Files.exists( ManifestService.manifestFile( folder, apps.get( 0 ) ) ) ) ) {
			return new CliResult( "ℹ️  " + noManifestMessage( folder, command.app() ), 0 );
		}
		StringBuilder sb = new StringBuilder();
		for ( String app : apps ) {
			OrmManifest manifest = ManifestService.read( folder, app, false );
			if ( sb.length() > 0 ) {
				sb.append( "\n\n" );
			}
			sb.append( "📦 ORM manifest [" ).append( app ).append( "] in [" ).append( folder ).append( "]\n" );
			sb.append( "  format version   : " ).append( manifest.getFormatVersion() ).append( '\n' );
			sb.append( "  built by ORM     : " ).append( manifest.getOrmVersion() ).append( '\n' );
			sb.append( "  config fingerprint: " ).append( shorten( manifest.getConfigFingerprint() ) ).append( '\n' );
			sb.append( "  entities         : " ).append( manifest.getEntities().size() ).append( '\n' );
			sb.append( "  integrity        : " )
			    .append( Files.exists( ManifestService.checksumFile( folder, app ) ) ? "✅ checksummed" : "⚠️  no checksum" );
			sb.append( '\n' ).append( "  facade jar       : " )
			    .append( Files.exists( ManifestService.facadesJar( folder, app ) ) ? "✅ present" : "➖ absent" );
		}
		return new CliResult( sb.toString(), 0 );
	}

	private static CliResult validate( Path folder, Command command ) {
		// A missing manifest fails closed (exit 1) with the same guidance as the other verbs; read() then performs the
		// integrity + format-version checks and throws on any problem. Every selected application is checked.
		List<String> apps = selectedApps( folder, command );
		if ( apps.isEmpty() || ( command.app() != null && !Files.exists( ManifestService.manifestFile( folder, apps.get( 0 ) ) ) ) ) {
			return new CliResult( "❌ " + noManifestMessage( folder, command.app() ), 1 );
		}
		StringBuilder	sb		= new StringBuilder();
		int				exit	= 0;
		for ( String app : apps ) {
			if ( sb.length() > 0 ) {
				sb.append( '\n' );
			}
			try {
				OrmManifest manifest = ManifestService.read( folder, app, true );
				sb.append( "✅ ORM manifest [" ).append( app ).append( "] at [" ).append( folder ).append( "] is valid (" )
				    .append( manifest.getEntities().size() ).append( " entities, format " ).append( manifest.getFormatVersion() ).append( ")." );
			} catch ( RuntimeException e ) {
				sb.append( "❌ ORM manifest [" ).append( app ).append( "] is INVALID: " ).append( e.getMessage() );
				exit = 1;
			}
		}
		return new CliResult( sb.toString(), exit );
	}

	private static CliResult entities( Path folder, Command command ) {
		String		app			= requireApp( folder, command );
		OrmManifest	manifest	= requireManifest( folder, app );
		if ( manifest.getEntities().isEmpty() ) {
			return new CliResult( "📭 The ORM manifest [" + app + "] contains no entities.", 0 );
		}
		StringBuilder sb = new StringBuilder( "📦 Entities in the ORM manifest [" + app + "] (" + manifest.getEntities().size() + "):\n" );
		for ( OrmManifest.Entity e : manifest.getEntities() ) {
			sb.append( "  • " ).append( e.entityName() )
			    .append( "  [" ).append( e.classFQN() ).append( "]" )
			    .append( "  datasource=" ).append( e.datasource() == null ? "<default>" : e.datasource() )
			    .append( '\n' );
		}
		return new CliResult( sb.toString().stripTrailing(), 0 );
	}

	private static CliResult entity( Path folder, Command command ) {
		String name = command.positional().isEmpty() ? null : command.positional().get( 0 );
		if ( name == null || name.isBlank() ) {
			return new CliResult( "Usage: bxorm entity <entityName> [--app=<name>]", 1 );
		}
		String				app			= requireApp( folder, command );
		OrmManifest			manifest	= requireManifest( folder, app );
		OrmManifest.Entity	match		= manifest.getEntities().stream()
		    .filter( e -> name.equalsIgnoreCase( e.entityName() ) )
		    .findFirst()
		    .orElse( null );
		if ( match == null ) {
			return new CliResult( "❌ No entity named [" + name + "] in the ORM manifest [" + app + "].", 1 );
		}
		StringBuilder sb = new StringBuilder();
		sb.append( "🔎 Entity [" ).append( match.entityName() ).append( "]\n" );
		sb.append( "  class     : " ).append( match.classFQN() ).append( '\n' );
		sb.append( "  datasource: " ).append( match.datasource() == null ? "<default>" : match.datasource() ).append( '\n' );
		sb.append( "  source    : " ).append( match.source() == null ? "<none>" : match.source() ).append( '\n' );
		sb.append( "  mapping XML:\n" ).append( match.mappingXml() == null ? "<none>" : match.mappingXml() );
		return new CliResult( sb.toString(), 0 );
	}

	private static CliResult mappings( Path folder, Command command ) {
		String		app			= requireApp( folder, command );
		OrmManifest	manifest	= requireManifest( folder, app );
		String		combined	= manifest.getCombinedMappingXml();
		if ( combined != null && !combined.isBlank() ) {
			return new CliResult( combined, 0 );
		}
		// Fall back to concatenating each entity's stored mapping XML.
		StringBuilder sb = new StringBuilder();
		for ( OrmManifest.Entity e : manifest.getEntities() ) {
			if ( e.mappingXml() != null ) {
				sb.append( e.mappingXml() ).append( '\n' );
			}
		}
		return new CliResult( sb.length() == 0 ? "📭 The ORM manifest [" + app + "] contains no mapping XML." : sb.toString().stripTrailing(), 0 );
	}

	private static CliResult clear( Path folder, Command command ) {
		if ( !Files.exists( folder ) ) {
			return new CliResult( "ℹ️  Nothing to clear; no [" + folder + "] folder exists.", 0 );
		}
		if ( command.all() ) {
			try ( var walk = Files.walk( folder ) ) {
				List<Path> paths = walk.sorted( Comparator.reverseOrder() ).toList();
				for ( Path p : paths ) {
					Files.deleteIfExists( p );
				}
			} catch ( IOException e ) {
				return new CliResult( "Failed to clear [" + folder + "]: " + e.getMessage(), 1 );
			}
			return new CliResult( "🧹 Cleared the ORM boot cache of every application at [" + folder + "].", 0 );
		}
		String	app		= requireApp( folder, command );
		int		deleted	= ManifestService.clear( folder, app );
		if ( deleted == 0 ) {
			return new CliResult( "ℹ️  Nothing to clear; no boot cache for application [" + app + "] in [" + folder + "].", 0 );
		}
		return new CliResult( "🧹 Cleared the ORM boot cache of application [" + app + "] at [" + folder + "].", 0 );
	}

	/** Read an application's manifest or raise a CLI error if it is absent. */
	private static OrmManifest requireManifest( Path folder, String app ) {
		OrmManifest manifest = ManifestService.read( folder, app, false );
		if ( manifest == null ) {
			throw new CliError( "❌ " + noManifestMessage( folder, app ) );
		}
		return manifest;
	}

	/**
	 * The guidance shown whenever no manifest exists at the folder the CLI is looking at. The CLI looks in
	 * {@code <--dir or current directory>/.bxorm} and does not read the application's {@code ormManifestLocation}, so say
	 * how to point it there.
	 *
	 * @param folder The {@code .bxorm/} folder.
	 *
	 * @return The message.
	 */
	static String noManifestMessage( Path folder ) {
		return noManifestMessage( folder, null );
	}

	/**
	 * The no-manifest guidance, naming the application when one was asked for.
	 *
	 * @param folder The {@code .bxorm/} folder.
	 * @param app    The application asked for, or {@code null}.
	 *
	 * @return The message.
	 */
	static String noManifestMessage( Path folder, String app ) {
		String what = app == null ? "No ORM manifest" : "No ORM manifest for application [" + ManifestService.appKey( app ) + "]";
		return what + " found at [" + folder + "]. Boot the app once with ormManifest=\"auto\" to generate it."
		    + " If your app sets ormManifestLocation, point the CLI at it with --dir=<that folder>.";
	}

	/**
	 * The guidance shown when a single-application verb finds several applications and no {@code --app}.
	 *
	 * @param folder The {@code .bxorm/} folder.
	 * @param apps   The application keys found.
	 *
	 * @return The message.
	 */
	static String severalAppsMessage( Path folder, List<String> apps ) {
		return "The ORM boot cache at [" + folder + "] holds several applications: " + String.join( ", ", apps )
		    + ". Pick one with --app=<name>.";
	}

	private static String shorten( String hash ) {
		return hash == null ? "<none>" : ( hash.length() > 12 ? hash.substring( 0, 12 ) : hash );
	}

	/**
	 * The usage / help text.
	 *
	 * @return The usage text.
	 */
	public static String usage() {
		return """
		       📦 bxorm - ORM manifest boot-cache tool

		       Usage: boxlang module:orm <verb> [args] [--app=<name>] [--dir=<path>]

		       Verbs:
		         info                 Show each application's manifest header and entity count (default).
		         validate             Integrity- and format-check the manifests; non-zero exit on failure.
		         entities             List the entities recorded in the manifest.
		         entity <name>        Show one entity's class, datasource, source and mapping XML.
		         mappings             Print the combined Hibernate mapping XML.
		         clear                Delete an application's boot cache; --all deletes the whole .bxorm/ folder.
		         version              Show module and manifest format versions.
		         help                 Show this help.

		       Options:
		         --app=<name>         The application to work on. Needed when the folder holds several.
		         --all                With clear: remove every application's cache.
		         --dir=<path>         The folder that holds .bxorm/ (default: the current directory).

		       Each application has its own manifest-{app}.json, manifest-{app}.sha256 and facades-{app}.jar.
		       The manifest is generated by booting your app once with ormManifest="auto";
		       in production, ormManifest="trust" loads it with no discovery or parsing.""";
	}
}
