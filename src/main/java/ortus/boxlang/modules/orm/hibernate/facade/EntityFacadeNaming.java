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

import ortus.boxlang.modules.orm.mapping.MappingXMLWriter;

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
	 * The name is namespaced per ORM application (a sanitized application-unique segment) so that two applications running
	 * in the same JVM - each mapping a same-named entity (for example {@code User}) to a different shape - generate
	 * distinct facade classes instead of colliding on one global name. The entity name is sanitized into a legal Java
	 * identifier (non-identifier characters become {@code _}) and suffixed with {@code Facade}, for example
	 * {@code (shopApp, Thing)} -> {@code ortus.boxlang.modules.orm.hibernate.facade.generated.shopApp.ThingFacade}.
	 *
	 * @param namespace  The facade namespace for the owning ORM application (see {@link #sanitizeNamespace(String)}).
	 * @param entityName The BoxLang entity name (the Hibernate entity-name).
	 *
	 * @return The facade class's fully-qualified name.
	 */
	public static String facadeClassName( String namespace, String entityName ) {
		StringBuilder	sb		= new StringBuilder();
		boolean			first	= true;
		for ( int i = 0; i < entityName.length(); i++ ) {
			char	c		= entityName.charAt( i );
			boolean	legal	= first ? Character.isJavaIdentifierStart( c ) : Character.isJavaIdentifierPart( c );
			sb.append( legal ? c : '_' );
			first = false;
		}
		return PACKAGE + "." + sanitizeNamespace( namespace ) + "." + sb + "Facade";
	}

	/**
	 * Sanitize an application-unique string into a single legal, lower-cased Java package segment used to namespace that
	 * application's generated facades. Non-identifier characters become {@code _}; a leading non-identifier-start
	 * character is prefixed with {@code a}; blank input yields {@code default}.
	 *
	 * @param namespace The raw application-unique string (for example the ORM application name).
	 *
	 * @return A legal, lower-cased Java package segment.
	 */
	public static String sanitizeNamespace( String namespace ) {
		if ( namespace == null || namespace.isBlank() ) {
			return "default";
		}
		StringBuilder sb = new StringBuilder();
		for ( int i = 0; i < namespace.length(); i++ ) {
			char c = namespace.charAt( i );
			sb.append( Character.isJavaIdentifierPart( c ) ? Character.toLowerCase( c ) : '_' );
		}
		if ( !Character.isJavaIdentifierStart( sb.charAt( 0 ) ) ) {
			sb.insert( 0, 'a' );
		}
		return sb.toString();
	}

	/**
	 * Recover the namespace segment from a facade's fully-qualified class name, i.e. the inverse of the namespace portion
	 * of {@link #facadeClassName(String, String)}.
	 *
	 * @param facadeClassName A facade FQN produced by {@link #facadeClassName(String, String)}.
	 *
	 * @return The namespace segment, or {@code null} if the name is not in the expected form.
	 */
	public static String namespaceOf( String facadeClassName ) {
		if ( facadeClassName == null || !facadeClassName.startsWith( PACKAGE + "." ) ) {
			return null;
		}
		String	remainder	= facadeClassName.substring( PACKAGE.length() + 1 );
		int		dot			= remainder.indexOf( '.' );
		return dot < 0 ? null : remainder.substring( 0, dot );
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
		return switch ( MappingXMLWriter.toHibernateType( ormType ) ) {
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
