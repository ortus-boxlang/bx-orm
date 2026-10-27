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
package ortus.boxlang.modules.orm.criteria;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Live tests for subclass properties queried from the base entity, on the TimeBox hierarchy: {@code User} (joined, with a
 * discriminator) and its subclasses {@code Employee} and {@code Contractor}, which both declare {@code costRate} and
 * {@code compensationType}; only {@code Employee} declares {@code salary}.
 * <p>
 * Each test runs on one employee (Emma: costRate 10, salary) and one contractor (Carl: costRate 20, fixed), inserted
 * before and removed after it.
 */
public class CriteriaSubtypePropertyTest extends CriteriaTestSupport {

	/** The base query: the two seeded users. */
	private static final String USERS = "entityCriteria( 'User' ).isLike( 'userId', 'amb-%' )";

	/**
	 * Insert the employee and the contractor.
	 */
	@BeforeEach
	public void seedUsers() {
		// @formatter:off
		instance.executeSource( """
			removeAmbiguousRows = () => {
				for ( var table in [ "employee", "contractor", "user" ] ) {
					queryExecute( "delete from #table# where userId like 'amb-%'" );
				}
			};
			removeAmbiguousRows();
			for ( u in [ [ "amb-e1", "employee", "Emma" ], [ "amb-c1", "contractor", "Carl" ] ] ) {
				queryExecute( "insert into user ( userId, userType, isActive, isPasswordReset, createdDate, updatedDate, country, fname, lname, timeZone, email, password )
					values ( ?, ?, 1, 0, now(), now(), 'US', ?, 'Test', 'UTC', ?, 'x' )", [ u[ 1 ], u[ 2 ], u[ 3 ], u[ 1 ] & "@x.com" ] );
			}
			queryExecute( "insert into employee ( userId, baseHours, hasPayroll, hasTimeOff, ptoPerYear, sickTimePerYear, startDate, compensationType, costRate, salary )
				values ( 'amb-e1', 40, 1, 1, 0, 0, now(), 'salary', 10, 75000 )" );
			queryExecute( "insert into contractor ( userId, hasPayroll, compensationType, costRate ) values ( 'amb-c1', 0, 'fixed', 20 )" );
		""", context );
		// @formatter:on
	}

	/**
	 * Remove the employee and the contractor.
	 */
	@AfterEach
	public void removeUsers() {
		instance.executeSource( "removeAmbiguousRows();", context );
	}

	/**
	 * Test: a property both subclasses declare is projected from the base entity with each row's own value (Hibernate 5
	 * read only one subclass's column, so the other subclass's rows were null), next to a property only one subclass
	 * declares.
	 */
	@DisplayName( "A property several subclasses declare is projected per row from the base entity" )
	@Test
	public void testProjectsEachRowsOwnValue() {
		List<Object> rows = list( "result = " + USERS
		    + ".withProjections( property = 'fname,costRate,compensationType,salary' ).asStruct().list( sortOrder = 'fname' );" );
		assertThat( rows ).hasSize( 2 );
		IStruct	carl	= ( IStruct ) rows.get( 0 );
		IStruct	emma	= ( IStruct ) rows.get( 1 );
		assertThat( carl.getAsString( Key.of( "fname" ) ) ).isEqualTo( "Carl" );
		assertThat( Double.parseDouble( carl.get( Key.of( "costRate" ) ).toString() ) ).isEqualTo( 20 );
		assertThat( carl.getAsString( Key.of( "compensationType" ) ) ).isEqualTo( "fixed" );
		assertThat( carl.get( Key.of( "salary" ) ) ).isNull();
		assertThat( Double.parseDouble( emma.get( Key.of( "costRate" ) ).toString() ) ).isEqualTo( 10 );
		assertThat( emma.getAsString( Key.of( "compensationType" ) ) ).isEqualTo( "salary" );
		assertThat( Double.parseDouble( emma.get( Key.of( "salary" ) ).toString() ) ).isEqualTo( 75000 );
	}

	/**
	 * Test: conditions and ordering on such a property match each row's own value.
	 */
	@DisplayName( "Conditions and ordering on a property several subclasses declare use each row's own value" )
	@Test
	public void testConditionsAndOrdering() {
		// Names are projected: loading TimeBox User entities needs the app's mementifier, which this test app lacks
		Array result = ( Array ) run( "result = [ "
		    + USERS + ".isEq( 'costRate', 20 ).withProjections( property = 'fname' ).list(), "
		    + USERS + ".isLike( 'compensationType', 'sal%' ).withProjections( property = 'fname' ).list(), "
		    + USERS + ".isGT( 'costRate', 5 ).count(), "
		    + USERS + ".order( 'costRate', 'desc' ).withProjections( property = 'fname' ).list() ];" );
		assertThat( ( ( Array ) result.get( 0 ) ).toList() ).containsExactly( "Carl" );
		assertThat( ( ( Array ) result.get( 1 ) ).toList() ).containsExactly( "Emma" );
		assertThat( Long.parseLong( result.get( 2 ).toString() ) ).isEqualTo( 2 );
		assertThat( ( ( Array ) result.get( 3 ) ).toList() ).containsExactly( "Carl", "Emma" ).inOrder();
	}

	/**
	 * Test: a path that continues past such a property is an orm.property.ambiguous error naming the subclasses.
	 */
	@DisplayName( "A path through a property several subclasses declare is orm.property.ambiguous" )
	@Test
	public void testPathThroughAmbiguousPropertyFails() {
		IStruct e = error( "x = " + USERS + ".isEq( 'costRate.value', 1 ).list();" );
		assertThat( e.getAsString( Key.of( "type" ) ) ).isEqualTo( "orm.property.ambiguous" );
		assertThat( e.getAsString( Key.of( "message" ) ) ).contains( "Employee" );
		assertThat( e.getAsString( Key.of( "message" ) ) ).contains( "Contractor" );
	}

}
