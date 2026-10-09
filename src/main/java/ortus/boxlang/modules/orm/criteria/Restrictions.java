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
package ortus.boxlang.modules.orm.criteria;

import java.util.Map;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.IReferenceable;
import ortus.boxlang.runtime.scopes.Key;

/**
 * The {@code c.restrictions} helper of {@code entityCriteria()}, for cborm-style code:
 *
 * <pre>
 * c.or( c.restrictions.isEq( "role", "admin" ), c.restrictions.isGt( "age", 30 ) );
 * c.add( c.restrictions.like( "name", "A%" ) );
 * </pre>
 *
 * Every condition method of the builder is available under the same names and aliases (including the {@code not...}
 * forms), plus {@code or} / {@code and} / {@code not} and their aliases to nest restrictions. Each call returns a
 * {@link Restriction} instead of adding the condition; the builder methods that take closures accept it too.
 *
 * @since 2.0.0
 */
public final class Restrictions implements IReferenceable {

	/** Shared instance: restrictions hold no state, the builder they are added to gives them their meaning. */
	static final Restrictions INSTANCE = new Restrictions();

	/**
	 * Not instantiable from outside: use {@code c.restrictions}.
	 */
	private Restrictions() {
	}

	/**
	 * Restrictions have no properties.
	 *
	 * @param context The context.
	 * @param name    The property name.
	 * @param safe    Whether a missing property returns null instead of failing.
	 *
	 * @return Null when safe.
	 */
	@Override
	public Object dereference( IBoxContext context, Key name, Boolean safe ) {
		if ( Boolean.TRUE.equals( safe ) ) {
			return null;
		}
		throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria restrictions have no property [" + name.getName() + "].",
		    "Call a condition method, e.g. c.restrictions.isEq( \"name\", value )." );
	}

	/**
	 * Build a restriction with positional arguments.
	 *
	 * @param context   The calling context.
	 * @param name      The condition method.
	 * @param arguments The arguments.
	 * @param safe      Unused.
	 *
	 * @return The restriction.
	 */
	@Override
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Object[] arguments, Boolean safe ) {
		return restriction( name.getName(), arguments, null );
	}

	/**
	 * Build a restriction with named arguments.
	 *
	 * @param context   The calling context.
	 * @param name      The condition method.
	 * @param arguments The arguments by name.
	 * @param safe      Unused.
	 *
	 * @return The restriction.
	 */
	@Override
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Map<Key, Object> arguments, Boolean safe ) {
		return restriction( name.getName(), null, arguments );
	}

	/**
	 * Restrictions cannot be assigned to.
	 *
	 * @param context The context.
	 * @param name    The property name.
	 * @param value   The value.
	 *
	 * @return Never returns.
	 */
	@Override
	public Object assign( IBoxContext context, Key name, Object value ) {
		throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria restrictions cannot be set ([" + name.getName() + "]).",
		    "Call a condition method, e.g. c.restrictions.isEq( \"name\", value )." );
	}

	/**
	 * Record a condition call, after checking it names a condition.
	 *
	 * @param method     The condition method.
	 * @param positional The positional arguments, or null.
	 * @param named      The named arguments, or null.
	 *
	 * @return The restriction.
	 *
	 * @throws ORMException {@code orm.argument} when the method is not a condition.
	 */
	private static Restriction restriction( String method, Object[] positional, Map<Key, Object> named ) {
		if ( !CriteriaMethods.isRestriction( method ) ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    "entityCriteria restrictions have no condition [" + method + "()]." + CriteriaMethods.suggestCondition( method ),
			    "Restrictions build conditions (isEq, like, between, isIn, isNull, or, and, not, ...); call other methods on the builder itself." );
		}
		return new Restriction( method, positional, named );
	}

	/**
	 * A readable form for dumps.
	 *
	 * @return The description.
	 */
	@Override
	public String toString() {
		return "entityCriteria restrictions";
	}
}
