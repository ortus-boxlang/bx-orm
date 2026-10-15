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
public class EntityLoadOrSave extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoadOrSave() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", ORMKeys.idOrFilter ),
		    new Argument( false, "Struct", Key.properties )
		};
	}

	/**
	 * Load an entity by id or filter, or create and save a new one when none exists.
	 * <p>
	 * Works like <code>entityLoadOrNew()</code>, then saves the new entity with <code>entitySave()</code>. As with any
	 * save, the row is written when the session flushes (at the end of the <code>transaction{}</code>).
	 *
	 * <pre>
	 * transaction {
	 *     tag = entityLoadOrSave( "Tag", { slug : "boxlang" }, { name : "BoxLang" } );
	 * }
	 * </pre>
	 * <p>
	 * Two requests can both find nothing and both insert. Put a unique constraint on the filter's columns (for example
	 * <code>unique="true"</code> or <code>uniquekey</code>) so the database rejects the second insert.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The entity found, or the new one, saved.
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
		IClassRunnable	found		= LoadOr.find( context, entityName, idOrFilter, "entityLoadOrSave" );
		if ( found != null ) {
			return found;
		}
		IClassRunnable created = LoadOr.newEntity( context, entityName, idOrFilter, ( IStruct ) arguments.get( Key.properties ) );
		EntitySave.save( context, created, false );
		return created;
	}
}
