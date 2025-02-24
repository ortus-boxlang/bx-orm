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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		;

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
		assertEquals( "Bugatti Automobiles", variables.get( Key._NAME ) );
		assertEquals( "101 Bugatti Way", variables.get( Key.of( "address" ) ) );
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
		assertTrue( variables.get( hasManufacturerExternalAccessPre ) instanceof Boolean );
		assertTrue( variables.get( hasManufacturerInternalAccessPre ) instanceof Boolean );
		assertFalse( variables.getAsBoolean( hasManufacturerExternalAccessPre ) );
		assertFalse( variables.getAsBoolean( hasManufacturerInternalAccessPre ) );

		// AFTER manufacturer is set
		Key	hasManufacturerExternalAccessPost	= Key.of( "hasManufacturerExternalAccessPost" );
		Key	hasManufacturerInternalAccessPost	= Key.of( "hasManufacturerinternalAccessPost" );
		assertTrue( variables.get( hasManufacturerExternalAccessPost ) instanceof Boolean );
		assertTrue( variables.get( hasManufacturerInternalAccessPost ) instanceof Boolean );
		assertTrue( variables.getAsBoolean( hasManufacturerExternalAccessPost ) );
		assertTrue( variables.getAsBoolean( hasManufacturerInternalAccessPost ) );
	}
}
