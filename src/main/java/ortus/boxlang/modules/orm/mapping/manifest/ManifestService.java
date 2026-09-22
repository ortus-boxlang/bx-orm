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
import ortus.boxlang.runtime.util.FileSystemUtil;

/**
 * Reads, writes and validates the ORM {@code .bxorm/} boot cache (manifest.json).
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
	/** The manifest file name inside the folder. */
	public static final String	MANIFEST_NAME	= "manifest.json";
	/** The integrity checksum sidecar (sha256 of manifest.json bytes). */
	public static final String	CHECKSUM_NAME	= "manifest.sha256";

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
		return Path.of( FileSystemUtil.expandPath( context, FOLDER_NAME ).absolutePath().toString() );
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
	 * Write the manifest atomically to the given folder (temp file + move), alongside its integrity checksum.
	 *
	 * @param manifest The manifest to write.
	 * @param folder   The {@code .bxorm/} folder.
	 */
	public static void write( OrmManifest manifest, Path folder ) {
		try {
			Files.createDirectories( folder );
			String	json	= manifest.toJSON();
			byte[]	bytes	= json.getBytes( StandardCharsets.UTF_8 );
			Path	tmp		= folder.resolve( MANIFEST_NAME + ".tmp" );
			Files.write( tmp, bytes );
			Files.move( tmp, folder.resolve( MANIFEST_NAME ), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
			    java.nio.file.StandardCopyOption.ATOMIC_MOVE );
			Files.write( folder.resolve( CHECKSUM_NAME ), sha256( bytes ).getBytes( StandardCharsets.UTF_8 ) );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Failed to write the ORM manifest to [" + folder + "]", e );
		}
	}

	/**
	 * Read and integrity-check the manifest from the given folder.
	 *
	 * @param folder       The {@code .bxorm/} folder.
	 * @param failIfAbsent When true (trust mode), a missing manifest is a hard error; when false, returns {@code null}.
	 *
	 * @return The loaded manifest, or {@code null} if absent and {@code failIfAbsent} is false.
	 */
	public static OrmManifest read( Path folder, boolean failIfAbsent ) {
		Path manifestFile = folder.resolve( MANIFEST_NAME );
		if ( !Files.exists( manifestFile ) ) {
			if ( failIfAbsent ) {
				throw new BoxRuntimeException(
				    "ORM manifest mode is [trust] but no manifest was found at [" + manifestFile
				        + "]. Generate it first (bxorm manifest generate) or switch ormManifest to [auto]." );
			}
			return null;
		}
		try {
			byte[]	bytes	= Files.readAllBytes( manifestFile );
			// Integrity guard: the recorded checksum must match the manifest bytes (detects corruption / naive tampering).
			Path	sumFile	= folder.resolve( CHECKSUM_NAME );
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
