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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.util.ListUtil;
import tools.BaseORMTest;

public class EntityNameListTest extends BaseORMTest {

	@DisplayName( "It returns a list of entity names" )
	@Test
	public void testEntityNameList() {
		// @formatter:off
		instance.executeSource( """
				result = entityNameList();
		""", context );
		// @formatter:on
		assertThat( ListUtil.asList( variables.getAsString( result ), "," ) ).containsAtLeast( "AlternateDS", "Feature", "Manufacturer", "Vehicle" );
	}

	@DisplayName( "It returns a list of entity names with a custom delimiter" )
	@Test
	public void testEntityNameListWithDelimiter() {
		// @formatter:off
		instance.executeSource( """
				result = entityNameList( '|' );
		""", context );
		// @formatter:on
		assertThat( ListUtil.asList( variables.getAsString( result ), "|" ) ).containsAtLeast( "AlternateDS", "Feature", "Manufacturer", "Vehicle" );
	}

	@DisplayName( "It can get entities for a custom datasource name" )
	@Test
	public void testEntityNameListWithCustomDatasource() {
		// @formatter:off
		instance.executeSource( """
				result = entityNameList( datasource = 'dsn2' );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( "AlternateDS" );
	}
}
