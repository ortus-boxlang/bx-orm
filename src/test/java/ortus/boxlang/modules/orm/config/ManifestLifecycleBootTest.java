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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Live, end-to-end ORM application lifecycle tests against embedded Derby: an {@code auto} boot that writes the
 * {@code .bxorm/} cache to a configured {@code ormManifestLocation}, a {@code trust} boot of the same application that
 * defines its entity facades from the written {@code facades.jar}, and an {@code ormReload()} after an existing entity
 * gains a property.
 * <p>
 * Every boot runs in this JVM against a fresh ORM application build, so each build gets its own facade classloader -
 * which is what lets a trust boot actually define facades from the jar (instead of reusing classes an earlier boot
 * generated) and lets a reload define changed facades. Entities live in a per-test temp folder (copied from the
 * {@code manifestLifecycleApp/models} fixtures), so nothing is written into the source tree.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class ManifestLifecycleBootTest {

	private static final Key	APP_NAME		= Key.of( "BXORMLifecycleTest" );
	private static final Path	APP_ROOT		= Path.of( "src/test/resources/manifestLifecycleApp" ).toAbsolutePath();
	private static final String	PROP_MODE		= "bxorm.test.lifecycle.mode";
	private static final String	PROP_LOCATION	= "bxorm.test.lifecycle.location";
	private static final String	PROP_MODELS		= "bxorm.test.lifecycle.models";
	private static final String	PROP_NAMING		= "bxorm.test.lifecycle.namingStrategy";
	private static final String	PROP_DB			= "bxorm.test.lifecycle.db";
	private static final String	AUTO_SAVE		= """
	                                              o = entityNew( "Owner", { name : "Ada" } );
	                                              entitySave( o );
	                                              ormFlush();
	                                              """;

	private BoxRuntime			instance;
	private Path				workDir;
	private Path				modelsDir;
	private Path				manifestParent;

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

	@BeforeEach
	public void prepareWorkDir() throws IOException {
		workDir			= Files.createTempDirectory( "bxorm-lifecycle" );
		modelsDir		= Files.createDirectories( workDir.resolve( "models" ) );
		manifestParent	= Files.createDirectories( workDir.resolve( "cache-home" ) );
		try ( var fixtures = Files.list( APP_ROOT.resolve( "models" ) ) ) {
			for ( Path fixture : fixtures.filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
				Files.copy( fixture, modelsDir.resolve( fixture.getFileName() ) );
			}
		}
		System.setProperty( PROP_MODELS, modelsDir.toString() );
		System.setProperty( PROP_LOCATION, manifestParent.toString() );
		// A fresh in-memory Derby database per test, so no test sees another's schema or open connections.
		System.setProperty( PROP_DB, "ormLifecycle" + System.nanoTime() );
	}

	@AfterEach
	public void cleanUp() throws IOException {
		instance.getApplicationService().shutdownApplication( APP_NAME );
		System.clearProperty( PROP_MODE );
		System.clearProperty( PROP_LOCATION );
		System.clearProperty( PROP_MODELS );
		System.clearProperty( PROP_NAMING );
		System.clearProperty( PROP_DB );
		try ( var walk = Files.walk( workDir ) ) {
			walk.sorted( Comparator.reverseOrder() ).forEach( p -> p.toFile().delete() );
		}
	}

	@AfterAll
	public void teardown() {
		RequestBoxContext.removeCurrent();
	}

	@DisplayName( "auto boot writes the cache to ormManifestLocation; a trust boot defines facades from facades.jar and CRUD works" )
	@Test
	public void testAutoThenTrustBootFromFacadesJar() {
		// 1) auto: discover, write manifest + facades.jar into <ormManifestLocation>/.bxorm, and do real CRUD.
		IScope autoVars = runRequest( "auto", """
		                                      o = entityNew( "Owner", { name : "Ada" } );
		                                      p = entityNew( "Pet", { name : "Byte" } );
		                                      p.setOwner( o );
		                                      o.addPet( p );
		                                      entitySave( o );
		                                      ormFlush();
		                                      ormClearSession();
		                                      autoPets = entityLoad( "Owner", { name : "Ada" }, true ).getPets().len();
		                                      """ );
		assertThat( autoVars.getAsInteger( Key.of( "autoPets" ) ) ).isEqualTo( 1 );

		Path bxorm = manifestParent.resolve( ManifestService.FOLDER_NAME );
		assertThat( Files.exists( bxorm.resolve( ManifestService.MANIFEST_NAME ) ) ).isTrue();
		assertThat( Files.exists( bxorm.resolve( ManifestService.FACADES_JAR ) ) ).isTrue();
		// Nothing was written at the application root: the configured location was honored end to end.
		assertThat( Files.exists( APP_ROOT.resolve( ManifestService.FOLDER_NAME ) ) ).isFalse();

		instance.getApplicationService().shutdownApplication( APP_NAME );

		// 2) trust: boot straight from the manifest, define the facades from facades.jar, and exercise every accessor kind
		// (id, plain property, to-one, to-many) through real saves and loads.
		IScope trustVars = runRequest( "trust", """
		                                        o = entityNew( "Owner", { name : "Grace" } );
		                                        p = entityNew( "Pet", { name : "Cobol" } );
		                                        p.setOwner( o );
		                                        o.addPet( p );
		                                        entitySave( o );
		                                        ormFlush();
		                                        ormClearSession();
		                                        loaded      = entityLoad( "Owner", { name : "Grace" }, true );
		                                        trustPets   = loaded.getPets().len();
		                                        petOwner    = entityLoad( "Pet", { name : "Cobol" }, true ).getOwner().getName();
		                                        petIdLength = len( entityLoad( "Pet", { name : "Cobol" }, true ).getId() );
		                                        entityTypes = ormGetSessionFactory().getMetamodel().getEntities().toArray();
		                                        jarDefined  = entityTypes[ 1 ].getJavaType().getClassLoader().getDefinedFromJarCount();
		                                        """ );
		assertThat( trustVars.getAsInteger( Key.of( "trustPets" ) ) ).isEqualTo( 1 );
		assertThat( trustVars.getAsString( Key.of( "petOwner" ) ) ).isEqualTo( "Grace" );
		assertThat( trustVars.getAsInteger( Key.of( "petIdLength" ) ) ).isGreaterThan( 0 );
		// Proves the facades really came from facades.jar (both Owner and Pet), not from a fresh ByteBuddy generation.
		assertThat( trustVars.getAsInteger( Key.of( "jarDefined" ) ) ).isEqualTo( 2 );
	}

	@DisplayName( "ormReload() after an existing entity gains a property persists the new property" )
	@Test
	public void testReloadPicksUpChangedEntity() throws IOException {
		Path owner = modelsDir.resolve( "Owner.bx" );

		runRequest( "off", """
		                   o = entityNew( "Owner", { name : "Linus" } );
		                   entitySave( o );
		                   ormFlush();
		                   """ );

		// The same entity (same facade class name) gains a property.
		String source = Files.readString( owner, StandardCharsets.UTF_8 );
		Files.writeString( owner, source.replace( "property name=\"name\" ormType=\"string\";",
		    "property name=\"name\" ormType=\"string\";\n\tproperty name=\"nickname\" ormType=\"string\";" ), StandardCharsets.UTF_8 );

		IScope vars = runRequest( "off", """
		                                 ormReload();
		                                 o = entityNew( "Owner", { name : "Margaret", nickname : "Maggie" } );
		                                 entitySave( o );
		                                 ormFlush();
		                                 ormClearSession();
		                                 nickname = entityLoad( "Owner", { name : "Margaret" }, true ).getNickname();
		                                 """ );
		assertThat( vars.getAsString( Key.of( "nickname" ) ) ).isEqualTo( "Maggie" );
	}

	@DisplayName( "trust mode refuses to boot when an entity source changed after the manifest was generated" )
	@Test
	public void testTrustFailsOnChangedEntity() throws IOException {
		runRequest( "auto", AUTO_SAVE );
		instance.getApplicationService().shutdownApplication( APP_NAME );

		Path	owner	= modelsDir.resolve( "Owner.bx" );
		String	source	= Files.readString( owner, StandardCharsets.UTF_8 );
		Files.writeString( owner, source.replace( "property name=\"name\" ormType=\"string\";",
		    "property name=\"name\" ormType=\"string\";\n\tproperty name=\"nickname\" ormType=\"string\";" ), StandardCharsets.UTF_8 );

		String message = bootFailure( "trust" );
		assertThat( message ).contains( "is stale" );
		assertThat( message ).contains( "entity [Owner] source changed" );
		assertThat( message ).doesNotContain( "entity [Pet]" );
	}

	@DisplayName( "trust mode refuses to boot when the ORM settings changed after the manifest was generated" )
	@Test
	public void testTrustFailsOnChangedSettings() {
		runRequest( "auto", AUTO_SAVE );
		instance.getApplicationService().shutdownApplication( APP_NAME );

		System.setProperty( PROP_NAMING, "smart" );
		String message = bootFailure( "trust" );
		assertThat( message ).contains( "is stale" );
		assertThat( message ).contains( "ORM settings changed" );
	}

	@DisplayName( "trust mode refuses to boot a manifest generated by a different bx-orm version" )
	@Test
	public void testTrustFailsOnDifferentModuleVersion() {
		runRequest( "auto", AUTO_SAVE );
		instance.getApplicationService().shutdownApplication( APP_NAME );

		// Rewrite the manifest (and its checksum) as if an older module had generated it.
		Path		bxorm		= manifestParent.resolve( ManifestService.FOLDER_NAME );
		OrmManifest	manifest	= ManifestService.read( bxorm, true );
		ManifestService.write( manifest.setOrmVersion( "1.0.0" ), bxorm );

		String message = bootFailure( "trust" );
		assertThat( message ).contains( "is stale" );
		assertThat( message ).contains( "generated by bx-orm 1.0.0" );
	}

	@DisplayName( "trust mode still boots when an entity file was only touched (newer mtime, same content)" )
	@Test
	public void testTrustBootsWhenSourceOnlyTouched() throws IOException {
		runRequest( "auto", AUTO_SAVE );
		instance.getApplicationService().shutdownApplication( APP_NAME );

		Path owner = modelsDir.resolve( "Owner.bx" );
		Files.setLastModifiedTime( owner, FileTime.fromMillis( Files.getLastModifiedTime( owner ).toMillis() + 60_000 ) );

		IScope vars = runRequest( "trust", """
		                                   entitySave( entityNew( "Owner", { name : "Touched" } ) );
		                                   ormFlush();
		                                   ormClearSession();
		                                   found = entityLoad( "Owner", { name : "Touched" }, true ).getName();
		                                   """ );
		assertThat( vars.getAsString( Key.of( "found" ) ) ).isEqualTo( "Touched" );
	}

	/**
	 * Boot the lifecycle app in the given mode, expecting startup to fail, and return the failure's full message chain.
	 */
	private String bootFailure( String mode ) {
		Throwable		failure	= assertThrows( Throwable.class, () -> runRequest( mode, "x = 1;" ) );
		StringBuilder	chain	= new StringBuilder();
		for ( Throwable t = failure; t != null; t = t.getCause() ) {
			chain.append( t.getMessage() ).append( '\n' );
		}
		return chain.toString();
	}

	/**
	 * Run one request against the lifecycle app in the given manifest mode and return its variables scope.
	 */
	private IScope runRequest( String mode, String source ) {
		System.setProperty( PROP_MODE, mode );
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
