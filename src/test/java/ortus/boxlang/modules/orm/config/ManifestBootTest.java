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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.orm.mapping.manifest.ManifestService;
import ortus.boxlang.modules.orm.mapping.manifest.OrmManifest;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

/**
 * Boots a full ORM application in {@code ormManifest="auto"} mode against embedded Derby and proves the {@code .bxorm/}
 * boot cache is written during the real boot, that the written manifest is valid and reloadable, and that entities work
 * end-to-end while the manifest is being maintained.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
@TestMethodOrder( MethodOrderer.OrderAnnotation.class )
public class ManifestBootTest {

	private static BoxRuntime	instance;
	private RequestBoxContext	context;
	private IScope				variables;
	private Path				manifestFolder;

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

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Path.of( "src/test/resources/manifestApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables		= context.getScopeNearby( VariablesScope.name );
		manifestFolder	= ManifestService.resolveFolder( context.getRequestContext() );
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMManifestTest" ) );
		// Clean up the generated .bxorm/ cache so it does not pollute the source tree.
		if ( manifestFolder != null && Files.exists( manifestFolder ) ) {
			try ( var walk = Files.walk( manifestFolder ) ) {
				walk.sorted( Comparator.reverseOrder() ).forEach( p -> p.toFile().delete() );
			} catch ( Exception ignored ) {
				// best-effort cleanup
			}
		}
	}

	@DisplayName( "auto mode writes a valid, reloadable .bxorm/ manifest during a real boot and entities work" )
	@Test
	@Order( 1 )
	public void testAutoModeWritesManifest() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				g = entityNew( "Gadget", { name : "Widget-A" } );
				entitySave( g );
				w = entityNew( "Widget", { label : "shiny" } );
				entitySave( w );
			}
			ormFlush();
			ormClearSession();
			gadgetCount = ormExecuteQuery( "FROM Gadget" ).len();
			widgetCount = ormExecuteQuery( "FROM Widget" ).len();
		""", context );
		// @formatter:on

		// Entities round-tripped through the real ORM while the manifest was being maintained.
		assertThat( variables.getAsInteger( Key.of( "gadgetCount" ) ) ).isEqualTo( 1 );
		assertThat( variables.getAsInteger( Key.of( "widgetCount" ) ) ).isEqualTo( 1 );

		// The manifest and the pre-generated facades.jar were written during boot.
		Path manifestFile = manifestFolder.resolve( ManifestService.MANIFEST_NAME );
		assertThat( Files.exists( manifestFile ) ).isTrue();
		assertThat( Files.exists( manifestFolder.resolve( ManifestService.CHECKSUM_NAME ) ) ).isTrue();
		assertThat( Files.exists( manifestFolder.resolve( ManifestService.FACADES_JAR ) ) ).isTrue();

		// The facades.jar contains the generated facade classes (so a trust-mode boot can inject them instead of ByteBuddy).
		int facadeClasses = 0;
		try ( var jar = new java.util.jar.JarInputStream( Files.newInputStream( manifestFolder.resolve( ManifestService.FACADES_JAR ) ) ) ) {
			java.util.jar.JarEntry e;
			while ( ( e = jar.getNextJarEntry() ) != null ) {
				if ( e.getName().endsWith( "Facade.class" ) ) {
					facadeClasses++;
				}
			}
		} catch ( java.io.IOException e ) {
			throw new RuntimeException( e );
		}
		assertThat( facadeClasses ).isAtLeast( 2 );

		// The written manifest is valid, integrity-checks, and rehydrates both entities (the trust-mode load path).
		OrmManifest manifest = ManifestService.read( manifestFolder, true );
		assertThat( manifest.getEntities() ).hasSize( 2 );
		assertThat( ManifestService.toEntityMap( manifest ).values().stream().mapToInt( java.util.List::size ).sum() ).isEqualTo( 2 );
	}

	@DisplayName( "auto mode registers an entity watcher for the application during boot" )
	@Test
	@Order( 2 )
	public void testAutoModeRegistersWatcher() {
		// The watcher is keyed orm-entities-<appName>; WatcherService/Key are core types, so no module cast is needed.
		assertThat( instance.getWatcherService().hasWatcher( Key.of( "orm-entities-BXORMManifestTest" ) ) ).isTrue();
	}

	@DisplayName( "a new entity source file is picked up by a live reload on the next request (watcher -> dirty -> reload)" )
	@Test
	@Order( 3 )
	public void testLiveReloadPicksUpNewEntity() throws Exception {
		Path	modelsDir	= Path.of( "src/test/resources/manifestApp/models" ).toAbsolutePath();
		Path	sprocket	= modelsDir.resolve( "Sprocket.bx" );
		Path	sprocketXml	= modelsDir.resolve( "Sprocket.orm.xml" );
		try {
			// Sanity: Sprocket is unknown before the file exists.
			assertThat( entityKnownInFreshRequest( "Sprocket" ) ).isFalse();

			// Drop a new entity into a watched path; the watcher marks the app dirty and the next request reloads.
			Files.writeString( sprocket,
			    "class persistent=\"true\" table=\"sprockets\" { property name=\"id\" fieldtype=\"id\" generator=\"increment\" ormType=\"integer\"; property name=\"label\" ormType=\"string\"; }",
			    java.nio.charset.StandardCharsets.UTF_8 );

			boolean	known		= false;
			long	deadline	= System.currentTimeMillis() + 15000;
			while ( System.currentTimeMillis() < deadline ) {
				if ( entityKnownInFreshRequest( "Sprocket" ) ) {
					known = true;
					break;
				}
				Thread.sleep( 300 );
			}
			assertThat( known ).isTrue();
		} finally {
			Files.deleteIfExists( sprocket );
			Files.deleteIfExists( sprocketXml );
			RequestBoxContext.setCurrent( context );
		}
	}

	@DisplayName( "the bxorm CLI dispatches through ModuleConfig.main() and honors --dir (clear removes the cache)" )
	@Test
	@Order( 4 )
	public void testModuleConfigMainCliDispatch( @TempDir Path tmp ) throws Exception {
		// Seed a .bxorm cache under a temp app root, then drive the REAL CLI entry point: ModuleConfig.main().
		Path bxorm = tmp.resolve( ManifestService.FOLDER_NAME );
		Files.createDirectories( bxorm );
		Files.writeString( bxorm.resolve( ManifestService.MANIFEST_NAME ), "{}" );
		assertThat( Files.exists( bxorm ) ).isTrue();

		ModuleRecord rec = instance.getModuleService().getRegistry().get( ORMKeys.moduleName );
		// main() parses --dir, resolves <dir>/.bxorm, and dispatches to the Java ManifestCli 'clear' verb.
		rec.moduleConfig.main( context, new String[] { "clear", "--dir=" + tmp.toAbsolutePath() } );

		assertThat( Files.exists( bxorm ) ).isFalse();
	}

	/**
	 * Resolve whether an entity is known, using a fresh request context each call so a new ORMContext is created - which
	 * is what triggers the deferred auto-reload (mid-request reloads are intentionally avoided). Restores the class
	 * context as current before returning.
	 */
	private boolean entityKnownInFreshRequest( String entityName ) {
		RequestBoxContext reqCtx = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		try {
			RequestBoxContext.setCurrent( reqCtx );
			reqCtx.loadApplicationDescriptor( Path.of( "src/test/resources/manifestApp/index.bxs" ).toAbsolutePath().toUri() );
			reqCtx.getApplicationListener().onRequestStart( reqCtx, null );
			IScope vars = reqCtx.getScopeNearby( VariablesScope.name );
			instance.executeSource( "k = false; try { entityNew( \"" + entityName + "\" ); k = true; } catch( any e ) { k = false; }", reqCtx );
			return Boolean.TRUE.equals( vars.get( Key.of( "k" ) ) );
		} finally {
			try {
				reqCtx.getApplicationListener().onRequestEnd( reqCtx, null );
			} catch ( Exception ignored ) {
				// ignore
			}
			reqCtx.shutdown();
			RequestBoxContext.setCurrent( context );
		}
	}
}
