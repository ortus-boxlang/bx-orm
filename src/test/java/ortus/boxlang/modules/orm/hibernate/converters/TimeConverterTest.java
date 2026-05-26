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

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TimeConverterTest {

	@DisplayName( "It coerces a string time to LocalTime" )
	@Test
	public void testStringToLocalTimeCoercion() {
		TimeConverter	converter	= new TimeConverter();
		LocalTime		result		= converter.convertToDatabaseColumn( "12:00:00" );
		assertThat( result ).isNotNull();
		assertThat( result.getHour() ).isEqualTo( 12 );
	}

	@DisplayName( "It handles null values" )
	@Test
	public void testNullToLocalTimeCoercion() {
		TimeConverter converter = new TimeConverter();
		assertThat( converter.convertToDatabaseColumn( null ) )
		    .isNull();
	}

	@DisplayName( "It returns LocalTime back from database" )
	@Test
	public void testLocalTimeFromDatabaseColumn() {
		TimeConverter	converter	= new TimeConverter();
		LocalTime		noon		= LocalTime.of( 12, 0 );
		Object			result		= converter.convertToEntityAttribute( noon );
		assertThat( result ).isNotNull();
	}

}
