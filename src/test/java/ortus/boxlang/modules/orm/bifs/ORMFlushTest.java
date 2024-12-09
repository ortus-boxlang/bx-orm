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

public class ORMFlushTest extends BaseORMTest {

	@DisplayName( "It can flush the session" )
	@Test
	public void testORMFlush() {
		// @formatter:off
		instance.executeSource(
			"""
			manufacturer = entityNew( "Manufacturer" );
			manufacturer.setAddress( "101 Dodge Circle" ).setName( "Dodge" );
			entitySave( manufacturer );
			result = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			ORMFlush();
			result2 = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			""",
			context
		);
		// @formatter:on

		assertThat( variables.getAsQuery( result ).size() )
		    .isEqualTo( 0 );
		assertThat( variables.getAsQuery( Key.of( "result2" ) ).size() )
		    .isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "result2" ) ).getRowAsStruct( 0 ).get( "address" ) )
		    .isEqualTo( "101 Dodge Circle" );
	}

}
