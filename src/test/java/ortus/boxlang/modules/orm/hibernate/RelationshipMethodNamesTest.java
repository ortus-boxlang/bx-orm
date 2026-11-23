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

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * The generated relationship methods: {@code hasX()}, {@code addX()} and {@code removeX()}.
 * <p>
 * Without a {@code singularName} they are named after the property ({@code hasNotes()}). With one, they are named after
 * it only ({@code hasVehicle()}), as in Adobe ColdFusion and Lucee: there is no {@code hasVehicles()}.
 * Fixtures: models/Manufacturer.bx ({@code vehicles}, singularName {@code Vehicle}) and models/native/BareFlagFolder.bx
 * ({@code notes}, no singularName).
 */
public class RelationshipMethodNamesTest extends BaseORMTest {

	@AfterEach
	public void clearSession() {
		instance.executeSource( "try { ormClearSession(); } catch ( any e ) {}", context );
	}

	@DisplayName( "Without a singularName, the methods are named after the property only" )
	@Test
	public void testPluralOnly() {
		// @formatter:off
		instance.executeSource( """
			folder   = entityNew( "BareFlagFolder", { name : "plural" } );
			plural   = isCustomFunction( folder.hasNotes ) && isCustomFunction( folder.addNotes ) && isCustomFunction( folder.removeNotes );
			singular = structKeyExists( folder, "hasNote" ) || structKeyExists( folder, "addNote" ) || structKeyExists( folder, "removeNote" );
			""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "plural" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "singular" ) ) ).isEqualTo( false );
	}

	@DisplayName( "With a singularName, the methods are named after it only" )
	@Test
	public void testSingularOnly() {
		// @formatter:off
		instance.executeSource( """
			m        = entityNew( "Manufacturer", { name : "Singular" } );
			singular = isCustomFunction( m.hasVehicle ) && isCustomFunction( m.addVehicle ) && isCustomFunction( m.removeVehicle );
			plural   = structKeyExists( m, "hasVehicles" ) || structKeyExists( m, "addVehicles" ) || structKeyExists( m, "removeVehicles" );
			""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "singular" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "plural" ) ) ).isEqualTo( false );
	}

	@DisplayName( "The singular methods add, find and remove items" )
	@Test
	public void testSingularMethods() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				try {
					m = entityNew( "Manufacturer", { name : "Singular" } );
					entitySave( m );
					v1 = entityNew( "Vehicle", { vin : "SINGPLUR000000001", model : "One" } );
					v2 = entityNew( "Vehicle", { vin : "SINGPLUR000000002", model : "Two" } );
					v1.setManufacturer( m );
					v2.setManufacturer( m );
					entitySave( [ v1, v2 ] );

					m.addVehicle( v1 ).addVehicle( v2 );
					anyHas      = m.hasVehicle();
					hasBoth     = m.hasVehicle( v1 ) && m.hasVehicle( v2 );
					afterAdd    = m.getVehicles().len();

					m.removeVehicle( v1 );
					stillHasV1  = m.hasVehicle( v1 );
					afterRemove = m.getVehicles().len();
				} finally {
					transactionRollback();
				}
			}
			""", context );
		// @formatter:on
		assertThat( variables.get( Key.of( "anyHas" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "hasBoth" ) ) ).isEqualTo( true );
		assertThat( ( ( Number ) variables.get( Key.of( "afterAdd" ) ) ).intValue() ).isEqualTo( 2 );
		assertThat( variables.get( Key.of( "stillHasV1" ) ) ).isEqualTo( false );
		assertThat( ( ( Number ) variables.get( Key.of( "afterRemove" ) ) ).intValue() ).isEqualTo( 1 );
	}
}
