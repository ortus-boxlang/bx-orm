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
package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class ORMClearSessionTest extends BaseORMTest {

	@DisplayName( "It can clear the session for the default datasource" )
	@Test
	public void testSessionClear() {
		instance.executeSource(
		    """
		    theEntity = entityNew( "Manufacturer", { name : "Bugatti Automobiles", address : "101 Bugatti Way" } );
		    entitySave( theEntity );

		    beforeClear = ormGetSession().contains( theEntity );
		    ormClearSession();
		    afterClear = ormGetSession().contains( theEntity );
		    """,
		    context
		);
		assertThat( variables.get( Key.of( "beforeClear" ) ) )
		    .isEqualTo( true );
		assertThat( variables.get( Key.of( "afterClear" ) ) )
		    .isEqualTo( false );
	}

	@DisplayName( "It can clear the session on a named (alternate) datasource" )
	@Test
	public void testSessionClearOnNamedDatasource() {
		instance.executeSource(
		    """
		    theEntity = entityNew( "AlternateDS", { id: createUUID(), name : "Testy McTesterson" } );
		    entitySave( theEntity );

		    beforeClear = ormGetSession( "dsn2" ).contains( theEntity );
		    ormClearSession( "dsn2" );
		    afterClear = ormGetSession( "dsn2" ).contains( theEntity );
		    """,
		    context
		);
		assertThat( variables.get( Key.of( "beforeClear" ) ) )
		    .isEqualTo( true );
		assertThat( variables.get( Key.of( "afterClear" ) ) )
		    .isEqualTo( false );
	}

}
