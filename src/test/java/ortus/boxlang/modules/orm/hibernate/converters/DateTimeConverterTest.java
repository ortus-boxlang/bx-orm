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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DateTimeConverterTest {

	@DisplayName( "It coerces a LocalDateTime to java.util.Date" )
	@Test
	public void testLocalDateTimeToDateCoercion() {
		DateTimeConverter	converter	= new DateTimeConverter();
		LocalDateTime		now			= LocalDateTime.of( 2026, 5, 26, 12, 0, 0 );
		Date				result		= converter.convertToDatabaseColumn( now );
		assertThat( result ).isNotNull();
	}

	@DisplayName( "It handles null values" )
	@Test
	public void testNullToDateCoercion() {
		DateTimeConverter converter = new DateTimeConverter();
		assertThat( converter.convertToDatabaseColumn( null ) )
		    .isNull();
	}

	@DisplayName( "It converts Date back from database" )
	@Test
	public void testDateFromDatabaseColumn() {
		DateTimeConverter	converter	= new DateTimeConverter();
		Date				now			= new Date();
		Object				result		= converter.convertToEntityAttribute( now );
		assertThat( result ).isNotNull();
	}

}
