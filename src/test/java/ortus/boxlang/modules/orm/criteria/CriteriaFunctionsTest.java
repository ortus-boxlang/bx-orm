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
import ortus.boxlang.runtime.types.IStruct;

/**
 * Functions in criteria paths (HQL built-ins and the application's named sqlFunctions) and ormGetSQLFunctions().
 * The test app registers nameLen (char_length, integer) and shout (upper).
 */
public class CriteriaFunctionsTest extends CriteriaTestSupport {

	/**
	 * Test: HQL built-in functions work in conditions, including nested calls, literals and cast.
	 */
	@DisplayName( "HQL functions work in condition paths, nested, with literals and cast" )
	@Test
	public void testBuiltInFunctions() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isEq( 'lower(name)', 'ford motor company' ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isEq( 'upper(substring(name, 1, 4))', 'FORD' ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isEq( 'cast(id as String)', '42' ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isEq( 'coalesce(address, ''none'')', 'none' ).count();" ) ).isEqualTo( 0 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'lower(manufacturer.name)', 'honda motor co.' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: functions work in order() and projections.
	 */
	@DisplayName( "Functions work in order() and projections" )
	@Test
	public void testFunctionsInOrderAndProjections() {
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).order( 'length(name) desc' ).list().map( ( m ) => m.getId() ).toList();" ) )
		    .isEqualTo( "77,1,42" );
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).isEq( 'id', 1 ).pluck( 'upper(name)' )[ 1 ];" ) ).isEqualTo( "FORD MOTOR COMPANY" );
	}

	/**
	 * Test: the application's named SQL functions work in criteria and HQL, and ormGetSQLFunctions() lists them.
	 */
	@DisplayName( "Named sqlFunctions work in criteria and HQL; ormGetSQLFunctions() lists them" )
	@Test
	public void testNamedSqlFunctions() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isEq( 'nameLen(name)', 18 ).count();" ) ).isEqualTo( 1 );
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).isEq( 'id', 42 ).pluck( 'shout(name)' )[ 1 ];" ) ).isEqualTo( "HONDA MOTOR CO." );
		assertThat( number( "result = ormExecuteQuery( 'select nameLen(m.name) from Manufacturer m where m.id = 1', [], true );" ) ).isEqualTo( 18 );
		IStruct functions = ( IStruct ) run( "result = ormGetSQLFunctions();" );
		assertThat( functions.getAsStruct( Key.of( "nameLen" ) ).getAsString( Key.of( "sql" ) ) ).isEqualTo( "char_length(?1)" );
		assertThat( functions.getAsStruct( Key.of( "nameLen" ) ).getAsString( Key.of( "returns" ) ) ).isEqualTo( "integer" );
		assertThat( functions.getAsStruct( Key.of( "shout" ) ).getAsString( Key.of( "returns" ) ) ).isEqualTo( "" );
	}

	/**
	 * Test: bad function paths are clear errors.
	 */
	@DisplayName( "Bad function paths are clear errors" )
	@Test
	public void testFunctionErrors() {
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).isEq( 'lower(nmae)', 'x' );" ) ) ).isEqualTo( "orm.property.unknown" );
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).isEq( 'lower(name; drop table x)', 'x' );" ) ) ).isEqualTo( "orm.argument" );
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).isEq( 'lower(upper(name)', 'x' );" ) ) ).isEqualTo( "orm.argument" );
		// Hibernate passes an unknown function through to SQL, so the database rejects it.
		assertThat( type( error( "entityCriteria( 'Manufacturer' ).isEq( 'nosuchfn(name)', 'x' ).count();" ) ) ).isEqualTo( "orm.sql" );
	}
}
