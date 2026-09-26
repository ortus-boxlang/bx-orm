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
 * subquery, and the cborm {@code property*}/{@code sub*} forms.
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
