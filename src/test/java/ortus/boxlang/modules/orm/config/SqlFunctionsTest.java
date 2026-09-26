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
package ortus.boxlang.modules.orm.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.types.Struct;

/**
 * Parsing and validation of the sqlFunctions ORM setting.
 */
public class SqlFunctionsTest {

	/**
	 * A string template and a { sql, returns } struct both parse.
	 */
	@DisplayName( "sqlFunctions parses string templates and { sql, returns } structs" )
	@Test
	public void testParse() {
		SqlFunctions functions = SqlFunctions.parse( Struct.of( "a", "upper(?1)", "b", Struct.of( "sql", "len(?1)", "returns", "Integer" ) ) );
		assertThat( functions.all().get( "a" ).returns() ).isNull();
		assertThat( functions.all().get( "b" ).returns() ).isEqualTo( "integer" );
		assertThat( SqlFunctions.parse( null ).isEmpty() ).isTrue();
	}

	/**
	 * Bad names, missing SQL, unknown return types and non-struct settings are orm.config errors.
	 */
	@DisplayName( "sqlFunctions rejects bad names, missing SQL and unknown return types" )
	@Test
	public void testErrors() {
		assertThat( assertThrows( ORMException.class, () -> SqlFunctions.parse( Struct.of( "bad-name", "x(?1)" ) ) ).getType() ).isEqualTo( "orm.config" );
		assertThat( assertThrows( ORMException.class, () -> SqlFunctions.parse( Struct.of( "a", "" ) ) ).getMessage() ).contains( "has no SQL" );
		assertThat( assertThrows( ORMException.class, () -> SqlFunctions.parse( Struct.of( "a", Struct.of( "sql", "x(?1)", "returns", "blob" ) ) ) )
		    .getMessage() ).contains( "not a known type" );
		assertThat( assertThrows( ORMException.class, () -> SqlFunctions.parse( "upper(?1)" ) ).getType() ).isEqualTo( "orm.config" );
	}
}
