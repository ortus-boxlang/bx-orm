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

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF
public class EntityLoadReadOnly extends EntityLoad {

	/**
	 * Constructor
	 */
	public EntityLoadReadOnly() {
		super();
	}

	/**
	 * Load entities like <code>entityLoad()</code>, read-only: they are not dirty-checked and changes to them are never
	 * saved. Takes the same arguments and options as <code>entityLoad()</code>.
	 *
	 * <pre>
	 * orders = entityLoadReadOnly( "Order", { status : "shipped" }, "createdDate desc" );
	 * order  = entityLoadReadOnly( "Order", 42, true );
	 * </pre>
	 * <p>
	 * An entity that is already in the session keeps its state.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return An array of read-only entities, or one entity (or null) for a unique load.
	 *
	 * @argument.entityName The name of the entity to load.
	 *
	 * @argument.idOrFilter Either the ID of the entity to load, or a struct of filter criteria.
	 *
	 * @argument.uniqueOrOrder Either a boolean indicating whether to return a unique result, or a string/array of order by clauses.
	 *
	 * @argument.options A struct of options, as for entityLoad(); readOnly is always on.
	 */
	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IStruct options = new Struct();
		if ( arguments.get( ORMKeys.options ) instanceof IStruct given ) {
			options.putAll( given );
		} else if ( arguments.get( ORMKeys.options ) == null && arguments.get( ORMKeys.uniqueOrOrder ) instanceof IStruct given ) {
			// entityLoad( name, filter, options ): the options came in the uniqueOrOrder position.
			options.putAll( given );
			arguments.remove( ORMKeys.uniqueOrOrder );
		}
		options.put( ORMKeys.readOnly, true );
		arguments.put( ORMKeys.options, options );
		return super._invoke( context, arguments );
	}
}
