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
package ortus.boxlang.modules.orm.mapping.manifest;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.orm.mapping.manifest.ManifestCli.CliResult;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Struct;

/**
 * Exercises the {@code bxorm} manifest CLI verbs against a written {@code .bxorm/} folder, entirely without a database
 * or an ORM boot: the CLI only reads/validates/clears the on-disk boot cache.
 */
public class ManifestCliTest {

	static BoxRuntime instance;

	@BeforeAll
	public static void setUp() {
		// JSON (de)serialization goes through the runtime's JSON builder; a headless instance is enough.
		instance = BoxRuntime.getInstance( false );
	}

	/** Write a minimal, valid manifest with two entities into {@code folder}, for the application {@code myapp}. */
	private void writeManifest( Path folder ) {
		writeManifest( folder, "myapp" );
	}

	/** Write a minimal, valid manifest with two entities into {@code folder}, for the given application. */
	private void writeManifest( Path folder, String app ) {
		OrmManifest manifest = new OrmManifest()
		    .setAppName( app )
		    .setOrmVersion( "2.0.0" )
		    .setConfigFingerprint( "abc123def456789" )
		    .setCombinedMappingXml( "<hibernate-mapping><class name=\"User\"/></hibernate-mapping>" );
		manifest.addEntity( new OrmManifest.Entity( "User", "models.User", "default",
		    Struct.of( "path", "models/User.bx" ), Struct.of( "name", "User" ), "<class name=\"User\"/>" ) );
		manifest.addEntity( new OrmManifest.Entity( "Role", "models.Role", "secondary",
		    Struct.of( "path", "models/Role.bx" ), Struct.of( "name", "Role" ), "<class name=\"Role\"/>" ) );
		ManifestService.write( manifest, folder );
	}

	@DisplayName( "info reports absence with exit 0 when no manifest exists" )
	@Test
	public void testInfoWhenAbsent( @TempDir Path folder ) {
		CliResult result = ManifestCli.run( folder, "2.0.0", "info" );
		assertThat( result.exitCode() ).isEqualTo( 0 );
		assertThat( result.message() ).contains( "No ORM manifest found" );
		assertThat( result.message() ).contains( "ormManifest=\"auto\"" );
		assertThat( result.message() ).contains( "--dir=" );
	}

	@DisplayName( "info summarizes a written manifest" )
	@Test
	public void testInfo( @TempDir Path folder ) {
		writeManifest( folder );
		CliResult result = ManifestCli.run( folder, "2.0.0" ); // default verb is info
		assertThat( result.exitCode() ).isEqualTo( 0 );
		assertThat( result.message() ).contains( "entities         : 2" );
		assertThat( result.message() ).contains( "checksummed" );
	}

	@DisplayName( "validate passes on a good manifest and fails on a tampered one" )
	@Test
	public void testValidate( @TempDir Path folder ) throws IOException {
		writeManifest( folder );
		assertThat( ManifestCli.run( folder, "2.0.0", "validate" ).exitCode() ).isEqualTo( 0 );

		// Tamper with the manifest bytes without updating the checksum.
		Path manifestFile = ManifestService.manifestFile( folder, "myapp" );
		Files.write( manifestFile, ( new String( Files.readAllBytes( manifestFile ), StandardCharsets.UTF_8 ) + " " ).getBytes( StandardCharsets.UTF_8 ) );

		CliResult tampered = ManifestCli.run( folder, "2.0.0", "validate" );
		assertThat( tampered.exitCode() ).isEqualTo( 1 );
		assertThat( tampered.message() ).contains( "INVALID" );
	}

	@DisplayName( "validate fails closed when the manifest is missing" )
	@Test
	public void testValidateMissing( @TempDir Path folder ) {
		CliResult result = ManifestCli.run( folder, "2.0.0", "validate" );
		assertThat( result.exitCode() ).isEqualTo( 1 );
		// Same guidance as the other verbs, and never a command that does not exist.
		assertThat( result.message() ).contains( "No ORM manifest found" );
		assertThat( result.message() ).contains( "ormManifest=\"auto\"" );
		assertThat( result.message() ).contains( "--dir=" );
		assertThat( result.message() ).doesNotContain( "manifest generate" );
	}

	@DisplayName( "entities, entity and mappings fail with exit 1 and the same guidance when no manifest exists" )
	@Test
	public void testReadVerbsWhenAbsent( @TempDir Path folder ) {
		for ( String[] args : new String[][] { { "entities" }, { "entity", "User" }, { "mappings" } } ) {
			CliResult result = ManifestCli.run( folder, "2.0.0", args );
			assertThat( result.exitCode() ).isEqualTo( 1 );
			assertThat( result.message() ).contains( "No ORM manifest found" );
			assertThat( result.message() ).contains( "--dir=" );
		}
	}

	@DisplayName( "entities lists all recorded entities" )
	@Test
	public void testEntities( @TempDir Path folder ) {
		writeManifest( folder );
		CliResult result = ManifestCli.run( folder, "2.0.0", "entities" );
		assertThat( result.exitCode() ).isEqualTo( 0 );
		assertThat( result.message() ).contains( "User" );
		assertThat( result.message() ).contains( "Role" );
	}

	@DisplayName( "entity <name> shows one entity, case-insensitively; unknown names exit 1" )
	@Test
	public void testEntityShow( @TempDir Path folder ) {
		writeManifest( folder );
		CliResult found = ManifestCli.run( folder, "2.0.0", "entity", "user" );
		assertThat( found.exitCode() ).isEqualTo( 0 );
		assertThat( found.message() ).contains( "models.User" );
		assertThat( found.message() ).contains( "mapping XML" );

		CliResult missing = ManifestCli.run( folder, "2.0.0", "entity", "Nope" );
		assertThat( missing.exitCode() ).isEqualTo( 1 );

		CliResult noArg = ManifestCli.run( folder, "2.0.0", "entity" );
		assertThat( noArg.exitCode() ).isEqualTo( 1 );
	}

	@DisplayName( "mappings prints the combined mapping XML" )
	@Test
	public void testMappings( @TempDir Path folder ) {
		writeManifest( folder );
		CliResult result = ManifestCli.run( folder, "2.0.0", "mappings" );
		assertThat( result.exitCode() ).isEqualTo( 0 );
		assertThat( result.message() ).contains( "hibernate-mapping" );
	}

	@DisplayName( "clear deletes the only application's cache; clear --all deletes the .bxorm/ folder" )
	@Test
	public void testClear( @TempDir Path folder ) {
		writeManifest( folder );
		assertThat( Files.exists( ManifestService.manifestFile( folder, "myapp" ) ) ).isTrue();
		CliResult result = ManifestCli.run( folder, "2.0.0", "clear" );
		assertThat( result.exitCode() ).isEqualTo( 0 );
		assertThat( result.message() ).contains( "[myapp]" );
		assertThat( ManifestService.listApps( folder ) ).isEmpty();

		writeManifest( folder );
		assertThat( ManifestCli.run( folder, "2.0.0", "clear", "--all" ).exitCode() ).isEqualTo( 0 );
		assertThat( Files.exists( folder ) ).isFalse();
	}

	@DisplayName( "several applications: info and validate cover all of them, other verbs ask for --app" )
	@Test
	public void testSeveralApps( @TempDir Path folder ) {
		writeManifest( folder, "shop" );
		writeManifest( folder, "admin" );

		CliResult info = ManifestCli.run( folder, "2.0.0", "info" );
		assertThat( info.exitCode() ).isEqualTo( 0 );
		assertThat( info.message() ).contains( "ORM manifest [admin]" );
		assertThat( info.message() ).contains( "ORM manifest [shop]" );

		CliResult validate = ManifestCli.run( folder, "2.0.0", "validate" );
		assertThat( validate.exitCode() ).isEqualTo( 0 );
		assertThat( validate.message() ).contains( "[admin]" );
		assertThat( validate.message() ).contains( "[shop]" );

		for ( String[] args : new String[][] { { "entities" }, { "entity", "User" }, { "mappings" }, { "clear" } } ) {
			CliResult result = ManifestCli.run( folder, "2.0.0", args );
			assertThat( result.exitCode() ).isEqualTo( 1 );
			assertThat( result.message() ).contains( "several applications: admin, shop" );
			assertThat( result.message() ).contains( "--app=<name>" );
		}

		CliResult entities = ManifestCli.run( folder, "2.0.0", "entities", "--app=shop" );
		assertThat( entities.exitCode() ).isEqualTo( 0 );
		assertThat( entities.message() ).contains( "ORM manifest [shop]" );

		CliResult entity = ManifestCli.run( folder, "2.0.0", "--app=Admin", "entity", "user" );
		assertThat( entity.exitCode() ).isEqualTo( 0 );
		assertThat( entity.message() ).contains( "models.User" );

		// clear --app removes only that application's cache.
		assertThat( ManifestCli.run( folder, "2.0.0", "clear", "--app=shop" ).exitCode() ).isEqualTo( 0 );
		assertThat( ManifestService.listApps( folder ) ).containsExactly( "admin" );
	}

	@DisplayName( "--app naming an application without a manifest reports it" )
	@Test
	public void testUnknownApp( @TempDir Path folder ) {
		writeManifest( folder, "shop" );
		CliResult info = ManifestCli.run( folder, "2.0.0", "info", "--app=nope" );
		assertThat( info.exitCode() ).isEqualTo( 0 );
		assertThat( info.message() ).contains( "No ORM manifest for application [nope]" );
		assertThat( ManifestCli.run( folder, "2.0.0", "validate", "--app=nope" ).exitCode() ).isEqualTo( 1 );
		assertThat( ManifestCli.run( folder, "2.0.0", "entities", "--app=nope" ).exitCode() ).isEqualTo( 1 );
	}

	@DisplayName( "validate fails when any one of several applications is tampered with" )
	@Test
	public void testValidateSeveralOneBad( @TempDir Path folder ) throws IOException {
		writeManifest( folder, "shop" );
		writeManifest( folder, "admin" );
		Path manifestFile = ManifestService.manifestFile( folder, "shop" );
		Files.write( manifestFile, ( new String( Files.readAllBytes( manifestFile ), StandardCharsets.UTF_8 ) + " " ).getBytes( StandardCharsets.UTF_8 ) );

		CliResult result = ManifestCli.run( folder, "2.0.0", "validate" );
		assertThat( result.exitCode() ).isEqualTo( 1 );
		assertThat( result.message() ).contains( "✅ ORM manifest [admin]" );
		assertThat( result.message() ).contains( "❌ ORM manifest [shop] is INVALID" );
	}

	@DisplayName( "version and help always succeed; unknown verbs exit 1" )
	@Test
	public void testMisc( @TempDir Path folder ) {
		assertThat( ManifestCli.run( folder, "2.0.0", "version" ).exitCode() ).isEqualTo( 0 );
		assertThat( ManifestCli.run( folder, "2.0.0", "help" ).exitCode() ).isEqualTo( 0 );
		CliResult unknown = ManifestCli.run( folder, "2.0.0", "frobnicate" );
		assertThat( unknown.exitCode() ).isEqualTo( 1 );
		assertThat( unknown.message() ).contains( "Unknown verb" );
	}

	@DisplayName( "BoxLang can invoke the CLI exactly as ModuleConfig.main does (interop + List overload)" )
	@Test
	public void testBoxLangInterop( @TempDir Path folder ) {
		writeManifest( folder );
		ScriptingRequestBoxContext	context		= new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		IScope						variables	= context.getScopeNearby( VariablesScope.name );
		variables.put( Key.of( "folderPath" ), folder.toString() );

		// This mirrors ModuleConfig.main(): createObject(...).run( <folder string>, <version>, <array> ) -> the List overload.
		// @formatter:off
		instance.executeSource( """
			result   = createObject( "java", "ortus.boxlang.modules.orm.mapping.manifest.ManifestCli" )
			           .run( javacast( "string", folderPath ), javacast( "string", "2.0.0" ), [ "info" ] );
			exitCode = result.exitCode();
			message  = result.message();
		""", context );
		// @formatter:on

		assertThat( variables.getAsInteger( Key.of( "exitCode" ) ) ).isEqualTo( 0 );
		assertThat( variables.getAsString( Key.of( "message" ) ) ).contains( "entities         : 2" );
	}
}
