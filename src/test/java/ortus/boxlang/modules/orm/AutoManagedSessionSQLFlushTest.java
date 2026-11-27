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
package ortus.boxlang.modules.orm;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Paths;

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

/**
 * BLMODULES-318 with {@code autoManageSession: true}: the session is AUTO, which only makes Hibernate flush before its
 * own queries. SQL ({@code queryExecute()}) goes around Hibernate, so before the fix it missed the ORM writes in this mode
 * too. {@code AutoProbe} has a uuid id, so {@code entitySave()} only queues the insert, and its {@code preInsert} runs
 * SQL while Hibernate is flushing.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class AutoManagedSessionSQLFlushTest {

	private static BoxRuntime	instance;
	private RequestBoxContext	context;
	private IScope				variables;

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
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/autoSessionApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	@AfterEach
	public void clearSession() {
		instance.executeSource( "ormClearSession();", context );
	}

	@AfterAll
	public void teardown() {
		context.getApplicationListener().onRequestEnd( context, null );
		RequestBoxContext.removeCurrent();
		context.shutdown();
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMAutoSessionTest" ) );
	}

	private int intVar( String name ) {
		return ( ( Number ) variables.get( Key.of( name ) ) ).intValue();
	}

	@DisplayName( "With autoManageSession on, queryExecute() after entitySave() in a transaction sees the row" )
	@Test
	public void testQueryExecuteSeesORMWrite() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				probe = entityNew( "AutoProbe" );
				probe.setName( "auto-" & createUUID() );
				entitySave( probe );
				flushMode = ormGetSession().getHibernateFlushMode().toString();
				seen = queryExecute( "SELECT count(*) AS c FROM auto_probes WHERE name = :n", { n : probe.getName() } ).c;
				transactionRollback();
			}
			""", context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "flushMode" ) ) ).isEqualTo( "AUTO" );
		assertThat( intVar( "seen" ) ).isEqualTo( 1 );
	}

	@DisplayName( "Outside a transaction, neither SQL nor an ORM query flushes, even with autoManageSession on" )
	@Test
	public void testNoFlushOutsideTransaction() {
		// @formatter:off
		instance.executeSource( """
			probe = entityNew( "AutoProbe" );
			probe.setName( "outside-" & createUUID() );
			entitySave( probe );
			sqlSeen = queryExecute( "SELECT count(*) AS c FROM auto_probes WHERE name = :n", { n : probe.getName() } ).c;
			hqlSeen = ormExecuteQuery( "select count(p) from AutoProbe p where p.name = :n", { n : probe.getName() }, true );
			ormClearSession();
			""", context );
		// @formatter:on
		// The ORM runs no Hibernate transaction, and Hibernate only auto-flushes inside one: outside transaction{} nothing
		// flushes before a query in either mode.
		assertThat( intVar( "sqlSeen" ) ).isEqualTo( 0 );
		assertThat( intVar( "hqlSeen" ) ).isEqualTo( 0 );
	}

	@DisplayName( "SQL run from preInsert during a flush does not start a second flush" )
	@Test
	public void testSQLDuringFlushDoesNotReflush() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				before = queryExecute( "SELECT count(*) AS c FROM auto_probes" ).c;
				for ( i = 1; i <= 3; i++ ) {
					probe = entityNew( "AutoProbe" );
					probe.setName( "event-" & i & "-" & createUUID() );
					entitySave( probe );
				}
				// This query flushes the three inserts; each preInsert runs SQL in the middle of that flush.
				seen = queryExecute( "SELECT count(*) AS c FROM auto_probes" ).c - before;
				rowsAtLastInsert = request.rowsAtInsert - before;
				transactionRollback();
			}
			""", context );
		// @formatter:on
		assertThat( intVar( "seen" ) ).isEqualTo( 3 );
		// The last preInsert ran before its own insert, after the first two: SQL inside a flush sees what is already written.
		assertThat( intVar( "rowsAtLastInsert" ) ).isEqualTo( 2 );
	}
}
