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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

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
 * Boots a full ORM application against a real PostgreSQL datasource and saves entities whose table names are on the reserved-word list.
 * <p>
 * Those table names are quoted in the generated mapping. With {@code generator="identity"} Hibernate reads the new key back with
 * {@code select currval('
 * 
<table>
 * _<column>_seq')}, so a quoted table name used to produce {@code currval('"comment"_id_seq')}, which PostgreSQL rejects
 * with "invalid name syntax" (BL-2739).
 * <p>
 * Connection settings come from {@code PG_HOST}, {@code PG_PORT}, {@code PG_DB}, {@code PG_USER} and {@code PG_PASSWORD}. The tests are skipped
 * when
 * no PostgreSQL server is reachable. Subclasses choose the ORM dialect: explicit, or blank to let Hibernate auto-detect it.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public abstract class AbstractPostgreSQLIdentityTest {

	private static BoxRuntime	instance;

	private RequestBoxContext	context;
	private IScope				variables;

	/**
	 * @return The value of the ORM {@code dialect} setting, or an empty string to auto-detect.
	 */
	protected abstract String ormDialect();

	/**
	 * @return A unique application name, so each subclass boots its own ORM application.
	 */
	protected abstract String appName();

	private static String env( String name, String defaultValue ) {
		String value = System.getenv( name );
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static boolean postgresIsReachable() {
		String url = "jdbc:postgresql://" + env( "PG_HOST", "127.0.0.1" ) + ":" + env( "PG_PORT", "5432" ) + "/" + env( "PG_DB", "ormtest" );
		try ( Connection ignored = DriverManager.getConnection( url, env( "PG_USER", "postgres" ), env( "PG_PASSWORD", "postgres" ) ) ) {
			return true;
		} catch ( Exception e ) {
			return false;
		}
	}

	@BeforeAll
	public void setUp() {
		assumeTrue( postgresIsReachable(), "No PostgreSQL server reachable, skipping" );

		System.setProperty( "PG_APP_NAME", appName() );
		System.setProperty( "PG_ORM_DIALECT", ormDialect() );

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/postgresIdentityApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
			instance.getApplicationService().shutdownApplication( Key.of( appName() ) );
		}
	}

	@DisplayName( "It reads back the identity of a table whose name is on the reserved-word list" )
	@Test
	public void testReservedWordTableIdentity() {
		// @formatter:off
		instance.executeSource( """
			c = entityNew( "Comment", { body : "hello" } );
			entitySave( c );
			ormFlush();
			commentId = c.getId();
		""", context );
		// @formatter:on

		assertThat( ( ( Number ) variables.get( Key.of( "commentId" ) ) ).intValue() ).isEqualTo( 1 );
	}

	@DisplayName( "It reads back the identity of a table that PostgreSQL itself requires to be quoted" )
	@Test
	public void testPostgresReservedTableIdentity() {
		// @formatter:off
		instance.executeSource( """
			m = entityNew( "Member", { name : "Jane" } );
			entitySave( m );
			ormFlush();
			memberId = m.getId();
		""", context );
		// @formatter:on

		assertThat( ( ( Number ) variables.get( Key.of( "memberId" ) ) ).intValue() ).isEqualTo( 1 );
	}

	@DisplayName( "It still reads back the identity of a table that needs no quoting" )
	@Test
	public void testPlainTableIdentity() {
		// @formatter:off
		instance.executeSource( """
			r = entityNew( "Remark", { body : "plain" } );
			entitySave( r );
			ormFlush();
			remarkId = r.getId();
		""", context );
		// @formatter:on

		assertThat( ( ( Number ) variables.get( Key.of( "remarkId" ) ) ).intValue() ).isEqualTo( 1 );
	}
}
