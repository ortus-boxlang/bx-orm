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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.DatabaseException;
import tools.BaseORMTest;

// @Disabled( "Tofix" )
public class ORMGetSessionTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testDefaultDatasource() {
		// @formatter:off
		instance.executeSource( """
			result = ormGetSession();
			isHibernateSession = isInstanceOf( result, "org.hibernate.Session" );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.getAsBoolean( Key.of( "isHibernateSession" ) ) ).isTrue();
	}

	@DisplayName( "It throws if the named datasource does not exist or is not configured for ORM" )
	@Test
	public void testBadDSN() {
		assertThrows(
		    DatabaseException.class,
		    () -> instance.executeSource( "result = ormGetSession( 'nonexistentDSN' ) ", context )
		);
	}

	@DisplayName( "It can get the ORM session from a named datasource" )
	@Test
	public void testNamedDatasource() {
		// @formatter:off
		instance.executeSource( """
			default = ormGetSession();
			named = ormGetSession( "dsn2" );
			isHibernateSession = isInstanceOf( named, "org.hibernate.Session" );
			isSameSession = default == named;
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "default" ) ) ).isNotNull();
		assertThat( variables.get( Key.of( "named" ) ) ).isNotNull();
		assertThat( variables.getAsBoolean( Key.of( "isHibernateSession" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "isSameSession" ) ) ).isFalse();
	}

	@DisplayName( "It can use queryExecute after opening ORM session" )
	@Test
	public void testSharedTransaction() {
		instance.executeSource(
		    """
		    transaction{
		    	ormGetSession();
		    	dev1 = entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } );
		    	entitySave( dev1 );
		    }
		    result = queryExecute( "SELECT COUNT(*) FROM vehicles" );
		    """, context );

		assertNotNull( variables.get( result ) );
		assertEquals( 1, variables.getAsQuery( result ).size() );
	}

}