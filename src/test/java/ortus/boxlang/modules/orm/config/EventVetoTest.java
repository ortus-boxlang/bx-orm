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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import tools.BaseORMTest;

/**
 * Live (MySQL) tests for vetoing preInsert, preUpdate and preDelete by returning {@code false}, from the entity's own
 * handler or from the global event handler (models/events/EventHandler.bx). Fixtures: models/VetoThing.bx (increment
 * id) and models/VetoIdentityThing.bx (identity id). Every write is rolled back.
 */
public class EventVetoTest extends BaseORMTest {

	/**
	 * Clear the ORM session after each test so no entity leaks into the next one.
	 */
	@AfterEach
	public void clearSession() {
		instance.executeSource( "try { ormClearSession(); } catch ( any e ) {}", context );
	}

	/**
	 * Run BoxLang code inside a rolled-back transaction and return the value it stores in {@code result}.
	 *
	 * @param code BoxLang statements that set {@code result}.
	 *
	 * @return The value of {@code result}.
	 */
	private IStruct runRolledBack( String code ) {
		instance.executeSource( "transaction { try { " + code + " } finally { transactionRollback(); } }", context );
		return variables.getAsStruct( result );
	}

	/**
	 * BoxLang expression that counts rows of a table with a given name.
	 *
	 * @param table The table name.
	 * @param name  The name value.
	 *
	 * @return The expression.
	 */
	private static String countRows( String table, String name ) {
		return "queryExecute( \"select count(*) as c from " + table + " where name = :n\", { n : \"" + name + "\" } ).c";
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* isVeto */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: only an explicit false (or the strings "false" / "no") vetoes.
	 */
	@DisplayName( "isVeto: only an explicit false vetoes" )
	@Test
	public void testIsVeto() {
		assertThat( ORMEventDispatcher.isVeto( Boolean.FALSE ) ).isTrue();
		assertThat( ORMEventDispatcher.isVeto( "false" ) ).isTrue();
		assertThat( ORMEventDispatcher.isVeto( " FALSE " ) ).isTrue();
		assertThat( ORMEventDispatcher.isVeto( "no" ) ).isTrue();
		assertThat( ORMEventDispatcher.isVeto( null ) ).isFalse();
		assertThat( ORMEventDispatcher.isVeto( Boolean.TRUE ) ).isFalse();
		assertThat( ORMEventDispatcher.isVeto( "true" ) ).isFalse();
		assertThat( ORMEventDispatcher.isVeto( "" ) ).isFalse();
		assertThat( ORMEventDispatcher.isVeto( 0 ) ).isFalse();
		assertThat( ORMEventDispatcher.isVeto( "anything" ) ).isFalse();
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* insert */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: an insert that no handler vetoes reaches the database, and the entity handler was called once.
	 */
	@DisplayName( "An insert with no veto is saved" )
	@Test
	public void testInsertNotVetoed() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-insert-ok" } );
		                           entitySave( e );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preInsert" ) };
		                           """.formatted( countRows( "veto_things", "veto-insert-ok" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "1" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: the entity's own preInsert returning false stops the insert.
	 */
	@DisplayName( "An entity preInsert returning false vetoes the insert" )
	@Test
	public void testEntityVetoesInsert() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-insert-entity", vetoEvents : "preInsert" } );
		                           entitySave( e );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preInsert" ) };
		                           """.formatted( countRows( "veto_things", "veto-insert-entity" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "0" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: the global handler's preInsert returning false stops the insert, and the entity handler still runs.
	 */
	@DisplayName( "A global preInsert returning false vetoes the insert; the entity handler still runs" )
	@Test
	public void testGlobalVetoesInsert() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-insert-global", globalVetoEvents : "preInsert" } );
		                           entitySave( e );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preInsert" ) };
		                           """.formatted( countRows( "veto_things", "veto-insert-global" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "0" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: after a vetoed insert the entity stays in the session; changing it makes the next flush try an update of a row
	 * that does not exist, while evicting it first is safe.
	 */
	@DisplayName( "After a vetoed insert, evict the entity before changing it" )
	@Test
	public void testChangeAfterVetoedInsert() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-after-insert", vetoEvents : "preInsert" } );
		                           entitySave( e );
		                           ormFlush();
		                           inSession = ormGetSessionStatistics().entityCount;
		                           e.setName( "veto-after-insert-changed" );
		                           try {
		                               ormFlush();
		                               changedType = "NO ERROR";
		                           } catch ( any ex ) {
		                               changedType = ex.type;
		                           }
		                           ormClearSession();
		                           f = entityNew( "VetoThing", { name : "veto-after-evict", vetoEvents : "preInsert" } );
		                           entitySave( f );
		                           ormFlush();
		                           ormGetSession().evict( f );
		                           f.setName( "veto-after-evict-changed" );
		                           ormFlush();
		                           result = { inSession : inSession, changedType : changedType, evictedCount : ormGetSessionStatistics().entityCount };
		                           """ );
		assertThat( r.get( "inSession" ).toString() ).isEqualTo( "1" );
		assertThat( r.getAsString( Key.of( "changedType" ) ) ).isEqualTo( "orm.stale" );
		assertThat( r.get( "evictedCount" ).toString() ).isEqualTo( "0" );
	}

	/**
	 * Test: a vetoed insert does not stop the other inserts of the same flush.
	 */
	@DisplayName( "A vetoed insert does not stop the other inserts in the same flush" )
	@Test
	public void testVetoOnlyAffectsOneEntity() {
		IStruct r = runRolledBack( """
		                           entitySave( entityNew( "VetoThing", { name : "veto-mixed", vetoEvents : "preInsert" } ) );
		                           entitySave( entityNew( "VetoThing", { name : "veto-mixed" } ) );
		                           entitySave( entityNew( "VetoThing", { name : "veto-mixed" } ) );
		                           ormFlush();
		                           result = { rows : %s };
		                           """.formatted( countRows( "veto_things", "veto-mixed" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "2" );
	}

	/**
	 * Test: vetoing the insert of an entity with a database identity id is a clear orm.event.veto error (Hibernate cannot
	 * skip an identity insert), and no row is written.
	 */
	@DisplayName( "Vetoing an identity-id insert is a clear orm.event.veto error" )
	@Test
	public void testEntityVetoesIdentityInsert() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoIdentityThing", { name : "veto-identity", vetoEvents : "preInsert" } );
		                           try {
		                               entitySave( e );
		                               ormFlush();
		                               err = { type : "NO ERROR", message : "" };
		                           } catch ( "orm.event" ex ) {
		                               err = { type : ex.type, message : ex.message };
		                           }
		                           ormClearSession();
		                           result = { rows : %s, type : err.type, message : err.message };
		                           """.formatted( countRows( "veto_identity_things", "veto-identity" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "0" );
		assertThat( r.getAsString( Key.type ) ).isEqualTo( "orm.event.veto" );
		assertThat( r.getAsString( Key.message ) ).contains( "[VetoIdentityThing]" );
		assertThat( r.getAsString( Key.message ) ).contains( "identity" );
		assertThat( r.getAsString( Key.message ) ).doesNotContain( "Facade" );
	}

	/**
	 * Test: an identity-id insert with no veto is saved and gets its id.
	 */
	@DisplayName( "An identity-id insert with no veto is saved" )
	@Test
	public void testIdentityInsertNotVetoed() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoIdentityThing", { name : "veto-identity-ok" } );
		                           entitySave( e );
		                           ormFlush();
		                           result = { rows : %s, hasId : !isNull( e.getId() ) };
		                           """.formatted( countRows( "veto_identity_things", "veto-identity-ok" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "1" );
		assertThat( r.getAsBoolean( Key.of( "hasId" ) ) ).isTrue();
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* update */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: an update that no handler vetoes reaches the database.
	 */
	@DisplayName( "An update with no veto is saved" )
	@Test
	public void testUpdateNotVetoed() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-update-before" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setName( "veto-update-after" );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preUpdate" ), dirty : entityIsDirty( e ) };
		                           """.formatted( countRows( "veto_things", "veto-update-after" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "1" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
		assertThat( r.getAsBoolean( Key.of( "dirty" ) ) ).isFalse();
	}

	/**
	 * Test: the entity's own preUpdate returning false stops the update; the entity keeps its change, stays dirty, and
	 * the update is tried again on the next flush.
	 */
	@DisplayName( "An entity preUpdate returning false vetoes the update; the change stays and re-fires on the next flush" )
	@Test
	public void testEntityVetoesUpdate() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-update-before" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setVetoEvents( "preUpdate" );
		                           e.setName( "veto-update-vetoed" );
		                           ormFlush();
		                           firstCalls = e.callCount( "preUpdate" );
		                           dirty = entityIsDirty( e );
		                           dirtyProps = entityGetDirtyProperties( e );
		                           ormFlush();
		                           secondCalls = e.callCount( "preUpdate" );
		                           result = {
		                               before      : %s,
		                               after       : %s,
		                               firstCalls  : firstCalls,
		                               secondCalls : secondCalls,
		                               dirty       : dirty,
		                               dirtyProps  : dirtyProps,
		                               inMemory    : e.getName()
		                           };
		                           """.formatted( countRows( "veto_things", "veto-update-before" ),
		    countRows( "veto_things", "veto-update-vetoed" ) ) );
		assertThat( r.get( "before" ).toString() ).isEqualTo( "1" );
		assertThat( r.get( "after" ).toString() ).isEqualTo( "0" );
		assertThat( r.get( "firstCalls" ).toString() ).isEqualTo( "1" );
		assertThat( r.get( "secondCalls" ).toString() ).isEqualTo( "2" );
		assertThat( r.getAsBoolean( Key.of( "dirty" ) ) ).isTrue();
		assertThat( r.getAsArray( Key.of( "dirtyProps" ) ).toList() ).containsExactly( "name" );
		assertThat( r.getAsString( Key.of( "inMemory" ) ) ).isEqualTo( "veto-update-vetoed" );
	}

	/**
	 * Test: the global handler's preUpdate returning false stops the update, and the entity handler still runs.
	 */
	@DisplayName( "A global preUpdate returning false vetoes the update; the entity handler still runs" )
	@Test
	public void testGlobalVetoesUpdate() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-gupdate-before" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setGlobalVetoEvents( "preUpdate" );
		                           e.setName( "veto-gupdate-vetoed" );
		                           ormFlush();
		                           calls = e.callCount( "preUpdate" );
		                           result = { after : %s, calls : calls };
		                           """.formatted( countRows( "veto_things", "veto-gupdate-vetoed" ) ) );
		assertThat( r.get( "after" ).toString() ).isEqualTo( "0" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: entityReload() discards a vetoed change.
	 */
	@DisplayName( "entityReload discards a vetoed update" )
	@Test
	public void testReloadDiscardsVetoedUpdate() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-reload-before" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setVetoEvents( "preUpdate" );
		                           e.setName( "veto-reload-vetoed" );
		                           ormFlush();
		                           entityReload( e );
		                           result = { name : e.getName(), dirty : entityIsDirty( e ) };
		                           """ );
		assertThat( r.getAsString( Key.of( "name" ) ) ).isEqualTo( "veto-reload-before" );
		assertThat( r.getAsBoolean( Key.of( "dirty" ) ) ).isFalse();
	}

	/**
	 * Test: once the handler stops vetoing, the pending update is written on the next flush.
	 */
	@DisplayName( "A vetoed update is written once the handler stops vetoing" )
	@Test
	public void testVetoedUpdateWrittenLater() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-later-before" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setVetoEvents( "preUpdate" );
		                           e.setName( "veto-later-after" );
		                           ormFlush();
		                           e.setVetoEvents( "" );
		                           ormFlush();
		                           result = { after : %s, dirty : entityIsDirty( e ) };
		                           """.formatted( countRows( "veto_things", "veto-later-after" ) ) );
		assertThat( r.get( "after" ).toString() ).isEqualTo( "1" );
		assertThat( r.getAsBoolean( Key.of( "dirty" ) ) ).isFalse();
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* delete */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: a delete that no handler vetoes removes the row.
	 */
	@DisplayName( "A delete with no veto removes the row" )
	@Test
	public void testDeleteNotVetoed() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-delete-ok" } );
		                           entitySave( e );
		                           ormFlush();
		                           entityDelete( e );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preDelete" ) };
		                           """.formatted( countRows( "veto_things", "veto-delete-ok" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "0" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: the entity's own preDelete returning false keeps the row.
	 */
	@DisplayName( "An entity preDelete returning false vetoes the delete" )
	@Test
	public void testEntityVetoesDelete() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-delete-entity" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setVetoEvents( "preDelete" );
		                           entityDelete( e );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preDelete" ) };
		                           """.formatted( countRows( "veto_things", "veto-delete-entity" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "1" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: the global handler's preDelete returning false keeps the row, and the entity handler still runs.
	 */
	@DisplayName( "A global preDelete returning false vetoes the delete; the entity handler still runs" )
	@Test
	public void testGlobalVetoesDelete() {
		IStruct r = runRolledBack( """
		                           e = entityNew( "VetoThing", { name : "veto-delete-global" } );
		                           entitySave( e );
		                           ormFlush();
		                           e.setGlobalVetoEvents( "preDelete" );
		                           entityDelete( e );
		                           ormFlush();
		                           result = { rows : %s, calls : e.callCount( "preDelete" ) };
		                           """.formatted( countRows( "veto_things", "veto-delete-global" ) ) );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "1" );
		assertThat( r.get( "calls" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: existing handlers that return nothing (void) never veto, so the classic event tests keep saving.
	 */
	@DisplayName( "Handlers that return nothing never veto" )
	@Test
	public void testVoidHandlersDoNotVeto() {
		IStruct r = runRolledBack( """
		                           v = entityNew( "Vehicle", { vin : "VETOVOID000000001", make : "Veto", model : "Void" } );
		                           entitySave( v );
		                           ormFlush();
		                           result = { rows : queryExecute( "select count(*) as c from vehicles where vin = 'VETOVOID000000001'" ).c };
		                           """ );
		assertThat( r.get( "rows" ).toString() ).isEqualTo( "1" );
	}
}
