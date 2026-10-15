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
package ortus.boxlang.modules.orm;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.hibernate.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Unit tests for {@link HQLQuery#applyCacheAndTimeout(Query, IStruct)}: the {@code cacheable}, {@code cacheName} /
 * {@code cacheRegion} and {@code timeout} options shared by ormExecuteQuery and entityLoad.
 */
public class HQLQueryOptionsTest {

	/** The mocked Hibernate query. */
	private Query<?> query;

	/**
	 * Create a fresh mock query for each test.
	 */
	@BeforeEach
	public void setUp() {
		query = mock( Query.class );
	}

	/**
	 * Test: no options touch nothing.
	 */
	@DisplayName( "No options leave the query untouched" )
	@Test
	public void testNoOptions() {
		HQLQuery.applyCacheAndTimeout( query, new Struct() );
		verify( query, never() ).setCacheable( anyBoolean() );
		verify( query, never() ).setCacheRegion( anyString() );
		verify( query, never() ).setTimeout( anyInt() );
	}

	/**
	 * Test: cacheable true is applied.
	 */
	@DisplayName( "cacheable: true is applied" )
	@Test
	public void testCacheable() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( "cacheable", true ) );
		verify( query ).setCacheable( true );
		verify( query, never() ).setCacheRegion( anyString() );
	}

	/**
	 * Test: cacheable given as the string "yes" is cast.
	 */
	@DisplayName( "cacheable: \"yes\" is cast to true" )
	@Test
	public void testCacheableString() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( "cacheable", "yes" ) );
		verify( query ).setCacheable( true );
	}

	/**
	 * Test: cacheName sets the region and implies cacheable.
	 */
	@DisplayName( "cacheName sets the region and implies cacheable" )
	@Test
	public void testCacheName() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( "cacheName", "people" ) );
		verify( query ).setCacheable( true );
		verify( query ).setCacheRegion( "people" );
	}

	/**
	 * Test: cacheRegion is an alias of cacheName.
	 */
	@DisplayName( "cacheRegion is an alias of cacheName" )
	@Test
	public void testCacheRegionAlias() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( "cacheRegion", "people" ) );
		verify( query ).setCacheable( true );
		verify( query ).setCacheRegion( "people" );
	}

	/**
	 * Test: an explicit cacheable false wins over cacheName.
	 */
	@DisplayName( "An explicit cacheable: false wins over cacheName" )
	@Test
	public void testCacheableFalseWins() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( "cacheName", "people", "cacheable", false ) );
		verify( query ).setCacheable( false );
		verify( query, never() ).setCacheable( true );
		verify( query ).setCacheRegion( "people" );
	}

	/**
	 * Test: a blank cacheName is ignored.
	 */
	@DisplayName( "A blank cacheName is ignored" )
	@Test
	public void testBlankCacheName() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( "cacheName", " " ) );
		verify( query, never() ).setCacheable( anyBoolean() );
		verify( query, never() ).setCacheRegion( anyString() );
	}

	/**
	 * Test: a positive timeout is applied, in seconds.
	 */
	@DisplayName( "A positive timeout is applied" )
	@Test
	public void testTimeout() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( Key.timeout, 5 ) );
		verify( query ).setTimeout( 5 );
	}

	/**
	 * Test: a timeout given as a string is cast.
	 */
	@DisplayName( "A string timeout is cast" )
	@Test
	public void testTimeoutString() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( Key.timeout, "7" ) );
		verify( query ).setTimeout( 7 );
	}

	/**
	 * Test: a timeout of 0 means no timeout.
	 */
	@DisplayName( "A timeout of 0 means no timeout" )
	@Test
	public void testZeroTimeout() {
		HQLQuery.applyCacheAndTimeout( query, Struct.of( Key.timeout, 0 ) );
		verify( query, never() ).setTimeout( anyInt() );
	}
}
