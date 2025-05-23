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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

public class EntityDeleteTest extends BaseORMTest {

	@DisplayName( "It can delete existing entities from the database" )
	@Test
	public void testEntityDelete() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entityDelete( entityLoadByPK( "Vehicle", '1HGCM82633A123456' ) );
			}
			result = queryExecute( "SELECT * FROM vehicles WHERE vin = '1HGCM82633A123456'" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 0, variables.getAsQuery( result ).size() );
	}

	@DisplayName( "It can delete child entities with all-delete-orphan" )
	@Test
	public void testEntityDeleteOrphan() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entityDelete( entityLoadByPK( "cbContentStore", '779ccbb8-a444-11eb-ab6f-0290cc502ae3' ) );
			}
			result = queryExecute( "SELECT * FROM cb_contentVersion WHERE FK_contentID = '779ccbb8-a444-11eb-ab6f-0290cc502ae3'" );
			""",
			context
		);
		// @formatter:on
		assertEquals( 0, variables.getAsQuery( result ).size() );
	}

}
