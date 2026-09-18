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
 * Boots a facade-mode ORM application whose bidirectional one-to-many uses a <em>capitalized</em> owning property name
 * ({@code Item.Owner}), and round-trips the association through the real BIFs.
 * <p>
 * This is a regression guard for the modern {@code mapping.xml} format: a collection's {@code mapped-by} names the
 * XML-declared attribute ({@code "Owner"}), but a facade class exposes a {@code getOwner()} bean accessor whose
 * JavaBean property name is the decapitalized {@code "owner"}. If Hibernate is allowed to bean-introspect the facade
 * (metadata not marked complete) it registers the attribute under {@code "owner"} and the {@code mapped-by="Owner"}
 * fails to resolve, breaking every entity in the application at SessionFactory build time.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FacadeMappedByBootTest {

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
		context.loadApplicationDescriptor( Paths.get( "src/test/resources/facadeMappedByApp/index.bxs" ).toAbsolutePath().toUri() );
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
		instance.getApplicationService().shutdownApplication( Key.of( "BXORMFacadeMappedByTest" ) );
	}

	@DisplayName( "It boots and round-trips a bidirectional one-to-many whose owning property name is capitalized" )
	@Test
	public void testCapitalizedMappedBy() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				owner = entityNew( "Owner", { name : "Acme" } );
				entitySave( owner );

				item = entityNew( "Item", { label : "Widget" } );
				item.setOwner( owner );
				item.setPhoto( charsetDecode( "hi-bytes", "utf-8" ) );
				entitySave( item );

				ownerId = owner.getId();
			}
			ormFlush();
			ormClearSession();

			loadedItem  = entityLoadByPK( "Item", ormExecuteQuery( "SELECT i.id FROM Item i" )[ 1 ] );
			itemOwner   = loadedItem.getOwner();
			ownerName   = itemOwner.getName();
			// The byte[] (binary) column round-tripped through the facade's concrete byte[] accessor.
			photoText   = charsetEncode( loadedItem.getPhoto(), "utf-8" );
		""", context );
		// @formatter:on

		assertThat( variables.get( Key.of( "ownerName" ) ) ).isEqualTo( "Acme" );
		assertThat( variables.get( Key.of( "photoText" ) ) ).isEqualTo( "hi-bytes" );
	}
}
