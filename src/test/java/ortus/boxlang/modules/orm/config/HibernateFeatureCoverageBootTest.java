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

import java.nio.file.Paths;

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
 * Broad, facade-mode (default) coverage of documented Hibernate mapping features that previously had no end-to-end test,
 * booted against embedded Derby. Each test round-trips a documented feature through the real ORM BIFs to prove the
 * facade / modern {@code mapping.xml} path still supports what the docs promise.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class HibernateFeatureCoverageBootTest {

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/featureApp/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables = context.getScopeNearby( VariablesScope.name );
	}

	@AfterAll
	public void teardown() {
		if ( context != null ) {
			context.getApplicationListener().onRequestEnd( context, null );
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMFeatureCoverageTest" ) );
	}

	@DisplayName( "It supports an optimistic-lock <version> column and a computed formula property" )
	@Test
	public void testVersionAndFormula() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				p = entityNew( "Product", { name : "Widget", price : 100 } );
				entitySave( p );
				id = p.getId();
			}
			ormFlush();
			ormClearSession();

			loaded        = entityLoadByPK( "Product", id );
			initialRev    = loaded.getRevision();
			taxed         = loaded.getPriceWithTax();

			transaction {
				loaded.setName( "Widget v2" );
				entitySave( loaded );
			}
			ormFlush();
			ormClearSession();

			reloaded      = entityLoadByPK( "Product", id );
			updatedRev    = reloaded.getRevision();
			finalName     = reloaded.getName();
		""", context );
		// @formatter:on

		// Version column is populated on insert and increments on update.
		assertThat( variables.get( Key.of( "initialRev" ) ) ).isNotNull();
		int	initialRev	= ( ( Number ) variables.get( Key.of( "initialRev" ) ) ).intValue();
		int	updatedRev	= ( ( Number ) variables.get( Key.of( "updatedRev" ) ) ).intValue();
		assertThat( updatedRev ).isGreaterThan( initialRev );
		assertThat( variables.get( Key.of( "finalName" ) ) ).isEqualTo( "Widget v2" );
		// Formula: price + price/10 = 110.
		assertThat( ( ( Number ) variables.get( Key.of( "taxed" ) ) ).intValue() ).isEqualTo( 110 );
	}
}
