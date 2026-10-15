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
package ortus.boxlang.modules.orm.config;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

/**
 * Live (embedded Derby) round-trip tests for the Hibernate 5 -> 7 mapping regressions: each test saves real rows,
 * clears the session, reloads them and checks the database itself, so a mapping that is merely well-formed but maps
 * the wrong column / key / discriminator fails here. Entities live in {@code src/test/resources/mappingRegressionApp}.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class MappingRegressionBootTest {

	private static final Key	APP_NAME	= Key.of( "BXORMMappingRegressionTest" );
	private static final Path	APP_ROOT	= Path.of( "src/test/resources/mappingRegressionApp" ).toAbsolutePath();

	private BoxRuntime			instance;

	@BeforeAll
	public void setUp() {
		instance = BoxRuntime.getInstance( false );
		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Path.of( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}
	}

	@AfterAll
	public void teardown() {
		instance.getApplicationService().shutdownApplication( APP_NAME );
		RequestBoxContext.removeCurrent();
	}

	@DisplayName( "A to-one association defaults to lazy and still resolves its target" )
	@Test
	public void testLazyToOneResolves() {
		IScope vars = runRequest( """
		                          post = entityNew( "MrPost", { title : "Lazy" } );
		                          c = entityNew( "MrComment", { body : "hi" } );
		                          post.addComment( c );
		                          entitySave( post );
		                          ormFlush();
		                          ormClearSession();
		                          loaded    = entityLoad( "MrComment", { body : "hi" }, true );
		                          postTitle = loaded.getPost().getTitle();
		                          """ );
		assertThat( vars.getAsString( Key.of( "postTitle" ) ) ).isEqualTo( "Lazy" );
	}

	@DisplayName( "A hierarchy root persists its own discriminator value (not the entity name)" )
	@Test
	public void testRootDiscriminatorValue() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrVehicle", { name : "plain" } ) );
		                          entitySave( entityNew( "MrCar", { name : "coupe", doors : 2 } ) );
		                          ormFlush();
		                          ormClearSession();
		                          kinds    = queryExecute( "SELECT kind FROM mr_vehicles ORDER BY id" );
		                          rootKind = kinds.kind[ 1 ];
		                          carKind  = kinds.kind[ 2 ];
		                          loadedAll = entityLoad( "MrVehicle" ).len();
		                          """ );
		assertThat( vars.getAsString( Key.of( "rootKind" ) ) ).isEqualTo( "vehicle" );
		assertThat( vars.getAsString( Key.of( "carKind" ) ) ).isEqualTo( "car" );
		assertThat( vars.getAsInteger( Key.of( "loadedAll" ) ) ).isEqualTo( 2 );
	}

	@DisplayName( "A one-to-one without fkcolumn shares the primary key (no foreign-key column)" )
	@Test
	public void testSharedPrimaryKeyOneToOne() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrProfile", { id : 7, bio : "hello" } ) );
		                          entitySave( entityNew( "MrPerson", { id : 7, name : "Ann" } ) );
		                          ormFlush();
		                          ormClearSession();
		                          bio     = entityLoadByPK( "MrPerson", 7 ).getProfile().getBio();
		                          columns = queryExecute( "SELECT * FROM mr_people" ).columnList;
		                          """ );
		assertThat( vars.getAsString( Key.of( "bio" ) ) ).isEqualTo( "hello" );
		assertThat( vars.getAsString( Key.of( "columns" ) ).toUpperCase() ).doesNotContain( "PROFILE" );
	}

	@DisplayName( "A one-to-many without fkcolumn uses its target's back-reference FK (no join table)" )
	@Test
	public void testOneToManyBorrowsBackReferenceColumn() {
		IScope vars = runRequest( """
		                          post = entityNew( "MrPost", { title : "Borrowed" } );
		                          post.addComment( entityNew( "MrComment", { body : "a" } ) );
		                          post.addComment( entityNew( "MrComment", { body : "b" } ) );
		                          entitySave( post );
		                          ormFlush();
		                          ormClearSession();
		                          reloaded   = entityLoad( "MrPost", { title : "Borrowed" }, true );
		                          commentCnt = reloaded.getComments().len();
		                          fkRows     = queryExecute( "SELECT count(*) AS n FROM mr_comments WHERE postId = :id", { id : reloaded.getId() } ).n;
		                          joinTables = queryExecute( "SELECT count(*) AS n FROM sys.systables WHERE tablename LIKE '%POST%COMMENT%'" ).n;
		                          """ );
		assertThat( vars.getAsInteger( Key.of( "commentCnt" ) ) ).isEqualTo( 2 );
		assertThat( vars.getAsInteger( Key.of( "fkRows" ) ) ).isEqualTo( 2 );
		assertThat( vars.getAsInteger( Key.of( "joinTables" ) ) ).isEqualTo( 0 );
	}

	@DisplayName( "A collection where= restricts the loaded rows" )
	@Test
	public void testCollectionWhere() {
		IScope vars = runRequest( """
		                          a = entityNew( "MrAuthor", { name : "Ursula" } );
		                          entitySave( a );
		                          b1 = entityNew( "MrBook", { title : "Earthsea", active : 1 } );
		                          b1.setAuthor( a );
		                          entitySave( b1 );
		                          b2 = entityNew( "MrBook", { title : "Lost Draft", active : 0 } );
		                          b2.setAuthor( a );
		                          entitySave( b2 );
		                          ormFlush();
		                          ormClearSession();
		                          reloaded    = entityLoad( "MrAuthor", { name : "Ursula" }, true );
		                          activeCount = reloaded.getActiveBooks().len();
		                          allCount    = reloaded.getBooks().len();
		                          activeTitle = reloaded.getActiveBooks()[ 1 ].getTitle();
		                          """ );
		assertThat( vars.getAsInteger( Key.of( "activeCount" ) ) ).isEqualTo( 1 );
		assertThat( vars.getAsInteger( Key.of( "allCount" ) ) ).isEqualTo( 2 );
		assertThat( vars.getAsString( Key.of( "activeTitle" ) ) ).isEqualTo( "Earthsea" );
	}

	@DisplayName( "A struct-typed one-to-many persists and reloads its entries by key" )
	@Test
	public void testStructOneToMany() {
		IScope vars = runRequest( """
		                          shelf = entityNew( "MrShelf", { name : "Pantry" } );
		                          shelf.setItemsByCode( {
		                              "a" : entityNew( "MrItem", { label : "Apple" } ),
		                              "b" : entityNew( "MrItem", { label : "Banana" } )
		                          } );
		                          entitySave( shelf );
		                          ormFlush();
		                          ormClearSession();
		                          items     = entityLoad( "MrShelf", { name : "Pantry" }, true ).getItemsByCode();
		                          itemCount = items.size();
		                          labelA    = items.get( "a" ).getLabel();
		                          labelB    = items.get( "b" ).getLabel();
		                          codes     = queryExecute( "SELECT itemCode FROM mr_items ORDER BY itemCode" );
		                          codeList  = arrayToList( queryColumnData( codes, "itemCode" ) );
		                          """ );
		assertThat( vars.getAsInteger( Key.of( "itemCount" ) ) ).isEqualTo( 2 );
		assertThat( vars.getAsString( Key.of( "labelA" ) ) ).isEqualTo( "Apple" );
		assertThat( vars.getAsString( Key.of( "labelB" ) ) ).isEqualTo( "Banana" );
		assertThat( vars.getAsString( Key.of( "codeList" ) ) ).isEqualTo( "a,b" );
	}

	@DisplayName( "A fieldtype=\"timestamp\" version boots and is stamped on save" )
	@Test
	public void testTimestampVersion() {
		IScope vars = runRequest( """
		                          d = entityNew( "MrDoc", { title : "v1" } );
		                          entitySave( d );
		                          ormFlush();
		                          stamped = !isNull( d.getLastModified() );
		                          ormClearSession();
		                          d2 = entityLoad( "MrDoc", { title : "v1" }, true );
		                          d2.setTitle( "v2" );
		                          entitySave( d2 );
		                          ormFlush();
		                          ormClearSession();
		                          reloadedTitle = entityLoad( "MrDoc", { title : "v2" }, true ).getTitle();
		                          """ );
		assertThat( vars.getAsBoolean( Key.of( "stamped" ) ) ).isTrue();
		assertThat( vars.getAsString( Key.of( "reloadedTitle" ) ) ).isEqualTo( "v2" );
	}

	/** Run one request against the mapping-regression app and return its variables scope. */
	private IScope runRequest( String source ) {
		ScriptingRequestBoxContext context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		try {
			context.loadApplicationDescriptor( APP_ROOT.resolve( "index.bxs" ).toUri() );
			context.getApplicationListener().onRequestStart( context, null );
			instance.executeSource( source, context );
			return context.getScopeNearby( VariablesScope.name );
		} finally {
			try {
				context.getApplicationListener().onRequestEnd( context, null );
			} catch ( Exception ignored ) {
				// best-effort
			}
			RequestBoxContext.removeCurrent();
			// Release the request context (restores thread state such as the context classloader), so a later test class in
			// this JVM starts clean.
			context.shutdown();
		}
	}
}
