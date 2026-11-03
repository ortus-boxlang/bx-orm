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

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import tools.BaseORMTest;

/**
 * BLMODULES-307: a string or decimal value for a typed property binds to the property's type, so a form or URL id
 * ({@code "1"}) works for an integer property. Hibernate 7 converts these values itself; these tests keep it that way.
 */
@DisplayName( "Parameter and filter values are converted to the mapped type" )
public class ParameterCoercionTest extends BaseORMTest {

	@DisplayName( "entityLoad converts a string id in a filter" )
	@Test
	public void testEntityLoadFilter() {
		instance.executeSource( """
		                        result = entityLoad( "Manufacturer", { id : "1" }, true ).getName();
		                        decimal = entityLoad( "Manufacturer", { id : 1.0 }, true ).getName();
		                        """, context );
		assertThat( variables.getAsString( result ) ).isEqualTo( "Ford Motor Company" );
		assertThat( variables.getAsString( ortus.boxlang.runtime.scopes.Key.of( "decimal" ) ) ).isEqualTo( "Ford Motor Company" );
	}

	@DisplayName( "ormExecuteQuery converts named, positional and list parameters" )
	@Test
	public void testORMExecuteQueryParams() {
		instance.executeSource( """
		                        named      = ormExecuteQuery( "FROM Manufacturer WHERE id = :id", { id : "1" } ).len();
		                        positional = ormExecuteQuery( "FROM Manufacturer WHERE id = ?", [ "1" ] ).len();
		                        list       = ormExecuteQuery( "FROM Manufacturer WHERE id IN (:ids)", { ids : [ "1", "42" ] } ).len();
		                        decimal    = ormExecuteQuery( "FROM Manufacturer WHERE id = :id", { id : 0.5 + 0.5 } ).len();
		                        """, context );
		assertThat( variables.get( ortus.boxlang.runtime.scopes.Key.of( "named" ) ) ).isEqualTo( 1 );
		assertThat( variables.get( ortus.boxlang.runtime.scopes.Key.of( "positional" ) ) ).isEqualTo( 1 );
		assertThat( variables.get( ortus.boxlang.runtime.scopes.Key.of( "list" ) ) ).isEqualTo( 2 );
		assertThat( variables.get( ortus.boxlang.runtime.scopes.Key.of( "decimal" ) ) ).isEqualTo( 1 );
	}

	@DisplayName( "an untyped cfqueryparam in a dbtype=hql query is converted" )
	@Test
	public void testUntypedQueryParam() {
		// @formatter:off
		instance.executeSource( """
		<cfset id = "1">
		<cfquery name="result" dbtype="hql">
			SELECT m.name FROM Manufacturer m WHERE m.id = <cfqueryparam value="#id#">
		</cfquery>
		""", context, BoxSourceType.CFTEMPLATE );
		// @formatter:on
		assertThat( variables.getAsArray( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsArray( result ).get( 0 ) ).isEqualTo( "Ford Motor Company" );
	}

	@DisplayName( "a value that cannot be converted fails with an orm.query.parameter error" )
	@Test
	public void testUnconvertibleValue() {
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class,
		    () -> instance.executeSource( """
		                                  result = ormExecuteQuery( "FROM Manufacturer WHERE id = :id", { id : "abc" } );
		                                  """, context ) );
		assertThat( e.getType() ).isEqualTo( "orm.query.parameter" );
	}
}
