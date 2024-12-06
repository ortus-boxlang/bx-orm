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

import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.Session;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.DatabaseException;
import tools.BaseORMTest;

@Disabled( "Tofix" )
public class ORMGetSessionTest extends BaseORMTest {

	@DisplayName( "It can get the current ORM session" )
	@Test
	public void testDefaultDatasource() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		;
		assertNotNull( session );

		instance.executeSource( "result = ormGetSession() ", context );
		assertNotNull( variables.get( result ) );
		assertEquals( session, variables.get( result ) );
	}

	@DisplayName( "It throws if the named datasource does not exist or is not configured for ORM" )
	@Test
	public void testBadDSN() {
		Session session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		;
		assertNotNull( session );

		assertThrows(
		    DatabaseException.class,
		    () -> instance.executeSource( "result = ormGetSession( 'nonexistentDSN' ) ", context )
		);
	}

	@DisplayName( "It can get the ORM session from a named datasource" )
	@Test
	public void testNamedDatasource() {
		Session defaultORMSession = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		;
		assertNotNull( defaultORMSession );

		Session secondDSNSession = ORMRequestContext.getForContext( context.getRequestContext() ).getSession( Key.of( "dsn2" ) );
		assertNotNull( secondDSNSession );

		instance.executeSource( "result = ormGetSession( 'dsn2' ) ", context );

		assertNotNull( variables.get( result ) );
		assertNotEquals( defaultORMSession, variables.get( result ) );
		assertEquals( secondDSNSession, variables.get( result ) );
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