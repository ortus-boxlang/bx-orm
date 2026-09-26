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
package ortus.boxlang.modules.orm.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

/**
 * Soft delete and automatic timestamps, mapped through Java annotations on the entity facades.
 */
public class NativeFeaturesTest extends BaseORMTest {

	/**
	 * entityDelete() on a softDelete entity marks the row instead of removing it, and loads skip it.
	 */
	@DisplayName( "softDelete marks rows deleted and hides them from loads" )
	@Test
	public void testSoftDelete() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				note = entityNew( "SoftNote", { title : "keep me" } );
				entitySave( note );
			}
			noteId = note.getId();
			transaction {
				entityDelete( entityLoadByPK( "SoftNote", noteId ) );
			}
			ormClearSession();
			raw     = queryExecute( "SELECT * FROM soft_notes WHERE id = :id", { id : noteId } );
			loaded  = entityLoadByPK( "SoftNote", noteId );
			listed  = entityLoad( "SoftNote", { title : "keep me" } );
			""",
			context
		);
		// @formatter:on
		var raw = variables.getAsQuery( result( "raw" ) );
		assertEquals( 1, raw.size() );
		assertEquals( true, ortus.boxlang.runtime.dynamic.casters.BooleanCaster.cast( raw.getRowAsStruct( 0 ).get( "deleted" ) ) );
		assertEquals( null, variables.get( ortus.boxlang.runtime.scopes.Key.of( "loaded" ) ) );
		assertEquals( 0, variables.getAsArray( ortus.boxlang.runtime.scopes.Key.of( "listed" ) ).size() );
	}

	/**
	 * autoTimestamp="create" is set on insert; autoTimestamp="update" on every write.
	 */
	@DisplayName( "autoTimestamp sets create and update timestamps" )
	@Test
	public void testAutoTimestamp() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				note = entityNew( "StampedNote", { title : "first" } );
				entitySave( note );
			}
			noteId = note.getId();
			ormClearSession();
			first = queryExecute( "SELECT * FROM stamped_notes WHERE id = :id", { id : noteId } );
			sleep( 1100 );
			transaction {
				loaded = entityLoadByPK( "StampedNote", noteId );
				loaded.setTitle( "second" );
			}
			ormClearSession();
			raw = queryExecute( "SELECT * FROM stamped_notes WHERE id = :id", { id : noteId } );
			sameCreate  = dateCompare( first.createdDate, raw.createdDate ) == 0;
			movedUpdate = dateCompare( first.updatedDate, raw.updatedDate ) < 0;
			isDateOnEntity = isDate( loaded.getUpdatedDate() );
			""",
			context
		);
		// @formatter:on
		var raw = variables.getAsQuery( result( "raw" ) );
		assertEquals( 1, raw.size() );
		assertNotNull( raw.getRowAsStruct( 0 ).get( "createdDate" ) );
		assertNotNull( raw.getRowAsStruct( 0 ).get( "updatedDate" ) );
		assertTrue( variables.getAsBoolean( result( "sameCreate" ) ) );
		assertTrue( variables.getAsBoolean( result( "movedUpdate" ) ) );
		assertTrue( variables.getAsBoolean( result( "isDateOnEntity" ) ) );
	}

	/**
	 * softDelete="timestamp" with softDeleteColumn writes the deletion time into that column.
	 */
	@DisplayName( "softDelete=timestamp records when the row was deleted in a custom column" )
	@Test
	public void testSoftDeleteTimestamp() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				note = entityNew( "ArchivedNote", { title : "old" } );
				entitySave( note );
			}
			noteId = note.getId();
			before = queryExecute( "SELECT archived_at FROM archived_notes WHERE id = :id", { id : noteId } );
			transaction {
				entityDelete( entityLoadByPK( "ArchivedNote", noteId ) );
			}
			ormClearSession();
			raw    = queryExecute( "SELECT archived_at FROM archived_notes WHERE id = :id", { id : noteId } );
			loaded = entityLoadByPK( "ArchivedNote", noteId );
			wasNull    = isNull( before.archived_at ) || before.archived_at == "";
			isDeleted  = isDate( raw.archived_at );
			""",
			context
		);
		// @formatter:on
		assertTrue( variables.getAsBoolean( result( "wasNull" ) ) );
		assertTrue( variables.getAsBoolean( result( "isDeleted" ) ) );
		assertEquals( null, variables.get( result( "loaded" ) ) );
	}

	/**
	 * softDelete="active" keeps an active flag that turns false on delete.
	 */
	@DisplayName( "softDelete=active flips the active column to false" )
	@Test
	public void testSoftDeleteActive() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				note = entityNew( "LiveNote", { title : "live" } );
				entitySave( note );
			}
			noteId = note.getId();
			before = queryExecute( "SELECT active FROM live_notes WHERE id = :id", { id : noteId } );
			transaction {
				entityDelete( entityLoadByPK( "LiveNote", noteId ) );
			}
			ormClearSession();
			raw         = queryExecute( "SELECT active FROM live_notes WHERE id = :id", { id : noteId } );
			activeBefore = booleanFormat( before.active );
			activeAfter  = booleanFormat( raw.active );
			count        = ormExecuteQuery( "select count(*) from LiveNote where id = :id", { id : noteId }, true );
			""",
			context
		);
		// @formatter:on
		assertEquals( "true", variables.getAsString( result( "activeBefore" ) ) );
		assertEquals( "false", variables.getAsString( result( "activeAfter" ) ) );
		assertEquals( 0L, ( ( Number ) variables.get( result( "count" ) ) ).longValue() );
	}

	/**
	 * An entity-level where hides rows from HQL, criteria and loads by id.
	 */
	@DisplayName( "Entity where hides rows from HQL, criteria and loads by id" )
	@Test
	public void testEntityWhere() {
		// @formatter:off
		instance.executeSource(
			"""
			queryExecute( "INSERT INTO flagged_notes ( id, title, is_active ) VALUES ( 901, 'on', 1 ), ( 902, 'off', 0 )" );
			ormClearSession();
			hqlCount      = ormExecuteQuery( "select count(*) from FlaggedNote where id in (901, 902)", [], true );
			criteriaCount = entityCriteria( "FlaggedNote" ).isIn( "id", [ 901, 902 ] ).count();
			byIdOn        = !isNull( entityLoadByPK( "FlaggedNote", 901 ) );
			byIdOff       = isNull( entityLoadByPK( "FlaggedNote", 902 ) );
			queryExecute( "DELETE FROM flagged_notes WHERE id in (901, 902)" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 1L, ( ( Number ) variables.get( result( "hqlCount" ) ) ).longValue() );
		assertEquals( 1L, ( ( Number ) variables.get( result( "criteriaCount" ) ) ).longValue() );
		assertTrue( variables.getAsBoolean( result( "byIdOn" ) ) );
		assertTrue( variables.getAsBoolean( result( "byIdOff" ) ) );
	}

	/**
	 * A collection-level where limits what the collection loads.
	 */
	@DisplayName( "Collection where limits the loaded collection" )
	@Test
	public void testCollectionWhere() {
		// @formatter:off
		instance.executeSource(
			"""
			queryExecute( "INSERT INTO notebooks ( id, name ) VALUES ( 901, 'book' )" );
			queryExecute( "INSERT INTO pin_notes ( id, title, pinned, notebook_id ) VALUES ( 901, 'a', 1, 901 ), ( 902, 'b', 0, 901 ), ( 903, 'c', 1, 901 )" );
			ormClearSession();
			pinned = entityLoadByPK( "Notebook", 901 ).getPinnedNotes().len();
			queryExecute( "DELETE FROM pin_notes WHERE notebook_id = 901" );
			queryExecute( "DELETE FROM notebooks WHERE id = 901" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 2, ( ( Number ) variables.get( result( "pinned" ) ) ).intValue() );
	}

	/**
	 * The variables key for a name.
	 *
	 * @param name The variable name.
	 *
	 * @return The key.
	 */
	private static ortus.boxlang.runtime.scopes.Key result( String name ) {
		return ortus.boxlang.runtime.scopes.Key.of( name );
	}
}
