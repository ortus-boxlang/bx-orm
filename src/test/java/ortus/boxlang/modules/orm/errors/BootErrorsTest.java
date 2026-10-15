package ortus.boxlang.modules.orm.errors;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.BoxLangException;

/**
 * Broken ORM startups must fail with one clear orm.config / orm.boot error naming the entity, property or setting.
 * Each scenario boots src/test/resources/bootErrorApp with a different bxorm.test.bootScenario (own app name and
 * in-memory Derby DB).
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class BootErrorsTest {

	private BoxRuntime instance;

	/**
	 * Load the ORM module into the runtime once for this class.
	 */
	@BeforeAll
	public void setUp() {
		instance = BoxRuntime.getInstance( false );
		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord record = new ModuleRecord( Paths.get( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, record );
			record.loadDescriptor( instance.getRuntimeContext() ).register( instance.getRuntimeContext() ).activate( instance.getRuntimeContext() );
		}
	}

	/**
	 * Clear the scenario system properties and shut down every scenario's application.
	 */
	@AfterAll
	public void tearDown() {
		System.clearProperty( "bxorm.test.bootScenario" );
		for ( String scenario : new String[] { "ok", "badOrmType", "duplicates", "badDatasource", "missingPath", "badFieldType", "missingCfc", "badSoftDelete",
		    "badAutoTimestamp" } ) {
			instance.getApplicationService().shutdownApplication( Key.of( "BXORMBootErrors_" + scenario ) );
		}
	}

	/**
	 * Boot the bootErrorApp for one scenario in a fresh request context.
	 *
	 * @param scenario The bxorm.test.bootScenario value (see bootErrorApp/Application.bx).
	 *
	 * @return The startup error, or null when the application started.
	 */
	private BoxLangException boot( String scenario ) {
		System.setProperty( "bxorm.test.bootScenario", scenario );
		ScriptingRequestBoxContext context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		try {
			context.loadApplicationDescriptor( Paths.get( "src/test/resources/bootErrorApp/index.bxs" ).toAbsolutePath().toUri() );
			context.getApplicationListener().onRequestStart( context, null );
			return null;
		} catch ( BoxLangException e ) {
			return e;
		} finally {
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
	}

	/**
	 * Test: A valid app starts (the scenario harness itself works).
	 */
	@DisplayName( "A valid app starts (the scenario harness itself works)" )
	@Test
	public void testOk() {
		assertThat( boot( "ok" ) ).isNull();
	}

	/**
	 * Test: A failed ormReload() is remembered: ormDiagnostics shows it and the running ORM keeps working.
	 */
	@DisplayName( "A failed ormReload() is remembered: ormDiagnostics shows it and the running ORM keeps working" )
	@Test
	public void testFailedReloadIsRemembered() {
		assertThat( boot( "ok" ) ).isNull();
		System.setProperty( "bxorm.test.bootScenario", "ok" );
		System.setProperty( "bxorm.test.bootBreak", "true" );
		ScriptingRequestBoxContext context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		try {
			context.loadApplicationDescriptor( Paths.get( "src/test/resources/bootErrorApp/index.bxs" ).toAbsolutePath().toUri() );
			context.getApplicationListener().onRequestStart( context, null );
			// @formatter:off
			instance.executeSource(
			    """
			    try { ormReload(); reloadType = "NO ERROR"; } catch ( any e ) { reloadType = e.type; reloadMessage = e.message; }
			    diag = ormDiagnostics();
			    stillWorks = isObject( entityNew( "Widget" ) );
			    """,
			    context );
			// @formatter:on
			var variables = context.getScopeNearby( ortus.boxlang.runtime.scopes.VariablesScope.name );
			assertThat( variables.getAsString( Key.of( "reloadType" ) ) ).isEqualTo( "orm.config" );
			assertThat( variables.getAsString( Key.of( "reloadMessage" ) ) ).contains( "one-to-mony" );
			var diag = variables.getAsStruct( Key.of( "diag" ) );
			assertThat( diag.getAsString( Key.of( "status" ) ) ).isEqualTo( "running" );
			assertThat( diag.getAsStruct( Key.of( "startupError" ) ).getAsString( Key.of( "message" ) ) ).contains( "one-to-mony" );
			assertThat( diag.getAsStruct( Key.of( "startupError" ) ).getAsString( Key.of( "type" ) ) ).isEqualTo( "orm.config" );
			assertThat( variables.get( Key.of( "stillWorks" ) ) ).isEqualTo( true );
		} finally {
			System.clearProperty( "bxorm.test.bootBreak" );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
	}

	/**
	 * Test: An unknown ormtype is named, with a suggestion, when Hibernate fails to start.
	 */
	@DisplayName( "An unknown ormtype is named, with a suggestion, when Hibernate fails to start" )
	@Test
	public void testBadOrmType() {
		BoxLangException e = boot( "badOrmType" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "Gadget.name has ormtype=\"strang\", which is not a known type. Did you mean [string]?" );
	}

	/**
	 * Test: Two entities with the same name on one datasource are both named.
	 */
	@DisplayName( "Two entities with the same name on one datasource are both named" )
	@Test
	public void testDuplicateEntityNames() {
		BoxLangException e = boot( "duplicates" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "2 entities are named [Thing] on datasource" );
		assertThat( e.getMessage() ).doesNotContain( "EntityRecord@" );
	}

	/**
	 * Test: An ormSettings datasource that does not exist is an orm.config error listing the real ones.
	 */
	@DisplayName( "An ormSettings datasource that does not exist is an orm.config error listing the real ones" )
	@Test
	public void testBadDatasource() {
		BoxLangException e = boot( "badDatasource" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "NoSuchDatasource" );
		assertThat( e.getMessage() ).contains( "BootErrorsDB" );
	}

	/**
	 * Test: A missing entity folder points at entityPaths.
	 */
	@DisplayName( "A missing entity folder points at entityPaths" )
	@Test
	public void testMissingEntityPath() {
		BoxLangException e = boot( "missingPath" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "doesNotExist" );
		assertThat( e.getMessage() ).contains( "entityPaths" );
	}

	/**
	 * Test: An unknown fieldtype suggests the right one and lists the valid ones.
	 */
	@DisplayName( "An unknown fieldtype suggests the right one and lists the valid ones" )
	@Test
	public void testBadFieldType() {
		BoxLangException e = boot( "badFieldType" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "Unknown fieldtype 'one-to-mony' for property 'parts' on entity 'Sprocket'. Did you mean" );
		assertThat( e.getMessage() ).contains( "one-to-many" );
		assertThat( e.getMessage() ).contains( "Valid field types:" );
	}

	/**
	 * Test: A relationship to a missing cfc names the entity and property.
	 */
	@DisplayName( "A relationship to a missing cfc names the entity and property" )
	@Test
	public void testMissingCfc() {
		BoxLangException e = boot( "missingCfc" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "Could not find entity 'Customr'" );
		assertThat( e.getMessage() ).contains( "property 'customer' on entity 'Order'" );
	}

	/**
	 * Test: An unknown softDelete value is an orm.config error listing the accepted values.
	 */
	@DisplayName( "An unknown softDelete value is an orm.config error listing the accepted values" )
	@Test
	public void testBadSoftDelete() {
		BoxLangException e = boot( "badSoftDelete" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "sometimes" );
	}

	/**
	 * Test: An unknown autoTimestamp value is an orm.config error.
	 */
	@DisplayName( "An unknown autoTimestamp value is an orm.config error" )
	@Test
	public void testBadAutoTimestamp() {
		BoxLangException e = boot( "badAutoTimestamp" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "autoTimestamp=\"always\"" );
	}

	/**
	 * Test: A defaultSort naming an unknown property fails the boot with a suggestion.
	 */
	@DisplayName( "A defaultSort naming an unknown property fails the boot with a suggestion" )
	@Test
	public void testBadDefaultSort() {
		BoxLangException e = boot( "badDefaultSort" );
		assertThat( e.getType() ).isEqualTo( "orm.config" );
		assertThat( e.getMessage() ).contains( "titel" );
		assertThat( e.getMessage() ).contains( "title" );
	}
}
