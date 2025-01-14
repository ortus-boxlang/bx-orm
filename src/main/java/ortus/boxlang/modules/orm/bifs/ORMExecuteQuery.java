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

import java.util.List;
import java.util.Set;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class ORMExecuteQuery extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMExecuteQuery() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.hql, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Any", Key.params, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Boolean", ORMKeys.unique, Set.of() ),
		    new Argument( false, "Struct", Key.options, Set.of() )
		};
	}

	/**
	 * ORMExecuteQuery
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMApp							app				= ormService.getORMAppByContext( RequestBoxContext.getCurrent() );
		ORMRequestContext				requestContext	= ORMRequestContext.getForContext( context.getRequestContext() );

		// @TODO: Use arguments.options.datasource
		Session							session			= requestContext.getSession();
		org.hibernate.query.Query<?>	hqlQuery		= session.createQuery( arguments.getAsString( ORMKeys.hql ) );

		List<?>							result			= hqlQuery.list();

		return Array.fromList( result );
	}

}
