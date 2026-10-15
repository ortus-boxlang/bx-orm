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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.CacheService;
import tools.BaseORMTest;

/**
 * Live (MySQL) tests of the BoxCache-backed second-level cache: per-region properties from <code>cacheConfig</code>, BoxLang
 * caches defined by the application used as regions, and collection caching. The test app enables the second-level cache and
 * sets <code>cacheConfig.regions.cachedOwner.maxObjects = 777</code>.
 */
public class SecondaryCacheTest extends BaseORMTest {

	private final CacheService cacheService = BoxRuntime.getInstance().getCacheService();

	/**
	 * Clear the ORM session after each test so no entity leaks into the next one.
	 */
	@AfterEach
	public void clearSession() {
		instance.executeSource( "try { ormClearSession(); } catch ( any e ) {}", context );
	}

	/**
	 * Save an owner with two items in its own transaction, then clear the session.
	 *
	 * @return The owner's id.
	 */
	private Object saveOwnerWithItems() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				owner = entityNew( "CachedOwner", { name : "Owner" } );
				entitySave( owner );
				for ( label in [ "a", "b" ] ) {
					item = entityNew( "CachedItem", { label : label } );
					entitySave( item );
					owner.addItem( item );
				}
			}
			ownerId = owner.getId();
			ormClearSession();
		""", context );
		// @formatter:on
		return variables.get( Key.of( "ownerId" ) );
	}

	@DisplayName( "An entity region is a BoxLang cache with the properties cacheConfig gives that region" )
	@Test
	public void testRegionGetsConfiguredProperties() {
		Object id = saveOwnerWithItems();
		variables.put( Key.of( "ownerId" ), id );
		instance.executeSource( "loaded = entityLoadByPK( \"CachedOwner\", ownerId );", context );

		assertThat( cacheService.hasCache( Key.of( "cachedOwner" ) ) ).isTrue();
		ICacheProvider region = cacheService.getCache( Key.of( "cachedOwner" ) );
		assertThat( region.getConfig().properties.get( Key.of( "maxObjects" ) ).toString() ).isEqualTo( "777" );
	}

	@DisplayName( "A collection with cacheUse is cached in its cacheName region" )
	@Test
	public void testCollectionIsCached() {
		Object id = saveOwnerWithItems();
		variables.put( Key.of( "ownerId" ), id );
		instance.executeSource( "itemCount = entityLoadByPK( \"CachedOwner\", ownerId ).getItems().len();", context );
		assertThat( variables.get( Key.of( "itemCount" ) ).toString() ).isEqualTo( "2" );

		// The collection's own region (cacheName="cachedOwnerItems") only exists when Hibernate caches the collection
		Key region = Key.of( "cachedOwnerItems" );
		assertThat( cacheService.hasCache( region ) ).isTrue();
		assertThat( cacheService.getCache( region ).getSize() ).isGreaterThan( 0 );
	}

	@DisplayName( "A BoxLang cache that already exists with a region's name is used as that region" )
	@Test
	public void testExistingBoxLangCacheIsUsed() {
		Key				name	= Key.of( "sc_userdefined" );
		ICacheProvider	mine	= cacheService.hasCache( name ) ? cacheService.getCache( name ) : cacheService.createDefaultCache( name );
		mine.clearAll();
		instance.executeSource( "rows = ormExecuteQuery( \"from Manufacturer\", [], false, { cacheName : \"sc_userdefined\" } );", context );

		assertThat( cacheService.getCache( name ) ).isSameInstanceAs( mine );
		assertThat( mine.getSize() ).isGreaterThan( 0 );
	}
}
