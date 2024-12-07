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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.KeyNotFoundException;
import tools.BaseORMTest;

public class EntityReloadTest extends BaseORMTest {

	@Disabled( "Context and variable names are colliding, which only allows the first test to pass. All tests run fine individually." )
	@DisplayName( "It can reload entities using a string variable name, for compat" )
	@Test
	public void testEntityRefreshByVariableName() {
		// @formatter:off
		instance.executeSource(
			"""
				myEntity = entityLoadByPK( "Manufacturer", 1 );
				result = myEntity.getAddress();
				
				queryExecute( "UPDATE Manufacturers SET address='101 Ford Circle, Detroit MI' WHERE id=1" );
				reloadedAddress = entityReload( "myEntity" ).getAddress();
			""",
			context
		);
		// @formatter:on
		assertEquals( "202 Ford Way, Dearborn MI", variables.get( result ) );
		assertEquals( "101 Ford Circle, Detroit MI", variables.get( Key.of( "reloadedAddress" ) ) );
	}

	@DisplayName( "It can reload entities" )
	@Test
	public void testEntityRefreshFromDB() {
		// @formatter:off
		instance.executeSource(
			"""
				manufacturer = entityLoadByPK( "Manufacturer", 1 );
				result = manufacturer.getAddress();
				
				queryExecute( "UPDATE Manufacturers SET address='101 Ford Circle, Detroit MI' WHERE id=1" );
				reloadedAddress = entityReload( manufacturer ).getAddress();
			""",
			context
		);
		// @formatter:on
		assertEquals( "202 Ford Way, Dearborn MI", variables.get( result ) );
		assertEquals( "101 Ford Circle, Detroit MI", variables.get( Key.of( "reloadedAddress" ) ) );
	}

	@DisplayName( "It throws if the argument is not a valid entity" )
	@Test
	public void testBadEntityName() {
		assertThrows( KeyNotFoundException.class, () -> {
			instance.executeSource(
			    """
			    	entityReload( "Fooey" );
			    """,
			    context
			);
		} );
	}

}
