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

import org.hibernate.Session;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

@Disabled( "Tofix" )
public class ORMCloseSessionTest extends BaseORMTest {

	@DisplayName( "It can close the session for the default datasource" )
	@Test
	public void testSessionClose() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		;
		// @formatter:off
		instance.executeSource(
			"""
				ormCloseSession();
			""",
			context
		);
		// @formatter:on

		assertFalse( session.isOpen() );
	}

	@DisplayName( "It can close the session on a named (alternate) datasource" )
	@Test
	public void testSessionCloseOnNamedDatasource() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession( Key.of( "dsn2" ) );
		// @formatter:off
		instance.executeSource(
			"""
				ormCloseSession( "dsn2" );
			""",
			context
		);
		// @formatter:on

		assertFalse( session.isOpen() );
	}

}
