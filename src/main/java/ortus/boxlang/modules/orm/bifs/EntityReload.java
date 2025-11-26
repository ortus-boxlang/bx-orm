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
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IBoxContext.ScopeSearchResult;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityReload extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityReload() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Reload an entity from the database. Will repopulate all persistent properties on the entity with the latest values from the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.entity The entity instance to reload.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IBoxContext	jdbcBoxContext	= context.getParentOfType( IJDBCCapableContext.class );
		Object		entity			= arguments.get( ORMKeys.entity );
		if ( entity instanceof String variableName ) {
			ScopeSearchResult entityLookup = context.scopeFindNearby( Key.of( ( String ) variableName ), null, true );
			if ( entityLookup == null ) {
				throw new IllegalArgumentException( "Entity variable not found: " + variableName );
			}
			entity = entityLookup.value();
		}
		ORMContext
		    .getForContext( jdbcBoxContext )
		    .getSession()
		    .refresh( entity );
		return entity;
	}

}
