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
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityGetReference extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityGetReference() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", Key.id, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Get a reference to an entity by id without loading it from the database.
	 * <p>
	 * Use it to set an association when all you have is the id: no <code>SELECT</code> runs.
	 *
	 * <pre>
	 * order.setCustomer( entityGetReference( "Customer", form.customerId ) );
	 * </pre>
	 * <p>
	 * The reference is a lazy proxy. Reading any of its properties or calling its methods loads the row then, and fails if
	 * no row has that id. When the session already holds the entity, that entity is returned.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The reference: an unloaded proxy, or the entity when the session already holds it.
	 *
	 * @argument.entityName The name of the entity.
	 *
	 * @argument.id The primary key value, or a struct of key/value pairs for composite keys.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return LoadOr.app( context ).getEntityReference( LoadOr.jdbc( context ), arguments.getAsString( ORMKeys.entityName ),
		    arguments.get( Key.id ) );
	}
}
