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

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;

/**
 * A condition built with {@code c.restrictions} but not added yet, e.g. {@code c.restrictions.isEq( "role", "admin" )}.
 * <p>
 * It records the condition method and its arguments, and replays that call on the builder it is added to, through
 * {@code c.add()}, {@code c.or()}, {@code c.and()}, {@code c.not()} or anywhere a closure is accepted. Property paths
 * are therefore resolved against that builder, the same as a direct call would be.
 *
 * @since 2.0.0
 */
public final class Restriction {

	/** The condition method, as called (e.g. {@code isEq}, {@code notLike}, {@code or}). */
	private final String			method;
	/** The positional arguments, or null when the call used named arguments. */
	private final Object[]			positional;
	/** The named arguments, or null when the call used positional arguments. */
	private final Map<Key, Object>	named;

	/**
	 * Record a condition call.
	 *
	 * @param method     The condition method, as called.
	 * @param positional The positional arguments, or null.
	 * @param named      The named arguments, or null.
	 */
	Restriction( String method, Object[] positional, Map<Key, Object> named ) {
		this.method		= method;
		this.positional	= positional;
		this.named		= named;
	}

	/**
	 * Add this condition to a builder, exactly as if the method had been called on it.
	 *
	 * @param builder The builder receiving the condition.
	 * @param context The calling context.
	 */
	void applyTo( CriteriaBuilder builder, IBoxContext context ) {
		CriteriaMethods.invoke( builder, context, method, positional, named );
	}

	/**
	 * The condition method this restriction calls.
	 *
	 * @return The method name, as called.
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * A readable form for dumps and messages, e.g. {@code restrictions.isEq(role, admin)}.
	 *
	 * @return The description.
	 */
	@Override
	public String toString() {
		Object[] args = positional != null ? positional
		    : ( named == null ? new Object[ 0 ]
		        : named.entrySet().stream()
		            .map( e -> e.getKey().getName() + "=" + e.getValue() ).toArray() );
		return "restrictions." + method + "(" + CriteriaMethods.formatArgs( args ) + ")";
	}
}
