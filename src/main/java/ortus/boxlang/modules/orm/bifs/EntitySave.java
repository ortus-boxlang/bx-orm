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

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntitySave extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntitySave() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Boolean", ORMKeys.forceinsert )
		};
	}

	/**
	 * Save the provided entity to the persistence context
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @return
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IClassRunnable	entity			= ( IClassRunnable ) arguments.get( ORMKeys.entity );
		String			entityName		= getEntityName( entity );
		ORMApp			ormApp			= ORMRequestContext.getForContext( context.getRequestContext() ).getORMApp();
		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ORMRequestContext.getForContext( context.getRequestContext() ).getSession( entityRecord.getDatasource() );
		Boolean			forceInsert		= BooleanCaster.cast( arguments.getOrDefault( ORMKeys.forceinsert, false ) );

		if ( forceInsert ) {
			session.save( entityName, entity );
		} else {
			session.saveOrUpdate( entityName, entity );
		}

		return null;
	}

}
