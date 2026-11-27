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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * BLMODULES-318: SQL run inside a {@code transaction{}} ({@code queryExecute()}, {@code bx:query}, and so qb) sees the ORM
 * writes made earlier in the same transaction. The test app uses the default {@code autoManageSession: false}, so the
 * session is MANUAL and nothing else would flush it. {@code Manufacturer} ids use the {@code increment} generator, so
 * {@code entitySave()} only queues the insert.
 */
public class TransactionSQLFlushTest extends BaseORMTest {

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

	private int intVar( String name ) {
		return ( ( Number ) variables.get( Key.of( name ) ) ).intValue();
	}

	private boolean boolVar( String name ) {
		return ( Boolean ) variables.get( Key.of( name ) );
	}

	@DisplayName( "queryExecute() after entitySave() in a transaction sees the row (the ticket's case)" )
	@Test
	public void testQueryExecuteSeesORMWrite() throws SQLException {
		String name = uniqueName( "Sql318" );
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
				seen = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" } ).c;
				dirtyAfterQuery = ormGetSession().isDirty();
			}
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 1 );
		assertThat( boolVar( "dirtyAfterQuery" ) ).isFalse();
		assertThat( committedCount( name ) ).isEqualTo( 1 );
	}

	@DisplayName( "bx:query after entitySave() in a transaction sees the row" )
	@Test
	public void testBxQuerySeesORMWrite() {
		String name = uniqueName( "Tag318" );
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
				bx:query name="q" { echo( "SELECT count(*) AS c FROM manufacturers WHERE name = '%1$s'" ); }
				seen = q.c;
				transactionRollback();
			}
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 1 );
	}

	@DisplayName( "An update made with SQL after entitySave() changes the saved row (cborm save() then a qb update)" )
	@Test
	public void testSQLUpdateAfterSave() throws SQLException {
		String name = uniqueName( "Upd318" );
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "before" } ) );
				queryExecute( "UPDATE manufacturers SET address = 'after' WHERE name = :n", { n : "%1$s" }, { result : "updateResult" } );
				updated = updateResult.recordCount;
			}
			address = queryExecute( "SELECT address FROM manufacturers WHERE name = :n", { n : "%1$s" } ).address;
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "updated" ) ).isEqualTo( 1 );
		assertThat( variables.getAsString( Key.of( "address" ) ) ).isEqualTo( "after" );
		assertThat( committedCount( name ) ).isEqualTo( 1 );
	}

	@DisplayName( "queryExecute() after a nested transaction block sees the block's ORM writes" )
	@Test
	public void testQueryAfterNestedBlockSeesORMWrite() {
		String name = uniqueName( "Nested318" );
		// @formatter:off
		instance.executeSource( """
			transaction {
				transaction {
					entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
				}
				seen = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" } ).c;
				transactionRollback();
			}
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 1 );
	}

	@DisplayName( "A rollback after the flush still discards the ORM write: the flush is not a commit" )
	@Test
	public void testRollbackAfterFlushDiscardsWrite() throws SQLException {
		String name = uniqueName( "Rollback318" );
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
				seen = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" } ).c;
				transactionRollback();
			}
			after = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" } ).c;
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 1 );
		assertThat( intVar( "after" ) ).isEqualTo( 0 );
		assertThat( committedCount( name ) ).isEqualTo( 0 );
	}

	@DisplayName( "Until the transaction commits, another connection does not see the flushed row" )
	@Test
	public void testFlushedRowIsNotCommittedEarly() throws SQLException {
		String name = uniqueName( "Isolated318" );
		// An inline datasource struct is a separate pool, so its query runs outside the transaction's connection.
		// @formatter:off
		instance.executeSource( """
			otherConnection = {
				driver   : "mysql",
				database : "test",
				host     : getSystemSetting( "DB_HOST", "127.0.0.1" ),
				port     : getSystemSetting( "DB_PORT", 3306 ),
				username : getSystemSetting( "DB_USER", "root" ),
				password : getSystemSetting( "DB_PASSWORD", "root" )
			};
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
				seen = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" } ).c;
				seenElsewhere = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" }, { datasource : otherConnection } ).c;
			}
			seenElsewhereAfterCommit = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" }, { datasource : otherConnection } ).c;
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 1 );
		assertThat( intVar( "seenElsewhere" ) ).isEqualTo( 0 );
		assertThat( intVar( "seenElsewhereAfterCommit" ) ).isEqualTo( 1 );
	}

	@DisplayName( "Outside a transaction, queryExecute() does not flush: MANUAL mode still waits for ormFlush()" )
	@Test
	public void testNoFlushOutsideTransaction() throws SQLException {
		String name = uniqueName( "Outside318" );
		// @formatter:off
		instance.executeSource( """
			entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
			seen = queryExecute( "SELECT count(*) AS c FROM manufacturers WHERE name = :n", { n : "%1$s" } ).c;
			dirty = ormGetSession().isDirty();
			ormClearSession();
			""".formatted( name ), context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 0 );
		assertThat( boolVar( "dirty" ) ).isTrue();
		assertThat( committedCount( name ) ).isEqualTo( 0 );
	}

	@DisplayName( "A query on another datasource does not flush the transaction's session" )
	@Test
	public void testQueryOnOtherDatasourceDoesNotFlush() throws SQLException {
		String name = uniqueName( "OtherDs318" );
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
				queryExecute( "SELECT 1 AS one", {}, { datasource : "dsn2" } );
				dirtyAfterOtherDatasource = ormGetSession().isDirty();
				transactionRollback();
			}
			""".formatted( name ), context );
		// @formatter:on
		assertThat( boolVar( "dirtyAfterOtherDatasource" ) ).isTrue();
		assertThat( committedCount( name ) ).isEqualTo( 0 );
	}

	@DisplayName( "A transaction with only SQL opens no ORM session" )
	@Test
	public void testSQLOnlyTransactionOpensNoSession() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				queryExecute( "SELECT 1 AS one" );
			}
			""", context );
		// @formatter:on
		assertThat( ORMContext.findForContext( context ) == null || ORMContext.findForContext( context ).getSessions().isEmpty() ).isTrue();
	}

	@DisplayName( "A flush error surfaces at the query, inside the transaction, and the transaction rolls back" )
	@Test
	public void testFlushErrorSurfacesAtQuery() throws SQLException {
		String name = uniqueName( "Err318" );
		// The increment generator hands out ids in memory; SQL takes the next id first, so the second ORM insert clashes
		// when the following query flushes it.
		// @formatter:off
		instance.executeSource( """
			try {
				transaction {
					first = entityNew( "manufacturer", { name : "%1$s", address : "318" } );
					entitySave( first );
					queryExecute( "INSERT INTO manufacturers ( id, name, address ) VALUES ( :id, :n, 'sql' )", { id : first.getId() + 1, n : "%1$s" } );
					entitySave( entityNew( "manufacturer", { name : "%1$s", address : "318" } ) );
					reached = "before query";
					queryExecute( "SELECT 1 AS one" );
					reached = "after query";
				}
				caught = "none";
			} catch ( any e ) {
				caught = e.type;
			}
			ormClearSession();
			""".formatted( name ), context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "caught" ) ) ).isNotEqualTo( "none" );
		assertThat( variables.getAsString( Key.of( "reached" ) ) ).isEqualTo( "before query" );
		assertThat( committedCount( name ) ).isEqualTo( 0 );
	}
}
