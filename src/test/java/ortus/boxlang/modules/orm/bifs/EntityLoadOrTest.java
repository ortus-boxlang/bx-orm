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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * entityLoadOrNew(), entityLoadOrSave(), entityLoadOrFail() and entityLoadByPKOrFail().
 */
public class EntityLoadOrTest extends BaseORMTest {

	/**
	 * A found entity is returned as is, by id or by filter, and properties are not applied to it.
	 */
	@DisplayName( "entityLoadOrNew returns the existing entity by id or filter, untouched" )
	@Test
	public void testLoadOrNewFound() {
		// @formatter:off
		instance.executeSource(
			"""
			byId     = entityLoadOrNew( "Manufacturer", 42, { name : "ignored" } ).getName();
			byFilter = entityLoadOrNew( "Manufacturer", { name : "Ford Motor Company" }, { address : "ignored" } ).getAddress();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "byId" ) ) ).isEqualTo( "Honda Motor Co." );
		assertThat( variables.getAsString( Key.of( "byFilter" ) ) ).isEqualTo( "202 Ford Way, Dearborn MI" );
	}

	/**
	 * A missing filter gives a new, unsaved entity filled from the filter and the properties.
	 */
	@DisplayName( "entityLoadOrNew builds an unsaved entity from the filter and properties" )
	@Test
	public void testLoadOrNewMissingFilter() {
		// @formatter:off
		instance.executeSource(
			"""
			m = entityLoadOrNew( "Manufacturer", { name : "Tesla" }, { address : "Austin TX" } );
			name    = m.getName();
			address = m.getAddress();
			transaction {}
			rows = queryExecute( "SELECT id FROM manufacturers WHERE name = 'Tesla'" ).recordCount;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "name" ) ) ).isEqualTo( "Tesla" );
		assertThat( variables.getAsString( Key.of( "address" ) ) ).isEqualTo( "Austin TX" );
		assertThat( variables.get( Key.of( "rows" ) ) ).isEqualTo( 0 );
	}

	/**
	 * A missing id is copied onto the new entity only when the id is assigned by the application.
	 */
	@DisplayName( "entityLoadOrNew copies an assigned id but not a generated one" )
	@Test
	public void testLoadOrNewMissingId() {
		// @formatter:off
		instance.executeSource(
			"""
			assignedVin  = entityLoadOrNew( "Vehicle", "NEWVIN0001" ).getVin();
			generated    = entityLoadOrNew( "Manufacturer", 99999 );
			generatedId  = generated.getId() ?: "";
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "assignedVin" ) ) ).isEqualTo( "NEWVIN0001" );
		assertThat( variables.get( Key.of( "generatedId" ) ).toString() ).isEmpty();
	}

	/**
	 * A filter matching several entities is an orm.query.nonUnique error.
	 */
	@DisplayName( "entityLoadOrNew with a filter matching several rows is orm.query.nonUnique" )
	@Test
	public void testLoadOrNewNonUnique() {
		// @formatter:off
		instance.executeSource(
			"""
			try { entityLoadOrNew( "Vehicle", { make : "Honda" } ); type = "NO ERROR"; } catch ( any e ) { type = e.type; }
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "type" ) ) ).isEqualTo( "orm.query.nonUnique" );
	}

	/**
	 * entityLoadOrSave saves a new entity and returns an existing one without inserting.
	 */
	@DisplayName( "entityLoadOrSave inserts once, then finds the saved entity" )
	@Test
	public void testLoadOrSave() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				first = entityLoadOrSave( "Manufacturer", { name : "Rivian" }, { address : "Irvine CA" } );
			}
			ormClearSession();
			transaction {
				second = entityLoadOrSave( "Manufacturer", { name : "Rivian" }, { address : "ignored" } );
			}
			rows          = queryExecute( "SELECT id FROM manufacturers WHERE name = 'Rivian'" ).recordCount;
			sameId        = first.getId() == second.getId();
			secondAddress = second.getAddress();
			queryExecute( "DELETE FROM manufacturers WHERE name = 'Rivian'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "rows" ) ) ).isEqualTo( 1 );
		assertThat( variables.get( Key.of( "sameId" ) ) ).isEqualTo( true );
		assertThat( variables.getAsString( Key.of( "secondAddress" ) ) ).isEqualTo( "Irvine CA" );
	}

	/**
	 * entityLoadOrFail returns the entity or throws orm.notFound, by id, filter or composite key.
	 */
	@DisplayName( "entityLoadOrFail finds by id, filter and composite key, and throws orm.notFound" )
	@Test
	public void testLoadOrFail() {
		// @formatter:off
		instance.executeSource(
			"""
			byId      = entityLoadOrFail( "Manufacturer", 1 ).getName();
			byFilter  = entityLoadOrFail( "Vehicle", { model : "Civic" } ).getVin();
			composite = entityLoadOrFail( "VehicleType", { make : "Ford", model : "Fusion" } ).getDescription();
			try { entityLoadOrFail( "Manufacturer", 99999 ); idType = "NO ERROR"; } catch ( any e ) { idType = e.type; idMessage = e.message; }
			try { entityLoadOrFail( "Vehicle", { model : "Nope" } ); filterType = "NO ERROR"; } catch ( any e ) { filterType = e.type; }
			try { entityLoadOrFail( "Vehicle", { make : "Honda" } ); manyType = "NO ERROR"; } catch ( any e ) { manyType = e.type; }
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "byId" ) ) ).isEqualTo( "Ford Motor Company" );
		assertThat( variables.getAsString( Key.of( "byFilter" ) ) ).isEqualTo( "2HGCM82633A654321" );
		assertThat( variables.getAsString( Key.of( "composite" ) ) ).isEqualTo( "Mid-size sedan" );
		assertThat( variables.getAsString( Key.of( "idType" ) ) ).isEqualTo( "orm.notFound" );
		assertThat( variables.getAsString( Key.of( "idMessage" ) ) ).isEqualTo( "No [Manufacturer] with id [99999] was found." );
		assertThat( variables.getAsString( Key.of( "filterType" ) ) ).isEqualTo( "orm.notFound" );
		assertThat( variables.getAsString( Key.of( "manyType" ) ) ).isEqualTo( "orm.query.nonUnique" );
	}

	/**
	 * entityLoadByPKOrFail returns the entity or throws orm.notFound, and takes load options.
	 */
	@DisplayName( "entityLoadByPKOrFail finds by id, takes options, and throws orm.notFound" )
	@Test
	public void testLoadByPKOrFail() {
		// @formatter:off
		instance.executeSource(
			"""
			found = entityLoadByPKOrFail( "Manufacturer", 77, { readOnly : true } ).getName();
			try { entityLoadByPKOrFail( "Manufacturer", 99999 ); type = "NO ERROR"; } catch ( any e ) { type = e.type; }
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "found" ) ) ).isEqualTo( "General Moters Corporation" );
		assertThat( variables.getAsString( Key.of( "type" ) ) ).isEqualTo( "orm.notFound" );
	}
}
