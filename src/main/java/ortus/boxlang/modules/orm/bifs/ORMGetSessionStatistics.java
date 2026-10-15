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

import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;
import ortus.boxlang.runtime.types.IStruct;

/**
 * {@code ormGetSessionStatistics()}: Statistics of the ORM session for a datasource: how many entities and collections it holds, and which.
 */
@BoxBIF
public class ORMGetSessionStatistics extends BaseORMBIF {

	/**
	 * Declare the BIF's arguments.
	 */
	public ORMGetSessionStatistics() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Statistics of the ORM session for a datasource: how many entities and collections it holds, and which.
	 *
	 * Returns { entityCount, collectionCount, entityKeys, collectionKeys }; keys are strings such as "User#5" and
	 * "User.orders#5".
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.datasource The datasource whose session to inspect. Defaults to the application's default datasource.
	 *
	 * @return The statistics struct.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext	ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		String		datasource	= arguments.getAsString( ORMKeys.datasource );
		Session		session		= datasource == null || datasource.isBlank() ? ormContext.getSession() : ormContext.getSession( Key.of( datasource ) );
		return ortus.boxlang.modules.orm.EntityInspector.sessionStatistics( session );
	}
}
