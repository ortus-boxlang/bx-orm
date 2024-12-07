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
package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.DatabaseException;
import tools.BaseORMTest;

public class ORMGetSessionFactoryTest extends BaseORMTest {

	@DisplayName( "It can get the default ORM session factory" )
	@Test
	public void testORMGetSessionFactory() {
		// @formatter:off
		instance.executeSource( """
			result = ormGetSessionFactory();
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();

		// Classloader hell means we can't compare the two factories directly
		// assertInstanceOf( SessionFactoryImpl.class, variables.get( result ) );
	}

	@DisplayName( "It throws if the named datasource does not exist" )
	@Test
	public void testBadDSN() {
		assertThrows(
		    DatabaseException.class,
		    () -> instance.executeSource( "result = ormGetSessionFactory( 'nonexistentDSN' ) ", context )
		);
	}

	@DisplayName( "It can get the session factory from a named datasource" )
	@Test
	public void testNamedDatasource() {
		// @formatter:off
		instance.executeSource( """
			defaultFactory       = ormGetSessionFactory();
			namedFactory         = ormGetSessionFactory( 'dsn2' );
			isSameSessionFactory = defaultFactory == namedFactory;
		""", context );
		// @formatter:on

		instance.executeSource( "result = ormGetSessionFactory( 'dsn2' )", context );

		assertThat( variables.get( Key.of( "defaultFactory" ) ) ).isNotNull();
		assertThat( variables.get( Key.of( "namedFactory" ) ) ).isNotNull();

		// Classloader hell means we can't compare the two factories directly
		// assertInstanceOf( SessionFactoryImpl.class, variables.get( Key.of( "defaultFactory" ) ) );
		// assertInstanceOf( SessionFactoryImpl.class, variables.get( Key.of( "namedFactory" ) ) );

		assertThat( variables.getAsBoolean( Key.of( "isSameSessionFactory" ) ) ).isFalse();
	}
}