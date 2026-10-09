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
package ortus.boxlang.modules.orm.memento;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DateTime;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Turns date and time values into ISO 8601 strings for {@code entityToStruct()}, {@code entityLoadAsStruct()} and the
 * criteria {@code asStruct()} output, so structs serialize the same way everywhere.
 * <p>
 * Date-times become {@code yyyy-MM-dd'T'HH:mm:ssXXX} (e.g. {@code 2026-09-26T13:39:48Z}), dates {@code yyyy-MM-dd} and
 * times {@code HH:mm:ss}. Other values are returned unchanged; arrays and structs are converted element by element.
 */
public final class IsoDates {

	/** The date-time format. */
	private static final DateTimeFormatter	DATE_TIME	= DateTimeFormatter.ofPattern( "yyyy-MM-dd'T'HH:mm:ssXXX" );
	/** The date format. */
	private static final DateTimeFormatter	DATE		= DateTimeFormatter.ISO_LOCAL_DATE;
	/** The time format. */
	private static final DateTimeFormatter	TIME		= DateTimeFormatter.ofPattern( "HH:mm:ss" );

	/**
	 * Not instantiable.
	 */
	private IsoDates() {
	}

	/**
	 * Convert a value's dates to ISO 8601 strings.
	 *
	 * @param value Any value.
	 *
	 * @return The ISO string for a date or time, a converted copy of an array or struct, else the value.
	 */
	public static Object convert( Object value ) {
		if ( value == null || value instanceof ortus.boxlang.runtime.runnables.IClassRunnable ) {
			return value;
		}
		if ( value instanceof DateTime dateTime ) {
			return dateTime.getWrapped().format( DATE_TIME );
		}
		if ( value instanceof ZonedDateTime zoned ) {
			return zoned.format( DATE_TIME );
		}
		if ( value instanceof OffsetDateTime offset ) {
			return offset.format( DATE_TIME );
		}
		if ( value instanceof Instant instant ) {
			return instant.atZone( ZoneId.systemDefault() ).format( DATE_TIME );
		}
		if ( value instanceof LocalDateTime local ) {
			return local.atZone( ZoneId.systemDefault() ).format( DATE_TIME );
		}
		if ( value instanceof LocalDate date ) {
			return date.format( DATE );
		}
		if ( value instanceof LocalTime time ) {
			return time.format( TIME );
		}
		if ( value instanceof java.sql.Date sqlDate ) {
			return sqlDate.toLocalDate().format( DATE );
		}
		if ( value instanceof java.sql.Time sqlTime ) {
			return sqlTime.toLocalTime().format( TIME );
		}
		if ( value instanceof java.util.Date date ) {
			return date.toInstant().atZone( ZoneId.systemDefault() ).format( DATE_TIME );
		}
		if ( value instanceof java.util.Calendar calendar ) {
			return calendar.toInstant().atZone( calendar.getTimeZone().toZoneId() ).format( DATE_TIME );
		}
		if ( value instanceof Array array ) {
			Array copy = new Array();
			array.forEach( item -> copy.add( convert( item ) ) );
			return copy;
		}
		if ( value instanceof IStruct struct ) {
			IStruct copy = new Struct( IStruct.TYPES.LINKED );
			struct.forEach( ( key, item ) -> copy.put( key, convert( item ) ) );
			return copy;
		}
		if ( value instanceof java.util.List<?> list ) {
			Array copy = new Array();
			list.forEach( item -> copy.add( convert( item ) ) );
			return copy;
		}
		if ( value instanceof Map<?, ?> map ) {
			IStruct copy = new Struct( IStruct.TYPES.LINKED );
			map.forEach( ( key, item ) -> copy.put( Key.of( String.valueOf( key ) ), convert( item ) ) );
			return copy;
		}
		return value;
	}
}
