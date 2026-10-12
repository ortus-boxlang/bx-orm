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
package ortus.boxlang.modules.orm.hibernate.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.config.segments.CacheConfig;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * The BoxCache properties bx-orm gives each Hibernate second-level cache region, read from the <code>cacheConfig</code> ORM
 * setting.
 * <p>
 * Every region is a BoxLang cache. Its properties are, in order (later wins): BoxLang's cache defaults
 * ({@link CacheConfig#DEFAULTS}), the shared properties of this object, then the overrides of the region that matches the
 * region's name. A region name matches a configured name when they are equal, or when the region name ends with
 * <code>.</code> plus the configured name (so <code>User</code> matches the region <code>bxorm.app.User</code>); the longest
 * matching name wins. Names are compared without regard to case.
 * <p>
 * <code>cacheConfig</code> can be:
 * <ul>
 * <li>a struct of BoxCache properties (<code>maxObjects</code>, <code>defaultTimeout</code>, ...) with an optional
 * <code>regions</code> struct of per-region overrides;</li>
 * <li>the path of a JSON file with the same shape;</li>
 * <li>the path of an EHCache 2 or 3 <code>ehcache.xml</code> file, mapped by {@link EhcacheXmlReader}.</li>
 * </ul>
 */
public final class CacheRegionSettings {

	/**
	 * The struct key that holds the per-region overrides.
	 */
	public static final Key					REGIONS	= Key.of( "regions" );

	/**
	 * No shared properties and no region overrides: every region gets BoxLang's cache defaults.
	 */
	public static final CacheRegionSettings	EMPTY	= new CacheRegionSettings( new Struct(), Map.of() );

	private final IStruct					shared;
	private final Map<String, IStruct>		regions;

	/**
	 * Create the settings.
	 *
	 * @param shared  BoxCache properties for every region.
	 * @param regions Per-region overrides, keyed by region name (any case).
	 */
	public CacheRegionSettings( IStruct shared, Map<String, IStruct> regions ) {
		this.shared = shared == null ? new Struct() : shared;
		Map<String, IStruct> byName = new LinkedHashMap<>();
		if ( regions != null ) {
			regions.forEach( ( name, properties ) -> byName.put( name.toLowerCase( Locale.ROOT ), properties ) );
		}
		this.regions = Collections.unmodifiableMap( byName );
	}

	/**
	 * Read the settings from a <code>cacheConfig</code> struct: every key but <code>regions</code> is a shared BoxCache
	 * property, and <code>regions</code> holds the per-region overrides.
	 *
	 * @param config The struct; may be null.
	 *
	 * @return The settings.
	 */
	public static CacheRegionSettings fromStruct( IStruct config ) {
		if ( config == null || config.isEmpty() ) {
			return EMPTY;
		}
		IStruct					shared	= new Struct();
		Map<String, IStruct>	regions	= new LinkedHashMap<>();
		config.forEach( ( key, value ) -> {
			if ( key.equals( REGIONS ) ) {
				if ( value instanceof IStruct regionStruct ) {
					regionStruct.forEach( ( region, properties ) -> {
						if ( properties instanceof IStruct props ) {
							regions.put( region.getName(), props );
						}
					} );
				} else if ( value != null ) {
					throw new ORMException( ORMErrorType.CONFIG, "The cacheConfig `regions` setting must be a struct of region names to "
					    + "BoxCache properties.", "Use regions : { \"User\" : { maxObjects : 500 } }." );
				}
			} else {
				shared.put( key, value );
			}
		} );
		return new CacheRegionSettings( shared, regions );
	}

	/**
	 * Read the settings from a file: an <code>.xml</code> file is read as an EHCache 2 or 3 configuration, a
	 * <code>.json</code> file as a struct like the <code>cacheConfig</code> struct.
	 *
	 * @param file The file.
	 *
	 * @return The settings.
	 */
	public static CacheRegionSettings fromFile( Path file ) {
		String name = file.getFileName().toString().toLowerCase( Locale.ROOT );
		if ( name.endsWith( ".xml" ) ) {
			return EhcacheXmlReader.read( file );
		}
		if ( name.endsWith( ".json" ) ) {
			try {
				Object parsed = JSONUtil.fromJSON( Files.readString( file ), true );
				if ( parsed instanceof IStruct struct ) {
					return fromStruct( struct );
				}
			} catch ( IOException | RuntimeException e ) {
				throw new ORMException( ORMErrorType.CONFIG, "The cacheConfig file [" + file + "] could not be read: " + e.getMessage(),
				    "Check that the file is valid JSON.", null, e );
			}
			throw new ORMException( ORMErrorType.CONFIG, "The cacheConfig file [" + file + "] must hold a JSON object.",
			    "Use { \"maxObjects\" : 1000, \"regions\" : { \"User\" : { \"maxObjects\" : 500 } } }." );
		}
		throw new ORMException( ORMErrorType.CONFIG, "The cacheConfig file [" + file + "] must be an .xml (EHCache) or .json file.",
		    "Point cacheConfig at an ehcache.xml or a .json file, or set it to a struct." );
	}

	/**
	 * The BoxCache properties for one region: BoxLang's cache defaults, then the shared properties, then the overrides of the
	 * best matching configured region.
	 *
	 * @param regionName The Hibernate region name.
	 *
	 * @return A new struct of properties.
	 */
	public IStruct propertiesFor( String regionName ) {
		IStruct properties = new Struct();
		properties.putAll( CacheConfig.DEFAULTS );
		properties.putAll( this.shared );
		IStruct overrides = overridesFor( regionName );
		if ( overrides != null ) {
			properties.putAll( overrides );
		}
		return properties;
	}

	/**
	 * The overrides of the configured region that best matches a region name: an equal name, else the longest configured
	 * name the region name ends with (after a dot).
	 *
	 * @param regionName The Hibernate region name.
	 *
	 * @return The overrides, or null when no configured region matches.
	 */
	IStruct overridesFor( String regionName ) {
		if ( regionName == null || this.regions.isEmpty() ) {
			return null;
		}
		String	name	= regionName.toLowerCase( Locale.ROOT );
		IStruct	exact	= this.regions.get( name );
		if ( exact != null ) {
			return exact;
		}
		String bestName = null;
		for ( String configured : this.regions.keySet() ) {
			if ( name.endsWith( "." + configured ) && ( bestName == null || configured.length() > bestName.length() ) ) {
				bestName = configured;
			}
		}
		return bestName == null ? null : this.regions.get( bestName );
	}

	/**
	 * @return The BoxCache properties shared by every region.
	 */
	public IStruct getShared() {
		return this.shared;
	}

	/**
	 * @return The per-region overrides, keyed by lower-cased region name.
	 */
	public Map<String, IStruct> getRegions() {
		return this.regions;
	}
}
