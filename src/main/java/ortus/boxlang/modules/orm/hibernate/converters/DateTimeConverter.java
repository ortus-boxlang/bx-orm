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

import java.sql.Timestamp;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.DateTimeCaster;

/**
 * Converts a BoxLang date/time value to a JDBC {@link Timestamp} and back.
 * <p>
 * The database column type is {@link Timestamp} rather than {@link java.util.Date}: bx-orm is the ORM abstraction and must
 * preserve the behavior applications relied on under Hibernate 5, which stored sub-second (millisecond) precision for
 * date/time properties. Hibernate 7 binds a bare {@link java.util.Date} as a whole-second {@code TIMESTAMP}, truncating the
 * fractional seconds even when the column is declared with precision (e.g. MySQL {@code datetime(6)}). Mapping to
 * {@link Timestamp} makes Hibernate bind the fractional seconds, restoring the Hibernate 5 behavior.
 *
 * @since 1.0.0
 */
@Converter( autoApply = true )
public class DateTimeConverter implements AttributeConverter<Object, Timestamp> {

	@Override
	public Timestamp convertToDatabaseColumn( Object attribute ) {
		return attribute != null ? Timestamp.from( DateTimeCaster.cast( attribute ).toInstant() ) : null;
	}

	@Override
	public Object convertToEntityAttribute( Timestamp dbData ) {
		return dbData != null ? DateTimeCaster.cast( dbData ) : dbData;
	}

}
