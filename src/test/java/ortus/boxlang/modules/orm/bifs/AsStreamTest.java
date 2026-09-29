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

import ortus.boxlang.modules.orm.criteria.CriteriaTestSupport;

/**
 * The {@code asStream} option of {@code ormExecuteQuery()} and {@code entityLoad()}: a Java stream read from the
 * database as it is consumed. Seed data: five vehicles, three of them Hondas.
 */
public class AsStreamTest extends CriteriaTestSupport {

	@DisplayName( "ormExecuteQuery asStream returns a Java stream of entities" )
	@Test
	public void testExecuteQueryStream() {
		assertThat( run( "result = ormExecuteQuery( 'from Vehicle', [], false, { asStream : true } ) instanceof 'java.util.stream.Stream';" ) )
		    .isEqualTo( true );
		assertThat( number( "result = ormExecuteQuery( 'from Vehicle', [], false, { asStream : true } ).count();" ) ).isEqualTo( 5 );
	}

	@DisplayName( "A streamed query takes BoxLang closures and keeps its parameters" )
	@Test
	public void testExecuteQueryStreamPipeline() {
		assertThat( list( """
		                  result = ormExecuteQuery( "from Vehicle where make = :make order by model", { make : "Honda" }, false, { asStream : true } )
		                      .map( ( v ) => v.getModel() )
		                      .toList();
		                  result = arrayNew( 1 ).append( result, true );
		                  """ ) ).containsExactly( "Accord", "Civic", "Ridgeline" ).inOrder();
		assertThat( number(
		    "result = ormExecuteQuery( 'from Vehicle', [], false, { asStream : true } ).filter( ( v ) => v.getMake() == 'Honda' ).count();" ) )
		    .isEqualTo( 3 );
	}

	@DisplayName( "asStream honors maxResults and offset" )
	@Test
	public void testExecuteQueryStreamPaging() {
		assertThat( number( "result = ormExecuteQuery( 'from Vehicle order by vin', [], false, { asStream : true, maxResults : 2, offset : 1 } ).count();" ) )
		    .isEqualTo( 2 );
	}

	@DisplayName( "asStream is rejected on unique and DML queries" )
	@Test
	public void testExecuteQueryStreamRejected() {
		assertThat( type( error( "ormExecuteQuery( 'from Vehicle', [], true, { asStream : true } );" ) ) ).isEqualTo( "orm.argument" );
		assertThat( type( error( "ormExecuteQuery( 'update Vehicle set model = model where 1 = 0', [], false, { asStream : true } );" ) ) )
		    .isEqualTo( "orm.argument" );
	}

	@DisplayName( "entityLoad asStream streams a filter, a sort order and all rows" )
	@Test
	public void testEntityLoadStream() {
		assertThat( number( "result = entityLoad( 'Vehicle', {}, { asStream : true } ).count();" ) ).isEqualTo( 5 );
		assertThat( number( "result = entityLoad( 'Vehicle', { make : 'Honda' }, { asStream : true } ).count();" ) ).isEqualTo( 3 );
		assertThat( list( """
		                  result = arrayNew( 1 ).append( entityLoad( 'Vehicle', { make : 'Honda' }, 'model desc', { asStream : true } )
		                      .map( ( v ) => v.getModel() ).toList(), true );
		                  """ ) ).containsExactly( "Ridgeline", "Civic", "Accord" ).inOrder();
	}

	@DisplayName( "entityLoad by id with asStream streams zero or one entity" )
	@Test
	public void testEntityLoadByIdStream() {
		assertThat( number( "result = entityLoad( 'Vehicle', '1HGCM82633A123456', { asStream : true } ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityLoad( 'Vehicle', 'NO-SUCH-VIN', { asStream : true } ).count();" ) ).isEqualTo( 0 );
	}

	@DisplayName( "entityLoad rejects asStream with unique" )
	@Test
	public void testEntityLoadStreamUnique() {
		assertThat( type( error( "entityLoad( 'Vehicle', { make : 'Honda' }, true, { asStream : true } );" ) ) ).isEqualTo( "orm.argument" );
	}
}
