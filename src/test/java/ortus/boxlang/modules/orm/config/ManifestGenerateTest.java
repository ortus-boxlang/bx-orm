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
import java.util.Comparator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import ortus.boxlang.modules.orm.mapping.manifest.ManifestCli;
import ortus.boxlang.modules.orm.mapping.manifest.ManifestGenerator;
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
 * Tests for {@code module:orm generate} ({@link ManifestGenerator}): building the {@code .bxorm/} boot cache from the
 * Application class with no server, no database and no application start, then booting {@code trust} from it.
 * <p>
 * Uses the manifest lifecycle test app ({@code src/test/resources/manifestLifecycleApp}), whose entities and cache
 * location come from system properties, so every test works in its own temp folder and never writes into the source
 * tree.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class ManifestGenerateTest {

	private static final Key	APP_NAME		= Key.of( "BXORMLifecycleTest" );
	private static final Path	APP_ROOT		= Path.of( "src/test/resources/manifestLifecycleApp" ).toAbsolutePath();
	private static final String	PROP_MODE		= "bxorm.test.lifecycle.mode";
	private static final String	PROP_LOCATION	= "bxorm.test.lifecycle.location";
	private static final String	PROP_MODELS		= "bxorm.test.lifecycle.models";
	private static final String	PROP_DB			= "bxorm.test.lifecycle.db";
	private static final String	TRUST_CRUD		= """
	                                              o = entityNew( "Owner", { name : "Grace" } );
	                                              p = entityNew( "Pet", { name : "Cobol" } );
	                                              p.setOwner( o );
	                                              o.addPet( p );
	                                              entitySave( o );
	                                              ormFlush();
	                                              ormClearSession();
	                                              trustPets   = entityLoad( "Owner", { name : "Grace" }, true ).getPets().len();
	                                              entityTypes = ormGetSessionFactory().getMetamodel().getEntities().toArray();
	                                              jarDefined  = entityTypes[ 1 ].getJavaType().getClassLoader().getDefinedFromJarCount();
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
		workDir			= Files.createTempDirectory( "bxorm-generate" );
		modelsDir		= copyModels( Files.createDirectories( workDir.resolve( "models" ) ) );
		manifestParent	= Files.createDirectories( workDir.resolve( "cache-home" ) );
		System.setProperty( PROP_MODELS, modelsDir.toString() );
		System.setProperty( PROP_LOCATION, manifestParent.toString() );
		System.setProperty( PROP_DB, "ormGenerate" + System.nanoTime() );
	}

	@AfterEach
	public void cleanUp() throws IOException {
		instance.getApplicationService().shutdownApplication( APP_NAME );
		System.clearProperty( PROP_MODE );
		System.clearProperty( PROP_LOCATION );
		System.clearProperty( PROP_MODELS );
		System.clearProperty( PROP_DB );
		try ( var walk = Files.walk( workDir ) ) {
			walk.sorted( Comparator.reverseOrder() ).forEach( p -> p.toFile().delete() );
		}
	}

	@AfterAll
	public void teardown() {
		RequestBoxContext.removeCurrent();
	}

	@DisplayName( "generate writes the manifest and facade jar without starting the app; a trust boot uses both" )
	@Test
	public void testGenerateThenTrustBoot() {
		ManifestGenerator.Result	result	= ManifestGenerator.generate( APP_ROOT );

		Path						bxorm	= manifestParent.resolve( ManifestService.FOLDER_NAME );
		assertThat( result.appName() ).isEqualTo( APP_NAME.getName() );
		assertThat( result.entities() ).isEqualTo( 2 );
		assertThat( result.manifest() ).isEqualTo( ManifestService.manifestFile( bxorm, APP_NAME.getName() ) );
		assertThat( Files.exists( result.manifest() ) ).isTrue();
		assertThat( Files.exists( ManifestService.checksumFile( bxorm, APP_NAME.getName() ) ) ).isTrue();
		assertThat( Files.exists( result.facades() ) ).isTrue();
		// The configured ormManifestLocation was honored, and the application was never defined or started.
		assertThat( Files.exists( APP_ROOT.resolve( ManifestService.FOLDER_NAME ) ) ).isFalse();
		assertThat( instance.getApplicationService().hasApplication( APP_NAME ) ).isFalse();

		IScope vars = runRequest( "trust", TRUST_CRUD );
		assertThat( vars.getAsInteger( Key.of( "trustPets" ) ) ).isEqualTo( 1 );
		// Both facades came from the generated jar, not from a fresh ByteBuddy generation.
		assertThat( vars.getAsInteger( Key.of( "jarDefined" ) ) ).isEqualTo( 2 );
	}

	@DisplayName( "generate records entity sources as BoxLang paths, not machine paths" )
	@Test
	public void testGenerateRecordsPortablePaths() {
		ManifestGenerator.generate( APP_ROOT );

		OrmManifest manifest = ManifestService.read( manifestParent.resolve( ManifestService.FOLDER_NAME ), APP_NAME.getName(), true );
		assertThat( manifest.getFormatVersion() ).isEqualTo( OrmManifest.FORMAT_VERSION );
		assertThat( manifest.getEntities().stream().map( e -> e.source().getAsString( Key.path ) ).toList() )
		    .containsExactly( "/lifecycleModels/Owner.bx", "/lifecycleModels/Pet.bx" );
	}

	@DisplayName( "a cache generated in one folder trust-boots from another, and still catches a changed entity there" )
	@Test
	public void testGeneratedCacheDetectsChangesAfterMove() throws IOException {
		ManifestGenerator.generate( APP_ROOT );

		// Deploy the entities somewhere else: the /lifecycleModels mapping now points at a different folder.
		Path deployed = copyModels( Files.createDirectories( workDir.resolve( "deployed" ).resolve( "models" ) ) );
		System.setProperty( PROP_MODELS, deployed.toString() );

		IScope vars = runRequest( "trust", TRUST_CRUD );
		assertThat( vars.getAsInteger( Key.of( "jarDefined" ) ) ).isEqualTo( 2 );
		instance.getApplicationService().shutdownApplication( APP_NAME );

		Path	owner	= deployed.resolve( "Owner.bx" );
		String	source	= Files.readString( owner, StandardCharsets.UTF_8 );
		Files.writeString( owner, source.replace( "property name=\"name\" ormType=\"string\";",
		    "property name=\"name\" ormType=\"string\";\n\tproperty name=\"nickname\" ormType=\"string\";" ), StandardCharsets.UTF_8 );

		String message = bootFailure( "trust" );
		assertThat( message ).contains( "entity [Owner] source changed: /lifecycleModels/Owner.bx" );
		assertThat( message ).contains( "module:orm generate" );
	}

	@DisplayName( "generate runs a pseudo-constructor that reads cgi and url, which a CLI run has no request for" )
	@Test
	public void testGenerateProvidesEmptyWebScopes() throws IOException {
		Path						appRoot	= writeApp( "webapp", "BXORMGenerateWebScopes",
		    """
		    this.sessionTimeout = findNoCase( "/api/", cgi.path_info ) ? createTimespan( 0, 0, 1, 0 ) : createTimespan( 0, 1, 0, 0 );
		    if ( structKeyExists( url, "reinit" ) ) {
		    	this.reinit = true;
		    }
		    """, "\"entityPaths\" : [ \"/models\" ]," );

		ManifestGenerator.Result	result	= ManifestGenerator.generate( appRoot );
		assertThat( result.entities() ).isEqualTo( 2 );
	}

	@DisplayName( "with no entityPaths, sources are recorded under the app root, which generate maps to /" )
	@Test
	public void testGenerateDefaultEntityRootIsTheAppRoot() throws IOException {
		Object						rootBefore	= instance.getConfiguration().mappings.get( Key.of( "/" ) );
		Path						appRoot		= writeApp( "rootapp", "BXORMGenerateRoot", "", "" );

		ManifestGenerator.Result	result		= ManifestGenerator.generate( appRoot );
		OrmManifest					manifest	= ManifestService.read( result.manifest().getParent(), "BXORMGenerateRoot", true );
		assertThat( manifest.getEntities().stream().map( e -> e.source().getAsString( Key.path ) ).toList() )
		    .containsExactly( "/models/Owner.bx", "/models/Pet.bx" );
		// The / mapping is only borrowed for the generation.
		assertThat( instance.getConfiguration().mappings.get( Key.of( "/" ) ) ).isEqualTo( rootBefore );
	}

	@DisplayName( "generate fails with exit code 1 when the folder has no Application class" )
	@Test
	public void testGenerateFailsWithoutApplication() throws IOException {
		ManifestCli.CliResult result = ManifestGenerator.run( Files.createDirectories( workDir.resolve( "empty" ) ).toString() );
		assertThat( result.exitCode() ).isEqualTo( 1 );
		assertThat( result.message() ).contains( "No Application.bx or Application.cfc" );
	}

	@DisplayName( "generate fails with exit code 1 when the application does not enable the ORM" )
	@Test
	public void testGenerateFailsWhenOrmDisabled() throws IOException {
		Path appRoot = Files.createDirectories( workDir.resolve( "noorm" ) );
		Files.writeString( appRoot.resolve( "Application.bx" ), "class {\n\tthis.name = \"BXORMGenerateNoORM\";\n}\n", StandardCharsets.UTF_8 );

		ManifestCli.CliResult result = ManifestGenerator.run( appRoot.toString() );
		assertThat( result.exitCode() ).isEqualTo( 1 );
		assertThat( result.message() ).contains( "ORM is not enabled" );
	}

	/**
	 * Copy the lifecycle app's entity fixtures into a folder.
	 */
	private Path copyModels( Path target ) throws IOException {
		try ( var fixtures = Files.list( APP_ROOT.resolve( "models" ) ) ) {
			for ( Path fixture : fixtures.filter( p -> p.toString().endsWith( ".bx" ) ).toList() ) {
				Files.copy( fixture, target.resolve( fixture.getFileName() ) );
			}
		}
		return target;
	}

	/**
	 * Write a throwaway ORM application under the work folder, with the lifecycle entities in its {@code models} folder.
	 *
	 * @param folder      The application folder name.
	 * @param name        The application name.
	 * @param extra       Extra pseudo-constructor code.
	 * @param entityPaths The {@code entityPaths} setting line (with its trailing comma), or empty for none.
	 */
	private Path writeApp( String folder, String name, String extra, String entityPaths ) throws IOException {
		Path appRoot = Files.createDirectories( workDir.resolve( folder ) );
		copyModels( Files.createDirectories( appRoot.resolve( "models" ) ) );
		Files.writeString( appRoot.resolve( "Application.bx" ),
		    """
		    class {
		    	this.name = "%s";
		    	this.mappings[ "/models" ] = getDirectoryFromPath( getCurrentTemplatePath() ) & "models";
		    	this.datasources = { "GenerateDB" : { "connectionString" : "jdbc:derby:memory:generate%d;create=true" } };
		    	this.datasource = "GenerateDB";
		    	%s
		    	this.ormSettings = {
		    		"datasource"          : "GenerateDB",
		    		"dialect"             : "Derby",
		    		%s
		    		"dbcreate"            : "dropcreate",
		    		"ormManifestLocation" : "%s"
		    	};
		    	this.ormEnabled = "true";
		    }
		    """.formatted( name, System.nanoTime(), extra, entityPaths, manifestParent.toString().replace( "\\", "/" ) ),
		    StandardCharsets.UTF_8 );
		return appRoot;
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
