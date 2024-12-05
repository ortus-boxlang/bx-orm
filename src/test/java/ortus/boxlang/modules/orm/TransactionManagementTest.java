package ortus.boxlang.modules.orm;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

@Disabled( "connection deadlocks" )
public class TransactionManagementTest extends BaseORMTest {

	// @Disabled( "Unimplemented." )
	@DisplayName( "It automatically begins a Hibernate session and transaction when you call an ORM method" )
	@Test
	public void testAutomaticTransactions() {
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ) );
			ormFlush();
			""",
			context
		);
		// @formatter:on
		// assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		// assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Audi Corp" );
	}

	@Disabled( "Broken. This HAS to be figured out for existing CFML apps to work." )
	@DisplayName( "It wont cause table/connection locking when ORM and native JDBC queries coexist" )
	@Test
	public void testORMAndNativeQueryCoexistence() {
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ) );
			ormFlush();
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Audi Corp'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Audi Corp" );
	}

	@DisplayName( "It commits on transaction close" )
	@Test
	public void testTransactionCommitOnClose() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ) );
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Audi Corp'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Audi Corp" );
	}

	@DisplayName( "It rolls back on transaction rollback" )
	@Test
	public void testTransactionRollback() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ) );
				transactionRollback();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Audi Corp'" );
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
				entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ) );
				transactionCommit();
				transactionRollback();
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Audi Corp'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Audi Corp" );
	}
}
