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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityEventsTest extends BaseORMTest {

	@DisplayName( "It fires preLoad,postLoad" )
	@Test
	public void testEntityLoadEvents() {

		// @formatter:off
		instance.executeSource(
			"""
				loadEntity = entityLoadByPK( "Vehicle", '1HGCM82633A123456' );
				entityReload( loadEntity );
				loadEvents = loadEntity.getEventLog();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "loadEvents" ) );
		Array eventLog = variables.getAsArray( Key.of( "loadEvents" ) );
		assertThat( eventLog.toList() ).containsExactly( "preLoad", "postLoad", "preLoad", "postLoad" );
	}

	@DisplayName( "It fires preInsert,postInsert" )
	@Test
	public void testEntityInsertEvents() {

		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				insertEntity = entityNew( "Vehicle", {
					vin : "1HGCM82633A654321",
					make : "Toyota",
					model : "Tacoma"
				} );
				entitySave( insertEntity );
			}
			result = insertEntity.getEventLog();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preInsert", "postInsert" );
	}

	@DisplayName( "It fires preUpdate,postUpdate" )
	@Test
	public void testEntityUpdateEvents() {

		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				updateEntity = entityLoadByPK( "Vehicle", '1HGCM82633A123456' );
				updateEntity.setModel( "Civic" );
				entitySave( updateEntity );
			}
			result = updateEntity.getEventLog();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preLoad", "postLoad", "preUpdate", "postUpdate" );
	}

	@DisplayName( "It fires preDelete,postDelete" )
	@Test
	public void testEntityDeleteEvents() {

		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				deleteEntity = entityLoadByPK( "Vehicle", '1HGCM82633A123456' );
				entityDelete( deleteEntity );
			}
			result = deleteEntity.getEventLog();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preLoad", "postLoad", "preDelete", "postDelete" );
	}
}
