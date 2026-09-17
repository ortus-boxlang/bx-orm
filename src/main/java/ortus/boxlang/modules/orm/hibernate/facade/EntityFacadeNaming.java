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
package ortus.boxlang.modules.orm.hibernate.facade;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

import ortus.boxlang.modules.orm.mapping.HibernateXMLWriter;

/**
 * Deterministic naming and id-type resolution for generated entity facades.
 * <p>
 * Both the facade code generator (at boot) and the mapping XML writer must agree, byte-for-byte, on the facade's
 * fully-qualified class name and on the concrete Java type of the id member, so this logic lives in one place.
 *
 * @since 2.0.0
 */
public final class EntityFacadeNaming {

	/**
	 * Package the generated facade classes are placed in.
	 */
	public static final String PACKAGE = "ortus.boxlang.modules.orm.hibernate.facade.generated";

	private EntityFacadeNaming() {
	}

	/**
	 * Compute the deterministic fully-qualified class name of the facade for a given BoxLang entity name.
	 * <p>
	 * The entity name is sanitized into a legal Java identifier (non-identifier characters become {@code _}) and suffixed
	 * with {@code Facade}, for example {@code Thing} -> {@code ortus.boxlang.modules.orm.hibernate.facade.generated.ThingFacade}.
	 *
	 * @param entityName The BoxLang entity name (the Hibernate entity-name).
	 *
	 * @return The facade class's fully-qualified name.
	 */
	public static String facadeClassName( String entityName ) {
		StringBuilder	sb		= new StringBuilder();
		boolean			first	= true;
		for ( int i = 0; i < entityName.length(); i++ ) {
			char	c		= entityName.charAt( i );
			boolean	legal	= first ? Character.isJavaIdentifierStart( c ) : Character.isJavaIdentifierPart( c );
			sb.append( legal ? c : '_' );
			first = false;
		}
		return PACKAGE + "." + sb + "Facade";
	}

	/**
	 * Resolve the concrete Java type a facade id accessor should use for a given BoxLang {@code ormType}.
	 * <p>
	 * The id is mapped by Hibernate with the raw (non-converted) basic type, so the facade's id getter/setter must use
	 * the matching wrapper type (e.g. a {@code uuid}/{@code string} id -> {@link String}, an {@code integer} id ->
	 * {@link Integer}). Anything unrecognized falls back to {@link String}.
	 *
	 * @param ormType The BoxLang property {@code ormType}, e.g. {@code string}, {@code integer}.
	 *
	 * @return The concrete Java type for the id accessor.
	 */
	public static Class<?> idJavaType( String ormType ) {
		if ( ormType == null || ormType.isBlank() ) {
			return String.class;
		}
		return switch ( HibernateXMLWriter.toHibernateType( ormType ) ) {
			case "string", "text" -> String.class;
			case "character" -> Character.class;
			case "boolean", "yes_no", "true_false" -> Boolean.class;
			case "short" -> Short.class;
			case "integer" -> Integer.class;
			case "long" -> Long.class;
			case "biginteger" -> BigInteger.class;
			case "float" -> Float.class;
			case "double" -> Double.class;
			case "bigdecimal" -> BigDecimal.class;
			case "timestamp" -> Date.class;
			default -> String.class;
		};
	}
}
