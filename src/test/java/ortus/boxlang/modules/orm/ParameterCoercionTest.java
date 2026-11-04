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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.modules.orm.hibernate.ParameterCoercer;
import tools.BaseORMTest;

/**
 * BLMODULES-307: a string or decimal value for a typed property is converted to the property's type before Hibernate
 * binds it, so a form or URL id ({@code "1"}) works for an integer property.
 */
@DisplayName( "Parameter and filter values are converted to the mapped type" )
public class ParameterCoercionTest extends BaseORMTest {

	@DisplayName( "ParameterCoercer converts numbers, booleans and strings" )
	@Test
	public void testCoercer() {
		assertThat( ParameterCoercer.coerce( "1", Integer.class ) ).isEqualTo( 1 );
		assertThat( ParameterCoercer.coerce( " 1 ", Integer.class ) ).isEqualTo( 1 );
		assertThat( ParameterCoercer.coerce( "1.0", Integer.class ) ).isEqualTo( 1 );
		assertThat( ParameterCoercer.coerce( new BigDecimal( "1.0" ), Integer.class ) ).isEqualTo( 1 );
		assertThat( ParameterCoercer.coerce( 1.0d, Integer.class ) ).isEqualTo( 1 );
		assertThat( ParameterCoercer.coerce( "12345678901", Long.class ) ).isEqualTo( 12345678901L );
		assertThat( ParameterCoercer.coerce( 7, Short.class ) ).isEqualTo( ( short ) 7 );
		assertThat( ParameterCoercer.coerce( "9", BigInteger.class ) ).isEqualTo( BigInteger.valueOf( 9 ) );
		assertThat( ParameterCoercer.coerce( "2.50", BigDecimal.class ) ).isEqualTo( new BigDecimal( "2.50" ) );
		assertThat( ParameterCoercer.coerce( "2.5", Double.class ) ).isEqualTo( 2.5d );
		assertThat( ParameterCoercer.coerce( 3, Float.class ) ).isEqualTo( 3.0f );
		assertThat( ParameterCoercer.coerce( "yes", Boolean.class ) ).isEqualTo( true );
		assertThat( ParameterCoercer.coerce( "false", Boolean.class ) ).isEqualTo( false );
		assertThat( ParameterCoercer.coerce( 0, Boolean.class ) ).isEqualTo( false );
		assertThat( ParameterCoercer.coerce( 5, String.class ) ).isEqualTo( "5" );
	}

	@DisplayName( "ParameterCoercer leaves values it cannot convert unchanged" )
	@Test
	public void testCoercerPassThrough() {
		// A fraction is never truncated for an integer type.
		assertThat( ParameterCoercer.coerce( "1.5", Integer.class ) ).isEqualTo( "1.5" );
		assertThat( ParameterCoercer.coerce( "abc", Integer.class ) ).isEqualTo( "abc" );
		assertThat( ParameterCoercer.coerce( "", Integer.class ) ).isEqualTo( "" );
		// Out of range for an int.
		assertThat( ParameterCoercer.coerce( "12345678901", Integer.class ) ).isEqualTo( "12345678901" );
		assertThat( ParameterCoercer.coerce( "maybe", Boolean.class ) ).isEqualTo( "maybe" );
		assertThat( ParameterCoercer.coerce( null, Integer.class ) ).isNull();
		Object unknown = new Object();
		assertThat( ParameterCoercer.coerce( unknown, java.util.Date.class ) ).isSameInstanceAs( unknown );
		assertThat( ParameterCoercer.coerce( "1", ( Class<?> ) null ) ).isEqualTo( "1" );
	}

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

	@DisplayName( "a value that cannot be converted still fails in Hibernate" )
	@Test
	public void testUnconvertibleValue() {
		assertThrows( Throwable.class, () -> instance.executeSource( """
		                                                             result = ormExecuteQuery( "FROM Manufacturer WHERE id = :id", { id : "abc" } );
		                                                             """, context ) );
	}
}
