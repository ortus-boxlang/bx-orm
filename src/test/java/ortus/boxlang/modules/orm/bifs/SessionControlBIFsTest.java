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
 * entityEvict(), entityGetReference(), entityLock(), ormReadOnly(), entityLoadReadOnly() and the readOnly / lock load
 * options.
 */
public class SessionControlBIFsTest extends BaseORMTest {

	/**
	 * An evicted entity's changes are not saved; one entity or an array.
	 */
	@DisplayName( "entityEvict drops one entity or an array from the session" )
	@Test
	public void testEvict() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				ford  = entityLoadByPK( "Manufacturer", 1 );
				honda = entityLoadByPK( "Manufacturer", 42 );
				gm    = entityLoadByPK( "Manufacturer", 77 );
				ford.setAddress( "changed" );
				honda.setAddress( "changed" );
				gm.setAddress( "changed" );
				entityEvict( ford );
				entityEvict( [ honda, gm ] );
				entityEvict( entityNew( "Manufacturer" ) );
				stillAttached = entityIsAttached( ford );
			}
			changed = queryExecute( "SELECT id FROM manufacturers WHERE address = 'changed'" ).recordCount;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "stillAttached" ) ) ).isEqualTo( false );
		assertThat( variables.get( Key.of( "changed" ) ) ).isEqualTo( 0 );
	}

	/**
	 * A reference loads nothing, can be used as an association value and loads on first use.
	 */
	@DisplayName( "entityGetReference returns an unloaded proxy usable as an association" )
	@Test
	public void testGetReference() throws ReflectiveOperationException {
		// @formatter:off
		instance.executeSource(
			"""
			ref = entityGetReference( "Manufacturer", 77 );
			transaction {
				v = entityNew( "Vehicle", { vin : "REFVIN0001", make : "GM", model : "Volt" } );
				v.setManufacturer( ref );
				entitySave( v );
			}
			fk = queryExecute( "SELECT FK_manufacturer FROM vehicles WHERE vin = 'REFVIN0001'" ).FK_manufacturer;
			""",
			context
		);
		// @formatter:on
		Object ref = variables.get( Key.of( "ref" ) );
		// The module has its own classloader, so compare by name and use the Hibernate proxy interface.
		assertThat( ref.getClass().getName() ).isEqualTo( "ortus.boxlang.modules.orm.hibernate.BoxProxy" );
		Object initializer = ref.getClass().getMethod( "getHibernateLazyInitializer" ).invoke( ref );
		assertThat( initializer.getClass().getMethod( "isUninitialized" ).invoke( initializer ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "fk" ) ).toString() ).isEqualTo( "77" );

		// @formatter:off
		instance.executeSource(
			"""
			name = ref.getName();
			queryExecute( "DELETE FROM vehicles WHERE vin = 'REFVIN0001'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "name" ) ) ).isEqualTo( "General Moters Corporation" );
	}

	/**
	 * entityLock locks a managed entity, and rejects bad modes, force on unversioned entities and detached entities.
	 */
	@DisplayName( "entityLock locks managed entities and explains misuse" )
	@Test
	public void testLock() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				m = entityLoadByPK( "Manufacturer", 1 );
				entityLock( m );
				entityLock( m, "read" );
				try { entityLock( m, "sideways" ); badMode = "NO ERROR"; } catch ( any e ) { badMode = e.type; }
				try { entityLock( m, "force" ); unversioned = "NO ERROR"; } catch ( any e ) { unversioned = e.type; }
				try { entityLock( entityNew( "Manufacturer" ) ); transient = "NO ERROR"; } catch ( any e ) { transient = e.type; }
			}
			transaction {
				note = entityNew( "VersionedNote", { title : "v" } );
				entitySave( note );
			}
			ormClearSession();
			transaction {
				note = entityLoadByPK( "VersionedNote", note.getId() );
				before = note.getVersion();
				entityLock( note, "force" );
			}
			after = queryExecute( "SELECT version FROM versioned_notes WHERE id = :id", { id : note.getId() } ).version;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "badMode" ) ) ).isEqualTo( "orm.argument" );
		assertThat( variables.getAsString( Key.of( "unversioned" ) ) ).isEqualTo( "orm.argument" );
		assertThat( variables.getAsString( Key.of( "transient" ) ) ).isEqualTo( "orm.transient" );
		int	before	= ( ( Number ) variables.get( Key.of( "before" ) ) ).intValue();
		int	after	= ( ( Number ) variables.get( Key.of( "after" ) ) ).intValue();
		assertThat( after ).isEqualTo( before + 1 );
	}

	/**
	 * entityLoadByPK with a lock and skipLocked returns null while another transaction holds the row.
	 */
	@DisplayName( "entityLoadByPK { lock, skipLocked } skips a row locked by another transaction" )
	@Test
	public void testLoadWithLockSkipLocked() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				mine = entityLoadByPK( "Manufacturer", 42, { lock : "write" } );
				thread name="lockProbe" {
					transaction {
						other = entityLoadByPK( "Manufacturer", 42, { lock : "write", skipLocked : true } );
						thread.skipped = isNull( other );
					}
				}
				thread action="join" name="lockProbe";
				skipped   = bxthread.lockProbe.skipped ?: bxthread.lockProbe.error.message;
				mineName  = mine.getName();
			}
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "mineName" ) ) ).isEqualTo( "Honda Motor Co." );
		assertThat( variables.get( Key.of( "skipped" ) ).toString() ).isEqualTo( "true" );
	}

	/**
	 * Entities loaded inside ormReadOnly are not saved; the block returns the closure's result and ends cleanly.
	 */
	@DisplayName( "ormReadOnly loads entities read-only and returns the closure's result" )
	@Test
	public void testOrmReadOnly() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				result = ormReadOnly( () => {
					var m = entityLoadByPK( "Manufacturer", 1 );
					m.setAddress( "changed" );
					return m.getName();
				} );
			}
			changedInside = queryExecute( "SELECT id FROM manufacturers WHERE address = 'changed'" ).recordCount;
			ormClearSession();
			transaction {
				entityLoadByPK( "Manufacturer", 1 ).setAddress( "changed after" );
			}
			changedAfter = queryExecute( "SELECT id FROM manufacturers WHERE address = 'changed after'" ).recordCount;
			queryExecute( "UPDATE manufacturers SET address = '202 Ford Way, Dearborn MI' WHERE id = 1" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "result" ) ) ).isEqualTo( "Ford Motor Company" );
		assertThat( variables.get( Key.of( "changedInside" ) ) ).isEqualTo( 0 );
		assertThat( variables.get( Key.of( "changedAfter" ) ) ).isEqualTo( 1 );
	}

	/**
	 * entityLoadReadOnly and the readOnly option load entities whose changes are not saved.
	 */
	@DisplayName( "entityLoadReadOnly and { readOnly : true } load read-only entities" )
	@Test
	public void testReadOnlyLoads() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entityLoadReadOnly( "Manufacturer", { name : "Ford Motor Company" } )[ 1 ].setAddress( "changed" );
				entityLoadReadOnly( "Manufacturer", 42, true ).setAddress( "changed" );
				entityLoad( "Manufacturer", { name : "General Moters Corporation" }, { readOnly : true } )[ 1 ].setAddress( "changed" );
			}
			ormClearSession();
			transaction {
				entityLoadByPK( "Manufacturer", 1, { readOnly : true } ).setAddress( "changed" );
				entityLoad( "Manufacturer", 42, true, { readOnly : true } ).setAddress( "changed" );
			}
			changed = queryExecute( "SELECT id FROM manufacturers WHERE address = 'changed'" ).recordCount;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "changed" ) ) ).isEqualTo( 0 );
	}
}
