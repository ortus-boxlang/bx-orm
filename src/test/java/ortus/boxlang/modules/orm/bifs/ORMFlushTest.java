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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
			transaction{
				manufacturer = entityNew( "Manufacturer" );
				manufacturer.setAddress( "101 Dodge Circle" ).setName( "Dodge" );
				entitySave( manufacturer );
				result = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			}
			result2 = queryExecute( "SELECT * FROM manufacturers WHERE name='Dodge'" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 0, variables.getAsQuery( result ).size() );
		assertEquals( 1, variables.getAsQuery( Key.of( "result2" ) ).size() );
		assertEquals( "101 Dodge Circle", variables.getAsQuery( Key.of( "result2" ) ).getRowAsStruct( 0 ).get( "address" ) );
	}

}
