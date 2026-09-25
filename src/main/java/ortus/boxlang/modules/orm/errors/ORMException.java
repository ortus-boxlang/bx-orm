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
package ortus.boxlang.modules.orm.errors;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The single exception family raised by bx-orm.
 * <p>
 * Every error carries a dotted {@code type} under {@code orm} (see {@link ORMErrorType}), so BoxLang code can catch all
 * ORM errors with {@code catch( "orm" e )} or one family with {@code catch( "orm.query" e )} (BoxLang matches a dotted
 * prefix). The {@code message} says what went wrong in BoxLang terms (entity and property names, never generated facade
 * class names), the {@code detail} says how to fix it, and {@code extendedInfo} is a struct with the context: entity,
 * property, HQL, parameters, SQL, and the original Hibernate message.
 */
public class ORMException extends BoxRuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Create an ORM error.
	 *
	 * @param type         The error type, e.g. {@link ORMErrorType#QUERY_SYNTAX}.
	 * @param message      What went wrong, in BoxLang terms.
	 * @param detail       How to fix it (may be empty or null).
	 * @param extendedInfo Context struct (may be null).
	 * @param cause        The original exception (may be null).
	 */
	public ORMException( ORMErrorType type, String message, String detail, IStruct extendedInfo, Throwable cause ) {
		super( message, detail == null ? "" : detail, type.type(), extendedInfo == null ? new Struct() : extendedInfo, cause );
	}

	/**
	 * Create an ORM error with no cause and no extra context.
	 *
	 * @param type    The error type.
	 * @param message What went wrong, in BoxLang terms.
	 * @param detail  How to fix it (may be empty or null).
	 */
	public ORMException( ORMErrorType type, String message, String detail ) {
		this( type, message, detail, null, null );
	}

	/**
	 * The context struct ({@code extendedInfo}) of this error.
	 *
	 * @return The struct with entity, property, HQL, params, SQL and similar context.
	 */
	public IStruct info() {
		return ( IStruct ) getExtendedInfo();
	}

	/**
	 * Read one context value.
	 *
	 * @param key The context key, e.g. {@code entityName}.
	 *
	 * @return The value, or null when absent.
	 */
	public Object info( String key ) {
		return info().get( Key.of( key ) );
	}
}
