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
import ortus.boxlang.modules.orm.criteria.StructLoads;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityLoadAsStruct extends BaseORMBIF {

	/** The {@code includes} argument. */
	private static final Key INCLUDES = Key.of( "includes" );

	/**
	 * Constructor
	 */
	public EntityLoadAsStruct() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "Any", ORMKeys.idOrFilter ),
		    new Argument( false, "Any", INCLUDES ),
		    new Argument( false, "Struct", ORMKeys.options )
		};
	}

	/**
	 * Load entities straight into structs, without loading the entities: one projection query for the plain values and
	 * to-one associations, plus one query per to-many association. The structs match what <code>entityToStruct()</code>
	 * builds (the entity's <code>this.memento</code>, includes, excludes, mappers, defaults, profiles, ISO 8601 dates).
	 *
	 * <pre>
	 * user  = entityLoadAsStruct( "User", 42, "id,name,role.name" );              // struct or null
	 * users = entityLoadAsStruct( "User", { active : true }, "id,name,orders" );   // array of structs
	 * users = entityLoadAsStruct( "User", { active : true }, "", { sortOrder : "name", maxResults : 20, profile : "list" } );
	 * </pre>
	 * <p>
	 * Getter includes need a loaded entity, so they are an error here; use <code>entityToStruct()</code> or a mapper. For
	 * the same reason a property whose getter you overrode comes back as its column value, not the getter's result.
	 * Collections come back in id order. Entities with a composite id are not supported (<code>orm.argument</code>); use
	 * <code>entityToStruct()</code> on the loaded entities.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return A struct or null for an id (or a filter with <code>unique</code>), else an array of structs.
	 *
	 * @argument.entityName The name of the entity.
	 *
	 * @argument.idOrFilter The primary key value, or a struct of property values to match.
	 *
	 * @argument.includes Properties and dotted association paths to add to the entity's defaults, as a list or array.
	 *
	 * @argument.options sortOrder, maxResults, offset, unique, and excludes, mappers, defaults, ignoreDefaults, profile.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IBoxContext jdbc = context.getParentOfType( IJDBCCapableContext.class );
		return StructLoads.load( ORMContext.getForContext( jdbc ).requireORMApp(), jdbc, arguments.getAsString( ORMKeys.entityName ),
		    arguments.get( ORMKeys.idOrFilter ), arguments.get( INCLUDES ), ( IStruct ) arguments.get( ORMKeys.options ) );
	}
}
