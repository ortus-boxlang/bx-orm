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
package ortus.boxlang.modules.orm.hibernate;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading an entity's id unsaved value back from its generated mapping, which {@link BoxPersisterFactory} applies to
 * Hibernate's boot model.
 */
public class UnsavedValueOfTest {

	@DisplayName( "It reads the unsaved value of the id element" )
	@Test
	public void testReadsIdUnsavedValue() {
		String xml = """
		             <entity-mappings><entity name="A"><attributes>
		               <id name="id"><unsaved-value>null</unsaved-value><target>Integer</target></id>
		               <basic name="code"/>
		             </attributes></entity></entity-mappings>
		             """;
		assertThat( BoxPersisterFactory.unsavedValueOf( xml ) ).isEqualTo( "null" );
		assertThat( BoxPersisterFactory.unsavedValueOf( xml.replace( ">null<", "> -1 <" ) ) ).isEqualTo( "-1" );
	}

	@DisplayName( "It returns null when the id has no unsaved value, or there is no id or mapping" )
	@Test
	public void testNoUnsavedValue() {
		assertThat( BoxPersisterFactory.unsavedValueOf( "<entity><attributes><id name=\"id\"><target>Integer</target></id></attributes></entity>" ) ).isNull();
		assertThat( BoxPersisterFactory.unsavedValueOf( "<entity><attributes><basic name=\"a\"/></attributes></entity>" ) ).isNull();
		assertThat( BoxPersisterFactory.unsavedValueOf( null ) ).isNull();
	}

	@DisplayName( "It only looks inside the id element" )
	@Test
	public void testOnlyInsideId() {
		String xml = "<entity><attributes><id name=\"id\"><target>Integer</target></id><version name=\"v\"><unsaved-value>0</unsaved-value></version></attributes></entity>";
		assertThat( BoxPersisterFactory.unsavedValueOf( xml ) ).isNull();
	}
}
