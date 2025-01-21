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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class ORMExecuteQueryTest extends BaseORMTest {

	@DisplayName( "It can run an HQL query with just HQL" )
	@Test
	public void testHQLOnly() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle" );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 3 );
	}

	@DisplayName( "It can run an HQL query on another datasource" )
	@Test
	public void testHQLOnAlternateDatasource() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM AlternateDS", [], { datasource: "dsn2" } );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 2 );
	}

	@DisplayName( "It can run an HQL query with hql, unique, and options" )
	@Test
	public void testUniqueAndOptions() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle ORDER BY model ASC", true, { readOnly: true, offset: 1, maxResults: 5 } );
		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( IClassRunnable.class );
		IClassRunnable vehicle = ( IClassRunnable ) item;
		assertThat( vehicle.get( Key.of( "model" ) ) ).isEqualTo( "Civic" );
	}

	@Disabled( "unimplemented" )
	@DisplayName( "It can run an HQL query with HQL and params" )
	@Test
	public void testHQLAndParams() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle WHERE make=?1", ['make'] );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 3 );
	}

	@Disabled( "unimplemented" )
	@DisplayName( "It can run an HQL query with HQL, params, and unique boolean" )
	@Test
	public void testHQLParamsAndUnique() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle WHERE id=?1 OR make=?2", ['1HGCM82633A123456','Honda'], true );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 3 );
	}

	@Disabled( "unimplemented" )
	@DisplayName( "It supports positional params" )
	@Test
	public void testHQLPositionalParams() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle WHERE id=? OR make=?", ['1HGCM82633A123456','Honda'], true );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 3 );
	}

}
