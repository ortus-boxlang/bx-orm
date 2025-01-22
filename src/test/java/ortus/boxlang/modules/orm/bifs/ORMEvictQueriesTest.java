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
package ortus.boxlang.modules.orm.bifs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class ORMEvictQueriesTest extends BaseORMTest {

	@DisplayName( "It can evict queries from the default cache" )
	@Test
	public void testORMEvictQueries() {
		// @formatter:off
		instance.executeSource(
			"""
			// isPresentBeforeEvict = ...
			ORMEvictQueries();
			// isPresentAfterEvict = ...
			""",
			context
		);
		// @formatter:on
		// assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		// assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}

	@DisplayName( "It can evict queries on the named cache" )
	@Test
	public void testORMEvictQueriesWithCacheName() {
		// @formatter:off
		instance.executeSource(
			"""
			// isPresentBeforeEvict = ...
			ORMEvictQueries( "queries" );
			// isPresentAfterEvict = ...
			""",
			context
		);
		// @formatter:on
		// assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		// assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}

	@DisplayName( "It can evict queries on the named cache and datasource" )
	@Test
	public void testORMEvictQueriesWithCacheNameAndDatasourceName() {
		// @formatter:off
		instance.executeSource(
			"""
			// isPresentBeforeEvict = ...
			ORMEvictQueries( "queries", "dsn2" );
			// isPresentAfterEvict = ...
			""",
			context
		);
		// @formatter:on
		// assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		// assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}
}
