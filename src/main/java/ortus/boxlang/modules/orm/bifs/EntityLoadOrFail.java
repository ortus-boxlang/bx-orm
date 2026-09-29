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

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityLoadOrFail extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoadOrFail() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", ORMKeys.idOrFilter )
		};
	}

	/**
	 * Load one entity by id or filter, or throw <code>orm.notFound</code> when none exists.
	 *
	 * <pre>
	 * order = entityLoadOrFail( "Order", url.id );
	 * user  = entityLoadOrFail( "User", { email : form.email } );
	 * </pre>
	 * <p>
	 * A filter must match exactly one entity: none is <code>orm.notFound</code>, several is <code>orm.query.nonUnique</code>.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The entity.
	 *
	 * @argument.entityName The name of the entity.
	 *
	 * @argument.idOrFilter The primary key value, a composite key struct, or a struct of property values to match.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String			entityName	= arguments.getAsString( ORMKeys.entityName );
		Object			idOrFilter	= arguments.get( ORMKeys.idOrFilter );
		IClassRunnable	found		= LoadOr.find( context, entityName, idOrFilter, "entityLoadOrFail" );
		if ( found == null ) {
			throw LoadOr.notFound( LoadOr.app( context ).lookupEntity( entityName, true ).getEntityName(), idOrFilter, "entityLoadOrFail" );
		}
		return found;
	}
}
