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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class TransactionManagementTest extends BaseORMTest {

	@DisplayName( "Can save/flush entity modifications outside of a transaction block" )
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
	}

	@DisplayName( "It wont cause table/connection locking when ORM and native JDBC queries coexist" )
	@Test
	public void testORMAndNativeQueryCoexistence() {
		// @formatter:off
		instance.executeSource(
			"""
			entitySave( entityNew( "manufacturer", { name : "Audi Corp", address : "101 Audi Way" } ), true );
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
}
