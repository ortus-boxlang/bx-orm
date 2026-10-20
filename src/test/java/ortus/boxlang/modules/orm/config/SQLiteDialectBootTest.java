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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
 * Boots a full ORM application/SessionFactory against a real, file-based SQLite datasource.
 * <p>
 * This is a standalone boot test (does not extend {@code tools.BaseORMTest}) because it uses its own,
 * isolated BoxLang application (`src/test/resources/sqliteApp`) with a SQLite datasource, rather than
 * the shared MySQL application every other ORM test boots against.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class SQLiteDialectBootTest {

	private static BoxRuntime	instance;
	private static Path			dbFilePath;
	private static final Key	result	= Key.of( "result" );

	private RequestBoxContext	context;
	private IScope				variables;

	@BeforeAll
	public void setUp() throws IOException {
		dbFilePath = Paths.get( "build/test-sqlite/orm-sqlite-boot-test.db" ).toAbsolutePath();
		Files.createDirectories( dbFilePath.getParent() );
		Files.deleteIfExists( dbFilePath );
		System.setProperty( "SQLITE_DB_PATH", dbFilePath.toString() );

		instance = BoxRuntime.getInstance( false );

		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Paths.get( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/sqliteApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMSQLiteTest" ) );
	}

	@DisplayName( "It round-trips a simple-value collection (fieldtype=collection): schema, insert, load, helpers, replace, clear" )
	@Test
	public void testElementCollectionRoundTrip() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				p = entityNew( "Product", { name : "Gadget" } );
				p.setTags( [ "red", "sale" ] );
				entitySave( p );
			}
			ormClearSession();

			tableCheck = queryExecute( "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'product_tags'" );
			rows       = queryExecute( "SELECT tag FROM product_tags ORDER BY tag" );

			loaded     = entityLoad( "Product", { name : "Gadget" }, true );
			loadedTags = loaded.getTags();
			// the mapping carries orderby="tag ASC", so the bag is ordered on load
			loadedTagList = loadedTags.toList();

			// generated helpers take the scalar value, not an entity
			hasRed    = loaded.hasTags( "red" );
			hasGreen  = loaded.hasTags( "green" );
			transaction {
				loaded.addTags( "green" ).removeTags( "red" );
				entitySave( loaded );
			}
			ormClearSession();
			rowsAfterHelpers = queryExecute( "SELECT tag FROM product_tags ORDER BY tag" );

			transaction {
				loaded = entityLoad( "Product", { name : "Gadget" }, true );
				loaded.setTags( [ "blue" ] );
				entitySave( loaded );
			}
			ormClearSession();
			rowsAfterReplace = queryExecute( "SELECT tag FROM product_tags ORDER BY tag" );

			transaction {
				again = entityLoad( "Product", { name : "Gadget" }, true );
				again.setTags( [] );
				entitySave( again );
			}
			ormClearSession();
			rowsAfterClear = queryExecute( "SELECT tag FROM product_tags ORDER BY tag" );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "tableCheck" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).size() ).isEqualTo( 2 );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 0 ).get( "tag" ) ).isEqualTo( "red" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 1 ).get( "tag" ) ).isEqualTo( "sale" );

		assertThat( variables.get( Key.of( "loadedTags" ) ) ).isNotNull();
		assertThat( variables.getAsString( Key.of( "loadedTagList" ) ) ).isEqualTo( "red,sale" );

		assertThat( variables.getAsBoolean( Key.of( "hasRed" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "hasGreen" ) ) ).isFalse();
		assertThat( variables.getAsQuery( Key.of( "rowsAfterHelpers" ) ).size() ).isEqualTo( 2 );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterHelpers" ) ).getRowAsStruct( 0 ).get( "tag" ) ).isEqualTo( "green" );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterHelpers" ) ).getRowAsStruct( 1 ).get( "tag" ) ).isEqualTo( "sale" );

		assertThat( variables.getAsQuery( Key.of( "rowsAfterReplace" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterReplace" ) ).getRowAsStruct( 0 ).get( "tag" ) ).isEqualTo( "blue" );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterClear" ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "It round-trips a struct collection (fieldtype=collection type=struct): schema, insert, load, helpers, replace, clear" )
	@Test
	public void testElementCollectionMapRoundTrip() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				p = entityNew( "Product", { name : "Gizmo" } );
				p.setSpecs( { color : "red", size : "L" } );
				entitySave( p );
			}
			ormClearSession();

			tableCheck = queryExecute( "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'product_specs'" );
			rows       = queryExecute( "SELECT spec_name, spec_value FROM product_specs ORDER BY spec_name" );

			loaded      = entityLoad( "Product", { name : "Gizmo" }, true );
			loadedSpecs = loaded.getSpecs();
			loadedColor = loadedSpecs[ "color" ];
			loadedKeys  = loadedSpecs.keyArray().sort( "text" ).toList();
			hasColor    = loaded.hasSpecs( "color" );
			hasWeight   = loaded.hasSpecs( "weight" );

			transaction {
				loaded.setSpecs( { weight : "2kg" } );
				entitySave( loaded );
			}
			ormClearSession();
			rowsAfterReplace = queryExecute( "SELECT spec_name, spec_value FROM product_specs ORDER BY spec_name" );

			transaction {
				again = entityLoad( "Product", { name : "Gizmo" }, true );
				again.setSpecs( {} );
				entitySave( again );
			}
			ormClearSession();
			rowsAfterClear = queryExecute( "SELECT spec_name FROM product_specs" );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "tableCheck" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).size() ).isEqualTo( 2 );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 0 ).get( "spec_name" ) ).isEqualTo( "color" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 0 ).get( "spec_value" ) ).isEqualTo( "red" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 1 ).get( "spec_name" ) ).isEqualTo( "size" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 1 ).get( "spec_value" ) ).isEqualTo( "L" );

		assertThat( variables.get( Key.of( "loadedSpecs" ) ) ).isNotNull();
		assertThat( variables.getAsString( Key.of( "loadedColor" ) ) ).isEqualTo( "red" );
		assertThat( variables.getAsString( Key.of( "loadedKeys" ) ) ).isEqualTo( "color,size" );
		assertThat( variables.getAsBoolean( Key.of( "hasColor" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "hasWeight" ) ) ).isFalse();

		assertThat( variables.getAsQuery( Key.of( "rowsAfterReplace" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterReplace" ) ).getRowAsStruct( 0 ).get( "spec_name" ) ).isEqualTo( "weight" );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterReplace" ) ).getRowAsStruct( 0 ).get( "spec_value" ) ).isEqualTo( "2kg" );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterClear" ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "It can boot an ORM SessionFactory against a SQLite datasource, and save/query an entity" )
	@Test
	public void testSQLiteORMBoot() {
		// @formatter:off
		instance.executeSource( """
			sessionFactory = ormGetSessionFactory();

			transaction {
				entitySave( entityNew( "Product", { name : "Widget" } ) );
			}

			tableCheck = queryExecute( "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'products'" );
			result     = queryExecute( "SELECT * FROM products WHERE name = 'Widget'" );
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "sessionFactory" ) ) ).isNotNull();
		assertThat( variables.getAsQuery( Key.of( "tableCheck" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( result ).getRowAsStruct( 0 ).get( "name" ) ).isEqualTo( "Widget" );
	}
}
