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

import org.hibernate.Session;

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
public class ORMCloseSession extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMCloseSession() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Close the Hibernate session for the current context and provided (or default) datasource
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.datasource The datasource on which to close the current session. If not provided, the default datasource will be used.
	 * 
	 * @return null.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session	session;
		String	datasourceName	= StringCaster.attempt( arguments.get( ORMKeys.datasource ) ).getOrDefault( "" );
		if ( !datasourceName.isBlank() ) {
			session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession( Key.of( datasourceName ) );
		} else {
			session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		}
		session.close();

		return null;
	}

}
