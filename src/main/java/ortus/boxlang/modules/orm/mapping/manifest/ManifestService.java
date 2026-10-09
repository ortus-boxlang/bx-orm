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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.util.StringUtil;
import ortus.boxlang.runtime.util.FileSystemUtil;

/**
 * Reads, writes and validates the ORM {@code .bxorm/} boot cache.
 * <p>
 * Several ORM applications can share one {@code .bxorm/} folder (sub-applications under the same root), so every file
 * carries the application key (the slugified application name, see {@link #appKey(String)}):
 * {@code manifest-{app}.json}, {@code manifest-{app}.sha256} and {@code facades-{app}.jar}.
 * <p>
 * In {@code auto} mode the manifest is (re)written after every full boot so it always reflects the current entities;
 * in {@code trust} mode it is loaded as-is, integrity-checked (fail-closed), and used to boot with zero entity
 * discovery, parsing or mapping generation.
 *
 * @since 2.0.0
 */
public final class ManifestService {

	/** The cache folder name at the application root. */
	public static final String	FOLDER_NAME		= ".bxorm";
	/** The manifest file name prefix; the full name is {@code manifest-{app}.json}. */
	public static final String	MANIFEST_PREFIX	= "manifest-";
	/** The manifest file extension. */
	public static final String	MANIFEST_EXT	= ".json";
	/** The integrity checksum sidecar extension (sha256 of the manifest bytes); the full name is {@code manifest-{app}.sha256}. */
	public static final String	CHECKSUM_EXT	= ".sha256";
	/** The facade bytecode archive prefix; the full name is {@code facades-{app}.jar} (written in auto, loaded in trust). */
	public static final String	FACADES_PREFIX	= "facades-";
	/** The facade bytecode archive extension. */
	public static final String	FACADES_EXT		= ".jar";

	private ManifestService() {
	}

	/**
	 * Resolve the {@code .bxorm/} folder for an application, at the application root.
	 *
	 * @param context The request context (used to expand the application-relative path).
	 *
	 * @return The absolute path to the {@code .bxorm/} folder.
	 */
	public static Path resolveFolder( IBoxContext context ) {
		return resolveFolder( context, null );
	}

	/**
	 * Resolve the {@code .bxorm/} folder for an application, under the given location.
	 * <p>
	 * A blank location resolves to the application root (the default). A relative location is resolved against the
	 * application root; an absolute location is used as-is. The folder name is always {@code .bxorm}; only its parent
	 * directory moves. Configured in {@code Application.bx} via the {@code ormManifestLocation} ORM setting.
	 *
	 * @param context  The request context (used to expand the application-relative path).
	 * @param location The directory that holds the {@code .bxorm/} folder, or {@code null}/blank for the application root.
	 *
	 * @return The absolute path to the {@code .bxorm/} folder.
	 */
	public static Path resolveFolder( IBoxContext context, String location ) {
		if ( location == null || location.isBlank() ) {
			return Path.of( FileSystemUtil.expandPath( context, FOLDER_NAME ).absolutePath().toString() );
		}
		Path base = Path.of( FileSystemUtil.expandPath( context, location.trim() ).absolutePath().toString() );
		return base.resolve( FOLDER_NAME );
	}

	/**
	 * The application key used in the boot cache file names: the application name slugified with BoxLang's own
	 * {@code slugify()} ({@link StringUtil#slugify(String, int, String)}): lower-cased, accents stripped, and every run of
	 * characters other than {@code a-z}, {@code 0-9} and {@code -} turned into {@code -}. {@code My Shop} becomes
	 * {@code my-shop}.
	 *
	 * @param appName The application name ({@code this.name}), or a key already derived from it.
	 *
	 * @return The application key; {@code default} for a blank name.
	 */
	public static String appKey( String appName ) {
		String key = appName == null ? "" : StringUtil.slugify( appName, 0, "" );
		return key.isEmpty() ? "default" : key;
	}

	/**
	 * The manifest file of an application: {@code manifest-{app}.json}.
	 *
	 * @param folder  The {@code .bxorm/} folder.
	 * @param appName The application name or key.
	 *
	 * @return The manifest file path.
	 */
	public static Path manifestFile( Path folder, String appName ) {
		return folder.resolve( MANIFEST_PREFIX + appKey( appName ) + MANIFEST_EXT );
	}

	/**
	 * The integrity checksum sidecar of an application's manifest: {@code manifest-{app}.sha256}.
	 *
	 * @param folder  The {@code .bxorm/} folder.
	 * @param appName The application name or key.
	 *
	 * @return The checksum file path.
	 */
	public static Path checksumFile( Path folder, String appName ) {
		return folder.resolve( MANIFEST_PREFIX + appKey( appName ) + CHECKSUM_EXT );
	}

	/**
	 * The facade bytecode archive of an application: {@code facades-{app}.jar}.
	 *
	 * @param folder  The {@code .bxorm/} folder.
	 * @param appName The application name or key.
	 *
	 * @return The facade jar path.
	 */
	public static Path facadesJar( Path folder, String appName ) {
		return folder.resolve( FACADES_PREFIX + appKey( appName ) + FACADES_EXT );
	}

	/**
	 * The application keys that have a manifest in the folder, found from the {@code manifest-{app}.json} file names.
	 *
	 * @param folder The {@code .bxorm/} folder.
	 *
	 * @return The application keys, sorted; empty when the folder or no manifest exists.
	 */
	public static List<String> listApps( Path folder ) {
		if ( folder == null || !Files.isDirectory( folder ) ) {
			return List.of();
		}
		try ( var files = Files.list( folder ) ) {
			return files
			    .map( p -> p.getFileName().toString() )
			    .filter( n -> n.startsWith( MANIFEST_PREFIX ) && n.endsWith( MANIFEST_EXT ) && n.length() > MANIFEST_PREFIX.length() + MANIFEST_EXT.length() )
			    .map( n -> n.substring( MANIFEST_PREFIX.length(), n.length() - MANIFEST_EXT.length() ) )
			    .sorted()
			    .toList();
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Failed to list the ORM boot cache at [" + folder + "]", e );
		}
	}

	/**
	 * Delete one application's boot cache files (manifest, checksum and facade jar), leaving other applications' files.
	 *
	 * @param folder  The {@code .bxorm/} folder.
	 * @param appName The application name or key.
	 *
	 * @return The number of files deleted.
	 */
	public static int clear( Path folder, String appName ) {
		int deleted = 0;
		for ( Path file : List.of( manifestFile( folder, appName ), checksumFile( folder, appName ), facadesJar( folder, appName ) ) ) {
			try {
				if ( Files.deleteIfExists( file ) ) {
					deleted++;
				}
			} catch ( IOException e ) {
				throw new BoxRuntimeException( "Failed to delete [" + file + "]", e );
			}
		}
		return deleted;
	}

	/**
	 * Build a manifest from a freshly discovered entity map (grouped by datasource).
	 *
	 * @param entityMap  The discovered entities, keyed by datasource.
	 * @param config     The ORM configuration (for the config fingerprint).
	 * @param ormVersion The current ORM/module version stamp.
	 *
	 * @return A populated manifest ready to serialize.
	 */
	public static OrmManifest build( Map<Key, List<EntityRecord>> entityMap, ORMConfig config, String ormVersion ) {
		OrmManifest manifest = new OrmManifest()
		    .setAppName( appKey( config.appName ) )
		    .setOrmVersion( ormVersion )
		    .setConfigFingerprint( configFingerprint( config ) );

		entityMap.forEach( ( datasource, records ) -> {
			for ( EntityRecord record : records ) {
				manifest.addEntity( new OrmManifest.Entity(
				    record.getEntityName(),
				    record.getClassFQN(),
				    datasource == null ? null : datasource.getName(),
				    sourceFingerprint( record ),
				    record.getMetadata(),
				    resolveXml( record )
				) );
			}
		} );
		return manifest;
	}

	/**
	 * Rehydrate a manifest's entities into an entity map grouped by datasource, ready for the session factories - with no
	 * entity discovery, parsing or mapping generation. Each rehydrated record carries its mapping XML in memory.
	 *
	 * @param manifest The loaded manifest.
	 *
	 * @return The entity map keyed by datasource.
	 */
	public static Map<Key, List<EntityRecord>> toEntityMap( OrmManifest manifest ) {
		Map<Key, List<EntityRecord>>	map		= new LinkedHashMap<>();
		List<EntityRecord>				records	= manifest.toEntityRecords();
		int								i		= 0;
		for ( OrmManifest.Entity entity : manifest.getEntities() ) {
			EntityRecord record = records.get( i++ );
			record.setXmlMapping( entity.mappingXml() );
			Key ds = record.getDatasource();
			map.computeIfAbsent( ds, k -> new ArrayList<>() ).add( record );
		}
		return map;
	}

	/**
	 * Write the manifest atomically to the given folder (temp file + move), alongside its integrity checksum. The file names
	 * carry the manifest's application key: {@code manifest-{app}.json} and {@code manifest-{app}.sha256}.
	 *
	 * @param manifest The manifest to write; its {@link OrmManifest#getAppName() application key} names the files.
	 * @param folder   The {@code .bxorm/} folder.
	 */
	public static void write( OrmManifest manifest, Path folder ) {
		String	app				= manifest.getAppName();
		Path	manifestFile	= manifestFile( folder, app );
		try {
			Files.createDirectories( folder );
			String	json	= manifest.toJSON();
			byte[]	bytes	= json.getBytes( StandardCharsets.UTF_8 );
			Path	tmp		= folder.resolve( manifestFile.getFileName() + ".tmp" );
			Files.write( tmp, bytes );
			Files.move( tmp, manifestFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
			    java.nio.file.StandardCopyOption.ATOMIC_MOVE );
			Files.write( checksumFile( folder, app ), sha256( bytes ).getBytes( StandardCharsets.UTF_8 ) );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Failed to write the ORM manifest to [" + manifestFile + "]", e );
		}
	}

	/**
	 * Read and integrity-check an application's manifest ({@code manifest-{app}.json}) from the given folder.
	 *
	 * @param folder       The {@code .bxorm/} folder.
	 * @param appName      The application name or key whose manifest to read.
	 * @param failIfAbsent When true (trust mode), a missing manifest is a hard error; when false, returns {@code null}.
	 *
	 * @return The loaded manifest, or {@code null} if absent and {@code failIfAbsent} is false.
	 */
	public static OrmManifest read( Path folder, String appName, boolean failIfAbsent ) {
		Path manifestFile = manifestFile( folder, appName );
		if ( !Files.exists( manifestFile ) ) {
			if ( failIfAbsent ) {
				throw new BoxRuntimeException(
				    "ORM manifest mode is [trust] but no manifest for application [" + appKey( appName ) + "] was found at [" + manifestFile
				        + "]. Boot the app once with ormManifest=\"auto\" to generate it, then switch back to [trust]." );
			}
			return null;
		}
		try {
			byte[]	bytes	= Files.readAllBytes( manifestFile );
			// Integrity guard: the recorded checksum must match the manifest bytes (detects corruption / naive tampering).
			Path	sumFile	= checksumFile( folder, appName );
			if ( Files.exists( sumFile ) ) {
				String recorded = new String( Files.readAllBytes( sumFile ), StandardCharsets.UTF_8 ).trim();
				if ( !recorded.equals( sha256( bytes ) ) ) {
					throw new BoxRuntimeException(
					    "ORM manifest integrity check failed at [" + manifestFile
					        + "]: checksum mismatch. The manifest is corrupt or was modified; regenerate it." );
				}
			}
			OrmManifest manifest = OrmManifest.fromJSON( new String( bytes, StandardCharsets.UTF_8 ) );
			if ( manifest.getFormatVersion() != OrmManifest.FORMAT_VERSION ) {
				throw new BoxRuntimeException( "ORM manifest at [" + manifestFile + "] has format version " + manifest.getFormatVersion()
				    + " but this module expects " + OrmManifest.FORMAT_VERSION + "; regenerate it." );
			}
			return manifest;
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Failed to read the ORM manifest from [" + manifestFile + "]", e );
		}
	}

	/**
	 * Check a loaded manifest against the running application before a trust-mode boot, so a stale manifest fails closed
	 * instead of silently booting old mappings. Compares:
	 * <ul>
	 * <li>the application key the manifest was written for (a manifest copied or renamed from another application);</li>
	 * <li>the ORM settings fingerprint ({@link #configFingerprint(ORMConfig)}), e.g. a changed naming strategy or
	 * application name (the facade namespace);</li>
	 * <li>the module version, when both the manifest and the running module carry a real (non-{@code dev}) version;</li>
	 * <li>each entity's source file, when it still exists at the recorded path: unchanged size + mtime is trusted, anything
	 * else is re-hashed and compared.</li>
	 * </ul>
	 * A source file that is not at its recorded path (e.g. the manifest was generated on a build machine with a different
	 * checkout path) cannot be checked and is skipped. Newly added entity files are not detected, since trust mode does no
	 * discovery.
	 *
	 * @param manifest   The loaded manifest.
	 * @param config     The running application's ORM configuration.
	 * @param ormVersion The running module version.
	 *
	 * @return The reasons the manifest is stale; empty when it is current.
	 */
	public static List<String> verify( OrmManifest manifest, ORMConfig config, String ormVersion ) {
		List<String>	problems	= new ArrayList<>();
		String			expected	= appKey( config.appName );
		if ( !expected.equals( manifest.getAppName() ) ) {
			problems.add( "it belongs to application [" + ( manifest.getAppName().isEmpty() ? "unknown" : manifest.getAppName() )
			    + "], not [" + expected + "]" );
		}
		if ( !configFingerprint( config ).equals( manifest.getConfigFingerprint() ) ) {
			problems.add( "the ORM settings changed (dialect, datasource, namingStrategy, application name, dbcreate, quoteIdentifiers or entityPaths)" );
		}
		if ( isRealVersion( ormVersion ) && isRealVersion( manifest.getOrmVersion() ) && !ormVersion.equals( manifest.getOrmVersion() ) ) {
			problems.add( "it was generated by bx-orm " + manifest.getOrmVersion() + " but this is bx-orm " + ormVersion );
		}
		for ( OrmManifest.Entity entity : manifest.getEntities() ) {
			String changed = sourceChange( entity.source() );
			if ( changed != null ) {
				problems.add( "entity [" + entity.entityName() + "] " + changed );
			}
		}
		return problems;
	}

	/** Whether a version stamp is a real release/snapshot version (not blank, {@code dev}, or an unreplaced build token). */
	private static boolean isRealVersion( String version ) {
		return version != null && !version.isBlank() && !version.equals( "dev" ) && !version.contains( "@" );
	}

	/**
	 * Describe how an entity's source file differs from its recorded fingerprint.
	 *
	 * @return A short reason, or {@code null} when unchanged or not checkable.
	 */
	private static String sourceChange( IStruct source ) {
		if ( source == null ) {
			return null;
		}
		String	path	= String.valueOf( source.getOrDefault( Key.path, "" ) );
		String	hash	= String.valueOf( source.getOrDefault( Key.of( "hash" ), "" ) );
		if ( path.isEmpty() || hash.isEmpty() ) {
			return null;
		}
		Path file = Path.of( path );
		if ( !Files.exists( file ) ) {
			return null;
		}
		try {
			long	size	= Files.size( file );
			long	mtime	= Files.getLastModifiedTime( file ).toMillis();
			if ( size == toLong( source.get( Key.of( "size" ) ) ) && mtime == toLong( source.get( Key.of( "mtime" ) ) ) ) {
				return null;
			}
			return sha256( Files.readAllBytes( file ) ).equals( hash ) ? null : "source changed: " + path;
		} catch ( IOException e ) {
			return "source unreadable: " + path;
		}
	}

	private static long toLong( Object value ) {
		return value instanceof Number n ? n.longValue() : -1L;
	}

	/**
	 * Compute a fingerprint of the ORM settings that affect generated output; a change invalidates a manifest in auto mode.
	 *
	 * @param config The ORM configuration.
	 *
	 * @return A hex sha256 of the relevant settings.
	 */
	public static String configFingerprint( ORMConfig config ) {
		StringBuilder sb = new StringBuilder();
		sb.append( "dialect=" ).append( config.dialect ).append( '\n' );
		sb.append( "datasource=" ).append( config.datasource ).append( '\n' );
		sb.append( "namingStrategy=" ).append( config.namingStrategy ).append( '\n' );
		sb.append( "facadeNamespace=" ).append( config.facadeNamespace ).append( '\n' );
		sb.append( "dbcreate=" ).append( config.dbcreate ).append( '\n' );
		sb.append( "quoteIdentifiers=" ).append( config.quoteIdentifiers ).append( '\n' );
		if ( config.entityPaths != null ) {
			sb.append( "entityPaths=" ).append( String.join( ",", config.entityPaths ) ).append( '\n' );
		}
		return sha256( sb.toString().getBytes( StandardCharsets.UTF_8 ) );
	}

	/**
	 * Build the source-file fingerprint (path, hash, mtime, size) for an entity, from its metadata's source path.
	 */
	private static IStruct sourceFingerprint( EntityRecord record ) {
		Object	pathObj	= record.getMetadata() == null ? null : record.getMetadata().get( Key.path );
		String	path	= pathObj == null ? "" : pathObj.toString();
		if ( path.isEmpty() ) {
			return Struct.of( "path", "", "hash", "", "mtime", 0L, "size", 0L );
		}
		try {
			Path p = Path.of( path );
			if ( Files.exists( p ) ) {
				byte[] bytes = Files.readAllBytes( p );
				return Struct.of( "path", path, "hash", sha256( bytes ), "mtime", Files.getLastModifiedTime( p ).toMillis(), "size", ( long ) bytes.length );
			}
		} catch ( IOException e ) {
			// Best-effort fingerprint; a missing/unreadable source just yields an empty hash.
		}
		return Struct.of( "path", path, "hash", "", "mtime", 0L, "size", 0L );
	}

	/** Resolve an entity's mapping XML: prefer the in-memory string, else read its generated file. */
	private static String resolveXml( EntityRecord record ) {
		if ( record.getXmlMapping() != null && !record.getXmlMapping().isEmpty() ) {
			return record.getXmlMapping();
		}
		if ( record.getXmlFilePath() != null ) {
			try {
				return new String( Files.readAllBytes( record.getXmlFilePath() ), StandardCharsets.UTF_8 );
			} catch ( IOException e ) {
				return "";
			}
		}
		return "";
	}

	/** Hex sha256 of the given bytes. */
	public static String sha256( byte[] bytes ) {
		try {
			byte[]			digest	= MessageDigest.getInstance( "SHA-256" ).digest( bytes );
			StringBuilder	sb		= new StringBuilder( digest.length * 2 );
			for ( byte b : digest ) {
				sb.append( Character.forDigit( ( b >> 4 ) & 0xF, 16 ) ).append( Character.forDigit( b & 0xF, 16 ) );
			}
			return sb.toString();
		} catch ( java.security.NoSuchAlgorithmException e ) {
			throw new BoxRuntimeException( "SHA-256 unavailable", e );
		}
	}
}
