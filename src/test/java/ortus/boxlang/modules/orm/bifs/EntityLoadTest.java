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

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityLoadTest extends BaseORMTest {

	@DisplayName( "It can load array of entities with no filter or ID" )
	@Test
	public void testEntityLoadByNameOnly() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle' );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );
		assertThat( variables.getAsArray( result ).getFirst() ).isInstanceOf( IClassRunnable.class );
	}

	@DisplayName( "It can load array of entities by ID" )
	@Test
	public void testEntityLoadIDArray() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', '1HGCM82633A123456' );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );
		assertThat( variables.getAsArray( result ).getFirst() ).isInstanceOf( IClassRunnable.class );
	}

	@DisplayName( "It can load UNIQUE entity by ID" )
	@Test
	public void testEntityLoadIDUnique() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', '1HGCM82633A123456', true );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
	}

	@DisplayName( "It can load SINGLE entity by filter criteria" )
	@Test
	public void testEntityLoadFilterUnique() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', { 'vin' : '1HGCM82633A123456' }, true );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
		assertThat( ( ( IClassRunnable ) variables.get( result ) ).get( "vin" ) ).isEqualTo( "1HGCM82633A123456" );
	}

	@DisplayName( "It can specify UPPERCASE property names for cfml compat" )
	@Test
	public void testEntityLoadWrongCasedProperties() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', { VIN : '1HGCM82633A123456' }, true );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( IClassRunnable.class );
		assertThat( ( ( IClassRunnable ) variables.get( result ) ).get( "vin" ) ).isEqualTo( "1HGCM82633A123456" );
	}

	@DisplayName( "It can load array of entities by filter criteria" )
	@Test
	public void testEntityLoadFilterArray() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', { 'make' : 'Honda' } );
		""", context );
		// @formatter:on
		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );
		variables.getAsArray( result ).forEach( item -> {
			assertThat( item ).isInstanceOf( IClassRunnable.class );
			assertThat( ( ( IClassRunnable ) item ).get( "make" ) ).isEqualTo( "Honda" );
		} );
	}

	@DisplayName( "It can load array of entities by filter criteria, sorting by custom order clause" )
	@Test
	public void testEntityLoadFilterSort() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', { 'make' : 'Honda' }, 'model DESC' );
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );

		Array	vehicles	= variables.getAsArray( result );
		var		first		= ( ( IClassRunnable ) vehicles.getAt( 1 ) );
		var		second		= ( ( IClassRunnable ) vehicles.getAt( 2 ) );
		var		third		= ( ( IClassRunnable ) vehicles.getAt( 3 ) );

		assertThat( first.get( "model" ) ).isEqualTo( "Ridgeline" );
		assertThat( second.get( "model" ) ).isEqualTo( "Civic" );
		assertThat( third.get( "model" ) ).isEqualTo( "Accord" );
	}

	@DisplayName( "It can load array of entities with maxResults and offset options" )
	@Test
	public void testEntityLoadMaxAndOffset() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( "Vehicle", { "make" : "Honda" }, "model ASC", { maxResults : 2, offset: 1 } )
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );

		Array vehicles = variables.getAsArray( result );
		assertThat( vehicles.size() ).isEqualTo( 2 );
		var	first	= ( ( IClassRunnable ) vehicles.getAt( 1 ) );
		var	second	= ( ( IClassRunnable ) vehicles.getAt( 2 ) );

		// skips or "offsets" past Accord, so Civic is first
		assertThat( first.get( "model" ) ).isEqualTo( "Civic" );
		assertThat( second.get( "model" ) ).isEqualTo( "Ridgeline" );
	}

	@DisplayName( "It can take options as the third argument" )
	@Test
	public void testEntityLoadOptionsThird() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( "Vehicle", { "make" : "Honda" }, { maxResults : 2, offset: 1 } )
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );

		Array vehicles = variables.getAsArray( result );
		assertThat( vehicles.size() ).isEqualTo( 2 );
	}

	@DisplayName( "It can load a subclass entity by a parent property value" )
	@Test
	public void testEntityLoadParentProperty() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad(
				'cbEntry',
				{ slug : 'copy-of-copy-of-another-test' },
				"",
				{
					ignorecase : true,
					timeout    : 0
				}
				);
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );

		Array	entries	= variables.getAsArray( result );
		var		first	= ( ( IClassRunnable ) entries.getAt( 1 ) );

		assertThat( first.get( "slug" ) ).isEqualTo( "copy-of-copy-of-another-test" );
	}

	@DisplayName( "It can load an entity by a relationship" )
	@Test
	public void testEntityLoadRelationship() {
		// @formatter:off
		instance.executeSource( """
			author = entityLoadByPK( "cbAuthor", "77abddba-a444-11eb-ab6f-0290cc502ae3" );
			result = entityLoad(
				'cbContent',
				{ "creator" : author }
				);
		""", context );
		// @formatter:on

		assertThat( variables.get( result ) ).isNotNull();
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );

		Array	entries	= variables.getAsArray( result );
		var		first	= ( ( IClassRunnable ) entries.getAt( 1 ) );

		assertThat( ( ( IClassRunnable ) first.get( "creator" ) ).get( "username" ) ).isEqualTo( "lmajano" );
	}

}
