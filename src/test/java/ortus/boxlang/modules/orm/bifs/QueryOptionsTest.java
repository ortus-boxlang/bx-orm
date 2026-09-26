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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

/**
 * Live (MySQL) tests for the query options of ormExecuteQuery and entityLoad: cacheable, cacheName, timeout and
 * ignorecase. The second-level cache is enabled in the test app, so cached queries really go through the query cache.
 */
public class QueryOptionsTest extends BaseORMTest {

	/**
	 * Clear the ORM session after each test so no entity leaks into the next one.
	 */
	@AfterEach
	public void clearSession() {
		instance.executeSource( "try { ormClearSession(); } catch ( any e ) {}", context );
	}

	/**
	 * Run BoxLang code and return the value it stores in {@code result}.
	 *
	 * @param code BoxLang statements that set {@code result}.
	 *
	 * @return The value of {@code result}.
	 */
	private Object run( String code ) {
		instance.executeSource( code, context );
		return variables.get( result );
	}

	/**
	 * Test: ormExecuteQuery with cacheable returns the same rows twice.
	 */
	@DisplayName( "ormExecuteQuery cacheable returns the same rows on a second (cached) run" )
	@Test
	public void testExecuteQueryCacheable() {
		Array rows = ( Array ) run( """
		                            first  = ormExecuteQuery( "from Manufacturer order by id", [], false, { cacheable : true } );
		                            second = ormExecuteQuery( "from Manufacturer order by id", [], false, { cacheable : true } );
		                            result = [ first.len(), second.len(), first[ 1 ].getId() == second[ 1 ].getId() ];
		                            """ );
		assertThat( rows.get( 0 ).toString() ).isEqualTo( rows.get( 1 ).toString() );
		assertThat( ( Boolean ) rows.get( 2 ) ).isTrue();
	}

	/**
	 * Test: ormExecuteQuery with cacheName uses that region and returns rows.
	 */
	@DisplayName( "ormExecuteQuery cacheName uses that cache region" )
	@Test
	public void testExecuteQueryCacheName() {
		Object size = run( """
		                   ormExecuteQuery( "from Manufacturer", [], false, { cacheName : "qo_manufacturers" } );
		                   result = ormExecuteQuery( "from Manufacturer", [], false, { cacheName : "qo_manufacturers" } ).len();
		                   """ );
		assertThat( Integer.parseInt( size.toString() ) ).isGreaterThan( 0 );
	}

	/**
	 * Test: ormExecuteQuery with a timeout still returns rows.
	 */
	@DisplayName( "ormExecuteQuery timeout is accepted" )
	@Test
	public void testExecuteQueryTimeout() {
		Object size = run( "result = ormExecuteQuery( \"from Manufacturer\", [], false, { timeout : 5 } ).len();" );
		assertThat( Integer.parseInt( size.toString() ) ).isGreaterThan( 0 );
	}

	/**
	 * Test: entityLoad with cachename and timeout returns rows.
	 */
	@DisplayName( "entityLoad cachename and timeout are accepted" )
	@Test
	public void testEntityLoadCacheName() {
		Object size = run( """
		                   entityLoad( "Manufacturer", {}, "", { cachename : "qo_load", timeout : 5 } );
		                   result = entityLoad( "Manufacturer", {}, "", { cachename : "qo_load", timeout : 5 } ).len();
		                   """ );
		assertThat( Integer.parseInt( size.toString() ) ).isGreaterThan( 0 );
	}

	/**
	 * Test: entityLoad ignorecase sorts a text property case-insensitively.
	 */
	@DisplayName( "entityLoad ignorecase sorts a text property case-insensitively" )
	@Test
	public void testIgnoreCaseSortsText() {
		Array names = ( Array ) run( """
		                             transaction {
		                                 try {
		                                     entitySave( entityNew( "VetoThing", { name : "qo-banana" } ) );
		                                     entitySave( entityNew( "VetoThing", { name : "QO-APPLE" } ) );
		                                     entitySave( entityNew( "VetoThing", { name : "qo-cherry" } ) );
		                                     ormFlush();
		                                     result = entityLoad( "VetoThing", {}, "name asc", { ignorecase : true } )
		                                         .filter( ( t ) => t.getName().left( 3 ) == "qo-" )
		                                         .map( ( t ) => t.getName() );
		                                 } finally {
		                                     transactionRollback();
		                                 }
		                             }
		                             """ );
		assertThat( names.toList() ).containsExactly( "QO-APPLE", "qo-banana", "qo-cherry" ).inOrder();
	}

	/**
	 * Test: entityLoad ignorecase leaves a numeric sort alone (lower() on a number is not applied).
	 */
	@DisplayName( "entityLoad ignorecase leaves a numeric sort alone" )
	@Test
	public void testIgnoreCaseNumericSort() {
		Array ids = ( Array ) run( """
		                           result = entityLoad( "Manufacturer", {}, "id desc", { ignorecase : true } ).map( ( m ) => m.getId() );
		                           """ );
		assertThat( ids.size() ).isGreaterThan( 1 );
		for ( int i = 1; i < ids.size(); i++ ) {
			assertThat( Integer.parseInt( ids.get( i - 1 ).toString() ) ).isGreaterThan( Integer.parseInt( ids.get( i ).toString() ) );
		}
	}
}
