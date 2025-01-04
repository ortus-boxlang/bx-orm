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

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class ORMFlushAllTest extends BaseORMTest {

	@DisplayName( "It can flush ALL sessions" )
	@Test
	public void testORMFlushAll() {
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "Manufacturer", { name : "Dodge", address : "101 Dodge Circle" } ) );
			entitySave( entityNew( "AlternateDS", { id: createUUID(), name : "test" } ) );
			
			defaultDS1 = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			alternateDS1 = queryExecute( "SELECT * FROM alternate_ds WHERE name='test'", {}, { datasource : "dsn2" } );
			
			ORMFlushAll();

			defaultDS2 = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			alternateDS2 = queryExecute( "SELECT * FROM alternate_ds WHERE name='test'", {}, { datasource : "dsn2" } );
			""",
			context
		);
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "defaultDS1" ) ).size() )
		    .isEqualTo( 0 );
		assertThat( variables.getAsQuery( Key.of( "alternateDS1" ) ).size() )
		    .isEqualTo( 0 );
		assertThat( variables.getAsQuery( Key.of( "defaultDS2" ) ).size() )
		    .isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "alternateDS2" ) ).size() )
		    .isEqualTo( 1 );
	}

}
