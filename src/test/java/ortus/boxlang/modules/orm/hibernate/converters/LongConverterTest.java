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
package ortus.boxlang.modules.orm.hibernate.converters;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LongConverterTest {

	@DisplayName( "It coerces an Integer value to Long" )
	@Test
	public void testIntegerToLongCoercion() {
		LongConverter converter = new LongConverter();
		assertThat( converter.convertToDatabaseColumn( Integer.valueOf( 42 ) ) )
		    .isEqualTo( Long.valueOf( 42L ) );
	}

	@DisplayName( "It handles null values" )
	@Test
	public void testNullToLongCoercion() {
		LongConverter converter = new LongConverter();
		assertThat( converter.convertToDatabaseColumn( null ) )
		    .isNull();
	}

	@DisplayName( "It returns Long back from database" )
	@Test
	public void testLongFromDatabaseColumn() {
		LongConverter converter = new LongConverter();
		assertThat( converter.convertToEntityAttribute( Long.valueOf( 999L ) ) )
		    .isEqualTo( Long.valueOf( 999L ) );
	}

}
