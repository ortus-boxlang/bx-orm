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

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BigDecimalConverterTest {

	@DisplayName( "It coerces an Integer value to BigDecimal" )
	@Test
	public void testIntegerToBigDecimalCoercion() {
		BigDecimalConverter converter = new BigDecimalConverter();
		assertThat( converter.convertToDatabaseColumn( Integer.valueOf( 0 ) ) )
		    .isEqualTo( BigDecimal.ZERO );
	}

	@DisplayName( "It handles null values" )
	@Test
	public void testNullToBigDecimalCoercion() {
		BigDecimalConverter converter = new BigDecimalConverter();
		assertThat( converter.convertToDatabaseColumn( null ) )
		    .isNull();
	}

	@DisplayName( "It returns BigDecimal back from database" )
	@Test
	public void testBigDecimalFromDatabaseColumn() {
		BigDecimalConverter converter = new BigDecimalConverter();
		assertThat( converter.convertToEntityAttribute( new BigDecimal( "1.50" ) ) )
		    .isEqualTo( new BigDecimal( "1.50" ) );
	}

}
