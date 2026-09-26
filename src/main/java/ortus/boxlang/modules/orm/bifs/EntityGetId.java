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

import ortus.boxlang.modules.orm.EntityInspector;
import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

/**
 * {@code entityGetId()}: The primary key of an entity. A lazy reference answers without being loaded. When given an entity name, there is no
 */
@BoxBIF
public class EntityGetId extends BaseORMBIF {

	/**
	 * Declare the BIF's arguments.
	 */
	public EntityGetId() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", ORMKeys.entity, Set.of( Validator.REQUIRED ) )
		};
	}

	/**
	 * The primary key of an entity. A lazy reference answers without being loaded. When given an entity name, there is no
	 * instance to read, so an orm.argument error is raised.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.entity An entity instance (loaded, new or a lazy reference).
	 *
	 * @return The id value; a struct of { property : value } for a composite id; null when the entity has no id yet.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.requireORMApp();
		Object			entity		= arguments.get( ORMKeys.entity );
		EntityRecord	record		= EntityInspector.resolve( ormApp, entity, "entityGetId" );
		if ( entity instanceof String ) {
			throw new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.ARGUMENT,
			    "entityGetId() needs an entity instance to read the id from, but received the entity name [" + entity + "].",
			    "Pass the entity itself." );
		}
		return EntityInspector.getId( record, entity );
	}
}
