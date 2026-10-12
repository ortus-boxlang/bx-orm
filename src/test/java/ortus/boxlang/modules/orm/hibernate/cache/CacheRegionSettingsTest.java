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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Unit tests for {@link CacheRegionSettings} and {@link EhcacheXmlReader}: how the <code>cacheConfig</code> setting becomes
 * the BoxCache properties of each second-level cache region.
 */
public class CacheRegionSettingsTest {

	private static final Key	MAX_OBJECTS	= Key.of( "maxObjects" );
	private static final Key	TIMEOUT		= Key.of( "defaultTimeout" );
	private static final Key	LAST_ACCESS	= Key.of( "defaultLastAccessTimeout" );
	private static final Key	EVICTION	= Key.of( "evictionPolicy" );

	static {
		BoxRuntime.getInstance( true );
	}

	@DisplayName( "With no cacheConfig every region gets BoxLang's cache defaults" )
	@Test
	public void testEmptyUsesDefaults() {
		IStruct properties = CacheRegionSettings.EMPTY.propertiesFor( "anything" );
		assertThat( properties.get( MAX_OBJECTS ) ).isEqualTo( 1000 );
		assertThat( properties.get( TIMEOUT ) ).isEqualTo( 3600 );
	}

	@DisplayName( "A cacheConfig struct sets shared properties and per-region overrides" )
	@Test
	public void testStructSharedAndRegions() {
		CacheRegionSettings	settings	= CacheRegionSettings.fromStruct( Struct.of(
		    "maxObjects", 2000,
		    "defaultTimeout", 600,
		    "regions", Struct.of( "User", Struct.of( "maxObjects", 50 ) )
		) );
		IStruct				user		= settings.propertiesFor( "User" );
		IStruct				other		= settings.propertiesFor( "Order" );
		assertThat( user.get( MAX_OBJECTS ) ).isEqualTo( 50 );
		assertThat( user.get( TIMEOUT ) ).isEqualTo( 600 );
		assertThat( other.get( MAX_OBJECTS ) ).isEqualTo( 2000 );
		// BoxLang defaults still fill what the config leaves out
		assertThat( other.get( LAST_ACCESS ) ).isEqualTo( 1800 );
	}

	@DisplayName( "A region name matches a configured name exactly, or by its last dotted segments, ignoring case" )
	@Test
	public void testRegionNameMatching() {
		CacheRegionSettings settings = CacheRegionSettings.fromStruct( Struct.of(
		    "regions", Struct.of(
		        "user", Struct.of( "maxObjects", 10 ),
		        "User.orders", Struct.of( "maxObjects", 20 )
		    )
		) );
		assertThat( settings.propertiesFor( "USER" ).get( MAX_OBJECTS ) ).isEqualTo( 10 );
		assertThat( settings.propertiesFor( "bxorm.myapp.User" ).get( MAX_OBJECTS ) ).isEqualTo( 10 );
		// the longest matching configured name wins
		assertThat( settings.propertiesFor( "bxorm.myapp.User.orders" ).get( MAX_OBJECTS ) ).isEqualTo( 20 );
		// a name that only contains the configured name does not match
		assertThat( settings.propertiesFor( "bxorm.myapp.SuperUser" ).get( MAX_OBJECTS ) ).isEqualTo( 1000 );
	}

	@DisplayName( "regions must be a struct" )
	@Test
	public void testRegionsMustBeStruct() {
		assertThrows( ORMException.class, () -> CacheRegionSettings.fromStruct( Struct.of( "regions", "User" ) ) );
	}

	@DisplayName( "A JSON cacheConfig file has the same shape as the struct" )
	@Test
	public void testJsonFile() {
		CacheRegionSettings settings = CacheRegionSettings.fromFile( Path.of( "src/test/resources/cache/cacheconfig.json" ) );
		assertThat( settings.propertiesFor( "User" ).get( MAX_OBJECTS ) ).isEqualTo( 50 );
		assertThat( settings.propertiesFor( "Order" ).get( MAX_OBJECTS ) ).isEqualTo( 2000 );
	}

	@DisplayName( "An EHCache 2 ehcache.xml maps defaultCache and each cache to BoxCache properties" )
	@Test
	public void testEhcache2() {
		CacheRegionSettings	settings	= CacheRegionSettings.fromFile( Path.of( "src/test/resources/cache/ehcache2.xml" ) );
		IStruct				other		= settings.propertiesFor( "Order" );
		assertThat( other.get( MAX_OBJECTS ) ).isEqualTo( 5000 );
		assertThat( other.get( TIMEOUT ) ).isEqualTo( 1200L );
		assertThat( other.get( LAST_ACCESS ) ).isEqualTo( 600L );
		assertThat( other.get( EVICTION ) ).isEqualTo( "LFU" );

		IStruct user = settings.propertiesFor( "User" );
		assertThat( user.get( MAX_OBJECTS ) ).isEqualTo( 250 );
		assertThat( user.get( TIMEOUT ) ).isEqualTo( 300L );
		assertThat( user.get( EVICTION ) ).isEqualTo( "LRU" );

		IStruct lookup = settings.propertiesFor( "Lookup" );
		assertThat( lookup.get( TIMEOUT ) ).isEqualTo( 0L );
		assertThat( lookup.get( LAST_ACCESS ) ).isEqualTo( 0L );
	}

	@DisplayName( "An EHCache 3 ehcache.xml maps heap, ttl, tti, none and templates" )
	@Test
	public void testEhcache3() {
		CacheRegionSettings	settings	= CacheRegionSettings.fromFile( Path.of( "src/test/resources/cache/ehcache3.xml" ) );
		IStruct				user		= settings.propertiesFor( "User" );
		// the cache's own heap wins over the template's
		assertThat( user.get( MAX_OBJECTS ) ).isEqualTo( 400 );
		// ttl from the template, 10 minutes
		assertThat( user.get( TIMEOUT ) ).isEqualTo( 600L );

		IStruct lookup = settings.propertiesFor( "Lookup" );
		assertThat( lookup.get( TIMEOUT ) ).isEqualTo( 0L );

		IStruct session = settings.propertiesFor( "Session" );
		assertThat( session.get( LAST_ACCESS ) ).isEqualTo( 7200L );
	}

	@DisplayName( "A cacheConfig file that is neither XML nor JSON is rejected" )
	@Test
	public void testUnknownFileType() {
		assertThrows( ORMException.class, () -> CacheRegionSettings.fromFile( Path.of( "src/test/resources/cache/cacheconfig.txt" ) ) );
	}

	@DisplayName( "A collection role maps back to its property name" )
	@Test
	public void testCollectionPropertyName() {
		assertThat( CollectionCacheIntegrator.propertyName( "bxorm.app.User.orders", "bxorm.app.User" ) ).isEqualTo( "orders" );
		assertThat( CollectionCacheIntegrator.propertyName( "User.orders", null ) ).isEqualTo( "orders" );
	}
}
