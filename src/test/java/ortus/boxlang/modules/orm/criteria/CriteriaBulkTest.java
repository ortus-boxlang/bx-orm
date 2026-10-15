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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.IStruct;

/**
 * Live tests for the criteria bulk statements ({@code updateAll()}, {@code deleteAll()}) and {@code lock()}. Each test
 * works on its own rows (manufacturers 901 and 902 and their vehicles) so the shared seed data is untouched.
 */
public class CriteriaBulkTest extends CriteriaTestSupport {

	/**
	 * Add the rows the bulk tests change.
	 */
	@BeforeEach
	public void addBulkRows() {
		// @formatter:off
		instance.executeSource(
			"""
			queryExecute( "INSERT INTO manufacturers ( id, name, address ) VALUES ( 901, 'Bulk One', 'a' ), ( 902, 'Bulk Two', 'b' )" );
			queryExecute( "INSERT INTO vehicles ( vin, make, model, FK_manufacturer ) VALUES
				( 'BULK001', 'BulkMake', 'M1', 901 ), ( 'BULK002', 'BulkMake', 'M2', 901 ), ( 'BULK003', 'BulkMake', 'M3', 902 )" );
			""",
			context
		);
		// @formatter:on
	}

	/**
	 * Remove the rows the bulk tests added.
	 */
	@AfterEach
	public void removeBulkRows() {
		instance.executeSource( """
		                        queryExecute( "DELETE FROM vehicles WHERE vin LIKE 'BULK%'" );
		                        queryExecute( "DELETE FROM manufacturers WHERE id IN ( 901, 902 )" );
		                        queryExecute( "DELETE FROM soft_notes WHERE title LIKE 'bulk%'" );
		                        """, context );
	}

	/**
	 * Test: updateAll on the entity's own properties.
	 */
	@DisplayName( "updateAll() sets properties on every matching row and returns the count" )
	@Test
	public void testUpdateAll() {
		assertThat(
		    number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'BulkMake' ).isIn( 'model', [ 'M1', 'M2' ] ).updateAll( { model : 'Updated' } );" ) )
		    .isEqualTo( 2 );
		assertThat( number( "result = queryExecute( \"SELECT vin FROM vehicles WHERE model = 'Updated'\" ).recordCount;" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: updateAll with an association condition runs through a subquery.
	 */
	@DisplayName( "updateAll() with an association condition updates the right rows" )
	@Test
	public void testUpdateAllThroughAssociation() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.name', 'Bulk Two' ).updateAll( { model : 'ViaJoin' } );" ) )
		    .isEqualTo( 1 );
		assertThat( run( "result = queryExecute( \"SELECT vin FROM vehicles WHERE model = 'ViaJoin'\" ).vin;" ) ).isEqualTo( "BULK003" );
	}

	/**
	 * Test: updateAll can set a to-one association by id and a property to null.
	 */
	@DisplayName( "updateAll() sets a to-one association by id and a property to null" )
	@Test
	public void testUpdateAllAssociationAndNull() {
		assertThat( number(
		    "result = entityCriteria( 'Vehicle' ).isEq( 'model', 'M1' ).isEq( 'make', 'BulkMake' ).updateAll( { manufacturer : 902, model : javacast( 'null', '' ) } );" ) )
		    .isEqualTo( 1 );
		assertThat(
		    number( "result = queryExecute( \"SELECT vin FROM vehicles WHERE vin = 'BULK001' AND FK_manufacturer = 902 AND model IS NULL\" ).recordCount;" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: updateAll flushes pending changes first, and loaded entities keep their old values until reloaded.
	 */
	@DisplayName( "updateAll() flushes first and leaves loaded entities unchanged" )
	@Test
	public void testUpdateAllSessionState() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				v = entityLoadByPK( "Vehicle", "BULK001" );
				v.setMake( "Pending" );
				count = entityCriteria( "Vehicle" ).isEq( "make", "Pending" ).updateAll( { model : "SawPending" } );
				stale = v.getModel();
			}
			""",
			context
		);
		// @formatter:on
		assertThat( number( "result = count;" ) ).isEqualTo( 1 );
		assertThat( run( "result = stale;" ) ).isEqualTo( "M1" );
	}

	/**
	 * Test: deleteAll deletes matching rows, including through an association condition.
	 */
	@DisplayName( "deleteAll() deletes every matching row and returns the count" )
	@Test
	public void testDeleteAll() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'model', 'M1' ).isEq( 'make', 'BulkMake' ).deleteAll();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.id', 901 ).deleteAll();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.name', 'Bulk Two' ).deleteAll();" ) ).isEqualTo( 1 );
		assertThat( number( "result = queryExecute( \"SELECT vin FROM vehicles WHERE vin LIKE 'BULK%'\" ).recordCount;" ) ).isEqualTo( 0 );
	}

	/**
	 * Test: deleteAll on a softDelete entity marks the rows deleted.
	 */
	@DisplayName( "deleteAll() on a softDelete entity marks rows deleted" )
	@Test
	public void testDeleteAllSoft() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entitySave( entityNew( "SoftNote", { title : "bulk a" } ) );
				entitySave( entityNew( "SoftNote", { title : "bulk b" } ) );
			}
			result = entityCriteria( "SoftNote" ).like( "title", "bulk%" ).deleteAll();
			""",
			context
		);
		// @formatter:on
		assertThat( number( "result = result;" ) ).isEqualTo( 2 );
		assertThat( number( "result = queryExecute( \"SELECT id FROM soft_notes WHERE title LIKE 'bulk%' AND deleted = 1\" ).recordCount;" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'SoftNote' ).like( 'title', 'bulk%' ).count();" ) ).isEqualTo( 0 );
	}

	/**
	 * Test: bulk statements explain misuse.
	 */
	@DisplayName( "updateAll() and deleteAll() explain misuse" )
	@Test
	public void testBulkErrors() {
		IStruct e = error( "entityCriteria( 'Vehicle' ).updateAll( { colour : 'red' } );" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.property.unknown" );
		e = error( "entityCriteria( 'Vehicle' ).updateAll( {} );" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.argument" );
		e = error( "entityCriteria( 'Manufacturer' ).updateAll( { vehicles : [] } );" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.argument" );
		e = error( "entityCriteria( 'Vehicle' ).maxResults( 1 ).deleteAll();" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.argument" );
		e = error( "entityCriteria( 'Vehicle' ).updateAll( 'model' );" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: lock() adds a locking clause to row selects only, and validates its mode.
	 */
	@DisplayName( "lock() locks selected rows, not counts, and validates the mode" )
	@Test
	public void testLock() {
		String sql = ( String ) run( "transaction { result = entityCriteria( 'Vehicle' ).isEq( 'make', 'BulkMake' ).lock().getSQL(); }" );
		assertThat( sql.toLowerCase() ).contains( "for update" );
		sql = ( String ) run( "transaction { result = entityCriteria( 'Vehicle' ).lock( 'read' ).getSQL(); }" );
		assertThat( sql.toLowerCase() ).containsMatch( "for share|lock in share mode" );
		assertThat( number( "transaction { result = entityCriteria( 'Vehicle' ).isEq( 'make', 'BulkMake' ).lock().count(); }" ) ).isEqualTo( 3 );
		assertThat( number( "transaction { result = entityCriteria( 'Vehicle' ).isEq( 'make', 'BulkMake' ).lock( 'write', { timeout : 5 } ).list().len(); }" ) )
		    .isEqualTo( 3 );
		IStruct e = error( "entityCriteria( 'Vehicle' ).lock( 'sideways' );" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.argument" );
		e = error( "entityCriteria( 'Vehicle' ).lock().list();" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.type ) ).isEqualTo( "orm.argument" );
		assertThat( e.getAsString( ortus.boxlang.runtime.scopes.Key.message ) ).contains( "needs a transaction" );
	}

	/**
	 * Test: lock( "write", { skipLocked : true } ) skips rows another transaction holds (the queue-worker pattern), and
	 * ormExecuteQuery takes the same lock option.
	 */
	@DisplayName( "lock() with skipLocked skips rows locked by another transaction; ormExecuteQuery takes { lock }" )
	@Test
	public void testLockSkipLocked() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				held = ormExecuteQuery( "from Manufacturer where id = 901", [], true, { lock : "write" } );
				thread name="queueWorker" {
					transaction {
						thread.ids = entityCriteria( "Manufacturer" ).isIn( "id", [ 901, 902 ] ).lock( "write", { skipLocked : true } ).list().map( ( m ) => m.getId() );
					}
				}
				thread action="join" name="queueWorker";
				ids = bxthread.queueWorker.ids ?: bxthread.queueWorker.error.message;
				heldName = held.getName();
			}
			""",
			context
		);
		// @formatter:on
		assertThat( run( "result = heldName;" ) ).isEqualTo( "Bulk One" );
		assertThat( run( "result = isArray( ids ) ? ids.toList() : ids;" ) ).isEqualTo( "902" );
	}
}
