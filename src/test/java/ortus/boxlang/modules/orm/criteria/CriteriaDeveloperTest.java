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

import ortus.boxlang.runtime.events.IInterceptorLambda;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Live tests for the developer tools of {@code entityCriteria()}: {@code toString()}/dump, {@code getHQL()},
 * {@code getSQL()}, {@code peekSQL()}, {@code logSQL()}, the flow helpers ({@code when}, {@code unless},
 * {@code apply}, {@code peek}), query options and the cborm interception points.
 */
public class CriteriaDeveloperTest extends CriteriaTestSupport {

	/**
	 * Test: toString shows the steps, HQL, params and SQL.
	 */
	@DisplayName( "toString() shows the calls, the HQL, the params and the SQL" )
	@Test
	public void testToString() {
		String text = ( String ) run( """
		                              result = entityCriteria( 'Vehicle' )
		                                  .isEq( 'make', 'Honda' )
		                                  .anyOf( ( c ) => c.isEq( 'model', 'Civic' ).like( 'model', 'Acc%' ) )
		                                  .order( 'model' )
		                                  .toString();
		                              """ );
		assertThat( text ).contains( "entityCriteria( \"Vehicle\" )" );
		assertThat( text ).contains( ".isEq( \"make\", \"Honda\" )" );
		assertThat( text ).contains( ".anyOf(" );
		assertThat( text ).contains( "      .like( \"model\", \"Acc%\" )" );
		assertThat( text ).contains( "HQL: select bx_this from Vehicle bx_this where" );
		assertThat( text ).contains( "Params: [\"Honda\", \"Civic\", \"Acc%\"]" );
		assertThat( text ).contains( "SQL: select" );
		assertThat( text ).contains( "from vehicles" );
	}

	/**
	 * Test: writeDump of a criteria does not fail and shows the description.
	 */
	@DisplayName( "writeDump( criteria ) shows the readable description" )
	@Test
	public void testDump() {
		String html = ( String ) run( """
		                              c = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' );
		                              bx:savecontent variable="result" { writeDump( var = c, format = "html", output = "buffer" ); }
		                              """ );
		assertThat( html ).contains( "isEq( &quot;make&quot;, &quot;Honda&quot; )" );
	}

	/**
	 * Test: getHQL.
	 */
	@DisplayName( "getHQL() returns the HQL" )
	@Test
	public void testGetHQL() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).getHQL();" ) )
		    .isEqualTo( "select bx_this from Vehicle bx_this where bx_this.make = ?1" );
	}

	/**
	 * Test: getSQL keeps placeholders by default and inlines values on request.
	 */
	@DisplayName( "getSQL() shows placeholders; getSQL( true ) inlines the values" )
	@Test
	public void testGetSQL() {
		String sql = ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).isGt( 'manufacturer.id', 1 ).getSQL();" );
		assertThat( sql ).contains( "vehicles" );
		assertThat( sql ).contains( "make=?" );
		assertThat( sql ).contains( "\nwhere" );
		String executable = ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'O''Brien' ).isGt( 'manufacturer.id', 1 ).getSQL( true );" );
		assertThat( executable ).contains( "make='O''Brien'" );
		assertThat( executable ).contains( ">1" );
	}

	/**
	 * Test: getSQL without formatting is one line.
	 */
	@DisplayName( "getSQL( format = false ) returns one line" )
	@Test
	public void testGetSQLUnformatted() {
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).getSQL( format = false );" ) ).doesNotContain( "\n" );
	}

	/**
	 * Test: getSQL runs no query and flushes nothing.
	 */
	@DisplayName( "getSQL() does not run the query or flush pending changes" )
	@Test
	public void testGetSQLRunsNothing() {
		assertThat( run( """
		                 transaction {
		                     try {
		                         v = entityLoadByPK( 'Vehicle', '0SB123' );
		                         v.setModel( 'Changed' );
		                         entityCriteria( 'Vehicle' ).isEq( 'model', 'Changed' ).getSQL();
		                         result = ormIsSessionDirty();
		                     } finally {
		                         transactionRollback();
		                     }
		                 }
		                 """ ) ).isEqualTo( true );
	}

	/**
	 * Test: getSQL with an entity value inlines its id.
	 */
	@DisplayName( "getSQL( true ) inlines an entity value as its id" )
	@Test
	public void testGetSQLEntity() {
		assertThat(
		    ( String ) run( "m = entityLoadByPK( 'Manufacturer', 42 ); result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer', m ).getSQL( true );" ) )
		    .contains( "42" );
	}

	/**
	 * Test: peekSQL hands the SQL to a closure and keeps chaining.
	 */
	@DisplayName( "peekSQL( closure ) receives the SQL and returns the builder" )
	@Test
	public void testPeekSQL() {
		assertThat( run( """
		                 seen = '';
		                 n = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).peekSQL( ( sql ) => seen = sql ).count();
		                 result = n & ':' & ( seen contains 'vehicles' );
		                 """ ) ).isEqualTo( "3:true" );
	}

	/**
	 * Test: logSQL returns the builder.
	 */
	@DisplayName( "logSQL( label ) logs and returns the builder" )
	@Test
	public void testLogSQL() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).logSQL( 'hondas' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: when applies on true, and the else branch on false.
	 */
	@DisplayName( "when( test, closure, else ) applies one branch" )
	@Test
	public void testWhen() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).when( true, ( c ) => c.isEq( 'make', 'Honda' ) ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).when( false, ( c ) => c.isEq( 'make', 'Honda' ) ).count();" ) ).isEqualTo( 5 );
		assertThat(
		    number( "result = entityCriteria( 'Vehicle' ).when( false, ( c ) => c.isEq( 'make', 'Honda' ), ( c ) => c.isEq( 'make', 'Ford' ) ).count();" ) )
		    .isEqualTo( 1 );
		assertThat( number( "q = ''; result = entityCriteria( 'Vehicle' ).when( ( c ) => len( q ), ( c ) => c.isEq( 'make', q ) ).count();" ) ).isEqualTo( 5 );
	}

	/**
	 * Test: unless is the opposite of when.
	 */
	@DisplayName( "unless( test, closure ) applies when the test is false" )
	@Test
	public void testUnless() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).unless( false, ( c ) => c.isEq( 'make', 'Honda' ) ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).unless( true, ( c ) => c.isEq( 'make', 'Honda' ) ).count();" ) ).isEqualTo( 5 );
	}

	/**
	 * Test: apply reuses a closure.
	 */
	@DisplayName( "apply( closure ) reuses a query fragment" )
	@Test
	public void testApply() {
		assertThat( number( """
		                    hondas = ( c ) => c.isEq( 'make', 'Honda' );
		                    result = entityCriteria( 'Vehicle' ).apply( hondas ).like( 'model', 'C%' ).count();
		                    """ ) ).isEqualTo( 1 );
	}

	/**
	 * Test: peek sees the builder mid-chain.
	 */
	@DisplayName( "peek( closure ) sees the builder and keeps chaining" )
	@Test
	public void testPeek() {
		assertThat( run( """
		                 seen = 0;
		                 n = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).peek( ( c ) => seen = c.count() ).isEq( 'model', 'Civic' ).count();
		                 result = seen & ':' & n;
		                 """ ) ).isEqualTo( "3:1" );
	}

	/**
	 * Test: the query options run.
	 */
	@DisplayName( "cache / readOnly / timeout / fetchSize / comment / queryHint options run" )
	@Test
	public void testOptions() {
		assertThat( number( """
		                    result = entityCriteria( 'Vehicle' )
		                        .isEq( 'make', 'Honda' )
		                        .cache( 'criteria_vehicles' )
		                        .readOnly()
		                        .timeout( 5 )
		                        .fetchSize( 50 )
		                        .comment( 'criteria test' )
		                        .queryHint( 'org.hibernate.readOnly', true )
		                        .list().len();
		                    """ ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).cache( true, 'criteria_vehicles2' ).list().len();" ) ).isEqualTo( 5 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).cacheable().list().len();" ) ).isEqualTo( 5 );
	}

	/**
	 * Test: readOnly entities are not saved.
	 */
	@DisplayName( "readOnly() loads entities whose changes are not saved" )
	@Test
	public void testReadOnly() {
		assertThat( run( """
		                 transaction {
		                     try {
		                         v = entityCriteria( 'Vehicle' ).idEq( '0SB123' ).readOnly().get();
		                         v.setModel( 'Changed' );
		                         ormFlush();
		                         result = queryExecute( "select model from vehicles where vin = '0SB123'" ).model;
		                     } finally {
		                         transactionRollback();
		                     }
		                 }
		                 """ ) ).isEqualTo( "Studious" );
	}

	/**
	 * Test: properties and assignment.
	 */
	@DisplayName( "Unknown properties and assignments are orm.argument errors" )
	@Test
	public void testProperties() {
		assertThat( type( error( "x = entityCriteria( 'Vehicle' ).nope;" ) ) ).isEqualTo( "orm.argument" );
		assertThat( run( "result = entityCriteria( 'Vehicle' ).RIGHT_JOIN;" ).toString() ).isEqualTo( "2" );
	}

	/**
	 * Test: the cborm interception points fire.
	 */
	@DisplayName( "before/after list, count and get and onCriteriaBuilderAddition interception points fire" )
	@Test
	public void testInterceptionPoints() {
		java.util.Map<String, Integer>		fired		= new java.util.concurrent.ConcurrentHashMap<>();
		java.util.List<IInterceptorLambda>	listeners	= new java.util.ArrayList<>();
		var									service		= instance.getInterceptorService();
		for ( String point : java.util.List.of( "beforeCriteriaBuilderList", "afterCriteriaBuilderList", "beforeCriteriaBuilderCount",
		    "afterCriteriaBuilderCount", "beforeCriteriaBuilderGet", "afterCriteriaBuilderGet", "onCriteriaBuilderAddition" ) ) {
			IInterceptorLambda listener = data -> {
				assertThat( data.get( Key.of( "criteriaBuilder" ) ) ).isNotNull();
				fired.merge( point, 1, Integer::sum );
				return false;
			};
			listeners.add( listener );
			service.register( listener, Key.of( point ) );
		}
		try {
			run( """
			     c = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).like( 'model', '%' );
			     c.list();
			     c.count();
			     entityCriteria( 'Vehicle' ).idEq( '0SB123' ).get();
			     result = true;
			     """ );
		} finally {
			listeners.forEach( service::unregister );
		}
		assertThat( fired.get( "beforeCriteriaBuilderList" ) ).isEqualTo( 1 );
		assertThat( fired.get( "afterCriteriaBuilderList" ) ).isEqualTo( 1 );
		assertThat( fired.get( "beforeCriteriaBuilderCount" ) ).isEqualTo( 1 );
		assertThat( fired.get( "afterCriteriaBuilderCount" ) ).isEqualTo( 1 );
		assertThat( fired.get( "beforeCriteriaBuilderGet" ) ).isEqualTo( 1 );
		assertThat( fired.get( "afterCriteriaBuilderGet" ) ).isEqualTo( 1 );
		assertThat( fired.get( "onCriteriaBuilderAddition" ) ).isEqualTo( 3 );
	}
}
