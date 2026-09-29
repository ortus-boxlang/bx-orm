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

import ortus.boxlang.runtime.types.IStruct;

/**
 * Live tests for {@code entityCriteria()} joins: dotted paths, join reuse, join types, aliases,
 * {@code with{Association}()}, {@code createCriteria()}, {@code fetch()} and to-many de-duplication.
 */
public class CriteriaJoinsTest extends CriteriaTestSupport {

	/**
	 * Test: a dotted path joins the association.
	 */
	@DisplayName( "A dotted path joins its association" )
	@Test
	public void testDottedPath() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.name', 'Ford Motor Company' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Fusion" );
	}

	/**
	 * Test: the same path is joined once.
	 */
	@DisplayName( "The same path is joined once and reused" )
	@Test
	public void testJoinReused() {
		String hql = ( String ) run( """
		                             result = entityCriteria( 'Vehicle' )
		                                 .like( 'manufacturer.name', 'Honda%' )
		                                 .like( 'manufacturer.address', '%Torrance%' )
		                                 .order( 'manufacturer.name' )
		                                 .getHQL();
		                             """ );
		assertThat( hql.split( " join ", -1 ).length - 1 ).isEqualTo( 1 );
		assertThat(
		    number( "result = entityCriteria( 'Vehicle' ).like( 'manufacturer.name', 'Honda%' ).like( 'manufacturer.address', '%Torrance%' ).count();" ) )
		    .isEqualTo( 3 );
	}

	/**
	 * Test: a condition inside anyOf uses a left join so rows without the association still match other branches.
	 */
	@DisplayName( "Paths inside anyOf use a left join" )
	@Test
	public void testAnyOfLeftJoin() {
		assertThat( number( """
		                    result = entityCriteria( 'Vehicle' )
		                        .anyOf( ( c ) => c.isEq( 'make', 'Studebaker' ).isEq( 'manufacturer.name', 'Ford Motor Company' ) )
		                        .count();
		                    """ ) ).isEqualTo( 2 );
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).anyOf( ( c ) => c.isEq( 'manufacturer.name', 'x' ) ).getHQL();" ) )
		    .contains( "left join" );
	}

	/**
	 * Test: ordering by an association path keeps rows without it (left join).
	 */
	@DisplayName( "Ordering by an association path keeps rows without it" )
	@Test
	public void testOrderLeftJoin() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).order( 'manufacturer.name' ).list().len();" ) ).isEqualTo( 5 );
	}

	/**
	 * Test: joinTo with an alias, then alias paths.
	 */
	@DisplayName( "joinTo / createAlias gives the join an alias for later paths" )
	@Test
	public void testJoinToAlias() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).joinTo( 'manufacturer', 'm' ).isEq( 'm.name', 'Honda Motor Co.' ).count();" ) )
		    .isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).createAlias( 'manufacturer', 'mf' ).like( 'mf.address', '%Dearborn%' ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: an inner join drops rows without the association; a left join keeps them.
	 */
	@DisplayName( "innerJoin drops rows without the association; leftJoin keeps them" )
	@Test
	public void testJoinTypes() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).innerJoin( 'manufacturer', 'm' ).list().len();" ) ).isEqualTo( 4 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).leftJoin( 'manufacturer', 'm' ).list().len();" ) ).isEqualTo( 5 );
	}

	/**
	 * Test: join type names and the cborm constants.
	 */
	@DisplayName( "Join types accept names and the cborm constants" )
	@Test
	public void testJoinTypeConstants() {
		assertThat( number( "c = entityCriteria( 'Vehicle' ); result = c.createAlias( 'manufacturer', 'm', c.LEFT_JOIN ).list().len();" ) ).isEqualTo( 5 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).joinTo( 'manufacturer', 'm', 'left outer' ).list().len();" ) ).isEqualTo( 5 );
		assertThat( number( "c = entityCriteria( 'Vehicle' ); result = c.joinTo( 'manufacturer', 'm', c.INNER_JOIN ).list().len();" ) ).isEqualTo( 4 );
		assertThat( type( error( "entityCriteria( 'Vehicle' ).joinTo( 'manufacturer', 'm', 'sideways' );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: with{Association}( closure ) scopes conditions to the association.
	 */
	@DisplayName( "with{Association}( closure ) scopes conditions to the association" )
	@Test
	public void testWithAssociationClosure() {
		assertThat( number( """
		                    result = entityCriteria( 'Vehicle' )
		                        .withManufacturer( ( m ) => m.like( 'name', 'Honda%' ) )
		                        .isEq( 'model', 'Civic' )
		                        .count();
		                    """ ) ).isEqualTo( 1 );
	}

	/**
	 * Test: with{Association}() without a closure switches until end().
	 */
	@DisplayName( "with{Association}() switches the context until end()" )
	@Test
	public void testWithAssociationEnd() {
		assertThat( number( """
		                    result = entityCriteria( 'Vehicle' )
		                        .withManufacturer()
		                            .like( 'name', 'Honda%' )
		                        .end()
		                        .isEq( 'model', 'Civic' )
		                        .count();
		                    """ ) ).isEqualTo( 1 );
	}

	/**
	 * Test: with{Association}( joinType ).
	 */
	@DisplayName( "with{Association}( 'left' ) uses a left join" )
	@Test
	public void testWithAssociationJoinType() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).withManufacturer( 'left' ).isNotNull( 'name' ).end().getHQL();" ) )
		    .contains( "left join" );
	}

	/**
	 * Test: createCriteria is cborm's with{Association}.
	 */
	@DisplayName( "createCriteria( association ) switches the context like with{Association}()" )
	@Test
	public void testCreateCriteria() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).createCriteria( 'manufacturer' ).like( 'name', 'Ford%' ).end().count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: this. reaches the root inside with{Association}.
	 */
	@DisplayName( "this.property reaches the root entity inside with{Association}()" )
	@Test
	public void testThisInsideWith() {
		assertThat(
		    number( "result = entityCriteria( 'Vehicle' ).withManufacturer( ( m ) => m.like( 'name', 'Honda%' ).isEq( 'this.model', 'Civic' ) ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: a to-many join returns each root entity once.
	 */
	@DisplayName( "A to-many condition returns each root entity once" )
	@Test
	public void testToManyDistinct() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).like( 'vehicles.make', 'Hon%' ).list().len();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).like( 'vehicles.make', 'Hon%' ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: fetch() loads the association with the root.
	 */
	@DisplayName( "fetch() loads the association so it is readable after the session is cleared" )
	@Test
	public void testFetch() {
		assertThat( run( """
		                 v = entityCriteria( 'Vehicle' ).fetch( 'manufacturer' ).idEq( '9ABAZ85656A776723' ).get();
		                 ormClearSession();
		                 result = v.getManufacturer().getName();
		                 """ ) ).isEqualTo( "Ford Motor Company" );
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).fetch( 'manufacturer' ).getHQL();" ) ).contains( "join fetch" );
	}

	/**
	 * Test: a fetch-only join is left out of count().
	 */
	@DisplayName( "count() ignores fetch-only joins" )
	@Test
	public void testFetchCount() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).fetch( 'vehicles' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: navigating through a value is a clear error.
	 */
	@DisplayName( "A path through a plain value is an orm.property.unknown error" )
	@Test
	public void testPathThroughValue() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).isEq( 'make.name', 'x' );" );
		assertThat( type( err ) ).isEqualTo( "orm.property.unknown" );
		assertThat( message( err ) ).contains( "not an association" );
	}

	/**
	 * Test: an unknown property on the association.
	 */
	@DisplayName( "An unknown property on an association suggests the right one" )
	@Test
	public void testUnknownAssociationProperty() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).isEq( 'manufacturer.nmae', 'x' );" );
		assertThat( type( err ) ).isEqualTo( "orm.property.unknown" );
		assertThat( message( err ) ).contains( "Manufacturer" );
		assertThat( message( err ) ).contains( "Did you mean [name]?" );
	}

	/**
	 * Test: joining a value is a clear error.
	 */
	@DisplayName( "Joining a plain value is an orm.property.unknown error" )
	@Test
	public void testJoinValue() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).joinTo( 'make', 'm' );" );
		assertThat( type( err ) ).isEqualTo( "orm.property.unknown" );
		assertThat( message( err ) ).contains( "not an association" );
	}

	/**
	 * Test: an invalid alias name.
	 */
	@DisplayName( "An invalid alias is an orm.argument error" )
	@Test
	public void testBadAlias() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).joinTo( 'manufacturer', 'bad alias' );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: a many-to-many path.
	 */
	@DisplayName( "A many-to-many path joins through the link table" )
	@Test
	public void testManyToMany() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'features.name', 'Sunroof' ).getHQL();" ) ).contains( "features" );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'features.name', 'No Such Feature' ).count();" ) ).isEqualTo( 0 );
	}

	/**
	 * Test: rightJoin and fullJoin produce the matching HQL join (running them needs database support).
	 */
	@DisplayName( "rightJoin / fullJoin produce right and full joins" )
	@Test
	public void testRightAndFullJoin() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).rightJoin( 'manufacturer', 'm' ).getHQL();" ) ).contains( "right join" );
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).fullJoin( 'manufacturer', 'm' ).getHQL();" ) ).contains( "full join" );
	}

	/**
	 * Test: toString indents the calls made inside with{Association}( closure ).
	 */
	@DisplayName( "toString() indents the calls inside with{Association}( closure )" )
	@Test
	public void testWithToString() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).withManufacturer( ( m ) => m.like( 'name', 'Honda%' ) ).toString();" ) )
		    .contains( ".withManufacturer( ( c ) => {...} )\n      .like( \"name\", \"Honda%\" )" );
	}
}
