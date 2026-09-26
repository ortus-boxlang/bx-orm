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
 * entityToStruct(): defaults, this.memento (defaultIncludes, neverInclude, defaults, mappers, profiles), includes,
 * excludes, nested paths, cycles and ISO dates. MemoAuthor declares a this.memento; Manufacturer does not.
 */
public class EntityToStructTest extends BaseORMTest {

	/**
	 * Add an author with two posts.
	 */
	@BeforeEach
	public void addRows() {
		instance.executeSource(
		    """
		    queryExecute( "INSERT INTO memo_authors ( id, firstName, lastName, passwordHash, joined ) VALUES ( 901, 'Ann', 'Baker', 'secret', '2024-03-05 10:20:30' )" );
		    queryExecute( "INSERT INTO memo_posts ( id, title, author_id ) VALUES ( 901, 'First', 901 ), ( 902, 'Second', 901 )" );
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
	 * Without a this.memento, the struct holds the id and plain properties.
	 */
	@DisplayName( "Without this.memento the struct holds the id and plain properties" )
	@Test
	public void testPlainDefaults() {
		IStruct s = ( IStruct ) run( "result = entityToStruct( entityLoadByPK( 'Manufacturer', 1 ) );" );
		assertThat( s.getKeysAsStrings() ).containsExactly( "id", "name", "address" ).inOrder();
		assertThat( s.getAsString( Key.of( "name" ) ) ).isEqualTo( "Ford Motor Company" );
		Array a = ( Array ) run( "result = entityToStruct( entityLoad( 'Manufacturer', {}, 'id' ) );" );
		assertThat( a.size() ).isEqualTo( 3 );
	}

	/**
	 * this.memento drives the default output: includes, computed getters, neverInclude, defaults, mappers, ISO dates.
	 */
	@DisplayName( "this.memento defaults, getters, neverInclude, defaults, mappers and ISO dates" )
	@Test
	public void testMemento() {
		IStruct s = ( IStruct ) run( "result = entityToStruct( entityLoadByPK( 'MemoAuthor', 901 ) );" );
		assertThat( s.getKeysAsStrings() ).containsExactly( "id", "firstName", "lastName", "nickName", "fullName", "joined", "initials" ).inOrder();
		assertThat( s.getAsString( Key.of( "lastName" ) ) ).isEqualTo( "BAKER" );
		assertThat( s.getAsString( Key.of( "nickName" ) ) ).isEqualTo( "none" );
		assertThat( s.getAsString( Key.of( "fullName" ) ) ).isEqualTo( "Ann Baker" );
		assertThat( s.getAsString( Key.of( "initials" ) ) ).isEqualTo( "AB" );
		assertThat( s.getAsString( Key.of( "joined" ) ) ).startsWith( "2024-03-05T10:20:30" );
		assertThat( s.containsKey( Key.of( "passwordHash" ) ) ).isFalse();
		// neverInclude wins over an explicit include
		IStruct forced = ( IStruct ) run( "result = entityToStruct( entityLoadByPK( 'MemoAuthor', 901 ), { includes : 'passwordHash' } );" );
		assertThat( forced.containsKey( Key.of( "passwordHash" ) ) ).isFalse();
	}

	/**
	 * Associations: a to-many becomes an array of structs; a nested path picks fields; a cycle ends with the id.
	 */
	@DisplayName( "Associations, nested paths and cycles" )
	@Test
	public void testAssociations() {
		IStruct	s		= ( IStruct ) run( "result = entityToStruct( entityLoadByPK( 'MemoAuthor', 901 ), { includes : 'posts' } );" );
		Array	posts	= s.getAsArray( Key.of( "posts" ) );
		assertThat( posts.size() ).isEqualTo( 2 );
		assertThat( ( ( IStruct ) posts.get( 0 ) ).getKeysAsStrings() ).containsExactly( "id", "title" ).inOrder();

		s		= ( IStruct ) run(
		    "result = entityToStruct( entityLoadByPK( 'MemoAuthor', 901 ), { includes : 'posts.title,posts.author', ignoreDefaults : true } );" );
		posts	= s.getAsArray( Key.of( "posts" ) );
		assertThat( s.getKeysAsStrings() ).containsExactly( "posts" );
		// the post's author is the author being written: its id ends the cycle
		assertThat( ( ( IStruct ) posts.get( 0 ) ).get( Key.of( "author" ) ).toString() ).isEqualTo( "901" );
		assertThat( ( ( IStruct ) posts.get( 0 ) ).getAsString( Key.of( "title" ) ) ).isEqualTo( "First" );

		IStruct post = ( IStruct ) run( "result = entityToStruct( entityLoadByPK( 'MemoPost', 902 ), { includes : 'author.firstName' } );" );
		assertThat( post.getAsStruct( Key.of( "author" ) ).getKeysAsStrings() ).containsExactly( "firstName" );
	}

	/**
	 * Excludes (top-level and nested), aliases, profiles and caller mappers and defaults.
	 */
	@DisplayName( "Excludes, aliases, profiles, caller mappers and defaults" )
	@Test
	public void testOptions() {
		IStruct s = ( IStruct ) run(
		    "result = entityToStruct( entityLoadByPK( 'MemoAuthor', 901 ), { includes : 'posts,firstName:first', excludes : 'joined,initials,posts.title' } );" );
		assertThat( s.containsKey( Key.of( "joined" ) ) ).isFalse();
		assertThat( s.containsKey( Key.of( "initials" ) ) ).isFalse();
		assertThat( s.getAsString( Key.of( "first" ) ) ).isEqualTo( "Ann" );
		assertThat( ( ( IStruct ) s.getAsArray( Key.of( "posts" ) ).get( 0 ) ).containsKey( Key.of( "title" ) ) ).isFalse();

		IStruct export = ( IStruct ) run( "result = entityToStruct( entityLoadByPK( 'MemoAuthor', 901 ), { profile : 'export' } );" );
		assertThat( export.getKeysAsStrings() ).containsExactly( "id", "lastName" ).inOrder();
		assertThat( export.getAsString( Key.of( "lastName" ) ) ).isEqualTo( "Baker" );

		IStruct mapped = ( IStruct ) run(
		    "result = entityToStruct( entityLoadByPK( 'Manufacturer', 1 ), { includes : 'shout', mappers : { name : ( v ) => v.len(), shout : ( v, m ) => ucase( m.address ) } } );" );
		assertThat( mapped.get( Key.of( "name" ) ).toString() ).isEqualTo( "18" );
		assertThat( mapped.getAsString( Key.of( "shout" ) ) ).isEqualTo( "202 FORD WAY, DEARBORN MI" );
	}

	/**
	 * Bad input is a clear error.
	 */
	@DisplayName( "Unknown includes and non-entities are clear errors" )
	@Test
	public void testErrors() {
		assertThat( run(
		    "try { entityToStruct( entityLoadByPK( 'Manufacturer', 1 ), { includes : 'nmae' } ); result = 'NO ERROR'; } catch ( any e ) { result = e.type; }" ) )
		    .isEqualTo( "orm.property.unknown" );
		assertThat( run( "try { entityToStruct( { a : 1 } ); result = 'NO ERROR'; } catch ( any e ) { result = e.type; }" ) ).isEqualTo( "orm.argument" );
	}
}
