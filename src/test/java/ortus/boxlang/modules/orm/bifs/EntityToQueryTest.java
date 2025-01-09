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

import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Query;
import tools.BaseORMTest;

public class EntityToQueryTest extends BaseORMTest {

	@DisplayName( "It can convert one entity into a single-row query" )
	@Test
	public void testEntityToQuerySingleEntity() {
		// @formatter:off
		instance.executeSource( """
			result = entityToQuery(
				entityLoad( 'Vehicle', '1HGCM82633A123456' )
			);
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Query.class );
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );

		IStruct row = variables.getAsQuery( result ).getRowAsStruct( 0 );

		// key properties should be present
		assertThat( row.containsKey( "vin" ) ).isTrue();

		// regular properties should be present
		assertThat( row.containsKey( "make" ) ).isTrue();
		assertThat( row.containsKey( "model" ) ).isTrue();
		assertThat( row.containsKey( "features" ) ).isTrue();

		// associations should be present, but NOT populated
		assertThat( row.containsKey( "manufacturer" ) ).isTrue();

		assertThat( row.get( "vin" ) ).isEqualTo( "1HGCM82633A123456" );
		assertThat( row.get( "make" ) ).isEqualTo( "Honda" );
		assertThat( row.get( "model" ) ).isEqualTo( "Accord" );
		assertThat( row.get( "features" ) ).isNull();
		assertThat( row.get( "manufacturer" ) ).isNull();
	}

}
