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
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF
public class EntityLoad extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoad() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName ),
		    new Argument( true, "Any", ORMKeys.idOrFilter ),
		    new Argument( false, "String", ORMKeys.uniqueOrOrder ),
		    new Argument( false, "String", ORMKeys.options )
		};
	}

	/**
	 * Load an entity or array of entities from the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( arguments.get( ORMKeys.idOrFilter ) instanceof String ) {
			return loadEntityById( context, arguments );
		}
		return loadEntitiesByFilter( context, arguments );
	}

	private Object loadEntityById( IBoxContext context, ArgumentsScope arguments ) {
		if ( BooleanCaster.cast( arguments.getOrDefault( ORMKeys.uniqueOrOrder, "false" ) ) ) {
			return this.ormApp.loadEntityById( context.getRequestContext(), arguments.getAsString( ORMKeys.entityName ),
			    arguments.get( ORMKeys.idOrFilter ) );
		}
		var entity = this.ormApp.loadEntityById( context.getRequestContext(), arguments.getAsString( ORMKeys.entityName ),
		    arguments.get( ORMKeys.idOrFilter ) );
		return entity == null ? Array.EMPTY : Array.of( entity );
	}

	private Object loadEntitiesByFilter( IBoxContext context, ArgumentsScope arguments ) {
		throw new BoxRuntimeException( "Unimplemented" );
	}
}
