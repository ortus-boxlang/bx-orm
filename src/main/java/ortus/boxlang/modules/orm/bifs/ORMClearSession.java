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
package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class ORMClearSession extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMClearSession() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Clear the Hibernate session for the current context and provided (or default) datasource
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.datasource The datasource on which to clear the current session. If not provided, the default datasource will be used.
	 *
	 * @return True if the session was cleared, false otherwise.
	 */
	public Boolean _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Key					datasourceName	= Key.of( StringCaster.attempt( arguments.get( ORMKeys.datasource ) ).getOrDefault( "" ) );
		ORMRequestContext	ormContext		= ORMRequestContext.getForContext( context.getRequestContext() );

		// If not ORM app found then ignore
		if ( ormContext.hasORMApp() ) {
			ormContext.getSession( datasourceName.isEmpty() ? null : datasourceName ).clear();
			return true;
		}

		return false;
	}

}
