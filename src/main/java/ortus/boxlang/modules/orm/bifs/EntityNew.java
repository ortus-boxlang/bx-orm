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

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.hibernate.BoxClassInstantiator;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityNew extends BIF {

	/**
	 * Constructor
	 */
	public EntityNew() {

		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Struct", Key.properties ),
		    new Argument( false, "Boolean", ORMKeys.ignoreExtras, Set.of( Validator.NOT_IMPLEMENTED ) )
		};
	}

	/**
	 * Instantiate a new entity, optionally with a struct of properties.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMApp			app				= ORMRequestContext.getForContext( context.getRequestContext() ).getORMApp();
		EntityRecord	entityRecord	= app.lookupEntity( arguments.getAsString( ORMKeys.entityName ), true );
		IStruct			properties		= arguments.containsKey( Key.properties ) ? arguments.getAsStruct( Key.properties ) : Struct.EMPTY;

		IClassRunnable	entity			= BoxClassInstantiator.instantiate( context.getRequestContext(), entityRecord, properties );

		// @TODO: Announce 'postNew' event

		return entity;
	}

}
