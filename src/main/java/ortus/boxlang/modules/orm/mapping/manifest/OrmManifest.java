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

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * The resolved boot model for an ORM application, serialized to {@code .bxorm/manifest.json}.
 * <p>
 * A manifest captures everything the ORM needs to boot <em>without</em> re-discovering, re-parsing or re-generating any
 * entity: the normalized per-entity metadata, the combined Hibernate {@code mapping.xml}, and the fingerprints used to
 * decide freshness (dev/auto) or integrity (prod/trust). It is stored as JSON so it round-trips through BoxLang's own
 * types; the per-entity metadata is the exact struct {@code AbstractEntityMeta.autoDiscoverMetaType} consumes, so
 * rehydrating an entity produces a byte-identical mapping.
 *
 * @since 2.0.0
 */
public class OrmManifest {

	/**
	 * Manifest schema version. Bump when the on-disk shape changes incompatibly; a mismatch invalidates a manifest.
	 */
	public static final int		FORMAT_VERSION			= 1;

	// Struct keys (also the JSON field names).
	private static final Key	FORMAT_VERSION_KEY		= Key.of( "formatVersion" );
	private static final Key	ORM_VERSION_KEY			= Key.of( "ormVersion" );
	private static final Key	CONFIG_FINGERPRINT_KEY	= Key.of( "configFingerprint" );
	private static final Key	COMBINED_XML_KEY		= Key.of( "combinedMappingXml" );
	private static final Key	ENTITIES_KEY			= Key.of( "entities" );

	private static final Key	NAME_KEY				= Key.of( "name" );
	private static final Key	CLASS_FQN_KEY			= Key.of( "classFQN" );
	private static final Key	DATASOURCE_KEY			= Key.of( "datasource" );
	private static final Key	SOURCE_KEY				= Key.of( "source" );
	private static final Key	METADATA_KEY			= Key.of( "metadata" );
	private static final Key	MAPPING_XML_KEY			= Key.of( "mappingXml" );

	private int					formatVersion			= FORMAT_VERSION;
	private String				ormVersion				= "";
	private String				configFingerprint		= "";
	private String				combinedMappingXml		= "";
	private final List<Entity>	entities				= new ArrayList<>();

	/**
	 * A single entity's captured boot state.
	 *
	 * @param entityName The BoxLang entity name.
	 * @param classFQN   The BoxLang class FQN, e.g. {@code models.User}.
	 * @param datasource The datasource name this entity is mapped on (may be {@code null} for the default).
	 * @param source     The source-file fingerprint struct ({@code path}, {@code hash}, {@code mtime}, {@code size}).
	 * @param metadata   The parsed BoxLang class metadata struct (what {@code autoDiscoverMetaType} consumes).
	 * @param mappingXml This entity's generated {@code <entity>} XML fragment (for inspection / mappings export).
	 */
	public record Entity( String entityName, String classFQN, String datasource, IStruct source, IStruct metadata, String mappingXml ) {
	}

	public OrmManifest() {
	}

	public int getFormatVersion() {
		return formatVersion;
	}

	public String getOrmVersion() {
		return ormVersion;
	}

	public OrmManifest setOrmVersion( String ormVersion ) {
		this.ormVersion = ormVersion == null ? "" : ormVersion;
		return this;
	}

	public String getConfigFingerprint() {
		return configFingerprint;
	}

	public OrmManifest setConfigFingerprint( String configFingerprint ) {
		this.configFingerprint = configFingerprint == null ? "" : configFingerprint;
		return this;
	}

	public String getCombinedMappingXml() {
		return combinedMappingXml;
	}

	public OrmManifest setCombinedMappingXml( String combinedMappingXml ) {
		this.combinedMappingXml = combinedMappingXml == null ? "" : combinedMappingXml;
		return this;
	}

	public List<Entity> getEntities() {
		return entities;
	}

	public OrmManifest addEntity( Entity entity ) {
		this.entities.add( entity );
		return this;
	}

	/**
	 * Rehydrate the manifest's entities into {@link EntityRecord}s ready for the session factory, rebuilding each entity's
	 * normalized {@link ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta} from its stored metadata struct - no
	 * source discovery or parsing.
	 *
	 * @return The rehydrated entity records.
	 */
	public List<EntityRecord> toEntityRecords() {
		List<EntityRecord> records = new ArrayList<>( entities.size() );
		for ( Entity entity : entities ) {
			IStruct meta = entity.metadata();
			// The metadata came through JSON, where a Key-valued entry (notably `datasource`, stamped at discovery) rehydrates
			// as a nested struct. Normalize the datasource back to its scalar name (we track it separately on the entity) so
			// AbstractEntityMeta can read it as a string.
			if ( entity.datasource() != null ) {
				meta.put( DATASOURCE_KEY, entity.datasource() );
			} else {
				meta.remove( DATASOURCE_KEY );
			}
			Key				ds		= entity.datasource() == null ? null : Key.of( entity.datasource() );
			EntityRecord	record	= new EntityRecord( entity.entityName(), entity.classFQN(), meta, ds );
			record.setEntityMeta( AbstractEntityMeta.autoDiscoverMetaType( meta ) );
			records.add( record );
		}
		return records;
	}

	/**
	 * Convert this manifest into a plain {@link IStruct} for JSON serialization.
	 *
	 * @return The struct representation.
	 */
	public IStruct toStruct() {
		Array entityArray = new Array();
		for ( Entity entity : entities ) {
			entityArray.add( Struct.of(
			    NAME_KEY, entity.entityName(),
			    CLASS_FQN_KEY, entity.classFQN(),
			    DATASOURCE_KEY, entity.datasource(),
			    SOURCE_KEY, entity.source(),
			    METADATA_KEY, entity.metadata(),
			    MAPPING_XML_KEY, entity.mappingXml()
			) );
		}
		return Struct.of(
		    FORMAT_VERSION_KEY, formatVersion,
		    ORM_VERSION_KEY, ormVersion,
		    CONFIG_FINGERPRINT_KEY, configFingerprint,
		    COMBINED_XML_KEY, combinedMappingXml,
		    ENTITIES_KEY, entityArray
		);
	}

	/**
	 * Rebuild a manifest from its {@link IStruct} representation (as parsed from JSON).
	 *
	 * @param struct The struct representation.
	 *
	 * @return The manifest.
	 */
	@SuppressWarnings( "unchecked" )
	public static OrmManifest fromStruct( IStruct struct ) {
		OrmManifest manifest = new OrmManifest();
		manifest.formatVersion		= struct.getAsInteger( FORMAT_VERSION_KEY );
		manifest.ormVersion			= String.valueOf( struct.getOrDefault( ORM_VERSION_KEY, "" ) );
		manifest.configFingerprint	= String.valueOf( struct.getOrDefault( CONFIG_FINGERPRINT_KEY, "" ) );
		manifest.combinedMappingXml	= String.valueOf( struct.getOrDefault( COMBINED_XML_KEY, "" ) );

		Object entitiesRaw = struct.get( ENTITIES_KEY );
		if ( entitiesRaw instanceof List<?> list ) {
			for ( Object entryRaw : list ) {
				IStruct	entry	= StructCaster.cast( entryRaw );
				Object	dsRaw	= entry.get( DATASOURCE_KEY );
				manifest.addEntity( new Entity(
				    String.valueOf( entry.get( NAME_KEY ) ),
				    String.valueOf( entry.get( CLASS_FQN_KEY ) ),
				    dsRaw == null ? null : String.valueOf( dsRaw ),
				    StructCaster.cast( entry.getOrDefault( SOURCE_KEY, Struct.of() ) ),
				    StructCaster.cast( entry.getOrDefault( METADATA_KEY, Struct.of() ) ),
				    String.valueOf( entry.getOrDefault( MAPPING_XML_KEY, "" ) )
				) );
			}
		}
		return manifest;
	}

	/**
	 * Serialize this manifest to a JSON string.
	 *
	 * @return The JSON representation.
	 */
	public String toJSON() {
		try {
			return JSONUtil.getJSONBuilder().asString( toStruct() );
		} catch ( Exception e ) {
			throw new BoxRuntimeException( "Unable to serialize the ORM manifest to JSON", e );
		}
	}

	/**
	 * Parse a manifest from a JSON string.
	 *
	 * @param json The JSON representation.
	 *
	 * @return The manifest.
	 */
	public static OrmManifest fromJSON( String json ) {
		Object parsed = JSONUtil.fromJSON( json, true );
		return fromStruct( StructCaster.cast( parsed ) );
	}
}
