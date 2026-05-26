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

public class BooleanConverterTest {

	@DisplayName( "It coerces an Integer 1 to Boolean true" )
	@Test
	public void testIntegerToBooleanCoercion() {
		BooleanConverter converter = new BooleanConverter();
		assertThat( converter.convertToDatabaseColumn( Integer.valueOf( 1 ) ) )
		    .isTrue();
	}

	@DisplayName( "It coerces an Integer 0 to Boolean false" )
	@Test
	public void testZeroToBooleanCoercion() {
		BooleanConverter converter = new BooleanConverter();
		assertThat( converter.convertToDatabaseColumn( Integer.valueOf( 0 ) ) )
		    .isFalse();
	}

	@DisplayName( "It handles null values" )
	@Test
	public void testNullToBooleanCoercion() {
		BooleanConverter converter = new BooleanConverter();
		assertThat( converter.convertToDatabaseColumn( null ) )
		    .isNull();
	}

	@DisplayName( "It returns Boolean back from database" )
	@Test
	public void testBooleanFromDatabaseColumn() {
		BooleanConverter converter = new BooleanConverter();
		assertThat( converter.convertToEntityAttribute( Boolean.TRUE ) )
		    .isEqualTo( Boolean.TRUE );
	}

}
