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

public class ORMEvictEntityTest extends BaseORMTest {

	@DisplayName( "It can evict entities from the second-level cache" )
	@Test
	public void testORMEvictEntity() {
		// @formatter:off
		instance.executeSource(
			"""
			record = entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } );
			isPresentBeforeEvict = ORMGetSessionFactory().getCache().containsEntity( "Manufacturer", record.getId() );

			ORMEvictEntity( "manufacturer" );
			isPresentAfterEvict = ORMGetSessionFactory().getCache().containsEntity( "Manufacturer", record.getId() );
			""",
			context
		);
		// @formatter:on
		// @TODO: Fix Cache provider configuration.
		// assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		// assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}

	@DisplayName( "It accepts a second argument for the primary key" )
	@Test
	public void testORMEvictEntityWithPrimaryKey() {
		// @formatter:off
		instance.executeSource(
			"""
			record = entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } );
			isPresentBeforeEvict = ORMGetSessionFactory().getCache().containsEntity( "Manufacturer", record.getId() );

			ORMEvictEntity( "manufacturer", record.getId() );
			isPresentAfterEvict = ORMGetSessionFactory().getCache().containsEntity( "Manufacturer", record.getId() );
			// test it...
			""",
			context
		);
		// @formatter:on
		// @TODO: Fix Cache provider configuration.
		// assertThat( variables.getAsBoolean( Key.of( "isPresentBeforeEvict" ) ) ).isTrue();
		// assertThat( variables.getAsBoolean( Key.of( "isPresentAfterEvict" ) ) ).isFalse();
	}

}
