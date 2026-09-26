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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * Phase 4 small helpers: entityLoadByPK with an array of ids, entitySave / entityDelete with arrays and
 * { flush : true }, and the defaultSort entity annotation.
 */
public class GormHelpersTest extends BaseORMTest {

	/**
	 * Remove the rows these tests add.
	 */
	@AfterEach
	public void cleanUp() {
		instance.executeSource( """
		                        queryExecute( "DELETE FROM manufacturers WHERE name LIKE 'Gorm %'" );
		                        queryExecute( "DELETE FROM sorted_people" );
		                        """, context );
	}

	/**
	 * An array of ids returns entities in the asked order, with null for missing ids.
	 */
	@DisplayName( "entityLoadByPK with an array returns entities in the asked order, null for missing ids" )
	@Test
	public void testLoadByPKArray() {
		// @formatter:off
		instance.executeSource(
			"""
			found   = entityLoadByPK( "Manufacturer", [ 42, 1, 99999, 42 ] );
			names   = found.map( ( m ) => isNull( m ) ? "NULL" : m.getName() ).toList( "|" );
			same    = found[ 1 ] === found[ 4 ];
			empty   = entityLoadByPK( "Manufacturer", [] ).len();
			types   = entityLoadByPK( "VehicleType", [ { make : "Honda", model : "Civic" }, { make : "Ford", model : "Fusion" } ] )
				.map( ( t ) => t.getDescription() ).toList( "|" );
			readOnly = entityLoadByPK( "Manufacturer", [ 77 ], { readOnly : true } )[ 1 ].getName();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "names" ) ) ).isEqualTo( "Honda Motor Co.|Ford Motor Company|NULL|Honda Motor Co." );
		assertThat( variables.get( Key.of( "same" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "empty" ) ) ).isEqualTo( 0 );
		assertThat( variables.getAsString( Key.of( "types" ) ) ).isEqualTo( "Compact sedan|Mid-size sedan" );
		assertThat( variables.getAsString( Key.of( "readOnly" ) ) ).isEqualTo( "General Moters Corporation" );
	}

	/**
	 * entitySave takes an array and flushes on request, including the options struct in the forceInsert position.
	 */
	@DisplayName( "entitySave saves an array and flushes with { flush : true }" )
	@Test
	public void testSaveArrayAndFlush() {
		// @formatter:off
		instance.executeSource(
			"""
			a = entityNew( "Manufacturer", { name : "Gorm A" } );
			b = entityNew( "Manufacturer", { name : "Gorm B" } );
			entitySave( [ a, b ], { flush : true } );
			afterArray = queryExecute( "SELECT id FROM manufacturers WHERE name LIKE 'Gorm %'" ).recordCount;
			c = entityNew( "Manufacturer", { name : "Gorm C" } );
			entitySave( c, false, { flush : true } );
			afterThird = queryExecute( "SELECT id FROM manufacturers WHERE name LIKE 'Gorm %'" ).recordCount;
			try { entitySave( [ a, "nope" ] ); badType = "NO ERROR"; } catch ( any e ) { badType = e.type; }
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "afterArray" ) ) ).isEqualTo( 2 );
		assertThat( variables.get( Key.of( "afterThird" ) ) ).isEqualTo( 3 );
		assertThat( variables.getAsString( Key.of( "badType" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * entityDelete takes an array and flushes on request.
	 */
	@DisplayName( "entityDelete deletes an array and flushes with { flush : true }" )
	@Test
	public void testDeleteArrayAndFlush() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entitySave( [ entityNew( "Manufacturer", { name : "Gorm D" } ), entityNew( "Manufacturer", { name : "Gorm E" } ) ] );
			}
			ormClearSession();
			transaction {
				doomed = entityLoad( "Manufacturer" ).filter( ( m ) => m.getName().startsWith( "Gorm " ) );
				entityDelete( doomed, { flush : true } );
				insideTx = queryExecute( "SELECT id FROM manufacturers WHERE name LIKE 'Gorm %'" ).recordCount;
			}
			after = queryExecute( "SELECT id FROM manufacturers WHERE name LIKE 'Gorm %'" ).recordCount;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "insideTx" ) ) ).isEqualTo( 0 );
		assertThat( variables.get( Key.of( "after" ) ) ).isEqualTo( 0 );
	}

	/**
	 * defaultSort orders entityLoad and criteria results when no order is given; an explicit order wins.
	 */
	@DisplayName( "defaultSort orders entityLoad and criteria when no order is given" )
	@Test
	public void testDefaultSort() {
		// @formatter:off
		instance.executeSource(
			"""
			queryExecute( "INSERT INTO sorted_people ( id, firstName, lastName ) VALUES
				( 1, 'Ann', 'Baker' ), ( 2, 'Zed', 'Adams' ), ( 3, 'Bob', 'Baker' ), ( 4, 'Amy', 'Adams' )" );
			ids       = ( list ) => list.map( ( p ) => p.getId() ).toList();
			loadAll   = ids( entityLoad( "SortedPerson" ) );
			loadWhere = ids( entityLoad( "SortedPerson", { lastName : "Baker" } ) );
			explicit  = ids( entityLoad( "SortedPerson", {}, "id desc" ) );
			criteria  = ids( entityCriteria( "SortedPerson" ).list() );
			ordered   = ids( entityCriteria( "SortedPerson" ).order( "id" ).list() );
			counted   = entityCriteria( "SortedPerson" ).count();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "loadAll" ) ) ).isEqualTo( "2,4,3,1" );
		assertThat( variables.getAsString( Key.of( "loadWhere" ) ) ).isEqualTo( "3,1" );
		assertThat( variables.getAsString( Key.of( "explicit" ) ) ).isEqualTo( "4,3,2,1" );
		assertThat( variables.getAsString( Key.of( "criteria" ) ) ).isEqualTo( "2,4,3,1" );
		assertThat( variables.getAsString( Key.of( "ordered" ) ) ).isEqualTo( "1,2,3,4" );
		assertThat( ( ( Number ) variables.get( Key.of( "counted" ) ) ).intValue() ).isEqualTo( 4 );
	}
}
