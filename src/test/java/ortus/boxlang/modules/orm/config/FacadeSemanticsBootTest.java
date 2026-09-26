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
 * Live (embedded Derby) tests for how BoxLang entity instances behave on top of their generated facades: a
 * {@code duplicate()}d entity saves its own state, a managed to-many getter is live for writes but snapshot-safe for
 * iteration, and a developer-written getter is never replaced. Uses {@code src/test/resources/facadeSemanticsApp} (the mappingRegressionApp entities
 * on their own app and Derby DB).
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FacadeSemanticsBootTest {

	private static final Key	APP_NAME	= Key.of( "BXORMFacadeSemanticsTest" );
	private static final Path	APP_ROOT	= Path.of( "src/test/resources/facadeSemanticsApp" ).toAbsolutePath();

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

	@DisplayName( "Saving a duplicate() of an entity persists the copy's changes (it does not reuse the original's facade)" )
	@Test
	public void testDuplicateSavesOwnState() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrItem", { label : "Original" } ) );
		                          ormFlush();
		                          ormClearSession();
		                          loaded = entityLoad( "MrItem", { label : "Original" }, true );
		                          copy   = duplicate( loaded );
		                          copy.setLabel( "Copy Label" );
		                          entitySave( copy );
		                          ormFlush();
		                          ormClearSession();
		                          persisted = entityLoadByPK( "MrItem", loaded.getId() ).getLabel();
		                          """ );
		assertThat( vars.getAsString( Key.of( "persisted" ) ) ).isEqualTo( "Copy Label" );
	}

	@DisplayName( "Appending through a managed to-many getter persists (append and arrayAppend)" )
	@Test
	public void testGetterAppendIsLive() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrPost", { title : "Live Getter" } ) );
		                          ormFlush();
		                          ormClearSession();
		                          post = entityLoad( "MrPost", { title : "Live Getter" }, true );
		                          post.getComments().append( entityNew( "MrComment", { body : "via append" } ) );
		                          arrayAppend( post.getComments(), entityNew( "MrComment", { body : "via arrayAppend" } ) );
		                          entitySave( post );
		                          ormFlush();
		                          ormClearSession();
		                          count = entityLoad( "MrPost", { title : "Live Getter" }, true ).getComments().len();
		                          """ );
		assertThat( vars.getAsInteger( Key.of( "count" ) ) ).isEqualTo( 2 );
	}

	@DisplayName( "Removing every child while iterating the managed getter visits all of them" )
	@Test
	public void testRemoveWhileIterating() {
		IScope vars = runRequest( """
		                          post = entityNew( "MrPost", { title : "Remove All" } );
		                          for ( i = 1; i <= 4; i++ ) {
		                              post.addComment( entityNew( "MrComment", { body : "c#i#" } ) );
		                          }
		                          entitySave( post );
		                          ormFlush();
		                          ormClearSession();
		                          managed = entityLoad( "MrPost", { title : "Remove All" }, true );
		                          visited = 0;
		                          managed.getComments().each( ( c ) => {
		                              visited++;
		                              managed.removeComment( c );
		                          } );
		                          entitySave( managed );
		                          ormFlush();
		                          ormClearSession();
		                          remaining = entityLoad( "MrPost", { title : "Remove All" }, true ).getComments().len();
		                          """ );
		assertThat( vars.getAsInteger( Key.of( "visited" ) ) ).isEqualTo( 4 );
		assertThat( vars.getAsInteger( Key.of( "remaining" ) ) ).isEqualTo( 0 );
	}

	@DisplayName( "A getter the developer wrote is never replaced by the ORM" )
	@Test
	public void testDeveloperGetterIsKept() {
		IScope vars = runRequest( """
		                          lib = entityNew( "MrLibrary", { name : "Alexandria" } );
		                          fromNew = lib.getBooks();
		                          entitySave( lib );
		                          ormFlush();
		                          ormClearSession();
		                          fromLoaded = entityLoad( "MrLibrary", { name : "Alexandria" }, true ).getBooks();
		                          """ );
		assertThat( vars.getAsString( Key.of( "fromNew" ) ) ).isEqualTo( "developer-getter" );
		assertThat( vars.getAsString( Key.of( "fromLoaded" ) ) ).isEqualTo( "developer-getter" );
	}

	/** Run one request against the regression app and return its variables scope. */
	@DisplayName( "entityReload() of an entity detached by ormClearSession() re-reads it and makes it managed again" )
	@Test
	public void testReloadDetachedAfterClearSession() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrItem", { label : "reload-original" } ) );
		                          ormFlush();
		                          ormClearSession();
		                          item = entityLoad( "MrItem", { label : "reload-original" }, true );
		                          ormClearSession();
		                          queryExecute( "UPDATE mr_items SET label = 'reload-db' WHERE id = :id", { id : item.getId() } );
		                          entityReload( item );
		                          reloaded = item.getLabel();
		                          // Managed again: a change is flushed without calling entitySave().
		                          item.setLabel( "reload-managed" );
		                          ormFlush();
		                          persisted = queryExecute( "SELECT label FROM mr_items WHERE id = :id", { id : item.getId() } ).label;
		                          """ );
		assertThat( vars.getAsString( Key.of( "reloaded" ) ) ).isEqualTo( "reload-db" );
		assertThat( vars.getAsString( Key.of( "persisted" ) ) ).isEqualTo( "reload-managed" );
	}

	@DisplayName( "entityReload() after a rolled-back transaction discards the rolled-back changes" )
	@Test
	public void testReloadAfterRollback() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrItem", { label : "rollback-original" } ) );
		                          ormFlush();
		                          ormClearSession();
		                          item = entityLoad( "MrItem", { label : "rollback-original" }, true );
		                          transaction {
		                              item.setLabel( "rollback-dirty" );
		                              transactionRollback();
		                          }
		                          entityReload( item );
		                          reloaded = item.getLabel();
		                          """ );
		assertThat( vars.getAsString( Key.of( "reloaded" ) ) ).isEqualTo( "rollback-original" );
	}

	@DisplayName( "entityReload() of a detached copy while another variable holds the managed row gives it the DB values" )
	@Test
	public void testReloadDetachedWhileRowIsManagedElsewhere() {
		IScope vars = runRequest( """
		                          entitySave( entityNew( "MrItem", { label : "twin-original" } ) );
		                          ormFlush();
		                          ormClearSession();
		                          stale = entityLoad( "MrItem", { label : "twin-original" }, true );
		                          ormClearSession();
		                          fresh = entityLoadByPK( "MrItem", stale.getId() );
		                          queryExecute( "UPDATE mr_items SET label = 'twin-db' WHERE id = :id", { id : stale.getId() } );
		                          entityReload( stale );
		                          staleLabel = stale.getLabel();
		                          freshLabel = fresh.getLabel();
		                          """ );
		assertThat( vars.getAsString( Key.of( "staleLabel" ) ) ).isEqualTo( "twin-db" );
		assertThat( vars.getAsString( Key.of( "freshLabel" ) ) ).isEqualTo( "twin-db" );
	}

	@DisplayName( "A child created with new (not entityNew) is saved through its parent's cascade" )
	@Test
	public void testNewInstanceSavedThroughCascade() {
		IScope vars = runRequest( """
		                          post = entityNew( "MrPost", { title : "cascade-new" } );
		                          comment = new root.models.MrComment();
		                          comment.setBody( "made-with-new" );
		                          post.addComment( comment );
		                          entitySave( post );
		                          ormFlush();
		                          ormClearSession();
		                          saved = queryExecute( "SELECT count(*) AS n FROM mr_comments WHERE body = 'made-with-new'" ).n;
		                          loadedBody = entityLoad( "MrPost", { title : "cascade-new" }, true ).getComments()[ 1 ].getBody();
		                          """ );
		assertThat( ( ( Number ) vars.get( Key.of( "saved" ) ) ).intValue() ).isEqualTo( 1 );
		assertThat( vars.getAsString( Key.of( "loadedBody" ) ) ).isEqualTo( "made-with-new" );
	}

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
			context.shutdown();
		}
	}
}
