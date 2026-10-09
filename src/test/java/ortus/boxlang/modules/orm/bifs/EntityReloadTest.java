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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class EntityReloadTest extends BaseORMTest {

	@DisplayName( "It can reload entities using a string variable name, for compat" )
	@Test
	public void testEntityRefreshByVariableName() {
		// @formatter:off
		instance.executeSource(
			"""
				myEntity = entityLoadByPK( "Manufacturer", 1 );
				originalAddress = myEntity.getAddress();
				
				queryExecute( "UPDATE manufacturers SET address='101 Ford Circle, Detroit MI' WHERE id=1" );
				reloadedAddress = entityReload( "myEntity" ).getAddress();
				queryExecute( "UPDATE manufacturers SET address='#originalAddress#' WHERE id=1" );
			""",
			context
		);
		// @formatter:on
		assertEquals( "202 Ford Way, Dearborn MI", variables.get( Key.of( "originalAddress" ) ) );
		assertEquals( "101 Ford Circle, Detroit MI", variables.get( Key.of( "reloadedAddress" ) ) );
	}

	@DisplayName( "It can reload entities" )
	@Test
	public void testEntityRefreshFromDB() {
		// @formatter:off
		instance.executeSource(
			"""
				manufacturer = entityLoadByPK( "Manufacturer", 1 );
				originalAddress = manufacturer.getAddress();
				
				queryExecute( "UPDATE manufacturers SET address='101 Ford Circle, Detroit MI' WHERE id=1" );
				reloadedAddress = entityReload( manufacturer ).getAddress();
				queryExecute( "UPDATE manufacturers SET address='#originalAddress#' WHERE id=1" );
			""",
			context
		);
		// @formatter:on
		assertEquals( "202 Ford Way, Dearborn MI", variables.get( Key.of( "originalAddress" ) ) );
		assertEquals( "101 Ford Circle, Detroit MI", variables.get( Key.of( "reloadedAddress" ) ) );
	}

	/**
	 * Test: It throws a clear orm.argument error if the argument names no variable.
	 */
	@DisplayName( "It throws a clear orm.argument error if the argument names no variable" )
	@Test
	public void testBadEntityName() {
		// Compare the BoxLang type: the module's ORMException class is loaded by the module class loader, not the test's.
		ortus.boxlang.runtime.types.exceptions.BoxLangException e = assertThrows( ortus.boxlang.runtime.types.exceptions.BoxLangException.class,
		    () -> instance.executeSource( "entityReload( \"Fooey\" );", context ) );
		assertEquals( "orm.argument", e.getType() );
		assertEquals( true, e.getMessage().contains( "[Fooey]" ) );
	}

}
