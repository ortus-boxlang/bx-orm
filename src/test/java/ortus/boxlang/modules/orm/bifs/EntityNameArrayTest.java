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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityNameArrayTest extends BaseORMTest {

	@DisplayName( "It returns an array of entity names for ALL datasources" )
	@Test
	public void testEntityNameArray() {
		// @formatter:off
		instance.executeSource( """
				result = entityNameArray();
		""", context );
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( result ) );
		assertThat( variables.getAsArray( result ) ).containsAtLeast( "AlternateDS", "Feature", "Manufacturer", "Vehicle" );
	}

	@DisplayName( "It can get entities for a custom datasource name" )
	@Test
	public void testEntityNameArrayWithCustomDatasource() {
		// @formatter:off
		instance.executeSource( """
			result = entityNameArray( datasource = 'dsn2' )
		""", context );
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( result ) );
		assertThat( variables.getAsArray( result ) ).isEqualTo( Array.of( "AlternateDS" ) );
	}
}
