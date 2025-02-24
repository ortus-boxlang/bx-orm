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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class EntityNewTest extends BaseORMTest {

	@DisplayName( "It can create new entities" )
	@Test
	public void testEntityNew() {
		// @formatter:off
		instance.executeSource(
			"""
				result = entityNew( "Manufacturer" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
	}

	@Disabled( "Can't get the test working." )
	@DisplayName( "It emits events" )
	@Test
	public void testEntityNewEvent() {
		variables.put( "post_new_fired", "false" );

		// DynamicObject listener = DynamicObject.of( ( properties ) -> {
		// variables.put( "post_new_fired", "true" );
		// } );
		// instance.getInterceptorService().register( listener, ORMKeys.EVENT_POST_NEW );

		// @formatter:off
		instance.executeSource(
			"""
				entityNew( "Manufacturer" );
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( "post_new_fired" ) ).isEqualTo( "true" );
		variables.put( "post_new_fired", "false" );
	}

	@DisplayName( "It can populate new entities with data" )
	@Test
	public void testEntityNewWithProperties() {
		// @formatter:off
		instance.executeSource(
			"""
				result = entityNew( "Manufacturer", { name : "Bugatti Automobiles", address : "101 Bugatti Way" } );
				name = result.getName();
				address = result.getAddress();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( IClassRunnable.class, variables.get( result ) );
		assertThat( variables.get( Key._NAME ) ).isEqualTo( "Bugatti Automobiles" );
		assertThat( variables.get( Key.of( "address" ) ) ).isEqualTo( "101 Bugatti Way" );
	}

	@DisplayName( "It can populate new entities with association UDFs" )
	@Test
	public void testEntityNewAssociationUDFs() {
		// @formatter:off
		instance.executeSource(
		    """
			vehicle = entityNew( 'Vehicle', { name : 'Dodge Powerwagon' } );
			// test we can call generated methods externally
			hasManufacturerExternalAccessPre = vehicle.hasManufacturer();
			// call internal method which calls hasManufacturer() to test we can call generated methods internally
			hasManufacturerInternalAccessPre = vehicle.checkHasManufacturer();

			vehicle.setManufacturer( entityNew( "Manufacturer", { name : "Dodge" } ) );
			// test we can call generated methods externally
			hasManufacturerExternalAccessPost = vehicle.hasManufacturer();
			// call internal method which calls hasManufacturer() to test we can call generated methods internally
			hasManufacturerInternalAccessPost = vehicle.checkHasManufacturer();
			""", context
		);
		// @formatter:on
		// BEFORE manufacturer is set
		Key	hasManufacturerExternalAccessPre	= Key.of( "hasManufacturerExternalAccessPre" );
		Key	hasManufacturerInternalAccessPre	= Key.of( "hasManufacturerinternalAccessPre" );
		assertThat( variables.get( hasManufacturerExternalAccessPre ) ).isInstanceOf( Boolean.class );
		assertThat( variables.get( hasManufacturerInternalAccessPre ) ).isInstanceOf( Boolean.class );
		assertThat( variables.getAsBoolean( hasManufacturerExternalAccessPre ) ).isFalse();
		assertThat( variables.getAsBoolean( hasManufacturerInternalAccessPre ) ).isFalse();

		// AFTER manufacturer is set
		Key	hasManufacturerExternalAccessPost	= Key.of( "hasManufacturerExternalAccessPost" );
		Key	hasManufacturerInternalAccessPost	= Key.of( "hasManufacturerinternalAccessPost" );
		assertThat( variables.get( hasManufacturerExternalAccessPost ) ).isInstanceOf( Boolean.class );
		assertThat( variables.get( hasManufacturerInternalAccessPost ) ).isInstanceOf( Boolean.class );
		assertThat( variables.getAsBoolean( hasManufacturerExternalAccessPost ) ).isTrue();
		assertThat( variables.getAsBoolean( hasManufacturerInternalAccessPost ) ).isTrue();
	}

	@DisplayName( "It supports hasManufacturer() with value argument comparison" )
	@Test
	public void testHasValueComparison() {
		// @formatter:off
		instance.executeSource(
			"""
				vehicle = entityNew( 'Vehicle', { name : 'Dodge Powerwagon' } );
				feature = entityNew( "Feature", { name : "Windshield wipers", description : "Do I really need to describe this to you?" } );
				
				beforeSet = vehicle.hasFeature( feature );
				vehicle.addFeature( feature );
				
				afterSet                     = vehicle.hasFeature( feature );
				afterSetWithDifferentFeature = vehicle.hasFeature( entityNew( "Feature", { name : "Powered windows" } ) );
			""",
			context
		);
		// @formatter:on

		Key	beforeSet						= Key.of( "beforeSet" );
		Key	afterSet						= Key.of( "afterSet" );
		Key	afterSetWithDifferentFeature	= Key.of( "afterSetWithDifferentFeature" );

		assertThat( variables.get( beforeSet ) ).isInstanceOf( Boolean.class );
		assertThat( variables.get( afterSet ) ).isInstanceOf( Boolean.class );
		assertThat( variables.get( afterSetWithDifferentFeature ) ).isInstanceOf( Boolean.class );

		assertThat( variables.getAsBoolean( beforeSet ) ).isFalse();
		assertThat( variables.getAsBoolean( afterSet ) ).isTrue();
		assertThat( variables.getAsBoolean( afterSetWithDifferentFeature ) ).isFalse();
	}
}
