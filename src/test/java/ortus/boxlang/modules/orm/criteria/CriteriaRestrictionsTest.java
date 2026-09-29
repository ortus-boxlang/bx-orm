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
 * {@code c.restrictions}: cborm-style condition objects passed to {@code add()}, {@code or()}, {@code and()} and
 * {@code not()}.
 */
public class CriteriaRestrictionsTest extends CriteriaTestSupport {

	@DisplayName( "or() takes restrictions: each one is an alternative" )
	@Test
	public void testOrWithRestrictions() {
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.or( c.restrictions.isEq( 'model', 'Civic' ), c.restrictions.isEq( 'model', 'Fusion' ) ).count();
		                    """ ) ).isEqualTo( 2 );
	}

	@DisplayName( "and() and add() take restrictions" )
	@Test
	public void testAndAndAdd() {
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.and( c.restrictions.isEq( 'make', 'Honda' ), c.restrictions.isEq( 'model', 'Civic' ) ).count();
		                    """ ) ).isEqualTo( 1 );
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.add( c.restrictions.isEq( 'make', 'Honda' ) ).count();
		                    """ ) ).isEqualTo( 3 );
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.add( c.restrictions.isEq( 'make', 'Honda' ), c.restrictions.isEq( 'model', 'Civic' ) ).count();
		                    """ ) ).isEqualTo( 1 );
	}

	@DisplayName( "not() takes a restriction, and not... forms work on restrictions" )
	@Test
	public void testNot() {
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.not( c.restrictions.isEq( 'make', 'Honda' ) ).count();
		                    """ ) ).isEqualTo( 2 );
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.add( c.restrictions.notEq( 'make', 'Honda' ) ).count();
		                    """ ) ).isEqualTo( 2 );
	}

	@DisplayName( "Restrictions nest with restrictions.or / and / not, and mix with closures" )
	@Test
	public void testNesting() {
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    r = c.restrictions;
		                    result = c.add( r.or( r.isEq( 'model', 'Civic' ), r.isEq( 'model', 'Fusion' ) ) ).count();
		                    """ ) ).isEqualTo( 2 );
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    r = c.restrictions;
		                    result = c.add( r.and( r.isEq( 'make', 'Honda' ), r.not( r.isEq( 'model', 'Civic' ) ) ) ).count();
		                    """ ) ).isEqualTo( 2 );
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.or( c.restrictions.isEq( 'model', 'Civic' ), ( c ) => c.isEq( 'make', 'Ford' ) ).count();
		                    """ ) ).isEqualTo( 2 );
	}

	@DisplayName( "Restrictions take named arguments and aliases, and match the direct call" )
	@Test
	public void testNamedArgumentsAndAliases() {
		assertThat( number( """
		                    c = entityCriteria( 'Vehicle' );
		                    result = c.add( c.restrictions.eq( property = 'make', value = 'Honda' ) ).count();
		                    """ ) ).isEqualTo( 3 );
		assertThat( run( """
		                 c = entityCriteria( 'Vehicle' );
		                 viaRestriction = c.copy().add( c.restrictions.like( 'model', 'C%' ) ).getSQL();
		                 direct = c.copy().like( 'model', 'C%' ).getSQL();
		                 result = viaRestriction == direct;
		                 """ ) ).isEqualTo( true );
	}

	@DisplayName( "A restriction is resolved against the builder it is added to" )
	@Test
	public void testResolvedAgainstTargetBuilder() {
		assertThat( number( """
		                    r = entityCriteria( 'Vehicle' ).restrictions.isEq( 'make', 'Honda' );
		                    result = entityCriteria( 'Vehicle' ).add( r ).count();
		                    """ ) ).isEqualTo( 3 );
	}

	@DisplayName( "Restrictions only build conditions: other methods and unknown names fail clearly" )
	@Test
	public void testErrors() {
		IStruct notACondition = error( "c = entityCriteria( 'Vehicle' ); c.restrictions.list();" );
		assertThat( type( notACondition ) ).isEqualTo( "orm.argument" );
		assertThat( message( notACondition ) ).contains( "restrictions have no condition [list()]" );

		IStruct misspelled = error( "c = entityCriteria( 'Vehicle' ); c.restrictions.isEqual( 'make', 'Honda' );" );
		assertThat( message( misspelled ) ).contains( "Did you mean" );

		IStruct badArgument = error( "entityCriteria( 'Vehicle' ).add( 'make' );" );
		assertThat( type( badArgument ) ).isEqualTo( "orm.argument" );
		assertThat( message( badArgument ) ).contains( "expected a closure" );
	}
}
