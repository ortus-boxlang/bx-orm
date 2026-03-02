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

import org.junit.jupiter.api.Disabled;
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

	// @Disabled( "Fails! Need to prevent inner transaction rollbacks from rolling back the outer transaction." )
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

	@Disabled( "Still working on this test case." )
	@DisplayName( "Inner transaction shares Hibernate session with outer transaction" )
	@Test
	public void testORMNestedTransactionSessionSharing() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				entityNew( "manufacturer" );
				outerSession = ORMGetSession();
				
				transaction{
					entityNew( "manufacturer" );
					innerSession = ORMGetSession();
				}
			}
			outsideTransactionSession = ORMGetSession();
			""",
			context
		);
		// @formatter:on

		// Verify they are the same session object (session sharing)
		assertThat( variables.get( Key.of( "outerSession" ) ) ).isSameInstanceAs( variables.get( Key.of( "innerSession" ) ) );
		assertThat( variables.get( Key.of( "outerSession" ) ) ).isNotSameInstanceAs( variables.get( Key.of( "outsideTransactionSession" ) ) );
	}
}
