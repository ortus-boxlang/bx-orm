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
import static org.junit.Assert.assertTrue;

import org.hibernate.Session;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class ORMClearSessionTest extends BaseORMTest {

	@Disabled( "Broken in suite; passes solo. Need to implement transaction/session management in the tests as well as the actual BIF/IBoxContext implementations." )
	@DisplayName( "It can clear the session for the default datasource" )
	@Test
	public void testSessionClear() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		;

		instance.executeSource(
		    """
		        result = entityNew( "Manufacturer", { name : "Bugatti Automobiles", address : "101 Bugatti Way" } );
		        entitySave( result );
		    """,
		    context
		);
		assertTrue( session.contains( variables.get( result ) ) );

		instance.executeSource( "ormClearSession();", context );

		assertFalse( session.contains( variables.get( result ) ) );
	}

	@Disabled( "Test fails due to a mismatch between the alternate datasource and the default datasource used in the entity save. We need to update our test entities to use alternate datasources, then also update the BIF methods to use the correct session for the entity datasource - not the default datasource" )
	@DisplayName( "It can clear the session on a named (alternate) datasource" )
	@Test
	public void testSessionClearOnNamedDatasource() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession( Key.of( "dsn2" ) );

		instance.executeSource(
		    """
		    	result = entityNew( "Manufacturer", { name : "Bugatti Automobiles", address : "101 Bugatti Way" } );
		        entitySave( result );
		    """,
		    context
		);
		assertTrue( session.contains( variables.get( result ) ) );

		instance.executeSource( "ormClearSession( 'dsn2' );", context );

		assertFalse( session.contains( variables.get( result ) ) );
	}

}
