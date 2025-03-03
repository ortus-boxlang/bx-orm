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

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class ORMCloseSessionTest extends BaseORMTest {

	@DisplayName( "It can close the session for the default datasource" )
	@Test
	public void testSessionClose() {
		instance.executeSource(
		    """
		    	mySession = ormGetSession();
		    	result = mySession.isOpen();
		    	ormCloseSession();
		    	result2 = mySession.isOpen();
		    """,
		    context
		);

		assertTrue( variables.getAsBoolean( result ) );
		assertFalse( variables.getAsBoolean( Key.of( "result2" ) ) );
	}

	@DisplayName( "It can close the session on a named (alternate) datasource" )
	@Test
	public void testSessionCloseOnNamedDatasource() {
		instance.executeSource(
		    """
		    	mySession = ormGetSession( "dsn2" );
		    	result = mySession.isOpen();
		    	ormCloseSession( "dsn2" );
		    	result2 = mySession.isOpen();
		    """,
		    context
		);

		assertTrue( variables.getAsBoolean( result ) );
		assertFalse( variables.getAsBoolean( Key.of( "result2" ) ) );
	}

	@DisplayName( "It removes the closed session so other code can do its thing." )
	@Test
	public void testSessionClearAfterClose() {
		instance.executeSource(
		    """
		    ormCloseSession();
		    ormClearSession();
		    """,
		    context
		);
	}
}
