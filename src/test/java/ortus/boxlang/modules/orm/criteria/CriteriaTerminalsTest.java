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

/**
 * Live tests for {@code entityCriteria()} terminal methods: count, exists, get, first, the *OrFail forms, paginate,
 * simplePaginate, pluck, aggregates, each and chunk, and the rule that terminals never change the builder.
 */
public class CriteriaTerminalsTest extends CriteriaTestSupport {

	/**
	 * Test: count and count( property ).
	 */
	@DisplayName( "count() counts rows; count( property ) counts distinct values" )
	@Test
	public void testCount() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).count();" ) ).isEqualTo( 5 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).count( 'make' );" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: exists.
	 */
	@DisplayName( "exists() tells whether any row matches" )
	@Test
	public void testExists() {
		assertThat( run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).exists();" ) ).isEqualTo( true );
		assertThat( run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Tesla' ).exists();" ) ).isEqualTo( false );
	}

	/**
	 * Test: get returns the single match or null.
	 */
	@DisplayName( "get() returns the single match, or null" )
	@Test
	public void testGet() {
		assertThat( run( "result = entityCriteria( 'Vehicle' ).isEq( 'model', 'Civic' ).get().getVin();" ) ).isEqualTo( "2HGCM82633A654321" );
		assertThat( run( "result = isNull( entityCriteria( 'Vehicle' ).isEq( 'model', 'Model S' ).get() );" ) ).isEqualTo( true );
	}

	/**
	 * Test: get with several matches.
	 */
	@DisplayName( "get() with several matches is an orm.query.nonUnique error; get( true ) takes the first" )
	@Test
	public void testGetNonUnique() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).get();" ) ) ).isEqualTo( "orm.query.nonUnique" );
		assertThat( run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).order( 'model' ).get( true ).getModel();" ) ).isEqualTo( "Accord" );
		assertThat( run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).order( 'model' ).get( uniqueFirst = true ).getModel();" ) )
		    .isEqualTo( "Accord" );
	}

	/**
	 * Test: getOrFail.
	 */
	@DisplayName( "getOrFail() is an orm.notFound error when nothing matches" )
	@Test
	public void testGetOrFail() {
		assertThat( run( "result = entityCriteria( 'Vehicle' ).isEq( 'model', 'Civic' ).getOrFail().getModel();" ) ).isEqualTo( "Civic" );
		IStruct err = error( "entityCriteria( 'Vehicle' ).isEq( 'model', 'Model S' ).getOrFail();" );
		assertThat( type( err ) ).isEqualTo( "orm.notFound" );
		assertThat( message( err ) ).contains( "No [Vehicle] matched" );
	}

	/**
	 * Test: first and firstOrFail.
	 */
	@DisplayName( "first() returns the first row in order; firstOrFail() fails when there is none" )
	@Test
	public void testFirst() {
		assertThat( run( "result = entityCriteria( 'Vehicle' ).order( 'model' ).first().getModel();" ) ).isEqualTo( "Accord" );
		assertThat( run( "result = isNull( entityCriteria( 'Vehicle' ).isEq( 'make', 'Tesla' ).first() );" ) ).isEqualTo( true );
		assertThat( type( error( "entityCriteria( 'Vehicle' ).isEq( 'make', 'Tesla' ).firstOrFail();" ) ) ).isEqualTo( "orm.notFound" );
		assertThat( run( "result = entityCriteria( 'Vehicle' ).order( 'model', 'desc' ).firstOrFail().getModel();" ) ).isEqualTo( "Studious" );
	}

	/**
	 * Test: paginate.
	 */
	@DisplayName( "paginate() returns the page and its numbers" )
	@Test
	public void testPaginate() {
		IStruct	page	= ( IStruct ) run( "result = entityCriteria( 'Vehicle' ).order( 'vin' ).paginate( 2, 2 );" );
		IStruct	paging	= page.getAsStruct( Key.of( "pagination" ) );
		assertThat( ( ( Array ) page.get( Key.of( "results" ) ) ).size() ).isEqualTo( 2 );
		assertThat( paging.get( Key.of( "page" ) ).toString() ).isEqualTo( "2" );
		assertThat( paging.get( Key.of( "maxRows" ) ).toString() ).isEqualTo( "2" );
		assertThat( paging.get( Key.of( "totalRecords" ) ).toString() ).isEqualTo( "5" );
		assertThat( paging.get( Key.of( "totalPages" ) ).toString() ).isEqualTo( "3" );
		IStruct last = ( IStruct ) run( "result = entityCriteria( 'Vehicle' ).order( 'vin' ).paginate( page = 3, maxRows = 2 );" );
		assertThat( ( ( Array ) last.get( Key.of( "results" ) ) ).size() ).isEqualTo( 1 );
	}

	/**
	 * Test: bad page numbers.
	 */
	@DisplayName( "paginate() with a page below 1 is an orm.argument error" )
	@Test
	public void testPaginateBadPage() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).paginate( 0, 10 );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: simplePaginate.
	 */
	@DisplayName( "simplePaginate() tells whether more rows follow without counting" )
	@Test
	public void testSimplePaginate() {
		IStruct first = ( IStruct ) run( "result = entityCriteria( 'Vehicle' ).order( 'vin' ).simplePaginate( 1, 3 );" );
		assertThat( ( ( Array ) first.get( Key.of( "results" ) ) ).size() ).isEqualTo( 3 );
		assertThat( first.getAsStruct( Key.of( "pagination" ) ).get( Key.of( "hasMore" ) ) ).isEqualTo( true );
		IStruct second = ( IStruct ) run( "result = entityCriteria( 'Vehicle' ).order( 'vin' ).simplePaginate( 2, 3 );" );
		assertThat( ( ( Array ) second.get( Key.of( "results" ) ) ).size() ).isEqualTo( 2 );
		assertThat( second.getAsStruct( Key.of( "pagination" ) ).get( Key.of( "hasMore" ) ) ).isEqualTo( false );
	}

	/**
	 * Test: pluck.
	 */
	@DisplayName( "pluck() returns one property's values in order" )
	@Test
	public void testPluck() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).order( 'model', 'desc' ).pluck( 'model' );" ) )
		    .containsExactly( "Ridgeline", "Civic", "Accord" ).inOrder();
		assertThat( list( "result = entityCriteria( 'Vehicle' ).isEq( 'model', 'Fusion' ).pluck( 'manufacturer.name' );" ) )
		    .containsExactly( "Ford Motor Company" );
	}

	/**
	 * Test: aggregates.
	 */
	@DisplayName( "sum / avg / min / max aggregate a property" )
	@Test
	public void testAggregates() {
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).sum( 'id' );" ).toString() ).isEqualTo( "120" );
		assertThat( Double.parseDouble( run( "result = entityCriteria( 'Manufacturer' ).avg( 'id' );" ).toString() ) ).isEqualTo( 40.0 );
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).min( 'id' );" ).toString() ).isEqualTo( "1" );
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).isLt( 'id', 50 ).max( 'id' );" ).toString() ).isEqualTo( "42" );
		assertThat( run( "result = isNull( entityCriteria( 'Manufacturer' ).isGt( 'id', 1000 ).max( 'id' ) );" ) ).isEqualTo( true );
	}

	/**
	 * Test: each.
	 */
	@DisplayName( "each() calls the closure once per row and returns the row count" )
	@Test
	public void testEach() {
		assertThat( run( """
		                 models = [];
		                 processed = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).each( ( v ) => models.append( v.getModel() ), 2 );
		                 result = processed & ':' & models.sort( 'text' ).toList();
		                 """ ) ).isEqualTo( "3:Accord,Civic,Ridgeline" );
	}

	/**
	 * Test: chunk batch sizes.
	 */
	@DisplayName( "chunk() passes rows in batches" )
	@Test
	public void testChunk() {
		assertThat( run( """
		                 sizes = [];
		                 processed = entityCriteria( 'Vehicle' ).chunk( 2, ( rows ) => sizes.append( rows.len() ) );
		                 result = processed & ':' & sizes.toList();
		                 """ ) ).isEqualTo( "5:2,2,1" );
	}

	/**
	 * Test: chunk respects maxResults and ordering (offset batches).
	 */
	@DisplayName( "chunk() honors maxResults and a custom order" )
	@Test
	public void testChunkMaxAndOrder() {
		assertThat( run( """
		                 seen = [];
		                 entityCriteria( 'Vehicle' ).order( 'model' ).maxResults( 3 ).chunk( 2, ( rows ) => rows.each( ( v ) => seen.append( v.getModel() ) ) );
		                 result = seen.toList();
		                 """ ) ).isEqualTo( "Accord,Civic,Fusion" );
	}

	/**
	 * Test: chunk saves changes made in the callback and deleting rows does not skip any.
	 */
	@DisplayName( "chunk() flushes changes per batch and never skips rows when rows are deleted" )
	@Test
	public void testChunkDeletes() {
		assertThat( run( """
		                 transaction {
		                     try {
		                         seen = 0;
		                         entityCriteria( 'Vehicle' ).chunk( 2, ( rows ) => {
		                             seen += rows.len();
		                             rows.each( ( v ) => entityDelete( v ) );
		                         } );
		                         result = seen & ':' & queryExecute( 'select count(*) as c from vehicles' ).c;
		                     } finally {
		                         transactionRollback();
		                     }
		                 }
		                 """ ) ).isEqualTo( "5:0" );
	}

	/**
	 * Test: each with a non-closure.
	 */
	@DisplayName( "each() with a non-closure is an orm.argument error" )
	@Test
	public void testEachNotClosure() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).each( 'nope' );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: terminals never change the builder.
	 */
	@DisplayName( "Terminal methods never change the builder" )
	@Test
	public void testTerminalsDoNotMutate() {
		assertThat( run( """
		                 c = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' );
		                 a = c.count();
		                 b = c.list( max = 1 ).len();
		                 p = c.paginate( 1, 2 ).results.len();
		                 d = c.list().len();
		                 result = a & ',' & b & ',' & p & ',' & d;
		                 """ ) ).isEqualTo( "3,1,2,3" );
	}

	/**
	 * Test: the builder can be reused after a terminal.
	 */
	@DisplayName( "A builder can keep building after a terminal" )
	@Test
	public void testReuseAfterTerminal() {
		assertThat( run( """
		                 c = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' );
		                 a = c.count();
		                 b = c.isEq( 'model', 'Civic' ).count();
		                 result = a & ',' & b;
		                 """ ) ).isEqualTo( "3,1" );
	}

	/**
	 * Test: copy branches a builder.
	 */
	@DisplayName( "copy() branches a builder" )
	@Test
	public void testCopy() {
		assertThat( run( """
		                 base = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' );
		                 civic = base.copy().isEq( 'model', 'Civic' );
		                 withJoin = base.copy().like( 'manufacturer.name', 'Honda%' ).order( 'model' );
		                 result = base.count() & ',' & civic.count() & ',' & withJoin.count() & ',' & base.count();
		                 """ ) ).isEqualTo( "3,1,3,3" );
	}
}
