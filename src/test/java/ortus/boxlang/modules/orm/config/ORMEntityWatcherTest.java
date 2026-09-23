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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;

/**
 * Exercises the auto-mode {@link ORMEntityWatcher} directly (no ORM boot / no database): a real {@code .bx} change in a
 * watched directory fires the change callback, generated/cache artifacts are ignored, and {@code stop()} deregisters
 * the watcher so no thread leaks.
 */
public class ORMEntityWatcherTest {

	static BoxRuntime	instance;
	RequestBoxContext	context;
	ORMConfig			ormConfig;
	BoxLangLogger		logger;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( false );
	}

	@BeforeEach
	public void setupEach() {
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		logger = instance.getLoggingService().getLogger( "orm" );
	}

	@AfterEach
	public void teardownEach() {
		RequestBoxContext.removeCurrent();
		context.shutdown();
	}

	/** Poll a condition up to timeoutMs, returning true as soon as it holds. */
	private boolean awaitTrue( java.util.function.BooleanSupplier cond, long timeoutMs ) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while ( System.currentTimeMillis() < deadline ) {
			if ( cond.getAsBoolean() ) {
				return true;
			}
			try {
				Thread.sleep( 100 );
			} catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return cond.getAsBoolean();
	}

	private ORMConfig configForPath( Path dir ) {
		return new ORMConfig(
		    Struct.of( "datasource", "x", "entityPaths", dir.toAbsolutePath().toString() ),
		    context.getRequestContext()
		);
	}

	@DisplayName( "a .bx change in a watched entity path fires the change callback; the watcher deregisters on stop" )
	@Test
	public void testWatchTriggersAndStops( @TempDir Path modelDir ) throws Exception {
		Files.writeString( modelDir.resolve( "Seed.bx" ), "class{}", StandardCharsets.UTF_8 );
		ormConfig = configForPath( modelDir );

		AtomicInteger		changes	= new AtomicInteger( 0 );
		Key					appName	= Key.of( "watcherTestApp" );
		ORMEntityWatcher	watcher	= ORMEntityWatcher.startFor( appName, ormConfig, context, changes::incrementAndGet, logger );

		assertThat( watcher ).isNotNull();
		Key watcherKey = Key.of( "orm-entities-watcherTestApp" );
		assertThat( instance.getWatcherService().hasWatcher( watcherKey ) ).isTrue();

		// A new BoxLang class file must trigger the callback (debounce is 500ms; allow generous headroom).
		Files.writeString( modelDir.resolve( "Widget.bx" ), "class persistent{ property name=\"id\" fieldtype=\"id\"; }", StandardCharsets.UTF_8 );
		assertThat( awaitTrue( () -> changes.get() > 0, 8000 ) ).isTrue();

		// stop() deregisters the watcher so no background thread leaks.
		watcher.stop();
		assertThat( awaitTrue( () -> !instance.getWatcherService().hasWatcher( watcherKey ), 3000 ) ).isTrue();
	}

	@DisplayName( "generated mapping files (.orm.xml) and .bxorm cache changes do not trigger a reload" )
	@Test
	public void testIrrelevantChangesIgnored( @TempDir Path modelDir ) throws Exception {
		Files.writeString( modelDir.resolve( "Seed.bx" ), "class{}", StandardCharsets.UTF_8 );
		ormConfig = configForPath( modelDir );

		AtomicInteger		changes	= new AtomicInteger( 0 );
		ORMEntityWatcher	watcher	= ORMEntityWatcher.startFor( Key.of( "ignoreApp" ), ormConfig, context, changes::incrementAndGet, logger );
		assertThat( watcher ).isNotNull();

		try {
			// A generated mapping file and a cache folder write must be ignored by the listener filter.
			Files.writeString( modelDir.resolve( "Seed.orm.xml" ), "<hibernate-mapping/>", StandardCharsets.UTF_8 );
			Path bxorm = modelDir.resolve( ".bxorm" );
			Files.createDirectories( bxorm );
			Files.writeString( bxorm.resolve( "manifest.json" ), "{}", StandardCharsets.UTF_8 );

			// Give the watcher ample time to (not) fire, then confirm nothing was reported.
			Thread.sleep( 2000 );
			assertThat( changes.get() ).isEqualTo( 0 );
		} finally {
			watcher.stop();
		}
	}

	@DisplayName( "startFor returns null when there are no existing directories to watch" )
	@Test
	public void testNoPathsNoWatcher() {
		ormConfig = new ORMConfig(
		    Struct.of( "datasource", "x", "entityPaths", "/nonexistent/path/for/orm/watcher/test" ),
		    context.getRequestContext()
		);
		AtomicInteger		changes	= new AtomicInteger( 0 );
		ORMEntityWatcher	watcher	= ORMEntityWatcher.startFor( Key.of( "emptyApp" ), ormConfig, context, changes::incrementAndGet, logger );
		assertThat( watcher ).isNull();
	}
}
