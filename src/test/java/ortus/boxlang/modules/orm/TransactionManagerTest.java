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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class TransactionManagerTest extends BaseORMTest {

	@DisplayName( "An ORM write outside any transaction is committed (visible from a separate connection)" )
	@Test
	public void testAutomaticTransactions() throws SQLException {
		String name = uniqueName( "Audi" );
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "manufacturer", { name : "%s", address : "101 Audi Way" } ) );
			ormFlush();
			""".formatted( name ),
			context
		);
		// @formatter:on
		assertThat( committedCount( name ) ).isEqualTo( 1 );
	}

	@DisplayName( "It wont cause table/connection locking when ORM and native JDBC queries coexist" )
	@Test
	public void testORMAndNativeQueryCoexistence() {
		String name = uniqueName( "Audi" );
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "manufacturer", { name : "%s", address : "101 Audi Way" } ), true );
			ormFlush();
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = :name", { name : "%s" } );
			""".formatted( name, name ),
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( name );
	}

	@DisplayName( "It commits on transaction close" )
	@Test
	public void testTransactionCommitOnClose() {
		// @formatter:off
		instance.executeSource(
			"""
			previousMfrs = EntityLoad( "manufacturer" ).len();
			transaction{
				bmw = entityNew( "manufacturer", { name : "BMW Group", address : "102 BMW Tower" } );
				entitySave( bmw );
				transaction {
					chrysler = entityNew( "manufacturer", { name : "Chrysler Corporation", address : "105 Chrysler Way" } );
					entitySave( chrysler );
					transaction{
						fiat = entityNew( "manufacturer", { name : "Fiat S.p.A.", address : "106 Fiat Road" } );
						entitySave( fiat );
					}
				}
				allMfrs = EntityLoad( "manufacturer" ).len();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'BMW Group'" );
			result2 = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Chrysler Corporation'" );
			result3 = allMfrs - previousMfrs;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "BMW Group" );
		assertThat( variables.getAsQuery( Key.of( "result2" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "result2" ) ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Chrysler Corporation" );
		assertThat( variables.getAsInteger( Key.of( "result3" ) ) ).isEqualTo( 3 );
	}

	@DisplayName( "Id allocation through Hibernate isolated work never commits the surrounding BoxLang transaction" )
	@Test
	public void testIsolatedWorkDoesNotCommitTransaction() {
		// @formatter:off
		instance.executeSource(
			"""
			queryExecute( "INSERT INTO manufacturers ( id, name, address ) VALUES ( 9001, 'Isolation Corp', 'original' )" );
			try {
				transaction {
					queryExecute( "UPDATE manufacturers SET address = 'changed-in-tx' WHERE id = 9001" );
					// A sequence-style id on MySQL/MariaDB is allocated via Hibernate isolated work, which commits its own connection.
					entitySave( entityNew( "SeqGadget", { name : "isolated" } ) );
					throw( "boom" );
				}
			} catch ( any e ) {
				// expected: the transaction rolls back
			}
			address  = queryExecute( "SELECT address FROM manufacturers WHERE id = 9001" ).address;
			gadgets  = queryExecute( "SELECT count(*) AS n FROM seq_gadgets WHERE name = 'isolated'" ).n;
			queryExecute( "DELETE FROM manufacturers WHERE id = 9001" );
			""",
			context
		);
		// @formatter:on
		// The native update and the ORM insert were both inside the rolled-back transaction.
		assertThat( variables.getAsString( Key.of( "address" ) ) ).isEqualTo( "original" );
		assertThat( ( ( Number ) variables.get( Key.of( "gadgets" ) ) ).intValue() ).isEqualTo( 0 );
	}

	@DisplayName( "It rolls back on transaction rollback" )
	@Test
	public void testTransactionRollback() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "Subaru Corporation", address : "101 Sub Way" } ) );
				transactionRollback();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Subaru Corporation'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "It commits on transaction commit, despite rollback" )
	@Test
	public void testTransactionCommit() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "Mitsubishi Corp", address : "101 Mitsubishi Way" } ) );
				transactionCommit();
				transactionRollback();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Mitsubishi Corp'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Mitsubishi Corp" );
	}

	@Disabled( "Requires autoManageSession=true, which the test app leaves off: only then does onTransactionBegin flush pre-transaction writes, so a later rollback cannot discard them." )
	@DisplayName( "Rollbacks are limited to changes in the transaction context" )
	@Test
	public void testORMChildTransactionRollback() {
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "manufacturer", { name : "outside_transaction" } ) );
			transaction{
				entitySave( entityNew( "manufacturer", { name : "inside_transaction" } ) );
				transactionRollback();
			}
			outside = queryExecute( "SELECT * FROM manufacturers WHERE name = 'outside_transaction'" );
			inside = queryExecute( "SELECT * FROM manufacturers WHERE name = 'inside_transaction'" );
			""",
			context
		);
		// @formatter:on

		// entity created BEFORE transaction block should be committed
		assertThat( variables.getAsQuery( Key.of( "outside" ) ).size() ).isEqualTo( 1 );
		// entity created INSIDE transaction block should be rolled back
		assertThat( variables.getAsQuery( Key.of( "inside" ) ).size() ).isEqualTo( 0 );
	}

	@Disabled( "Requires the runtime's experimental enableNestedTransactions=true. With the default (false), BoxLang flattens nested transaction{} blocks into a single demarcation unit, so a nested transactionRollback() rolls back the parent's work too." )
	@DisplayName( "Child transaction cannot roll back parent transaction" )
	@Test
	public void testORMChildTransactionCantRollbackParent() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "outer_transaction" } ) );
				transaction{
					entitySave( entityNew( "manufacturer", { name : "inner_transaction" } ) );
					transactionRollback();
				}
			}
			outside = queryExecute( "SELECT * FROM manufacturers WHERE name = 'outer_transaction'" );
			inside = queryExecute( "SELECT * FROM manufacturers WHERE name = 'inner_transaction'" );
			""",
			context
		);
		// @formatter:on

		// entity created in OUTER transaction should be committed
		assertThat( variables.getAsQuery( Key.of( "outside" ) ).size() ).isEqualTo( 1 );
		// entity created in INNER transaction should be rolled back
		assertThat( variables.getAsQuery( Key.of( "inside" ) ).size() ).isEqualTo( 0 );
	}

	@Disabled( "Requires the runtime's experimental enableNestedTransactions=true. With the default (false), BoxLang flattens nested transaction{} blocks into a single demarcation unit, so a nested transactionCommit() performs a real JDBC commit on the shared connection and does commit the parent." )
	@DisplayName( "Child transaction cannot commit parent transaction" )
	@Test
	public void testORMChildTransactionCantCommitParent() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "outer_transaction" } ) );
				transaction{
					entitySave( entityNew( "manufacturer", { name : "inner_transaction" } ) );
					transactionCommit();
				}
				// should roll back the entire transaction.
				transactionRollback();
			}
			outside = queryExecute( "SELECT * FROM manufacturers WHERE name = 'outer_transaction'" );
			inside = queryExecute( "SELECT * FROM manufacturers WHERE name = 'inner_transaction'" );
			""",
			context
		);
		// @formatter:on

		// entity created in OUTER transaction should be committed
		assertThat( variables.getAsQuery( Key.of( "outside" ) ).size() ).isEqualTo( 0 );
		// entity created in INNER transaction should be committed
		assertThat( variables.getAsQuery( Key.of( "inside" ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "Inner transaction can be rolled back from outer" )
	@Test
	public void testORMNestedTransactionRollback() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				transaction{
					outerManufacturer = entityNew( "manufacturer", { name : "Session Test Corp", address : "500 Session Way" } );
					entitySave( outerManufacturer );
				}
				transactionRollback();
				outerFound = EntityLoad( "manufacturer", { name = "Session Test Corp" } ).len();
			}

			// After transaction commits, verify entity was rolled back
			finalFound = EntityLoad( "manufacturer", { name = "Session Test Corp" } ).len();
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Session Test Corp'" );
			""",
			context
		);
		// @formatter:on

		assertThat( variables.getAsInteger( Key.of( "finalFound" ) ) ).isEqualTo( 0 );
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "ORM writes and queryExecute inside one transaction share its connection and roll back together" )
	@Test
	public void testORMNestedTransactionSessionSharing() throws SQLException {
		String name = uniqueName( "Shared" );
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "outer" } ) );
				transaction {
					ormFlush();
					// Only the transaction's own connection can see this uncommitted ORM row.
					insideCount = queryExecute( "SELECT count(*) AS n FROM manufacturers WHERE name = :name", { name : "%1$s" } ).n;
				}
				transactionRollback();
			}
			afterCount = queryExecute( "SELECT count(*) AS n FROM manufacturers WHERE name = :name", { name : "%1$s" } ).n;
			""".formatted( name ),
			context
		);
		// @formatter:on
		assertThat( ( ( Number ) variables.get( Key.of( "insideCount" ) ) ).intValue() ).isEqualTo( 1 );
		assertThat( ( ( Number ) variables.get( Key.of( "afterCount" ) ) ).intValue() ).isEqualTo( 0 );
		assertThat( committedCount( name ) ).isEqualTo( 0 );
	}

	@DisplayName( "An exception inside transaction{} rolls back both the ORM write and the native queryExecute write" )
	@Test
	public void testExceptionRollsBackORMAndNativeWrites() throws SQLException {
		String	ormName		= uniqueName( "OrmBoom" );
		String	nativeName	= uniqueName( "NativeBoom" );
		// @formatter:off
		instance.executeSource(
			"""
			try {
				transaction {
					entitySave( entityNew( "manufacturer", { name : "%s", address : "orm" } ) );
					ormFlush();
					queryExecute( "INSERT INTO manufacturers ( id, name, address ) VALUES ( 9101, :name, 'native' )", { name : "%s" } );
					throw( "boom" );
				}
			} catch ( any e ) {
				caught = e.message;
			}
			""".formatted( ormName, nativeName ),
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "caught" ) ) ).isEqualTo( "boom" );
		assertThat( committedCount( ormName ) ).isEqualTo( 0 );
		assertThat( committedCount( nativeName ) ).isEqualTo( 0 );
	}

	@DisplayName( "An ORM query inside a transaction sees the transaction's unflushed ORM writes (read-your-writes)" )
	@Test
	public void testReadYourWritesInsideTransaction() throws SQLException {
		String name = uniqueName( "Ryw" );
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "ryw" } ) );
				// No ormFlush(): the ORM query must flush pending writes on the transaction connection before it runs.
				loadedCount = entityLoad( "manufacturer", { name : "%1$s" } ).len();
				hqlCount    = ormExecuteQuery( "select count(m) from Manufacturer m where m.name = :name", { name : "%1$s" }, true );
			}
			""".formatted( name ),
			context
		);
		// @formatter:on
		assertThat( variables.getAsInteger( Key.of( "loadedCount" ) ) ).isEqualTo( 1 );
		assertThat( ( ( Number ) variables.get( Key.of( "hqlCount" ) ) ).intValue() ).isEqualTo( 1 );
		assertThat( committedCount( name ) ).isEqualTo( 1 );
	}

	/** A manufacturer name unique to this run, so tests never depend on each other's rows or on execution order. */
	private static String uniqueName( String prefix ) {
		return prefix + " " + UUID.randomUUID().toString().substring( 0, 8 );
	}

	/**
	 * Count committed manufacturer rows with the given name, from a fresh pooled connection outside any transaction.
	 */
	private int committedCount( String name ) throws SQLException {
		DataSource datasource = ( ( IJDBCCapableContext ) context ).getConnectionManager().getDefaultDatasourceOrThrow();
		try ( Connection conn = datasource.getBoxConnection();
		    PreparedStatement statement = conn.prepareStatement( "SELECT count(*) FROM manufacturers WHERE name = ?" ) ) {
			statement.setString( 1, name );
			try ( ResultSet rows = statement.executeQuery() ) {
				rows.next();
				return rows.getInt( 1 );
			}
		}
	}
}
