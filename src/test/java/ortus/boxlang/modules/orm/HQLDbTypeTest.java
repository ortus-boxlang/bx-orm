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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Query;
import tools.BaseORMTest;

/**
 * BLMODULES-306: {@code <cfquery dbtype="hql">} and {@code queryExecute( hql, params, { dbtype : "hql" } )} run HQL and
 * return the same array as {@code ormExecuteQuery()}.
 */
@DisplayName( "dbtype=\"hql\" queries" )
public class HQLDbTypeTest extends BaseORMTest {

	@DisplayName( "cfquery dbtype=hql returns an array of entities" )
	@Test
	public void testCFQueryReturnsArray() {
		// @formatter:off
		instance.executeSource( """
		<cfquery name="result" dbtype="hql">
			FROM Vehicle ORDER BY model
		</cfquery>
		""", context, BoxSourceType.CFTEMPLATE );
		// @formatter:on
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );
		Array vehicles = variables.getAsArray( result );
		assertThat( vehicles.size() ).isEqualTo( 5 );
		assertThat( vehicles.get( 0 ) ).isInstanceOf( IClassRunnable.class );
		assertThat( ( ( IClassRunnable ) vehicles.get( 0 ) ).get( Key.of( "model" ) ) ).isEqualTo( "Accord" );
	}

	@DisplayName( "cfqueryparam binds positional parameters, typed or not" )
	@Test
	public void testCFQueryParams() {
		// @formatter:off
		instance.executeSource( """
		<cfset make = "Honda">
		<cfquery name="result" dbtype="hql">
			FROM Vehicle
			WHERE make = <cfqueryparam value="#make#" cfsqltype="cf_sql_varchar">
			AND model IN ( <cfqueryparam value="Civic,Accord" list="true"> )
		</cfquery>
		<cfquery name="byManufacturer" dbtype="hql">
			SELECT v FROM Vehicle v WHERE v.manufacturer.id = <cfqueryparam value="42" cfsqltype="cf_sql_integer">
		</cfquery>
		""", context, BoxSourceType.CFTEMPLATE );
		// @formatter:on
		assertThat( variables.getAsArray( result ).size() ).isEqualTo( 2 );
		assertThat( variables.getAsArray( Key.of( "byManufacturer" ) ).size() ).isEqualTo( 3 );
	}

	@DisplayName( "a projection returns an array of rows, and ormoptions and result apply" )
	@Test
	public void testProjectionOptionsAndResult() {
		// @formatter:off
		instance.executeSource( """
		<cfquery name="result" dbtype="hql" ormoptions="#{ maxResults : 2 }#" result="info">
			SELECT v.make, v.model FROM Vehicle v ORDER BY v.model
		</cfquery>
		<cfset firstModel = result[ 1 ][ 2 ]>
		<cfset sameAsORM = ormExecuteQuery( "SELECT v.make, v.model FROM Vehicle v ORDER BY v.model", [], false, { maxResults : 2 } )[ 1 ][ 2 ]>
		""", context, BoxSourceType.CFTEMPLATE );
		// @formatter:on
		Array rows = variables.getAsArray( result );
		assertThat( rows.size() ).isEqualTo( 2 );
		assertThat( variables.get( Key.of( "firstModel" ) ) ).isEqualTo( "Accord" );
		assertThat( variables.get( Key.of( "sameAsORM" ) ) ).isEqualTo( "Accord" );
		IStruct info = variables.getAsStruct( Key.of( "info" ) );
		assertThat( info.get( Key.recordCount ) ).isEqualTo( 2 );
		assertThat( info.getAsString( Key.sql ) ).contains( "FROM Vehicle" );
	}

	@DisplayName( "bx:query dbtype=hql in script returns an array" )
	@Test
	public void testBxQueryScript() {
		// @formatter:off
		instance.executeSource( """
		bx:query name="result" dbtype="hql" {
			echo( "FROM Vehicle WHERE make = " );
			bx:queryparam value="Ford";
		}
		""", context );
		// @formatter:on
		assertThat( variables.getAsArray( result ).size() ).isEqualTo( 1 );
	}

	@DisplayName( "queryExecute with dbtype hql returns the same array as ormExecuteQuery" )
	@Test
	public void testQueryExecute() {
		// @formatter:off
		instance.executeSource( """
		result    = queryExecute( "FROM Vehicle WHERE make = :make", { make : "Honda" }, { dbtype : "hql" } );
		expected  = ormExecuteQuery( "FROM Vehicle WHERE make = :make", { make : "Honda" } );
		positional = queryExecute( "FROM Vehicle WHERE make = ?", [ { value : "Ford", cfsqltype : "varchar" } ], { dbtype : "hql", result : "info" } );
		limited   = queryExecute( "FROM Vehicle ORDER BY model", [], { dbtype : "hql", maxrows : 3 } );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );
		assertThat( variables.getAsArray( result ).size() ).isEqualTo( variables.getAsArray( Key.of( "expected" ) ).size() );
		assertThat( variables.getAsArray( result ).size() ).isEqualTo( 3 );
		assertThat( variables.getAsArray( Key.of( "positional" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsStruct( Key.of( "info" ) ).get( Key.recordCount ) ).isEqualTo( 1 );
		assertThat( variables.getAsArray( Key.of( "limited" ) ).size() ).isEqualTo( 3 );
	}

	@DisplayName( "queries without dbtype hql still return a query" )
	@Test
	public void testSQLUnchanged() {
		// @formatter:off
		instance.executeSource( """
		result = queryExecute( "SELECT vin FROM vehicles" );
		""", context );
		instance.executeSource( """
		<cfquery name="tagResult">
			SELECT vin FROM vehicles WHERE make = <cfqueryparam value="Honda">
		</cfquery>
		""", context, BoxSourceType.CFTEMPLATE );
		// @formatter:on
		assertThat( variables.get( result ) ).isInstanceOf( Query.class );
		assertThat( ( ( Query ) variables.get( result ) ).size() ).isEqualTo( 5 );
		assertThat( variables.get( Key.of( "tagResult" ) ) ).isInstanceOf( Query.class );
		assertThat( ( ( Query ) variables.get( Key.of( "tagResult" ) ) ).size() ).isEqualTo( 3 );
	}
}
