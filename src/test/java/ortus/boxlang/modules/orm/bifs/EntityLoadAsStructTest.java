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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import tools.BaseORMTest;

/**
 * entityLoadAsStruct() and criteria asStruct( includes ): structs read with projection queries, matching
 * entityToStruct().
 */
public class EntityLoadAsStructTest extends BaseORMTest {

	/**
	 * Add an author with two posts.
	 */
	@BeforeEach
	public void addRows() {
		instance.executeSource(
		    """
		    queryExecute( "INSERT INTO memo_authors ( id, firstName, lastName, passwordHash, joined, email, handle ) VALUES ( 901, 'Ann', 'Baker', 'secret', '2024-03-05 10:20:30', 'ANN@EXAMPLE.COM', 'x1' )" );
		    queryExecute( "INSERT INTO memo_posts ( id, title, author_id ) VALUES ( 902, 'Second', 901 ), ( 901, 'First', 901 )" );
		    """,
		    context );
	}

	/**
	 * Remove the rows.
	 */
	@AfterEach
	public void removeRows() {
		instance.executeSource( """
		                        ormClearSession();
		                        queryExecute( "DELETE FROM memo_posts" );
		                        queryExecute( "DELETE FROM memo_authors" );
		                        """, context );
	}

	/**
	 * Run code that sets result and return it.
	 *
	 * @param code BoxLang code.
	 *
	 * @return The result.
	 */
	private Object run( String code ) {
		instance.executeSource( code, context );
		return variables.get( Key.of( "result" ) );
	}

	/**
	 * By id: the entity's memento defaults (a getter in them is left out), mappers, defaults and ISO dates.
	 */
	@DisplayName( "By id: memento defaults, mappers, defaults and ISO dates, without loading the entity" )
	@Test
	public void testById() {
		IStruct s = ( IStruct ) run( "result = entityLoadAsStruct( 'MemoAuthor', 901 );" );
		assertThat( s.getKeysAsStrings() ).containsExactly( "id", "firstName", "lastName", "nickName", "joined", "initials" ).inOrder();
		assertThat( s.getAsString( Key.of( "lastName" ) ) ).isEqualTo( "BAKER" );
		assertThat( s.getAsString( Key.of( "nickName" ) ) ).isEqualTo( "none" );
		assertThat( s.getAsString( Key.of( "initials" ) ) ).isEqualTo( "AB" );
		assertThat( s.getAsString( Key.of( "joined" ) ) ).startsWith( "2024-03-05T10:20:30" );
		assertThat( run( "result = isNull( entityLoadAsStruct( 'MemoAuthor', 99999 ) );" ) ).isEqualTo( true );
		assertThat( run( "result = ormGetSessionStatistics().entityCount;" ).toString() ).isEqualTo( "0" );
	}

	/**
	 * The struct matches entityToStruct() for the same includes.
	 */
	@DisplayName( "Matches entityToStruct() for the same includes" )
	@Test
	public void testMatchesEntityToStruct() {
		// @formatter:off
		instance.executeSource( """
			projected = entityLoadAsStruct( "MemoAuthor", 901, "posts", { excludes : "fullName" } );
			loaded    = entityToStruct( entityLoadByPK( "MemoAuthor", 901 ), { includes : "posts", excludes : "fullName" } );
			same      = jsonSerialize( projected ) == jsonSerialize( loaded );
			json      = jsonSerialize( projected );
		""", context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "json" ) ) ).contains( "\"posts\":[{\"id\":901,\"title\":\"First\"},{\"id\":902,\"title\":\"Second\"}]" );
		assertThat( variables.get( Key.of( "same" ) ) ).isEqualTo( true );
	}

	/**
	 * A filter returns an array; to-one paths come from joins; a missing to-one uses the default.
	 */
	@DisplayName( "Filters, to-one paths, sorting and a null to-one" )
	@Test
	public void testFilterAndToOne() {
		Array rows = ( Array ) run( "result = entityLoadAsStruct( 'Vehicle', { make : 'Honda' }, 'manufacturer.name', { sortOrder : 'model desc' } );" );
		assertThat( rows.size() ).isEqualTo( 3 );
		IStruct first = ( IStruct ) rows.get( 0 );
		assertThat( first.getAsString( Key.of( "model" ) ) ).isEqualTo( "Ridgeline" );
		assertThat( first.getAsStruct( Key.of( "manufacturer" ) ).getKeysAsStrings() ).containsExactly( "name" );
		assertThat( first.getAsStruct( Key.of( "manufacturer" ) ).getAsString( Key.of( "name" ) ) ).isEqualTo( "Honda Motor Co." );
		IStruct orphan = ( IStruct ) run( "result = entityLoadAsStruct( 'Vehicle', '0SB123', 'manufacturer.name' );" );
		assertThat( orphan.get( Key.of( "manufacturer" ) ) ).isEqualTo( "" );
	}

	/**
	 * Criteria asStruct( includes ): collections from one extra query, grouped by parent, with paging.
	 */
	@DisplayName( "Criteria asStruct( includes ) with collections and paging" )
	@Test
	public void testCriteria() {
		Array rows = ( Array ) run( "result = entityCriteria( 'Manufacturer' ).order( 'id' ).asStruct( 'vehicles.model' ).list();" );
		assertThat( rows.size() ).isEqualTo( 3 );
		assertThat( ( ( IStruct ) rows.get( 0 ) ).getAsArray( Key.of( "vehicles" ) ).size() ).isEqualTo( 1 );
		assertThat( ( ( IStruct ) rows.get( 1 ) ).getAsArray( Key.of( "vehicles" ) ).size() ).isEqualTo( 3 );
		assertThat( ( ( IStruct ) rows.get( 2 ) ).getAsArray( Key.of( "vehicles" ) ).size() ).isEqualTo( 0 );
		IStruct page = ( IStruct ) run( "result = entityCriteria( 'Manufacturer' ).order( 'id' ).asStruct( 'name' ).paginate( 2, 2 );" );
		assertThat( page.getAsArray( Key.of( "results" ) ).size() ).isEqualTo( 1 );
		IStruct one = ( IStruct ) run( "result = entityCriteria( 'Manufacturer' ).isEq( 'id', 42 ).asStruct( 'vehicles' ).get();" );
		assertThat( one.getAsArray( Key.of( "vehicles" ) ).size() ).isEqualTo( 3 );
	}

	/**
	 * Profiles, plain asStruct() dates and errors.
	 */
	@DisplayName( "Profiles, plain asStruct() ISO dates, and errors" )
	@Test
	public void testProfilesDatesAndErrors() {
		IStruct export = ( IStruct ) run( "result = entityLoadAsStruct( 'MemoAuthor', 901, '', { profile : 'export' } );" );
		assertThat( export.getKeysAsStrings() ).containsExactly( "id", "lastName" ).inOrder();
		assertThat( export.getAsString( Key.of( "lastName" ) ) ).isEqualTo( "Baker" );
		Object joined = run( "result = entityCriteria( 'MemoAuthor' ).asStruct().list()[ 1 ].joined;" );
		assertThat( joined.toString() ).startsWith( "2024-03-05T10:20:30" );
		assertThat( run( "try { entityLoadAsStruct( 'MemoAuthor', 901, 'fullName' ); result = 'NO ERROR'; } catch ( any e ) { result = e.type; }" ) )
		    .isEqualTo( "orm.argument" );
		assertThat(
		    run( "try { entityCriteria( 'MemoAuthor' ).asStruct( 'id' ).each( ( r ) => r ); result = 'NO ERROR'; } catch ( any e ) { result = e.type; }" ) )
		    .isEqualTo( "orm.argument" );
		assertThat( run( "try { entityLoadAsStruct( 'MemoAuthor', 901, 'nmae' ); result = 'NO ERROR'; } catch ( any e ) { result = e.type; }" ) )
		    .isEqualTo( "orm.property.unknown" );
	}

	/**
	 * Hand-written getters shape the value here too, including one that reads a sibling property, and the output still
	 * matches entityToStruct().
	 */
	@DisplayName( "Hand-written getters shape the value, as in entityToStruct()" )
	@Test
	public void testOverriddenGetters() {
		// @formatter:off
		instance.executeSource( """
			projected = entityLoadAsStruct( "MemoAuthor", 901, "email,handle", { excludes : "fullName" } );
			listed    = entityCriteria( "MemoAuthor" ).isEq( "id", 901 ).asStruct( "email,handle", { excludes : "fullName" } ).list()[ 1 ];
			loaded    = entityToStruct( entityLoadByPK( "MemoAuthor", 901 ), { includes : "email,handle", excludes : "fullName" } );
			same      = jsonSerialize( projected ) == jsonSerialize( loaded ) && jsonSerialize( listed ) == jsonSerialize( loaded );
			loadedNow = ormGetSessionStatistics().entityCount;
		""", context );
		// @formatter:on
		IStruct projected = variables.getAsStruct( Key.of( "projected" ) );
		assertThat( projected.getAsString( Key.of( "email" ) ) ).isEqualTo( "ann@example.com" );
		assertThat( projected.getAsString( Key.of( "handle" ) ) ).isEqualTo( "ann_x1" );
		assertThat( variables.get( Key.of( "same" ) ) ).isEqualTo( true );
	}
}
