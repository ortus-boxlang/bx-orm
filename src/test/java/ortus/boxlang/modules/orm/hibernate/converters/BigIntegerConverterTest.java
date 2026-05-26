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

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BigIntegerConverterTest {

	@DisplayName( "It coerces an Integer value to BigInteger" )
	@Test
	public void testIntegerToBigIntegerCoercion() {
		BigIntegerConverter converter = new BigIntegerConverter();
		assertThat( converter.convertToDatabaseColumn( Integer.valueOf( 42 ) ) )
		    .isEqualTo( BigInteger.valueOf( 42L ) );
	}

	@DisplayName( "It handles null values" )
	@Test
	public void testNullToBigIntegerCoercion() {
		BigIntegerConverter converter = new BigIntegerConverter();
		assertThat( converter.convertToDatabaseColumn( null ) )
		    .isNull();
	}

	@DisplayName( "It returns BigInteger back from database" )
	@Test
	public void testBigIntegerFromDatabaseColumn() {
		BigIntegerConverter converter = new BigIntegerConverter();
		assertThat( converter.convertToEntityAttribute( BigInteger.valueOf( 99L ) ) )
		    .isEqualTo( BigInteger.valueOf( 99L ) );
	}

}
