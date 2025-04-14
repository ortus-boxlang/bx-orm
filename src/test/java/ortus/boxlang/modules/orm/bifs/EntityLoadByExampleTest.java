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

import ortus.boxlang.runtime.runnables.IBoxRunnable;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityLoadByExampleTest extends BaseORMTest {

	@DisplayName( "Tests the function returning an array" )
	@Test
	public void testEntityLoadByExample() {
		instance.executeSource( """
		                        	example = entityNew( "Category" );
		                        	example.setCategory( "general" );
		                        	result = entityLoadByExample( example );
		                        """, context );
		assertThat( variables.get( result ) ).isInstanceOf( Array.class );
		variables.getAsArray( result ).stream()
		    .forEach( entity -> assertThat( ( ( IClassRunnable ) entity ).get( Key.of( "category" ) ) ).isEqualTo( "general" ) );
	}

	@DisplayName( "Tests the function with a unique flag" )
	@Test
	public void testEntityLoadByExampleUnique() {
		instance.executeSource( """
		                        	example = entityNew( "Category" );
		                        	example.setCategory( "general" );
		                        	result = entityLoadByExample( example, true );
		                        """, context );
		assertThat( variables.get( result ) ).isInstanceOf( IBoxRunnable.class );
		assertThat( variables.getAsClassRunnable( result ).get( Key.of( "category" ) ) ).isEqualTo( "general" );
	}

}
