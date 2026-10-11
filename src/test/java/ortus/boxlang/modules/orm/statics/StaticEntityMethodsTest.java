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
package ortus.boxlang.modules.orm.statics;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.BoxClassSupport;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.StaticScope;
import tools.BaseORMTest;

/**
 * Static entity methods: {@code root.models.Manufacturer::get( 1 )} and the rest, added to every entity class at ORM startup.
 */
public class StaticEntityMethodsTest extends BaseORMTest {

	private static final Path MANUFACTURER = Path.of( "src/test/resources/app/models/Manufacturer.bx" );

	@DisplayName( "Loading: get, getOrFail, getAll, read, getReference" )
	@Test
	public void testLoading() {
		// @formatter:off
		instance.executeSource(
			"""
			byPosition = root.models.Manufacturer::get( 42 ).getName();
			byName     = root.models.Manufacturer::get( id = 1 ).getName();
			missing    = isNull( root.models.Manufacturer::get( 99999 ) );
			try { root.models.Manufacturer::getOrFail( 99999 ); failType = "NO ERROR"; } catch ( any e ) { failType = e.type; }
			allCount   = root.models.Manufacturer::getAll().len();
			ordered    = root.models.Manufacturer::getAll( [ 77, 1 ] ).map( m => m.getId() ).toList();
			fromList   = root.models.Manufacturer::getAll( "1,42" ).map( m => m.getId() ).toList();
			oneId      = root.models.Manufacturer::getAll( 77 ).len();
			readName   = root.models.Manufacturer::read( 1 ).getName();
			refName    = root.models.Manufacturer::getReference( 77 ).getName();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "byPosition" ) ) ).isEqualTo( "Honda Motor Co." );
		assertThat( variables.getAsString( Key.of( "byName" ) ) ).isEqualTo( "Ford Motor Company" );
		assertThat( variables.get( Key.of( "missing" ) ) ).isEqualTo( true );
		assertThat( variables.getAsString( Key.of( "failType" ) ) ).isEqualTo( "orm.notFound" );
		assertThat( variables.get( Key.of( "allCount" ) ) ).isEqualTo( 3 );
		assertThat( variables.getAsString( Key.of( "ordered" ) ) ).isEqualTo( "77,1" );
		assertThat( variables.getAsString( Key.of( "fromList" ) ) ).isEqualTo( "1,42" );
		assertThat( variables.get( Key.of( "oneId" ) ) ).isEqualTo( 1 );
		assertThat( variables.getAsString( Key.of( "readName" ) ) ).isEqualTo( "Ford Motor Company" );
		assertThat( variables.getAsString( Key.of( "refName" ) ) ).isEqualTo( "General Moters Corporation" );
	}

	@DisplayName( "Finding: findWhere, findWhereOrFail, findAllWhere, first, exists, count, list" )
	@Test
	public void testFinding() {
		// @formatter:off
		instance.executeSource(
			"""
			whereId    = root.models.Manufacturer::findWhere( { name : "Ford Motor Company" } ).getId();
			whereNone  = isNull( root.models.Manufacturer::findWhere( { name : "Nope" } ) );
			try { root.models.Manufacturer::findWhere( {} ); manyType = "NO ERROR"; } catch ( any e ) { manyType = e.type; }
			firstOfMany = isNull( root.models.Manufacturer::findWhere( {}, { uniqueFirst : true } ) );
			try { root.models.Manufacturer::findWhereOrFail( { name : "Nope" } ); orFailType = "NO ERROR"; } catch ( any e ) { orFailType = e.type; }
			allWhere   = root.models.Manufacturer::findAllWhere( {}, "name desc" ).map( m => m.getId() ).toList();
			firstName  = root.models.Manufacturer::first( {}, "name" ).getName();
			firstNone  = isNull( root.models.Manufacturer::first( { name : "Nope" } ) );
			existsId   = root.models.Manufacturer::exists( 42 );
			existsNot  = root.models.Manufacturer::exists( 99999 );
			existsBy   = root.models.Manufacturer::exists( { name : "Honda Motor Co." } );
			countAll   = root.models.Manufacturer::count();
			countBy    = root.models.Manufacturer::count( { name : "Ford Motor Company" } );
			page       = root.models.Manufacturer::list( { max : 2, offset : 1, sort : "id" } ).map( m => m.getId() ).toList();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "whereId" ) ) ).isEqualTo( 1 );
		assertThat( variables.get( Key.of( "whereNone" ) ) ).isEqualTo( true );
		assertThat( variables.getAsString( Key.of( "manyType" ) ) ).isEqualTo( "orm.query.nonUnique" );
		assertThat( variables.get( Key.of( "firstOfMany" ) ) ).isEqualTo( false );
		assertThat( variables.getAsString( Key.of( "orFailType" ) ) ).isEqualTo( "orm.notFound" );
		assertThat( variables.getAsString( Key.of( "allWhere" ) ) ).isEqualTo( "42,77,1" );
		assertThat( variables.getAsString( Key.of( "firstName" ) ) ).isEqualTo( "Ford Motor Company" );
		assertThat( variables.get( Key.of( "firstNone" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "existsId" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "existsNot" ) ) ).isEqualTo( false );
		assertThat( variables.get( Key.of( "existsBy" ) ) ).isEqualTo( true );
		assertThat( ( ( Number ) variables.get( Key.of( "countAll" ) ) ).longValue() ).isEqualTo( 3L );
		assertThat( ( ( Number ) variables.get( Key.of( "countBy" ) ) ).longValue() ).isEqualTo( 1L );
		assertThat( variables.getAsString( Key.of( "page" ) ) ).isEqualTo( "42,77" );
	}

	@DisplayName( "Queries: executeQuery, criteria, newCriteria" )
	@Test
	public void testQueries() {
		// @formatter:off
		instance.executeSource(
			"""
			hqlRows   = root.models.Manufacturer::executeQuery( "from Manufacturer where id = :id", { id : 1 } ).len();
			hqlUnique = root.models.Manufacturer::executeQuery( "from Manufacturer where id = :id", { id : 42 }, { unique : true } ).getName();
			critCount = root.models.Manufacturer::criteria().isEq( "name", "Ford Motor Company" ).count();
			newCrit   = root.models.Manufacturer::newCriteria().isGt( "id", 1 ).list().len();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "hqlRows" ) ) ).isEqualTo( 1 );
		assertThat( variables.getAsString( Key.of( "hqlUnique" ) ) ).isEqualTo( "Honda Motor Co." );
		assertThat( ( ( Number ) variables.get( Key.of( "critCount" ) ) ).longValue() ).isEqualTo( 1L );
		assertThat( variables.get( Key.of( "newCrit" ) ) ).isEqualTo( 2 );
	}

	@DisplayName( "Creating and writing: new, firstOrNew, firstOrCreate, updateWhere, deleteWhere" )
	@Test
	public void testWriting() {
		// @formatter:off
		instance.executeSource(
			"""
			fresh       = root.models.StaticThing::new( { name : "fresh" } );
			freshName   = fresh.getName();
			freshSaved  = !isNull( fresh.getId() );
			orNew       = root.models.StaticThing::firstOrNew( { name : "orNew" } );
			orNewSaved  = queryExecute( "SELECT id FROM static_things WHERE name = 'orNew'" ).recordCount;
			transaction {
				created = root.models.StaticThing::firstOrCreate( { name : "created" } );
			}
			transaction {
				again = root.models.StaticThing::firstOrCreate( { name : "created" } );
			}
			sameRow     = created.getId() == again.getId();
			transaction {
				updated = root.models.StaticThing::updateWhere( { name : "created" }, { name : "renamed" } );
			}
			transaction {
				deleted = root.models.StaticThing::deleteWhere( { name : "renamed" } );
			}
			remaining   = queryExecute( "SELECT id FROM static_things" ).recordCount;
			try { root.models.StaticThing::deleteWhere( {} ); emptyType = "NO ERROR"; } catch ( any e ) { emptyType = e.type; }
			try { root.models.StaticThing::updateWhere( {}, { name : "x" } ); emptyUpdateType = "NO ERROR"; } catch ( any e ) { emptyUpdateType = e.type; }
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "freshName" ) ) ).isEqualTo( "fresh" );
		assertThat( variables.get( Key.of( "freshSaved" ) ) ).isEqualTo( false );
		assertThat( variables.get( Key.of( "orNewSaved" ) ) ).isEqualTo( 0 );
		assertThat( variables.get( Key.of( "sameRow" ) ) ).isEqualTo( true );
		assertThat( ( ( Number ) variables.get( Key.of( "updated" ) ) ).longValue() ).isEqualTo( 1L );
		assertThat( ( ( Number ) variables.get( Key.of( "deleted" ) ) ).longValue() ).isEqualTo( 1L );
		assertThat( variables.get( Key.of( "remaining" ) ) ).isEqualTo( 0 );
		assertThat( variables.getAsString( Key.of( "emptyType" ) ) ).isEqualTo( "orm.argument" );
		assertThat( variables.getAsString( Key.of( "emptyUpdateType" ) ) ).isEqualTo( "orm.argument" );
	}

	@DisplayName( "An entity's own static and instance methods win; instances reach the added statics otherwise" )
	@Test
	public void testOwnMethodsWin() {
		// @formatter:off
		instance.executeSource(
			"""
			ownStatic     = root.models.StaticThing::count();
			addedStatic   = isNull( root.models.StaticThing::get( 99999 ) );
			thing         = entityNew( "StaticThing" );
			ownInstance   = thing.list();
			instanceCount = entityNew( "Manufacturer" ).count();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "ownStatic" ) ) ).isEqualTo( "own count" );
		assertThat( variables.get( Key.of( "addedStatic" ) ) ).isEqualTo( true );
		assertThat( variables.getAsString( Key.of( "ownInstance" ) ) ).isEqualTo( "own list" );
		assertThat( ( ( Number ) variables.get( Key.of( "instanceCount" ) ) ).longValue() ).isEqualTo( 3L );
	}

	@DisplayName( "A recompiled entity class gets its static methods back at request start and on instance creation" )
	@Test
	public void testRecompiledClass() throws IOException {
		String original = Files.readString( MANUFACTURER );
		try {
			// Edit the entity: the next lookup recompiles it into a new class with an empty static scope.
			touch( original + "\n// edited by StaticEntityMethodsTest (request start)\n" );
			// A new request: onRequestStart puts the methods back (trusted cache is off in tests).
			teardownEach();
			setupEach();
			// @formatter:off
			instance.executeSource(
				"""
				afterRequestStart = root.models.Manufacturer::get( 1 ).getName();
				""",
				context
			);
			// @formatter:on
			assertThat( variables.getAsString( Key.of( "afterRequestStart" ) ) ).isEqualTo( "Ford Motor Company" );

			// Edit again, mid-request: creating an instance of the new class puts the methods back.
			touch( original + "\n// edited by StaticEntityMethodsTest (instance creation)\n" );
			// @formatter:off
			instance.executeSource(
				"""
				m = new root.models.Manufacturer();
				afterInstance = root.models.Manufacturer::get( 42 ).getName();
				""",
				context
			);
			// @formatter:on
			assertThat( variables.getAsString( Key.of( "afterInstance" ) ) ).isEqualTo( "Honda Motor Co." );
		} finally {
			touch( original );
		}
	}

	@DisplayName( "Every documented method is added" )
	@Test
	public void testMethodList() {
		DynamicObject	boxClass	= ClassLocator.getInstance().load( context, "root.models.Manufacturer", "bx" );
		StaticScope		scope		= BoxClassSupport.getStaticScope( context, boxClass );
		for ( String method : new String[] { "get", "getOrFail", "getAll", "read", "getReference", "findWhere", "findWhereOrFail", "findAllWhere",
		    "first", "exists", "count", "list", "executeQuery", "criteria", "newCriteria", "new", "firstOrNew", "firstOrCreate", "deleteWhere",
		    "updateWhere" } ) {
			assertThat( scope.containsKey( Key.of( method ) ) ).isTrue();
		}
	}

	/**
	 * Rewrite the entity file with a modification time later than the compiled class, so BoxLang recompiles it.
	 */
	private static void touch( String content ) throws IOException {
		Files.writeString( MANUFACTURER, content );
		Files.setLastModifiedTime( MANUFACTURER, FileTime.from( Instant.now().plusSeconds( 2 ) ) );
	}
}
