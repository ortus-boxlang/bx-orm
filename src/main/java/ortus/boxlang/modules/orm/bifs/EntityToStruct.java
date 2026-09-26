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

import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.memento.EntityMemento;
import ortus.boxlang.modules.orm.memento.MementoSpec;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityToStruct extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityToStruct() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED ) ),
		    new Argument( false, "Struct", ORMKeys.options )
		};
	}

	/**
	 * Turn an entity, or an array of entities, into a struct (or an array of structs), for JSON APIs and views.
	 * <p>
	 * By default the struct holds the id and the plain properties. Options:
	 * <ul>
	 * <li><code>includes</code>: properties, getters (without <code>get</code>) or dotted association paths to add, as a list
	 * or array: <code>"id,name,role.name,orders"</code>. An association with no path below it uses the associated
	 * entity's own defaults. <code>"lastLoginTime:lastLogin"</code> renames a key. <code>"*"</code> is every plain property.</li>
	 * <li><code>excludes</code>: names or dotted paths to leave out.</li>
	 * <li><code>mappers</code>: a struct of key to <code>( value, memento ) =&gt; newValue</code>; a mapper may add a new key.</li>
	 * <li><code>defaults</code>: a struct of key to the value to use when the value is null (otherwise an empty string, or an
	 * empty array for a collection).</li>
	 * <li><code>ignoreDefaults</code>: ignore the entity's <code>this.memento</code> default includes and excludes.</li>
	 * <li><code>profile</code>: use a <code>this.memento.profiles</code> entry, down the whole graph.</li>
	 * </ul>
	 * <p>
	 * Entities may declare mementifier's <code>this.memento = { defaultIncludes, defaultExcludes, neverInclude, defaults,
	 * mappers, profiles }</code>; the options add to it. Dates are ISO 8601 strings. An entity that already appears higher
	 * up the same branch is written as its id, so cycles end.
	 *
	 * <pre>
	 * entityToStruct( user );
	 * entityToStruct( user, { includes : "role.name,orders.total", excludes : "passwordHash" } );
	 * entityToStruct( entityLoad( "User" ), { profile : "export" } );
	 * </pre>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return A struct, or an array of structs for an array of entities.
	 *
	 * @argument.entity An entity, or an array of entities.
	 *
	 * @argument.options includes, excludes, mappers, defaults, ignoreDefaults, profile.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		return new EntityMemento( ormContext.requireORMApp(), context, "entityToStruct" )
		    .write( arguments.get( ORMKeys.entity ), MementoSpec.fromOptions( ( IStruct ) arguments.get( ORMKeys.options ), "entityToStruct" ) );
	}
}
