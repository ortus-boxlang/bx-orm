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

public class EntitySaveTest extends BaseORMTest {

	@DisplayName( "It can save new entities to the database" )
	@Test
	public void testEntitySave() {
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

	@DisplayName( "It can save new entities to the database with forceinsert:true" )
	@Test
	public void testEntitySaveWithForceInsert() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction{
				manufacturer = entityNew( "manufacturer", { name : "Toyota", address : "101 Toyota Way" } )
				entitySave( manufacturer, true );
			}
			result = queryExecute( "SELECT * FROM manufacturers WHERE name = 'Toyota'" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Toyota" );
	}

	@DisplayName( "It tests the dirty states a of a new and saved entity" )
	@Test
	public void testDirtyStates() {
		// @formatter:off
		instance.executeSource(
			"""
			function isDirty( entity ){
					var sessionFactory = ormGetSessionFactory();
					var session = ormGetSession();
					var md = sessionFactory.getClassMetaData( "AbstractCategory" );
					println( "Identifier: " & md.getIdentifier( arguments.entity ) );
					var snapshot = md.getDatabaseSnapshot( md.getIdentifier( arguments.entity ), session );
					var currentState = md.getPropertyValues( arguments.entity );
					if( isNull( snapshot ) ){
						println( "Snapshot is null" );
						return false;
					}
					var modified = md.findModified(
						snapshot,
						currentState,
						arguments.entity,
						session
					);
					var dirtyArray = !isNull( local.modified ) ? modified : [];

					return ( arrayLen( dirtyArray ) > 0 );
			}
			transaction {
				try {
					category = entityNew( "AbstractCategory", { category : "Testing", description: "foo" } );
					preSaveDirty = isDirty( category );
					entitySave( category, true );
					ormFlush();
					postSaveDirty = isDirty( category );
					category.setDescription( "bar" );
					postModifyDirty = isDirty( category );
				} finally {
					transactionRollback();
				}
			}
			ormClearSession();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsBoolean( Key.of( "preSaveDirty" ) ) ).isFalse();
		assertThat( variables.getAsBoolean( Key.of( "postSaveDirty" ) ) ).isFalse();
		assertThat( variables.getAsBoolean( Key.of( "postModifyDirty" ) ) ).isTrue();
	}

}
