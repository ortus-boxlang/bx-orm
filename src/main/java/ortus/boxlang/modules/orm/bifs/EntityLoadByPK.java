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
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityLoadByPK extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoadByPK() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", Key.id, Set.of( Validator.REQUIRED ) ),
		    new Argument( false, "Any", ORMKeys.options )
		};
	}

	/**
	 * Load an entity by its primary key.
	 * <p>
	 * <code>
	 * var myAuto = entityLoadByPK( "Automobile", "1HGCM82633A123456" );
	 * </code>
	 * <p>
	 * In Lucee, by default, an array of entities is returned and you must pass a third `unique=true` argument to return only a single entity. In BoxLang,
	 * only a single entity is returned - matching the Adobe ColdFusion behavior. A boolean third argument (Lucee's
	 * `unique`) is accepted and ignored. To return an array of entities, use the `entityLoad` BIF.
	 * <p>
	 * Pass an array of ids to load several entities in one query. The result is an array in the order of the ids, with
	 * null where no row has that id:
	 *
	 * <pre>
	 * users = entityLoadByPK( "User", [ 3, 1, 99 ] ); // [ user3, user1, null ]
	 * </pre>
	 * <p>
	 * Composite keys are also supported:
	 *
	 * <pre>
	 * entityLoadByPK( "VehicleType", { make : "Ford", model: "Fusion" } );
	 * </pre>
	 * <p>
	 * Options:
	 * <ul>
	 * <li><code>lock</code>: lock the row while loading it: <code>read</code>, <code>write</code> or <code>force</code> (see
	 * <code>entityLock()</code>). Needs <code>transaction{}</code>; the lock is released when it ends.</li>
	 * <li><code>timeout</code>: seconds to wait for the lock; 0 means do not wait.</li>
	 * <li><code>skipLocked</code>: return null instead of waiting when the row is locked by someone else.</li>
	 * <li><code>readOnly</code>: load the entity read-only, so changes to it are never saved.</li>
	 * </ul>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.entity The name of the entity to load.
	 *
	 * @argument.id The primary key value, a struct of key/value pairs for composite keys, or an array of either.
	 *
	 * @argument.options A struct of load options: lock, timeout, skipLocked, readOnly.
	 *
	 * @return The entity or null; for an array of ids, an array in the same order with null for missing ids.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String		entityName		= arguments.getAsString( ORMKeys.entity );
		Object		keyValue		= arguments.get( Key.id );
		IStruct		options			= arguments.get( ORMKeys.options ) instanceof IStruct struct ? struct : null;

		IBoxContext	jdbcBoxContext	= context.getParentOfType( IJDBCCapableContext.class );
		if ( keyValue instanceof Array ids ) {
			return ormService.requireORMApp( context ).loadEntitiesByIds( jdbcBoxContext, entityName, ids, options );
		}
		return ormService.requireORMApp( context ).loadEntityById( jdbcBoxContext, entityName, keyValue, options );
	}
}
