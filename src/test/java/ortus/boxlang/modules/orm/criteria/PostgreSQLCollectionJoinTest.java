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
package ortus.boxlang.modules.orm.criteria;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Query;

/**
 * BLMODULES-319 on PostgreSQL: a criteria condition on a collection of an entity with a {@code json} column.
 * PostgreSQL has no equality operator for {@code json}, so the {@code select distinct} the criteria used to emit failed
 * with "could not identify an equality operator for type json". Skipped when no PostgreSQL server is reachable.
 * <p>
 * Data: Ann (team Beta; admin, editor), Bob (team Alpha; admin, editor, viewer), Cid (team Alpha; viewer), Dee (team
 * Beta; editor). The account entity sorts by name by default.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class PostgreSQLCollectionJoinTest {

	private static BoxRuntime	instance;
	private RequestBoxContext	context;
	private IScope				variables;

	private static String env( String name, String defaultValue ) {
		String value = System.getenv( name );
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static boolean postgresIsReachable() {
		String					url			= "jdbc:postgresql://" + env( "PG_HOST", "127.0.0.1" ) + ":" + env( "PG_PORT", "5432" ) + "/"
		    + env( "PG_DB", "ormtest" );
		java.util.Properties	credentials	= new java.util.Properties();
		credentials.setProperty( "user", env( "PG_USER", "postgres" ) );
		credentials.setProperty( "password", env( "PG_PASSWORD", "postgres" ) );
		// The driver is used directly: once the BoxLang runtime has started in this JVM (another test class ran first),
		// DriverManager no longer finds the test classpath's PostgreSQL driver ("No suitable driver").
		try ( Connection ignored = new org.postgresql.Driver().connect( url, credentials ) ) {
			return ignored != null;
		} catch ( Exception e ) {
			return false;
		}
	}

	@BeforeAll
	public void setUp() {
		assumeTrue( postgresIsReachable(), "No PostgreSQL server reachable, skipping" );
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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/postgresCriteriaApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
		// @formatter:off
		instance.executeSource( """
			queryExecute( "INSERT INTO pg319_teams ( id, name ) VALUES ( 1, 'Alpha' ), ( 2, 'Beta' )" );
			queryExecute( "INSERT INTO pg319_roles ( id, name ) VALUES ( 1, 'admin' ), ( 2, 'editor' ), ( 3, 'viewer' )" );
			queryExecute( "INSERT INTO pg319_accounts ( id, name, preferences, FK_team ) VALUES
				( 1, 'Ann', '{""theme"":""dark""}'::json, 2 ),
				( 2, 'Bob', '{""theme"":""light""}'::json, 1 ),
				( 3, 'Cid', '{}'::json, 1 ),
				( 4, 'Dee', NULL, 2 )" );
			queryExecute( "INSERT INTO pg319_account_roles ( FK_account, FK_role ) VALUES
				( 1, 1 ), ( 1, 2 ), ( 2, 1 ), ( 2, 2 ), ( 2, 3 ), ( 3, 3 ), ( 4, 2 )" );
		""", context );
		// @formatter:on
	}

	@AfterEach
	public void clearSession() {
		if ( context != null ) {
			instance.executeSource( "ormClearSession();", context );
		}
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
			instance.getApplicationService().shutdownApplication( Key.of( "BXORMPostgreSQLCriteriaTest" ) );
		}
	}

	private Object run( String code ) {
		instance.executeSource( code, context );
		return variables.get( Key.of( "result" ) );
	}

	private List<Object> names( String criteria ) {
		return ( ( Array ) run( "result = " + criteria + ".map( ( a ) => a.getName() );" ) ).toList();
	}

	private IStruct error( String code ) {
		instance.executeSource(
		    "try { " + code + " result = { type : 'NO ERROR', message : '' }; } catch ( any e ) { result = { type : e.type, message : e.message }; }",
		    context );
		return variables.getAsStruct( Key.of( "result" ) );
	}

	@DisplayName( "The ticket's query: createAlias on a collection with a json column on the root" )
	@Test
	public void testTicketReproduction() {
		assertThat( names( "entityCriteria( 'Pg319Account' ).createAlias( 'roles', 'assignedRole' ).isEq( 'assignedRole.id', 1 ).list()" ) )
		    .containsExactly( "Ann", "Bob" ).inOrder();
	}

	@DisplayName( "list() returns each account once and reads the json column" )
	@Test
	public void testListReadsJson() {
		assertThat( names( "entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'name' ).list()" ) )
		    .containsExactly( "Ann", "Bob", "Dee" ).inOrder();
		assertThat(
		    ( String ) run( "result = entityCriteria( 'Pg319Account' ).isEq( 'roles.name', 'admin' ).order( 'name' ).list().first().getPreferences();" ) )
		    .contains( "dark" );
	}

	@DisplayName( "count() counts each account once" )
	@Test
	public void testCount() {
		assertThat( ( ( Number ) run( "result = entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).count();" ) ).intValue() ).isEqualTo( 3 );
	}

	@DisplayName( "Paging counts accounts, not role rows" )
	@Test
	public void testPaging() {
		assertThat( names( "entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'name' ).maxResults( 2 ).list()" ) )
		    .containsExactly( "Ann", "Bob" ).inOrder();
		assertThat( names( "entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'name' ).maxResults( 2 ).firstResult( 2 ).list()" ) )
		    .containsExactly( "Dee" );
		IStruct page = ( IStruct ) run( "result = entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).paginate( 1, 2 );" );
		assertThat( ( ( Number ) page.getAsStruct( Key.of( "pagination" ) ).get( "totalRecords" ) ).intValue() ).isEqualTo( 3 );
		assertThat( ( ( Array ) page.get( "results" ) ).size() ).isEqualTo( 2 );
	}

	@DisplayName( "asStruct(), asQuery() and asStruct( includes ) return each account once with the json column" )
	@Test
	public void testShapes() {
		Array structs = ( Array ) run( "result = entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'name' ).asStruct().list();" );
		assertThat( structs.size() ).isEqualTo( 3 );
		assertThat( String.valueOf( ( ( IStruct ) structs.get( 0 ) ).get( "preferences" ) ) ).contains( "dark" );
		Query query = ( Query ) run( "result = entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'name' ).asQuery().list();" );
		assertThat( query.size() ).isEqualTo( 3 );
		Array memento = ( Array ) run(
		    "result = entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'name' ).asStruct( 'name,preferences' ).list();" );
		assertThat( memento.size() ).isEqualTo( 3 );
	}

	@DisplayName( "Ordering by a to-one property and the default sort apply to the outer query" )
	@Test
	public void testOrdering() {
		assertThat( names( "entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'team.name desc, name' ).list()" ) )
		    .containsExactly( "Ann", "Dee", "Bob" ).inOrder();
		assertThat( names( "entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 2, 3 ] ).list()" ) )
		    .containsExactly( "Ann", "Bob", "Cid", "Dee" ).inOrder();
		assertThat( ( String ) run( "result = entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 2, 3 ] ).getHQL();" ) )
		    .endsWith( "order by bx_o.name asc" );
	}

	@DisplayName( "fetch() still loads the team with a role condition" )
	@Test
	public void testFetch() {
		assertThat( ( ( Array ) run( """
		                             accounts = entityCriteria( 'Pg319Account' ).fetch( 'team' ).isEq( 'roles.name', 'viewer' ).order( 'name' ).list();
		                             ormClearSession();
		                             result = accounts.map( ( a ) => a.getTeam().getName() );
		                             """ ) ).toList() ).containsExactly( "Alpha", "Alpha" );
	}

	@DisplayName( "Ordering by a role property is a clear orm.argument error, not a database error" )
	@Test
	public void testOrderByCollectionIsError() {
		IStruct err = error( "entityCriteria( 'Pg319Account' ).isIn( 'roles.id', [ 1, 2 ] ).order( 'roles.name' ).list();" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.argument" );
	}
}
