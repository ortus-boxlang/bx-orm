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
package ortus.boxlang.modules.orm.mapping;

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
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

/**
 * useDBForMapping against embedded Derby: the table exists before the ORM starts, and the entity declares neither
 * ormtypes nor an id.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class DatabaseMappingTest {

	/** The BoxLang runtime. */
	private static BoxRuntime	instance;

	/** The request context the app runs in. */
	private RequestBoxContext	context;

	/** The request's variables scope. */
	private IScope				variables;

	/**
	 * Create the legacy table, then start the app.
	 */
	@BeforeAll
	public void setUp() {
		instance = BoxRuntime.getInstance( false );
		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Paths.get( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}
		// Create the table through BoxLang's own driver, before the ORM app exists.
		ScriptingRequestBoxContext setup = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		// @formatter:off
		instance.executeSource( """
			ds = { connectionString : "jdbc:derby:memory:ormDbMapping;create=true" };
			queryExecute( "CREATE TABLE legacy_people ( person_id INT NOT NULL PRIMARY KEY, full_name VARCHAR(50), born DATE, salary DECIMAL(10,2), active BOOLEAN )", {}, { datasource : ds } );
			queryExecute( "INSERT INTO legacy_people VALUES ( 1, 'Ann Baker', '1990-04-01', 1234.50, true )", {}, { datasource : ds } );
		""", setup );
		// @formatter:on
		setup.shutdown();

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/dbMappingApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	/**
	 * End the request and shut the app down.
	 */
	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMDbMappingTest" ) );
	}

	/**
	 * The id comes from the primary key and each ormtype from its column.
	 */
	@DisplayName( "useDBForMapping infers the id from the primary key and ormtypes from column types" )
	@Test
	public void testInferredMapping() {
		// @formatter:off
		instance.executeSource( """
			person   = entityLoadByPK( "LegacyPerson", 1 );
			name     = person.getFullName();
			bornDate = isDate( person.getBorn() );
			salary   = person.getSalary() + 0.5;
			active   = person.getActive();
			meta     = entityGetMetadata( "LegacyPerson" );
			transaction {
				entitySave( entityNew( "LegacyPerson", { personId : 2, fullName : "Bob", born : now(), salary : 10, active : false } ) );
			}
			count = ormExecuteQuery( "select count(*) from LegacyPerson", [], true );
		""", context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "name" ) ) ).isEqualTo( "Ann Baker" );
		assertThat( variables.get( Key.of( "bornDate" ) ) ).isEqualTo( true );
		// DECIMAL(10,2) maps to big_decimal, so the scale is kept.
		assertThat( variables.get( Key.of( "salary" ) ).toString() ).isEqualTo( "1235.00" );
		assertThat( variables.get( Key.of( "active" ) ) ).isEqualTo( true );
		assertThat( ( ( Number ) variables.get( Key.of( "count" ) ) ).intValue() ).isEqualTo( 2 );
	}

	/**
	 * JDBC types map to the expected ormtypes.
	 */
	@DisplayName( "JDBC column types map to ormtypes" )
	@Test
	public void testTypeMapping() {
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.VARCHAR ) ).isEqualTo( "string" );
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.INTEGER ) ).isEqualTo( "integer" );
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.DECIMAL ) ).isEqualTo( "big_decimal" );
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.DATE ) ).isEqualTo( "timestamp" );
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.BOOLEAN ) ).isEqualTo( "boolean" );
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.CLOB ) ).isEqualTo( "text" );
		assertThat( DatabaseMappingInspector.ormType( java.sql.Types.ARRAY ) ).isNull();
	}
}
