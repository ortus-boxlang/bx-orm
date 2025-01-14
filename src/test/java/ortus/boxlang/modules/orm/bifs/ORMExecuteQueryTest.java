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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class ORMExecuteQueryTest extends BaseORMTest {

	@DisplayName( "It can run an HQL query" )
	@Test
	public void testTestBIF() {
		instance.executeSource( """
		                        result = ormExecuteQuery( "FROM Vehicle");
		                        """, context );
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 3 );

		// Object query = variables.get( result );
		// assertThat( query ).isInstanceOf( Query.class );
		// Query q = ( Query ) query;
		// assertThat( q.size() ).isEqualTo( 3 );
		// IStruct first = q.getRowAsStruct( 0 );
		// assertThat( first ).containsKey( Key.of( "make" ) );
		// assertThat( first.get( Key.of( "make" ) ) ).isEqualTo( "Honda" );
	}

}
