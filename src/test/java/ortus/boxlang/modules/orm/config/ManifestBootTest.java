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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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

		// The manifest was written during boot.
		Path manifestFile = manifestFolder.resolve( ManifestService.MANIFEST_NAME );
		assertThat( Files.exists( manifestFile ) ).isTrue();
		assertThat( Files.exists( manifestFolder.resolve( ManifestService.CHECKSUM_NAME ) ) ).isTrue();

		// The written manifest is valid, integrity-checks, and rehydrates both entities (the trust-mode load path).
		OrmManifest manifest = ManifestService.read( manifestFolder, true );
		assertThat( manifest.getEntities() ).hasSize( 2 );
		assertThat( ManifestService.toEntityMap( manifest ).values().stream().mapToInt( java.util.List::size ).sum() ).isEqualTo( 2 );
	}
}
