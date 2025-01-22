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

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

// @TODO: Fix Cache provider configuration.
@Disabled
public class ORMEvictCollectionTest extends BaseORMTest {

	@DisplayName( "It can evict entity collections for all entities of type name" )
	@Test
	public void testORMEvictCollection() {
		// @formatter:off
		instance.executeSource(
			"""
			// isPresentBeforeEvict = ...
			
			ORMEvictCollection( "manufacturer", "vehicles" );
			// isPresentAfterEvict = ...
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}

	@DisplayName( "It can evict entity collections for a specific entity" )
	@Test
	public void testORMEvictCollectionWithPrimaryKey() {
		// @formatter:off
		instance.executeSource(
			"""
			// isPresentBeforeEvict = ...
			
			ORMEvictCollection( "manufacturer", "vehicles", record.getId() );
			// isPresentAfterEvict = ...
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}
}
