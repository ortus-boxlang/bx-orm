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

import org.hibernate.dialect.CockroachDialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.IStruct;

/**
 * Live tests for the escape character of {@code like()} and {@code ilike()}. The suite runs on MySQL, where Hibernate 7
 * would render {@code escape ''} (escape off) for a {@code like} with no escape. The default must stay the 1.x
 * behavior: a backslash escapes {@code %}, {@code _} and itself. An explicit escape character overrides it.
 */
public class CriteriaLikeEscapeTest extends CriteriaTestSupport {

	/**
	 * Add vehicles whose model holds literal wildcard and backslash characters, plus look-alikes that only a wildcard
	 * would match.
	 */
	@BeforeEach
	public void addLikeRows() {
		// @formatter:off
		instance.executeSource(
			"""
			rows = [
				[ "LIKE001", "50%-off" ],
				[ "LIKE002", "50X-off" ],
				[ "LIKE003", "a_b" ],
				[ "LIKE004", "aXb" ],
				[ "LIKE005", "c:\\d" ],
				[ "LIKE006", "c:\\\\d" ],
				[ "LIKE007", "Mixed!Case" ]
			];
			rows.each( ( r ) => queryExecute(
				"INSERT INTO vehicles ( vin, make, model ) VALUES ( ?, 'LikeEsc', ? )",
				[ r[ 1 ], r[ 2 ] ]
			) );
			""",
			context
		);
		// @formatter:on
	}

	/**
	 * Remove the rows the tests added.
	 */
	@AfterEach
	public void removeLikeRows() {
		instance.executeSource( "queryExecute( \"DELETE FROM vehicles WHERE vin LIKE 'LIKE0%'\" );", context );
	}

	/**
	 * Count the rows of the test make that match a criteria expression.
	 *
	 * @param condition The condition call, e.g. {@code like( 'model', 'a\_b' )}.
	 *
	 * @return The count.
	 */
	private long count( String condition ) {
		return number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'LikeEsc' )." + condition + ".count();" );
	}

	/**
	 * Count the rows of the test make that match a restriction.
	 *
	 * @param restriction The restriction call, e.g. {@code like( 'model', 'a\_b' )}.
	 *
	 * @return The count.
	 */
	private long countRestriction( String restriction ) {
		return number(
		    "c = entityCriteria( 'Vehicle' ).isEq( 'make', 'LikeEsc' ); result = c.add( c.restrictions." + restriction + " ).count();" );
	}

	@DisplayName( "like() treats a backslash as the escape character by default" )
	@Test
	public void testDefaultBackslashEscape() {
		// \% is a literal percent, so the look-alike 50X-off does not match
		assertThat( count( "like( 'model', '%50\\%-off%' )" ) ).isEqualTo( 1 );
		// \_ is a literal underscore, so aXb does not match
		assertThat( count( "like( 'model', 'a\\_b' )" ) ).isEqualTo( 1 );
		// \\ is a literal backslash: c:\d matches, c:\\d does not
		assertThat( count( "like( 'model', 'c:\\\\d' )" ) ).isEqualTo( 1 );
		assertThat( count( "like( 'model', 'c:%' )" ) ).isEqualTo( 2 );
	}

	@DisplayName( "ilike() treats a backslash as the escape character by default" )
	@Test
	public void testDefaultBackslashEscapeIlike() {
		assertThat( count( "ilike( 'model', '%50\\%-OFF%' )" ) ).isEqualTo( 1 );
		assertThat( count( "ilike( 'model', 'A\\_B' )" ) ).isEqualTo( 1 );
		assertThat( count( "ilike( 'model', 'C:\\\\D' )" ) ).isEqualTo( 1 );
	}

	@DisplayName( "restrictions.like() and restrictions.ilike() treat a backslash as the escape character by default" )
	@Test
	public void testDefaultBackslashEscapeRestrictions() {
		assertThat( countRestriction( "like( 'model', '%50\\%-off%' )" ) ).isEqualTo( 1 );
		assertThat( countRestriction( "like( 'model', 'a\\_b' )" ) ).isEqualTo( 1 );
		assertThat( countRestriction( "like( 'model', 'c:\\\\d' )" ) ).isEqualTo( 1 );
		assertThat( countRestriction( "ilike( 'model', 'A\\_B' )" ) ).isEqualTo( 1 );
	}

	@DisplayName( "where( property, 'like', value ) keeps the default escape too" )
	@Test
	public void testWhereLikeDefaultEscape() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'LikeEsc' ).where( 'model', 'like', 'a\\_b' ).count();" ) ).isEqualTo( 1 );
	}

	@DisplayName( "An explicit escape character is honoured, positionally and by name" )
	@Test
	public void testExplicitEscape() {
		assertThat( count( "like( 'model', '%50!%-off%', '!' )" ) ).isEqualTo( 1 );
		assertThat( count( "like( 'model', 'a!_b', '!' )" ) ).isEqualTo( 1 );
		assertThat( count( "like( 'model', 'Mixed!!Case', '!' )" ) ).isEqualTo( 1 );
		assertThat( count( "ilike( 'model', 'MIXED!!CASE', '!' )" ) ).isEqualTo( 1 );
		assertThat( count( "like( property = 'model', value = 'a!_b', escapeChar = '!' )" ) ).isEqualTo( 1 );
		assertThat( countRestriction( "like( 'model', '%50!%-off%', '!' )" ) ).isEqualTo( 1 );
		assertThat( countRestriction( "ilike( 'model', 'A!_B', '!' )" ) ).isEqualTo( 1 );
	}

	@DisplayName( "An explicit escape replaces the backslash default, so a backslash is then literal" )
	@Test
	public void testExplicitEscapeDisablesBackslash() {
		// with ! as the escape, a backslash is an ordinary character: c:\d matches c:\d
		assertThat( count( "like( 'model', 'c:\\d', '!' )" ) ).isEqualTo( 1 );
		// and \% is a backslash followed by a wildcard, so it matches nothing here
		assertThat( count( "like( 'model', '50\\%-off', '!' )" ) ).isEqualTo( 0 );
	}

	@DisplayName( "Without escapes the two-argument form still matches wildcards" )
	@Test
	public void testWildcardsStillWork() {
		assertThat( count( "like( 'model', '50%-off' )" ) ).isEqualTo( 2 );
		assertThat( count( "like( 'model', 'a_b' )" ) ).isEqualTo( 2 );
		assertThat( count( "like( 'model', '5_%' )" ) ).isEqualTo( 2 );
		assertThat( count( "ilike( 'model', 'A_B' )" ) ).isEqualTo( 2 );
		assertThat( countRestriction( "like( 'model', 'a_b' )" ) ).isEqualTo( 2 );
	}

	@DisplayName( "notLike() and the escape argument combine" )
	@Test
	public void testNotLikeEscape() {
		assertThat( count( "notLike( 'model', '%50!%-off%', '!' )" ) ).isEqualTo( 6 );
		assertThat( count( "notLike( 'model', 'a\\_b' )" ) ).isEqualTo( 6 );
	}

	@DisplayName( "getSQL() shows the escape clause on MySQL" )
	@Test
	public void testGetSql() {
		String	defaultSql	= ( String ) run( "result = entityCriteria( 'Vehicle' ).like( 'model', 'a\\_b' ).getSQL();" );
		String	explicitSql	= ( String ) run( "result = entityCriteria( 'Vehicle' ).like( 'model', 'a!_b', '!' ).getSQL();" );
		String	inlined		= ( String ) run( "result = entityCriteria( 'Vehicle' ).like( 'model', 'a\\_b' ).getSQL( true );" );
		assertThat( defaultSql.toLowerCase() ).contains( " like ? escape " );
		assertThat( defaultSql ).doesNotContain( "escape ''" );
		assertThat( explicitSql.toLowerCase() ).contains( " like ? escape " );
		assertThat( inlined ).contains( "'\\" );
	}

	@DisplayName( "An escape of more than one character is rejected" )
	@Test
	public void testInvalidEscape() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).like( 'model', 'a!_b', '!!' );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "single escape character" );
	}

	@DisplayName( "The default escape is a backslash exactly where Hibernate 7 renders escape '' and the database default is a backslash" )
	@Test
	public void testDefaultEscapeByDialect() {
		assertThat( CriteriaBuilder.defaultLikeEscape( new MySQLDialect() ) ).isEqualTo( "\\" );
		assertThat( CriteriaBuilder.defaultLikeEscape( new MariaDBDialect() ) ).isEqualTo( "\\" );
		assertThat( CriteriaBuilder.defaultLikeEscape( new PostgreSQLDialect() ) ).isEqualTo( "\\" );
		assertThat( CriteriaBuilder.defaultLikeEscape( new CockroachDialect() ) ).isEqualTo( "\\" );
		assertThat( CriteriaBuilder.defaultLikeEscape( new H2Dialect() ) ).isEqualTo( "\\" );
		assertThat( CriteriaBuilder.defaultLikeEscape( new OracleDialect() ) ).isNull();
		assertThat( CriteriaBuilder.defaultLikeEscape( new SQLServerDialect() ) ).isNull();
	}
}
