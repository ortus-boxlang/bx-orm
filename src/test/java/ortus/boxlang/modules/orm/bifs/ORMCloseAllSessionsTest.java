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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class ORMCloseAllSessionsTest extends BaseORMTest {

	@DisplayName( "It can close all ORM sessions" )
	@Test
	public void testORMCloseAllSessions() {
		instance.executeSource(
		    """
		    mySession                       = ormGetSession();
		    alternateSession                = ormGetSession( "dsn2" );
		    defaultSessionOpenBeforeClose   = mySession.isOpen();
		    alternateSessionOpenBeforeClose = alternateSession.isOpen();

		    ORMCloseAllSessions();

		    defaultSessionOpenAfterClose   = mySession.isOpen();
		    alternateSessionOpenAfterClose = alternateSession.isOpen();
		    """,
		    context
		);

		assertTrue( variables.getAsBoolean( Key.of( "defaultSessionOpenBeforeClose" ) ) );
		assertTrue( variables.getAsBoolean( Key.of( "alternateSessionOpenBeforeClose" ) ) );
		assertFalse( variables.getAsBoolean( Key.of( "defaultSessionOpenAfterClose" ) ) );
		assertFalse( variables.getAsBoolean( Key.of( "alternateSessionOpenAfterClose" ) ) );
	}

}
