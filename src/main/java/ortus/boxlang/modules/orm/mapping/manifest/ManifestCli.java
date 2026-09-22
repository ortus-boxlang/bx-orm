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
 *
 * @since 2.0.0
 */
public final class ManifestCli {

	/** The result of a verb: a message to print and a process exit code. */
	public record CliResult( String message, int exitCode ) {
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
	 * @param args            The verb and its arguments.
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
	 * @param args          The verb and its arguments; an empty array defaults to {@code info}.
	 *
	 * @return The verb result (message + exit code).
	 */
	public static CliResult run( Path folder, String moduleVersion, String... args ) {
		String verb = ( args == null || args.length == 0 || args[ 0 ] == null || args[ 0 ].isBlank() ) ? "info" : args[ 0 ].trim().toLowerCase();
		try {
			return switch ( verb ) {
				case "help", "-h", "--help" -> new CliResult( usage(), 0 );
				case "version", "-v", "--version" -> version( folder, moduleVersion );
				case "info" -> info( folder );
				case "validate" -> validate( folder );
				case "entities" -> entities( folder );
				case "entity" -> entity( folder, args.length > 1 ? args[ 1 ] : null );
				case "mappings" -> mappings( folder );
				case "clear" -> clear( folder );
				default -> new CliResult( "Unknown verb [" + verb + "].\n\n" + usage(), 1 );
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

	private static CliResult version( Path folder, String moduleVersion ) {
		OrmManifest		manifest	= readOptional( folder );
		StringBuilder	sb			= new StringBuilder();
		sb.append( "bx-orm module : " ).append( moduleVersion == null ? "unknown" : moduleVersion ).append( '\n' );
		sb.append( "manifest format expected : " ).append( OrmManifest.FORMAT_VERSION );
		if ( manifest != null ) {
			sb.append( '\n' ).append( "manifest format on disk  : " ).append( manifest.getFormatVersion() );
			sb.append( '\n' ).append( "manifest built by ORM    : " ).append( manifest.getOrmVersion() );
		}
		return new CliResult( sb.toString(), 0 );
	}

	private static CliResult info( Path folder ) {
		OrmManifest manifest = readOptional( folder );
		if ( manifest == null ) {
			return new CliResult( "No ORM manifest found at [" + folder + "]. Boot the app once with ormManifest=\"auto\" to generate it.", 0 );
		}
		StringBuilder sb = new StringBuilder();
		sb.append( "ORM manifest [" ).append( folder ).append( "]\n" );
		sb.append( "  format version   : " ).append( manifest.getFormatVersion() ).append( '\n' );
		sb.append( "  built by ORM     : " ).append( manifest.getOrmVersion() ).append( '\n' );
		sb.append( "  config fingerprint: " ).append( shorten( manifest.getConfigFingerprint() ) ).append( '\n' );
		sb.append( "  entities         : " ).append( manifest.getEntities().size() ).append( '\n' );
		sb.append( "  integrity        : " ).append( Files.exists( folder.resolve( ManifestService.CHECKSUM_NAME ) ) ? "checksummed" : "no checksum" );
		sb.append( '\n' ).append( "  facades.jar      : " ).append( Files.exists( folder.resolve( ManifestService.FACADES_JAR ) ) ? "present" : "absent" );
		return new CliResult( sb.toString(), 0 );
	}

	private static CliResult validate( Path folder ) {
		// read( failIfAbsent=true ) performs the integrity + format-version checks and throws on any problem.
		try {
			OrmManifest manifest = ManifestService.read( folder, true );
			return new CliResult( "ORM manifest at [" + folder + "] is valid (" + manifest.getEntities().size() + " entities, format "
			    + manifest.getFormatVersion() + ").", 0 );
		} catch ( RuntimeException e ) {
			return new CliResult( "ORM manifest is INVALID: " + e.getMessage(), 1 );
		}
	}

	private static CliResult entities( Path folder ) {
		OrmManifest manifest = requireManifest( folder );
		if ( manifest.getEntities().isEmpty() ) {
			return new CliResult( "The ORM manifest contains no entities.", 0 );
		}
		StringBuilder sb = new StringBuilder( "Entities in the ORM manifest:\n" );
		for ( OrmManifest.Entity e : manifest.getEntities() ) {
			sb.append( "  " ).append( e.entityName() )
			    .append( "  [" ).append( e.classFQN() ).append( "]" )
			    .append( "  datasource=" ).append( e.datasource() == null ? "<default>" : e.datasource() )
			    .append( '\n' );
		}
		return new CliResult( sb.toString().stripTrailing(), 0 );
	}

	private static CliResult entity( Path folder, String name ) {
		if ( name == null || name.isBlank() ) {
			return new CliResult( "Usage: bxorm entity <entityName>", 1 );
		}
		OrmManifest			manifest	= requireManifest( folder );
		OrmManifest.Entity	match		= manifest.getEntities().stream()
		    .filter( e -> name.equalsIgnoreCase( e.entityName() ) )
		    .findFirst()
		    .orElse( null );
		if ( match == null ) {
			return new CliResult( "No entity named [" + name + "] in the ORM manifest.", 1 );
		}
		StringBuilder sb = new StringBuilder();
		sb.append( "Entity [" ).append( match.entityName() ).append( "]\n" );
		sb.append( "  class     : " ).append( match.classFQN() ).append( '\n' );
		sb.append( "  datasource: " ).append( match.datasource() == null ? "<default>" : match.datasource() ).append( '\n' );
		sb.append( "  source    : " ).append( match.source() == null ? "<none>" : match.source() ).append( '\n' );
		sb.append( "  mapping XML:\n" ).append( match.mappingXml() == null ? "<none>" : match.mappingXml() );
		return new CliResult( sb.toString(), 0 );
	}

	private static CliResult mappings( Path folder ) {
		OrmManifest	manifest	= requireManifest( folder );
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
		return new CliResult( sb.length() == 0 ? "The ORM manifest contains no mapping XML." : sb.toString().stripTrailing(), 0 );
	}

	private static CliResult clear( Path folder ) {
		if ( !Files.exists( folder ) ) {
			return new CliResult( "Nothing to clear; no [" + folder + "] folder exists.", 0 );
		}
		try ( var walk = Files.walk( folder ) ) {
			List<Path> paths = walk.sorted( Comparator.reverseOrder() ).toList();
			for ( Path p : paths ) {
				Files.deleteIfExists( p );
			}
		} catch ( IOException e ) {
			return new CliResult( "Failed to clear [" + folder + "]: " + e.getMessage(), 1 );
		}
		return new CliResult( "Cleared the ORM boot cache at [" + folder + "].", 0 );
	}

	/** Read the manifest, tolerating absence (returns null). */
	private static OrmManifest readOptional( Path folder ) {
		return ManifestService.read( folder, false );
	}

	/** Read the manifest or raise a CLI error if it is absent. */
	private static OrmManifest requireManifest( Path folder ) {
		OrmManifest manifest = ManifestService.read( folder, false );
		if ( manifest == null ) {
			throw new CliError( "No ORM manifest found at [" + folder + "]. Boot the app once with ormManifest=\"auto\" to generate it." );
		}
		return manifest;
	}

	private static String shorten( String hash ) {
		return hash == null ? "<none>" : ( hash.length() > 12 ? hash.substring( 0, 12 ) : hash );
	}

	/** The usage / help text. */
	public static String usage() {
		return """
		       bxorm - ORM manifest boot-cache tool

		       Usage: boxlang module:orm <verb> [args]

		       Verbs:
		         info                 Show the manifest header and entity count (default).
		         validate             Integrity- and format-check the manifest; non-zero exit on failure.
		         entities             List the entities recorded in the manifest.
		         entity <name>        Show one entity's class, datasource, source and mapping XML.
		         mappings             Print the combined Hibernate mapping XML.
		         clear                Delete the .bxorm/ boot cache.
		         version              Show module and manifest format versions.
		         help                 Show this help.

		       The manifest is generated by booting your app once with ormManifest="auto";
		       in production, ormManifest="trust" loads it with no discovery or parsing.""";
	}
}
