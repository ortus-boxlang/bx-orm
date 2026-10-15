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
package ortus.boxlang.modules.orm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import tools.BaseORMTest;

/**
 * HQL names entities and properties in any case (see {@link HQLCaseResolver}). Each wrong-case query must return what
 * the declared-case query returns. Fixtures: models/cms (cbAuthor, and cbEntry which inherits from cbContent).
 */
public class HQLCaseInsensitiveTest extends BaseORMTest {

	/**
	 * Run two HQL strings and return both results.
	 *
	 * @param wrongCase    HQL with names in the wrong case.
	 * @param declaredCase The same HQL with names in declared case.
	 *
	 * @return The two results, wrong case first.
	 */
	private Object[] both( String wrongCase, String declaredCase ) {
		instance.executeSource( """
		                        wrongResult = ormExecuteQuery( "%s" );
		                        rightResult = ormExecuteQuery( "%s" );
		                        """.formatted( wrongCase, declaredCase ), context );
		return new Object[] { variables.get( Key.of( "wrongResult" ) ), variables.get( Key.of( "rightResult" ) ) };
	}

	@DisplayName( "A bare property in the wrong case resolves" )
	@Test
	public void testBareProperty() {
		Object[] r = both( "select count(*) from cbAuthor where firstname is not null",
		    "select count(*) from cbAuthor where firstName is not null" );
		assertThat( r[ 0 ] ).isEqualTo( r[ 1 ] );
	}

	@DisplayName( "An alias.property in the wrong case resolves" )
	@Test
	public void testAliasProperty() {
		Object[] r = both( "select count(*) from cbAuthor a where a.FIRSTNAME is not null and a.LastName is not null",
		    "select count(*) from cbAuthor a where a.firstName is not null and a.lastName is not null" );
		assertThat( r[ 0 ] ).isEqualTo( r[ 1 ] );
	}

	@DisplayName( "An entity name in the wrong case resolves" )
	@Test
	public void testEntityName() {
		Object[] r = both( "select count(*) from CBAUTHOR", "select count(*) from cbAuthor" );
		assertThat( r[ 0 ] ).isEqualTo( r[ 1 ] );
	}

	@DisplayName( "An inherited property of a subclass in the wrong case resolves" )
	@Test
	public void testInheritedProperty() {
		Object[] r = both( "select count(*) from cbEntry e where e.createddate is not null",
		    "select count(*) from cbEntry e where e.createdDate is not null" );
		assertThat( r[ 0 ] ).isEqualTo( r[ 1 ] );
	}

	@DisplayName( "A joined association and its properties in the wrong case resolve" )
	@Test
	public void testJoin() {
		Object[] r = both( "select count(*) from cbEntry e join e.CREATOR a where a.FIRSTNAME is not null",
		    "select count(*) from cbEntry e join e.creator a where a.firstName is not null" );
		assertThat( r[ 0 ] ).isEqualTo( r[ 1 ] );
	}

	@DisplayName( "An order by property in the wrong case resolves" )
	@Test
	public void testOrderBy() {
		Object[]	r		= both( "select a.authorID from cbAuthor a order by a.LASTNAME desc, a.authorid",
		    "select a.authorID from cbAuthor a order by a.lastName desc, a.authorID" );
		Array		wrong	= ( Array ) r[ 0 ];
		assertThat( wrong ).isEqualTo( r[ 1 ] );
	}

	@DisplayName( "A select new map with a wrong-case property resolves" )
	@Test
	public void testSelectNewMap() {
		instance.executeSource( """
		                        result = ormExecuteQuery( "select new map( a.firstname as fn ) from cbAuthor a", [], false, { maxResults : 1 } );
		                        """, context );
		Array result = variables.getAsArray( Key.of( "result" ) );
		if ( !result.isEmpty() ) {
			assertThat( ( ( java.util.Map<?, ?> ) result.get( 0 ) ).containsKey( "fn" ) ).isTrue();
		}
	}

	@DisplayName( "Named parameters keep their own case" )
	@Test
	public void testNamedParameter() {
		instance.executeSource(
		    """
		    result = ormExecuteQuery( "select count(*) from cbAuthor where firstname = :firstname", { firstname : "nobody-by-this-name" }, true );
		    """, context );
		assertThat( variables.get( result ).toString() ).isEqualTo( "0" );
	}

	@DisplayName( "A name inside a string literal is not rewritten" )
	@Test
	public void testLiteralUntouched() {
		instance.executeSource( """
		                        result = ormExecuteQuery( "select count(*) from cbAuthor where firstname = 'firstname'", [], true );
		                        """, context );
		assertThat( variables.get( result ).toString() ).isEqualTo( "0" );
		assertThat( HQLCaseResolver.replaceIdentifier( "from A where firstname = 'firstname' and x = \"firstname\"", "firstname", "firstName" ) )
		    .isEqualTo( "from A where firstName = 'firstname' and x = \"firstname\"" );
		assertThat( HQLCaseResolver.replaceIdentifier( "from A where firstname = :firstname", "firstname", "firstName" ) )
		    .isEqualTo( "from A where firstName = :firstname" );
		assertThat( HQLCaseResolver.replaceIdentifier( "from A where a = 'it''s firstname'", "firstname", "firstName" ) ).isNull();
		assertThat( HQLCaseResolver.replaceIdentifier( "from A where firstnames = 1", "firstname", "firstName" ) ).isNull();
	}

	@DisplayName( "A misspelled property still fails with a Did you mean" )
	@Test
	public void testMisspelledStillFails() {
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class,
		    () -> instance.executeSource( "ormExecuteQuery( \"from cbAuthor where fristname = 'x'\" )", context ) );
		assertThat( e.getMessage() ).contains( "has no property [fristname]. Did you mean" );
		assertThat( e.getMessage() ).contains( "firstName" );
	}

	@DisplayName( "A wrong-case query runs again from the corrected HQL" )
	@Test
	public void testRepeatedQuery() {
		instance.executeSource( """
		                        q = "select count(*) from cbauthor a where a.LASTNAME is not null";
		                        first = ormExecuteQuery( q, [], true );
		                        second = ormExecuteQuery( q, [], true );
		                        """, context );
		assertThat( variables.get( Key.of( "first" ) ) ).isEqualTo( variables.get( Key.of( "second" ) ) );
	}
}
