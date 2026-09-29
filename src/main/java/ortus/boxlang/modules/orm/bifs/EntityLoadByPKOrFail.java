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
public class EntityLoadByPKOrFail extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoadByPKOrFail() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", Key.id, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Struct", ORMKeys.options )
		};
	}

	/**
	 * Load an entity by its primary key, or throw <code>orm.notFound</code> when no row has that id.
	 *
	 * <pre>
	 * order = entityLoadByPKOrFail( "Order", url.id );
	 * order = entityLoadByPKOrFail( "Order", url.id, { lock : "write" } );
	 * </pre>
	 * <p>
	 * Takes the same options as <code>entityLoadByPK()</code>: lock, timeout, skipLocked, readOnly.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The entity.
	 *
	 * @argument.entity The name of the entity to load.
	 *
	 * @argument.id The primary key value, or a struct of key/value pairs for composite keys.
	 *
	 * @argument.options A struct of load options: lock, timeout, skipLocked, readOnly.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String			entityName	= arguments.getAsString( ORMKeys.entity );
		Object			id			= arguments.get( Key.id );
		IClassRunnable	found		= LoadOr.app( context ).loadEntityById( LoadOr.jdbc( context ), entityName, id,
		    ( IStruct ) arguments.get( ORMKeys.options ) );
		if ( found == null ) {
			throw LoadOr.notFound( LoadOr.app( context ).lookupEntity( entityName, true ).getEntityName(), id, "entityLoadByPKOrFail" );
		}
		return found;
	}
}
