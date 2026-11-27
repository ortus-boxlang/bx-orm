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

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Query;

/**
 * A condition on a collection repeats the root entity once per matching element. Criteria list each root entity once
 * by matching its id in a subquery instead of {@code select distinct} over every column, which fails on columns a
 * database cannot compare (PostgreSQL {@code json}, SQL Server {@code text}, Oracle {@code CLOB}); see BLMODULES-319.
 * <p>
 * Seed data: Honda (id 42) has three vehicles, Ford (id 1) one, General Motors (id 77) none. So a condition on
 * {@code vehicles} repeats Honda three times. Vehicles also get two features here, Accord and Civic both having both,
 * so a condition on {@code features} repeats each of them twice.
 */
public class CriteriaCollectionJoinTest extends CriteriaTestSupport {

	/** Matches both Honda (3 vehicles) and Ford (1 vehicle): 4 join rows for 2 manufacturers. */
	private static final String	BOTH_MAKERS	= "entityCriteria( 'Manufacturer' ).like( 'vehicles.make', '%o%' )";

	/** Accord, Civic (both features) and Fusion (one feature): 5 join rows for 3 vehicles. */
	private static final String	FEATURED	= "entityCriteria( 'Vehicle' ).like( 'features.name', 'Spec319%' )";

	@BeforeEach
	public void addFeatures() {
		instance.executeSource(
		    """
		    queryExecute( "DELETE FROM vehicle_features WHERE FK_feature IN ( 31901, 31902 )" );
		    queryExecute( "DELETE FROM features WHERE id IN ( 31901, 31902 )" );
		    queryExecute( "INSERT INTO features ( id, name, description ) VALUES ( 31901, 'Spec319 Roof', 'r' ), ( 31902, 'Spec319 Seats', 's' )" );
		    queryExecute( "INSERT INTO vehicle_features ( FK_vehicle, FK_feature ) VALUES
		        ( '1HGCM82633A123456', 31901 ), ( '1HGCM82633A123456', 31902 ),
		        ( '2HGCM82633A654321', 31901 ), ( '2HGCM82633A654321', 31902 ),
		        ( '9ABAZ85656A776723', 31901 )" );
		    """, context );
	}

	@AfterEach
	public void removeFeatures() {
		instance.executeSource( """
		                        ormClearSession();
		                        queryExecute( "DELETE FROM vehicle_features WHERE FK_feature IN ( 31901, 31902 )" );
		                        queryExecute( "DELETE FROM features WHERE id IN ( 31901, 31902 )" );
		                        """, context );
	}

	/* ================================================================================ */
	/* The generated query */
	/* ================================================================================ */

	@DisplayName( "A collection condition matches root ids in a subquery instead of select distinct" )
	@Test
	public void testHQLUsesIdSubquery() {
		String hql = ( String ) run( "result = " + BOTH_MAKERS + ".getHQL();" );
		assertThat( hql ).doesNotContain( "distinct" );
		assertThat( hql ).contains( " in (select bx_this from Manufacturer bx_this" );
		assertThat( hql ).startsWith( "select bx_o from Manufacturer bx_o where bx_o in (" );
	}

	@DisplayName( "A query without a collection condition keeps its exact HQL" )
	@Test
	public void testHQLWithoutCollectionUnchanged() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.name', 'Ford Motor Company' ).order( 'model' ).getHQL();" ) )
		    .isEqualTo(
		        "select bx_this from Vehicle bx_this inner join bx_this.manufacturer bx_j1 where bx_j1.name = ?1 order by bx_this.model asc" );
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).getHQL();" ) )
		    .isEqualTo( "select bx_this from Vehicle bx_this where bx_this.make = ?1" );
	}

	@DisplayName( "asDistinct() keeps an explicit select distinct" )
	@Test
	public void testExplicitDistinctUnchanged() {
		String hql = ( String ) run( "result = " + BOTH_MAKERS + ".asDistinct().getHQL();" );
		assertThat( hql ).startsWith( "select distinct bx_this from Manufacturer bx_this" );
		assertThat( number( "result = " + BOTH_MAKERS + ".asDistinct().list().len();" ) ).isEqualTo( 2 );
	}

	@DisplayName( "Projections keep their own query" )
	@Test
	public void testProjectionsUnchanged() {
		String hql = ( String ) run( "result = " + BOTH_MAKERS + ".withProjections( property = 'name' ).getHQL();" );
		assertThat( hql ).doesNotContain( "bx_o" );
		assertThat( list( "result = " + BOTH_MAKERS + ".withProjections( property = 'name' ).order( 'name' ).list();" ) )
		    .containsExactly( "Ford Motor Company", "Honda Motor Co.", "Honda Motor Co.", "Honda Motor Co." ).inOrder();
	}

	/* ================================================================================ */
	/* Rows */
	/* ================================================================================ */

	@DisplayName( "list() returns each root entity once" )
	@Test
	public void testListOnce() {
		assertThat( list( "result = " + BOTH_MAKERS + ".order( 'name' ).list().map( ( m ) => m.getName() );" ) )
		    .containsExactly( "Ford Motor Company", "Honda Motor Co." ).inOrder();
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Accord", "Civic", "Fusion" ).inOrder();
	}

	@DisplayName( "count() counts each root entity once" )
	@Test
	public void testCount() {
		assertThat( number( "result = " + BOTH_MAKERS + ".count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = " + FEATURED + ".count();" ) ).isEqualTo( 3 );
	}

	@DisplayName( "Paging counts root entities, not join rows: no entity on two pages, no short page" )
	@Test
	public void testPagingOverDuplicates() {
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).maxResults( 1 ).firstResult( 0 ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Accord" );
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).maxResults( 1 ).firstResult( 1 ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Civic" );
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).maxResults( 1 ).firstResult( 2 ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Fusion" );
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).maxResults( 2 ).firstResult( 0 ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Accord", "Civic" ).inOrder();
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).maxResults( 5 ).firstResult( 3 ).list();" ) ).isEmpty();
	}

	@DisplayName( "paginate() returns full pages and the right totals" )
	@Test
	public void testPaginate() {
		IStruct	page1	= ( IStruct ) run( "result = " + FEATURED + ".order( 'model desc' ).paginate( 1, 2 );" );
		IStruct	paging	= page1.getAsStruct( ortus.boxlang.runtime.scopes.Key.of( "pagination" ) );
		assertThat( ( ( Number ) paging.get( "totalRecords" ) ).intValue() ).isEqualTo( 3 );
		assertThat( ( ( Number ) paging.get( "totalPages" ) ).intValue() ).isEqualTo( 2 );
		assertThat( list( "result = " + FEATURED + ".order( 'model desc' ).paginate( 1, 2 ).results.map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Fusion", "Civic" ).inOrder();
		assertThat( list( "result = " + FEATURED + ".order( 'model desc' ).paginate( 2, 2 ).results.map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Accord" );
	}

	@DisplayName( "get() and first() return the entity" )
	@Test
	public void testGetAndFirst() {
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).like( 'vehicles.make', 'Hon%' ).get().getName();" ) ).isEqualTo( "Honda Motor Co." );
		assertThat( run( "result = " + FEATURED + ".order( 'model desc' ).first().getModel();" ) ).isEqualTo( "Fusion" );
	}

	@DisplayName( "each() and chunk() visit each root entity once" )
	@Test
	public void testEachAndChunk() {
		assertThat( list( "seen = []; " + FEATURED + ".each( ( v ) => seen.append( v.getModel() ), 1 ); result = seen.sort( 'text' );" ) )
		    .containsExactly( "Accord", "Civic", "Fusion" ).inOrder();
		assertThat( number( "n = 0; " + FEATURED + ".chunk( 2, ( rows ) => n += rows.len() ); result = n;" ) ).isEqualTo( 3 );
	}

	@DisplayName( "asStream() streams each root entity once" )
	@Test
	public void testStream() {
		assertThat( number( "result = " + FEATURED + ".asStream().list().count();" ) ).isEqualTo( 3 );
	}

	/* ================================================================================ */
	/* Shapes */
	/* ================================================================================ */

	@DisplayName( "asStruct() returns each root once, with the same keys" )
	@Test
	public void testAsStruct() {
		Array rows = ( Array ) run( "result = " + FEATURED + ".order( 'model' ).asStruct().list();" );
		assertThat( rows.size() ).isEqualTo( 3 );
		IStruct first = ( IStruct ) rows.get( 0 );
		assertThat( first.get( "model" ) ).isEqualTo( "Accord" );
		assertThat( first.containsKey( "make" ) ).isTrue();
		assertThat( first.containsKey( "vin" ) ).isTrue();
	}

	@DisplayName( "asQuery() returns each root once" )
	@Test
	public void testAsQuery() {
		Query rows = ( Query ) run( "result = " + FEATURED + ".order( 'model' ).asQuery().list();" );
		assertThat( rows.size() ).isEqualTo( 3 );
		assertThat( rows.getRowAsStruct( 2 ).get( "model" ) ).isEqualTo( "Fusion" );
	}

	@DisplayName( "asStruct( includes ) returns each root once, paged by root" )
	@Test
	public void testMementoStructs() {
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).asStruct( 'model,make' ).list().map( ( s ) => s.model );" ) )
		    .containsExactly( "Accord", "Civic", "Fusion" ).inOrder();
		assertThat( list( "result = " + FEATURED + ".order( 'model' ).asStruct( 'model' ).maxResults( 2 ).list().map( ( s ) => s.model );" ) )
		    .containsExactly( "Accord", "Civic" ).inOrder();
	}

	@DisplayName( "resultTransformer( DISTINCT_ROOT_ENTITY ) still works with a collection condition" )
	@Test
	public void testDistinctRootEntity() {
		assertThat( number( "c = " + FEATURED + "; result = c.resultTransformer( c.DISTINCT_ROOT_ENTITY ).list().len();" ) ).isEqualTo( 3 );
	}

	/* ================================================================================ */
	/* Ordering */
	/* ================================================================================ */

	@DisplayName( "Ordering by a to-one property sorts the outer query" )
	@Test
	public void testOrderByToOne() {
		assertThat( list( "result = " + FEATURED + ".order( 'manufacturer.name desc, model' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Accord", "Civic", "Fusion" ).inOrder();
		assertThat( list( "result = " + FEATURED + ".order( 'manufacturer.name asc, model desc' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Fusion", "Civic", "Accord" ).inOrder();
	}

	@DisplayName( "A case-insensitive sort and a joinTo alias sort work too" )
	@Test
	public void testOrderIgnoreCaseAndAlias() {
		assertThat( list( "result = " + FEATURED + ".order( 'model', 'desc', true ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Fusion", "Civic", "Accord" ).inOrder();
		assertThat( list( """
		                  result = entityCriteria( 'Vehicle' )
		                      .joinTo( 'manufacturer', 'm' )
		                      .like( 'features.name', 'Spec319%' )
		                      .order( 'm.name desc, model' )
		                      .list()
		                      .map( ( v ) => v.getModel() );
		                  """ ) ).containsExactly( "Accord", "Civic", "Fusion" ).inOrder();
	}

	@DisplayName( "Ordering by a collection property is a clear orm.argument error" )
	@Test
	public void testOrderByCollectionIsError() {
		IStruct err = error( "result = " + BOTH_MAKERS + ".order( 'vehicles.model' ).list();" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "vehicles.model" );
	}

	@DisplayName( "Sorting by a collection value works through a projection that picks one value per entity" )
	@Test
	public void testOrderByCollectionThroughProjection() {
		Array rows = ( Array ) run( "result = " + BOTH_MAKERS
		    + ".project( ( p ) => p.group( 'id' ).group( 'name' ).min( 'vehicles.model', 'firstModel' ) ).order( 'firstModel desc' ).asStruct().list();" );
		assertThat( rows.size() ).isEqualTo( 2 );
		List<Object> firstModels = rows.stream().map( r -> ( ( IStruct ) r ).get( "firstModel" ) ).toList();
		assertThat( firstModels ).isInOrder( java.util.Comparator.<Object, String>comparing( String::valueOf ).reversed() );
	}

	/* ================================================================================ */
	/* Joins */
	/* ================================================================================ */

	@DisplayName( "fetch() still loads the association with a collection condition" )
	@Test
	public void testFetchWithCollectionCondition() {
		assertThat( list( """
		                  vs = entityCriteria( 'Vehicle' ).fetch( 'manufacturer' ).like( 'features.name', 'Spec319%' ).order( 'model' ).list();
		                  ormClearSession();
		                  result = vs.map( ( v ) => v.getManufacturer().getName() );
		                  """ ) ).containsExactly( "Honda Motor Co.", "Honda Motor Co.", "Ford Motor Company" ).inOrder();
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).fetch( 'manufacturer' ).like( 'features.name', 'Spec319%' ).getHQL();" ) )
		    .contains( "join fetch bx_o.manufacturer" );
	}

	@DisplayName( "A collection condition combined with a to-one condition filters inside the subquery" )
	@Test
	public void testMixedConditions() {
		assertThat( list( """
		                  result = entityCriteria( 'Vehicle' )
		                      .like( 'features.name', 'Spec319%' )
		                      .isEq( 'manufacturer.name', 'Honda Motor Co.' )
		                      .order( 'model desc' )
		                      .list()
		                      .map( ( v ) => v.getModel() );
		                  """ ) ).containsExactly( "Civic", "Accord" ).inOrder();
	}

	@DisplayName( "A nested collection path (vehicles.features) lists each root once" )
	@Test
	public void testNestedCollectionPath() {
		assertThat( list(
		    "result = entityCriteria( 'Manufacturer' ).like( 'vehicles.features.name', 'Spec319%' ).order( 'name' ).list().map( ( m ) => m.getName() );" ) )
		    .containsExactly( "Ford Motor Company", "Honda Motor Co." ).inOrder();
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).like( 'vehicles.features.name', 'Spec319%' ).count();" ) ).isEqualTo( 2 );
	}

	@DisplayName( "with{Association}( closure ) on a collection lists each root once" )
	@Test
	public void testWithCollectionClosure() {
		assertThat( list( """
		                  result = entityCriteria( 'Vehicle' )
		                      .withFeatures( ( f ) => f.like( 'name', 'Spec319%' ) )
		                      .order( 'model' )
		                      .list()
		                      .map( ( v ) => v.getModel() );
		                  """ ) ).containsExactly( "Accord", "Civic", "Fusion" ).inOrder();
	}

	@DisplayName( "each() with keyset batches still visits each root once" )
	@Test
	public void testKeysetEach() {
		assertThat( list( "seen = []; " + BOTH_MAKERS + ".each( ( m ) => seen.append( m.getName() ), 1 ); result = seen.sort( 'text' );" ) )
		    .containsExactly( "Ford Motor Company", "Honda Motor Co." ).inOrder();
	}

	@DisplayName( "A to-one sort is a left join of the outer query; the subquery keeps the conditions" )
	@Test
	public void testOuterSortJoinHQL() {
		String hql = ( String ) run( "result = " + FEATURED + ".order( 'manufacturer.name desc' ).getHQL();" );
		assertThat( hql ).startsWith( "select bx_o from Vehicle bx_o left join bx_o.manufacturer bx_o" );
		assertThat( hql ).contains( " where bx_o in (select bx_this from Vehicle bx_this " );
		assertThat( hql ).endsWith( ".name desc" );
	}

	@DisplayName( "Fetching the collection a condition uses loads the whole collection of each matching root" )
	@Test
	public void testFetchSameCollection() {
		assertThat( list( """
		                  ms = entityCriteria( 'Manufacturer' ).fetch( 'vehicles' ).like( 'vehicles.model', 'Acc%' ).list();
		                  ormClearSession();
		                  result = ms.map( ( m ) => m.getName() & ':' & m.getVehicles().len() );
		                  """ ) ).containsExactly( "Honda Motor Co.:3" );
	}

	@DisplayName( "A collection condition inside anyOf / or still lists each root once" )
	@Test
	public void testCollectionConditionInOr() {
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  result = c.or( c.restrictions.like( 'vehicles.make', 'Hon%' ), c.restrictions.isEq( 'name', 'Ford Motor Company' ) )
		                      .order( 'name' )
		                      .list()
		                      .map( ( m ) => m.getName() );
		                  """ ) ).containsExactly( "Ford Motor Company", "Honda Motor Co." ).inOrder();
	}

	@DisplayName( "A collection condition with values keeps its parameters bound in order" )
	@Test
	public void testParametersBound() {
		List<Object> models = list( """
		                            result = entityCriteria( 'Vehicle' )
		                                .isEq( 'make', 'Honda' )
		                                .isIn( 'features.name', [ 'Spec319 Roof', 'Spec319 Seats' ] )
		                                .ne( 'model', 'Ridgeline' )
		                                .order( 'model' )
		                                .list()
		                                .map( ( v ) => v.getModel() );
		                            """ );
		assertThat( models ).containsExactly( "Accord", "Civic" ).inOrder();
	}
}
