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

import java.util.List;

import org.junit.jupiter.api.AfterEach;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import tools.BaseORMTest;

/**
 * Shared helpers for the live {@code entityCriteria()} tests. The seed data (see {@code JDBCTestUtils}) has five
 * vehicles (Honda Accord, Civic and Ridgeline by manufacturer 42, Ford Fusion by manufacturer 1, and a Studebaker with
 * no manufacturer) and three manufacturers (1 Ford Motor Company, 42 Honda Motor Co., 77 General Moters Corporation
 * with no vehicles).
 */
public abstract class CriteriaTestSupport extends BaseORMTest {

	/**
	 * Clear the ORM session after each test so no entity leaks into the next one.
	 */
	@AfterEach
	public void clearCriteriaSession() {
		instance.executeSource( "try { ormClearSession(); } catch ( any e ) {}", context );
	}

	/**
	 * Run BoxLang code and return the value it stores in {@code result}.
	 *
	 * @param code BoxLang statements that set {@code result}.
	 *
	 * @return The value of {@code result}.
	 */
	protected Object run( String code ) {
		instance.executeSource( code, context );
		return variables.get( result );
	}

	/**
	 * Run BoxLang code that sets {@code result} to an array and return it as a Java list.
	 *
	 * @param code BoxLang statements.
	 *
	 * @return The list.
	 */
	protected List<Object> list( String code ) {
		return ( ( Array ) run( code ) ).toList();
	}

	/**
	 * Run BoxLang code that sets {@code result} to a number and return it as a long.
	 *
	 * @param code BoxLang statements.
	 *
	 * @return The number.
	 */
	protected long number( String code ) {
		return Long.parseLong( String.valueOf( run( code ) ).replaceAll( "\\.0+$", "" ) );
	}

	/**
	 * Run BoxLang code that must throw, and return { type, message, detail }.
	 *
	 * @param code BoxLang statements.
	 *
	 * @return The error's type, message and detail, or { type : "NO ERROR" }.
	 */
	protected IStruct error( String code ) {
		instance.executeSource(
		    "try { " + code
		        + " result = { type : \"NO ERROR\", message : \"\", detail : \"\" }; } catch ( any e ) { result = { type : e.type, message : e.message, detail : e.detail }; }",
		    context );
		return variables.getAsStruct( result );
	}

	/**
	 * The type of an error.
	 *
	 * @param err The error struct from {@link #error(String)}.
	 *
	 * @return The type.
	 */
	protected static String type( IStruct err ) {
		return err.getAsString( Key.type );
	}

	/**
	 * The message of an error.
	 *
	 * @param err The error struct from {@link #error(String)}.
	 *
	 * @return The message.
	 */
	protected static String message( IStruct err ) {
		return err.getAsString( Key.message );
	}
}
