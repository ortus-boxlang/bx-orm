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
package ortus.boxlang.modules.orm.criteria;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Query;

/**
 * Live tests for {@code entityCriteria()} result shapes: projections, {@code withProjections()}, struct/query/stream
 * results, distinct rows, ordering and paging.
 */
public class CriteriaShapeTest extends CriteriaTestSupport {

	/**
	 * Test: one property projection returns plain values.
	 */
	@DisplayName( "A single property projection returns plain values" )
	@Test
	public void testSingleProjection() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).project( ( p ) => p.property( 'model' ) ).order( 'model' ).list();" ) )
		    .containsExactly( "Accord", "Civic", "Ridgeline" ).inOrder();
	}

	/**
	 * Test: several projections return arrays.
	 */
	@DisplayName( "Several projections return one array per row" )
	@Test
	public void testMultipleProjections() {
		Array rows = ( Array ) run(
		    "result = entityCriteria( 'Vehicle' ).idEq( '9ABAZ85656A776723' ).project( ( p ) => p.property( 'make' ).property( 'model' ) ).list();" );
		assertThat( ( ( Array ) rows.get( 0 ) ).toList() ).containsExactly( "Ford", "Fusion" ).inOrder();
	}

	/**
	 * Test: group and count as structs.
	 */
	@DisplayName( "group + count with asStruct returns one struct per group" )
	@Test
	public void testGroupCount() {
		Array	rows	= ( Array ) run(
		    "result = entityCriteria( 'Vehicle' ).project( ( p ) => p.group( 'make' ).count( 'vin', 'total' ) ).asStruct().order( 'make' ).list();" );
		IStruct	honda	= ( IStruct ) rows.get( 1 );
		assertThat( honda.get( Key.of( "make" ) ) ).isEqualTo( "Honda" );
		assertThat( honda.get( Key.of( "total" ) ).toString() ).isEqualTo( "3" );
		assertThat( rows.size() ).isEqualTo( 3 );
	}

	/**
	 * Test: aggregate projections.
	 */
	@DisplayName( "sum / avg / min / max / countDistinct / rowCount projections" )
	@Test
	public void testAggregateProjections() {
		IStruct row = ( IStruct ) run( """
		                               result = entityCriteria( 'Manufacturer' ).project( ( p ) => p
		                                   .sum( 'id', 'total' ).avg( 'id', 'average' ).min( 'id', 'lowest' ).max( 'id', 'highest' )
		                                   .countDistinct( 'name', 'names' ).rowCount( 'rows' )
		                               ).asStruct().get();
		                               """ );
		assertThat( row.get( Key.of( "total" ) ).toString() ).isEqualTo( "120" );
		assertThat( Double.parseDouble( row.get( Key.of( "average" ) ).toString() ) ).isEqualTo( 40.0 );
		assertThat( row.get( Key.of( "lowest" ) ).toString() ).isEqualTo( "1" );
		assertThat( row.get( Key.of( "highest" ) ).toString() ).isEqualTo( "77" );
		assertThat( row.get( Key.of( "names" ) ).toString() ).isEqualTo( "3" );
		assertThat( row.get( Key.of( "rows" ) ).toString() ).isEqualTo( "3" );
	}

	/**
	 * Test: default aliases and de-duplication.
	 */
	@DisplayName( "Projection aliases default to the property name and never collide" )
	@Test
	public void testDefaultAliases() {
		IStruct row = ( IStruct ) run(
		    "result = entityCriteria( 'Manufacturer' ).idEq( 42 ).project( ( p ) => p.property( 'name' ).count( 'name' ).id() ).asStruct().get();" );
		assertThat( row.keySet().stream().map( Key::getName ).toList() ).containsExactly( "name", "countName", "id" );
	}

	/**
	 * Test: a projection on an association path.
	 */
	@DisplayName( "A projection can read an association path" )
	@Test
	public void testProjectionPath() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).isEq( 'model', 'Civic' ).project( ( p ) => p.property( 'manufacturer.name' ) ).list();" ) )
		    .containsExactly( "Honda Motor Co." );
	}

	/**
	 * Test: named projection arguments.
	 */
	@DisplayName( "Projection methods accept named arguments" )
	@Test
	public void testNamedProjection() {
		IStruct row = ( IStruct ) run(
		    "result = entityCriteria( 'Manufacturer' ).project( ( p ) => p.max( property = 'id', alias = 'top' ) ).asStruct().get();" );
		assertThat( row.get( Key.of( "top" ) ).toString() ).isEqualTo( "77" );
	}

	/**
	 * Test: an unknown projection method.
	 */
	@DisplayName( "An unknown projection method is an orm.argument error" )
	@Test
	public void testUnknownProjection() {
		IStruct err = error( "entityCriteria( 'Manufacturer' ).project( ( p ) => p.summ( 'id' ) );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "Did you mean [sum]?" );
	}

	/**
	 * Test: cborm withProjections with a struct.
	 */
	@DisplayName( "withProjections( struct ) builds cborm-style projections" )
	@Test
	public void testWithProjectionsStruct() {
		Array rows = ( Array ) run(
		    "result = entityCriteria( 'Vehicle' ).withProjections( { groupProperty : 'make', count : 'vin:total' } ).asStruct().order( 'make' ).list();" );
		assertThat( ( ( IStruct ) rows.get( 1 ) ).get( Key.of( "total" ) ).toString() ).isEqualTo( "3" );
	}

	/**
	 * Test: cborm withProjections with named arguments.
	 */
	@DisplayName( "withProjections( named arguments ) builds cborm-style projections" )
	@Test
	public void testWithProjectionsNamed() {
		IStruct row = ( IStruct ) run( "result = entityCriteria( 'Manufacturer' ).withProjections( max = 'id:top', rowCount = true ).asStruct().get();" );
		assertThat( row.get( Key.of( "top" ) ).toString() ).isEqualTo( "77" );
		assertThat( row.get( Key.of( "count" ) ).toString() ).isEqualTo( "3" );
	}

	/**
	 * Test: withProjections distinct.
	 */
	@DisplayName( "withProjections( distinct = 'make' ) returns distinct values" )
	@Test
	public void testWithProjectionsDistinct() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).withProjections( distinct = 'make' ).order( 'make' ).list();" ) )
		    .containsExactly( "Ford", "Honda", "Studebaker" ).inOrder();
	}

	/**
	 * Test: an unknown withProjections key.
	 */
	@DisplayName( "An unknown withProjections key is an orm.argument error" )
	@Test
	public void testWithProjectionsUnknown() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).withProjections( { summ : 'x' } );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: asDistinct with a property projection.
	 */
	@DisplayName( "asDistinct removes duplicate rows" )
	@Test
	public void testAsDistinct() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).project( ( p ) => p.property( 'make' ) ).asDistinct().list().len();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: asStruct without projections returns the plain columns.
	 */
	@DisplayName( "asStruct without projections returns the id and plain values" )
	@Test
	public void testAsStructEntity() {
		IStruct row = ( IStruct ) run( "result = entityCriteria( 'Vehicle' ).idEq( '0SB123' ).asStruct().get();" );
		assertThat( row.keySet().stream().map( Key::getName ).toList() ).containsExactly( "vin", "make", "model" );
		assertThat( row.get( Key.of( "make" ) ) ).isEqualTo( "Studebaker" );
	}

	/**
	 * Test: asQuery.
	 */
	@DisplayName( "asQuery returns a query" )
	@Test
	public void testAsQuery() {
		Query q = ( Query ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).order( 'model' ).asQuery().list();" );
		assertThat( q.size() ).isEqualTo( 3 );
		assertThat( q.getColumnData( Key.of( "model" ) ) ).asList().containsExactly( "Accord", "Civic", "Ridgeline" ).inOrder();
	}

	/**
	 * Test: asQuery with projections uses the aliases as columns.
	 */
	@DisplayName( "asQuery with projections uses the aliases as columns" )
	@Test
	public void testAsQueryProjections() {
		Query q = ( Query ) run(
		    "result = entityCriteria( 'Vehicle' ).project( ( p ) => p.group( 'make' ).count( 'vin', 'n' ) ).order( 'make' ).asQuery().list();" );
		assertThat( q.getColumnNames().toList() ).containsExactly( "make", "n" );
		assertThat( q.size() ).isEqualTo( 3 );
	}

	/**
	 * Test: asStream.
	 */
	@DisplayName( "asStream returns a stream of entities" )
	@Test
	public void testAsStream() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).asStream().list().count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: order ascending and descending.
	 */
	@DisplayName( "order sorts ascending by default and descending on request" )
	@Test
	public void testOrder() {
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).order( 'id' ).list().map( ( m ) => m.getId() );" ) ).containsExactly( 1, 42, 77 )
		    .inOrder();
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).order( 'id', 'desc' ).list().map( ( m ) => m.getId() );" ) )
		    .containsExactly( 77, 42, 1 ).inOrder();
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).orderByDesc( 'id' ).list().map( ( m ) => m.getId() );" ) )
		    .containsExactly( 77, 42, 1 ).inOrder();
	}

	/**
	 * Test: several orderings in one string.
	 */
	@DisplayName( "orderBy accepts 'a desc, b asc'" )
	@Test
	public void testOrderList() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).orderBy( 'make desc, model asc' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Studious", "Accord", "Civic", "Ridgeline", "Fusion" ).inOrder();
	}

	/**
	 * Test: an unknown sort direction.
	 */
	@DisplayName( "An unknown sort direction is an orm.argument error" )
	@Test
	public void testBadDirection() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).order( 'make', 'sideways' );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: ignoreCase ordering wraps text in lower().
	 */
	@DisplayName( "order( p, dir, ignoreCase ) lowers text properties only" )
	@Test
	public void testOrderIgnoreCase() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).order( 'make', 'asc', true ).getHQL();" ) ).contains( "lower(bx_this.make)" );
		assertThat( ( String ) run( "result = entityCriteria( 'Manufacturer' ).order( 'id', 'asc', true ).getHQL();" ) ).doesNotContain( "lower(" );
	}

	/**
	 * Test: paging.
	 */
	@DisplayName( "firstResult / maxResults and their aliases page the rows" )
	@Test
	public void testPaging() {
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).order( 'id' ).firstResult( 1 ).maxResults( 1 ).list().map( ( m ) => m.getId() );" ) )
		    .containsExactly( 42 );
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).order( 'id' ).offset( 2 ).limit( 5 ).list().map( ( m ) => m.getId() );" ) )
		    .containsExactly( 77 );
	}

	/**
	 * Test: negative paging values.
	 */
	@DisplayName( "A negative maxResults is an orm.argument error" )
	@Test
	public void testNegativePaging() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).maxResults( -1 );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: cborm list( max, offset, timeout, sortOrder, ignoreCase, asQuery ).
	 */
	@DisplayName( "list( max, offset, timeout, sortOrder, ignoreCase, asQuery ) works like cborm" )
	@Test
	public void testCbormList() {
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).list( 2, 1, 0, 'id desc' ).map( ( m ) => m.getId() );" ) ).containsExactly( 42, 1 )
		    .inOrder();
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).list( sortOrder = 'id', asQuery = true );" ) ).isInstanceOf( Query.class );
	}
}
