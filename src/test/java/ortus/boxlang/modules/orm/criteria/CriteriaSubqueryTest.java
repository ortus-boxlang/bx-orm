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

/**
 * Live tests for {@code entityCriteria()} subqueries: correlated {@code exists}/{@code notExists}, {@code isIn} with a
 * subquery, and the cborm {@code property*}/{@code sub*} forms (including the quantified {@code *All}/{@code *Some} ones).
 */
public class CriteriaSubqueryTest extends CriteriaTestSupport {

	/**
	 * Test: correlated exists.
	 */
	@DisplayName( "exists( subquery ) with a correlated condition" )
	@Test
	public void testExists() {
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  result = c.exists( c.subquery( 'Vehicle', 'v' ).eqProperty( 'v.manufacturer', 'this' ).isEq( 'make', 'Honda' ) )
		                      .list().map( ( m ) => m.getName() );
		                  """ ) ).containsExactly( "Honda Motor Co." );
	}

	/**
	 * Test: notExists.
	 */
	@DisplayName( "notExists( subquery ) keeps rows without a match" )
	@Test
	public void testNotExists() {
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  result = c.notExists( c.subquery( 'Vehicle', 'v' ).eqProperty( 'v.manufacturer', 'this' ) )
		                      .list().map( ( m ) => m.getName() );
		                  """ ) ).containsExactly( "General Moters Corporation" );
	}

	/**
	 * Test: unqualified paths in a subquery start at its own entity; this. reaches the outer root.
	 */
	@DisplayName( "Unqualified subquery paths start at the subquery entity; this. is the outer root" )
	@Test
	public void testSubqueryPaths() {
		assertThat( number( """
		                    c = entityCriteria( 'Manufacturer' );
		                    result = c.exists( c.subquery( 'Vehicle' ).eqProperty( 'manufacturer.id', 'this.id' ).like( 'model', 'R%' ) ).count();
		                    """ ) ).isEqualTo( 1 );
	}

	/**
	 * Test: isIn with a subquery projection.
	 */
	@DisplayName( "isIn( property, subquery ) uses the subquery's projection" )
	@Test
	public void testIsInSubquery() {
		assertThat(
		    number( """
		            c = entityCriteria( 'Manufacturer' );
		            result = c.isIn( 'id', c.subquery( 'Vehicle', 'v' ).isEq( 'make', 'Ford' ).project( ( p ) => p.property( 'manufacturer.id' ) ) ).count();
		            """ ) ).isEqualTo( 1 );
	}

	/**
	 * Test: propertyIn / propertyNotIn.
	 */
	@DisplayName( "propertyIn / propertyNotIn compare a property with a subquery" )
	@Test
	public void testPropertyIn() {
		assertThat( number( """
		                    c = entityCriteria( 'Manufacturer' );
		                    sub = c.subquery( 'Vehicle', 'v' ).isNotNull( 'manufacturer' ).project( ( p ) => p.property( 'manufacturer.id' ) );
		                    result = c.propertyIn( 'id', sub ).count();
		                    """ ) ).isEqualTo( 2 );
		assertThat( number( """
		                    c = entityCriteria( 'Manufacturer' );
		                    sub = c.subquery( 'Vehicle', 'v' ).isNotNull( 'manufacturer' ).project( ( p ) => p.property( 'manufacturer.id' ) );
		                    result = c.propertyNotIn( 'id', sub ).count();
		                    """ ) ).isEqualTo( 1 );
	}

	/**
	 * Test: propertyEq with an aggregate subquery.
	 */
	@DisplayName( "propertyEq compares a property with a single-value subquery" )
	@Test
	public void testPropertyEq() {
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  result = c.propertyEq( 'id', c.subquery( 'Manufacturer', 'm2' ).project( ( p ) => p.max( 'id' ) ) ).list().map( ( m ) => m.getId() );
		                  """ ) ).containsExactly( 77 );
		assertThat( number( """
		                    c = entityCriteria( 'Manufacturer' );
		                    result = c.propertyLt( 'id', c.subquery( 'Manufacturer', 'm2' ).project( ( p ) => p.max( 'id' ) ) ).count();
		                    """ ) ).isEqualTo( 2 );
	}

	/**
	 * Test: subLe with a correlated count.
	 */
	@DisplayName( "subLe / subEq compare a value with a correlated count" )
	@Test
	public void testSubValue() {
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  counted = c.subquery( 'Vehicle', 'v' ).eqProperty( 'v.manufacturer', 'this' ).project( ( p ) => p.rowCount() );
		                  result = c.subLe( 2, counted ).list().map( ( m ) => m.getId() );
		                  """ ) ).containsExactly( 42 );
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  counted = c.subquery( 'Vehicle', 'v' ).eqProperty( 'v.manufacturer', 'this' ).project( ( p ) => p.rowCount() );
		                  result = c.subEq( 0, counted ).list().map( ( m ) => m.getId() );
		                  """ ) ).containsExactly( 77 );
	}

	/**
	 * Test: property*All / property*Some (manufacturer ids are 1, 42 and 77).
	 */
	@DisplayName( "property*All / property*Some compare a property with every or some rows of a subquery" )
	@Test
	public void testPropertyQuantified() {
		String ids = "c = entityCriteria( 'Manufacturer' ); ids = c.subquery( 'Manufacturer', 'm2' ).project( ( p ) => p.property( 'id' ) );";
		assertThat( list( ids + "result = c.propertyGeAll( 'id', ids ).list().map( ( m ) => m.getId() );" ) ).containsExactly( 77 );
		assertThat( list( ids + "result = c.propertyLeAll( 'id', ids ).list().map( ( m ) => m.getId() );" ) ).containsExactly( 1 );
		assertThat( number( ids + "result = c.propertyGtSome( 'id', ids ).count();" ) ).isEqualTo( 2 );
		assertThat( number( ids + "result = c.propertyLtSome( 'id', ids ).count();" ) ).isEqualTo( 2 );
		assertThat( number( ids + "result = c.propertyGeSome( 'id', ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.propertyLeSome( 'id', ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.propertyGtAll( 'id', ids ).count();" ) ).isEqualTo( 0 );
		assertThat( number( ids + "result = c.propertyLtAll( 'id', ids ).count();" ) ).isEqualTo( 0 );
		assertThat( list( """
		                  c = entityCriteria( 'Manufacturer' );
		                  honda = c.subquery( 'Manufacturer', 'm2' ).isEq( 'id', 42 ).project( ( p ) => p.property( 'id' ) );
		                  result = c.propertyEqAll( 'id', honda ).list().map( ( m ) => m.getId() );
		                  """ ) ).containsExactly( 42 );
	}

	/**
	 * Test: sub*All / sub*Some (manufacturer ids are 1, 42 and 77).
	 */
	@DisplayName( "sub*All / sub*Some compare a value with every or some rows of a subquery" )
	@Test
	public void testSubQuantified() {
		String ids = "c = entityCriteria( 'Manufacturer' ); ids = c.subquery( 'Manufacturer', 'm2' ).project( ( p ) => p.property( 'id' ) );";
		assertThat( number( ids + "result = c.subGeAll( 77, ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.subGtAll( 77, ids ).count();" ) ).isEqualTo( 0 );
		assertThat( number( ids + "result = c.subGtSome( 77, ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.subGeSome( 1, ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.subLeAll( 1, ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.subLeSome( 0, ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.subLtAll( 1, ids ).count();" ) ).isEqualTo( 0 );
		assertThat( number( ids + "result = c.subLtSome( 1, ids ).count();" ) ).isEqualTo( 3 );
		assertThat( number( ids + "result = c.subGeSome( 0, ids ).count();" ) ).isEqualTo( 0 );
		assertThat( number( """
		                    c = entityCriteria( 'Manufacturer' );
		                    honda = c.subquery( 'Manufacturer', 'm2' ).isEq( 'id', 42 ).project( ( p ) => p.property( 'id' ) );
		                    result = c.subEqAll( 42, honda ).count();
		                    """ ) ).isEqualTo( 3 );
	}

	/**
	 * Test: quantified forms compile to all / some, work as restrictions and take not... forms.
	 */
	@DisplayName( "Quantified forms compile to all / some, and work as restrictions and not... forms" )
	@Test
	public void testQuantifiedForms() {
		String ids = "c = entityCriteria( 'Manufacturer' ); ids = c.subquery( 'Manufacturer', 'm2' ).project( ( p ) => p.property( 'id' ) );";
		assertThat( ( String ) run( ids + "result = c.propertyGeAll( 'id', ids ).getHQL();" ) ).contains( ">= all (" );
		assertThat( ( String ) run( ids + "result = c.subLtSome( 1, ids ).getHQL();" ) ).contains( "< some (" );
		assertThat( number( ids + "result = c.add( c.restrictions.propertyGeAll( 'id', ids ) ).count();" ) ).isEqualTo( 1 );
		assertThat( number( ids + "result = c.notPropertyGeAll( 'id', ids ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: a subquery cannot run on its own.
	 */
	@DisplayName( "Running a subquery on its own is an orm.argument error" )
	@Test
	public void testSubqueryAlone() {
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).subquery( 'Vehicle' ).list();" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: a non-subquery passed where one is needed.
	 */
	@DisplayName( "exists() with a non-subquery is an orm.argument error" )
	@Test
	public void testNotASubquery() {
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).exists( entityCriteria( 'Vehicle' ) );" ) ) ).isEqualTo( "orm.argument" );
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).propertyIn( 'id', [ 1, 2 ] );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: subquery parameters are numbered with the outer query's.
	 */
	@DisplayName( "Subquery parameters are bound in order with the outer query's" )
	@Test
	public void testParameterOrder() {
		assertThat( number( """
		                    c = entityCriteria( 'Manufacturer' ).like( 'name', '%Motor%' );
		                    result = c.exists( c.subquery( 'Vehicle', 'v' ).eqProperty( 'v.manufacturer', 'this' ).isEq( 'model', 'Fusion' ) )
		                        .isGt( 'id', 0 ).count();
		                    """ ) ).isEqualTo( 1 );
	}
}
