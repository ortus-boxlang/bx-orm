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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityLoadTest extends BaseORMTest {

	@DisplayName( "It can load array of entities by ID" )
	@Test
	public void testEntityLoadIDArray() {
		// @formatter:off
		instance.executeSource( """
			result = entityLoad( 'Vehicle', '1HGCM82633A123456');
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

	@Disabled( "Unimplemented" )
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
		assertThat( ( ( IClassRunnable ) variables.get( result ) ).get( "vin" ) ).isEqualTo( "load" );
	}

	@Disabled( "Unimplemented" )
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

	@Disabled( "Unimplemented" )
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

}
