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
public class EntityLoadOrNew extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoadOrNew() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", ORMKeys.idOrFilter ),
		    new Argument( false, "Struct", Key.properties )
		};
	}

	/**
	 * Load an entity by id or filter, or return a new, unsaved one when none exists.
	 * <p>
	 * The new entity is filled with the filter's values (or the id, when the entity's id is assigned rather than
	 * generated), then with <code>properties</code>, which win. It is not saved: call <code>entitySave()</code> when you
	 * want it stored, or use <code>entityLoadOrSave()</code>.
	 *
	 * <pre>
	 * user = entityLoadOrNew( "User", { email : "ann@example.com" }, { status : "invited" } );
	 * </pre>
	 * <p>
	 * A filter must match at most one entity (<code>orm.query.nonUnique</code> otherwise). <code>properties</code> is only
	 * applied to a new entity, never to one that was found.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The entity found, or a new, unsaved one.
	 *
	 * @argument.entityName The name of the entity.
	 *
	 * @argument.idOrFilter The primary key value, a composite key struct, or a struct of property values to match.
	 *
	 * @argument.properties Property values for the new entity when none is found.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String			entityName	= arguments.getAsString( ORMKeys.entityName );
		Object			idOrFilter	= arguments.get( ORMKeys.idOrFilter );
		IClassRunnable	found		= LoadOr.find( context, entityName, idOrFilter, "entityLoadOrNew" );
		if ( found != null ) {
			return found;
		}
		return LoadOr.newEntity( context, entityName, idOrFilter, ( IStruct ) arguments.get( Key.properties ) );
	}
}
