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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.type.Type;

import ortus.boxlang.runtime.dynamic.casters.BigDecimalCaster;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.CastAttempt;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;

/**
 * Converts a query parameter or filter value to the Java type Hibernate expects for it (BLMODULES-307).
 * <p>
 * Hibernate 5 binds values as they are, so a string id from a form ({@code "1"}) or a BoxLang decimal ({@code 1.0}) fails
 * against an integer property with a {@code ClassCastException}. This converts numbers, booleans and strings with the
 * BoxLang casters. Integer types only take whole numbers, so {@code "1.5"} is never truncated to {@code 1}.
 * <p>
 * A value that cannot be converted, or a type this does not handle (entities, dates, collections), is returned unchanged,
 * so Hibernate sees the same value it did before.
 */
public final class ParameterCoercer {

	private ParameterCoercer() {
	}

	/**
	 * Convert a value to the Java class a Hibernate type returns.
	 *
	 * @param value The value to bind.
	 * @param type  The Hibernate type of the parameter or property, or null when unknown.
	 *
	 * @return The converted value, or the value unchanged.
	 */
	public static Object coerce( Object value, Type type ) {
		if ( value == null || type == null || type.isAssociationType() || type.isComponentType() || type.isCollectionType() ) {
			return value;
		}
		Class<?> target = type.getReturnedClass();
		// An array bound to one parameter (WHERE id IN (:ids)) is expanded by Hibernate: convert each item.
		if ( value instanceof Collection<?> values && !Collection.class.isAssignableFrom( target ) ) {
			List<Object> converted = new ArrayList<>( values.size() );
			for ( Object item : values ) {
				converted.add( coerce( item, target ) );
			}
			return converted;
		}
		return coerce( value, target );
	}

	/**
	 * Convert a value to a target Java class.
	 *
	 * @param value  The value to bind.
	 * @param target The class Hibernate expects, or null when unknown.
	 *
	 * @return The converted value, or the value unchanged.
	 */
	public static Object coerce( Object value, Class<?> target ) {
		if ( value == null || target == null || target.isInstance( value ) ) {
			return value;
		}
		try {
			if ( target == Integer.class || target == int.class ) {
				BigDecimal whole = wholeNumber( value );
				return whole == null ? value : whole.intValueExact();
			}
			if ( target == Long.class || target == long.class ) {
				BigDecimal whole = wholeNumber( value );
				return whole == null ? value : whole.longValueExact();
			}
			if ( target == Short.class || target == short.class ) {
				BigDecimal whole = wholeNumber( value );
				return whole == null ? value : whole.shortValueExact();
			}
			if ( target == Byte.class || target == byte.class ) {
				BigDecimal whole = wholeNumber( value );
				return whole == null ? value : whole.byteValueExact();
			}
			if ( target == BigInteger.class ) {
				BigDecimal whole = wholeNumber( value );
				return whole == null ? value : whole.toBigIntegerExact();
			}
			if ( target == BigDecimal.class ) {
				BigDecimal number = number( value );
				return number == null ? value : number;
			}
			if ( target == Double.class || target == double.class ) {
				BigDecimal number = number( value );
				return number == null ? value : number.doubleValue();
			}
			if ( target == Float.class || target == float.class ) {
				BigDecimal number = number( value );
				return number == null ? value : number.floatValue();
			}
			if ( target == Boolean.class || target == boolean.class ) {
				if ( value instanceof String || value instanceof Number ) {
					CastAttempt<Boolean> attempt = BooleanCaster.attempt( value );
					return attempt.wasSuccessful() ? attempt.get() : value;
				}
				return value;
			}
			if ( target == String.class ) {
				if ( value instanceof Number || value instanceof Boolean || value instanceof Character ) {
					CastAttempt<String> attempt = StringCaster.attempt( value );
					return attempt.wasSuccessful() ? attempt.get() : value;
				}
				return value;
			}
		} catch ( ArithmeticException e ) {
			// Out of range for the target type: leave it for Hibernate to report.
			return value;
		}
		return value;
	}

	/**
	 * The value as a number, when it is a number or a numeric string.
	 *
	 * @param value The value.
	 *
	 * @return The number, or null.
	 */
	private static BigDecimal number( Object value ) {
		if ( ! ( value instanceof Number || value instanceof String ) ) {
			return null;
		}
		if ( value instanceof String str && str.isBlank() ) {
			return null;
		}
		CastAttempt<BigDecimal> attempt = BigDecimalCaster.attempt( value instanceof String str ? str.trim() : value );
		return attempt.wasSuccessful() ? attempt.get() : null;
	}

	/**
	 * The value as a whole number, when it is a number or numeric string with no fractional part.
	 *
	 * @param value The value.
	 *
	 * @return The whole number, or null.
	 */
	private static BigDecimal wholeNumber( Object value ) {
		BigDecimal number = number( value );
		if ( number == null ) {
			return null;
		}
		BigDecimal stripped = number.stripTrailingZeros();
		return stripped.scale() <= 0 ? stripped : null;
	}
}
