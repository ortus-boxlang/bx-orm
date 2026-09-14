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
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityIsAttached extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityIsAttached() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "class", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Check whether an entity is attached to the current ORM session.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.entity The entity instance to check.
	 *
	 * @return True when the entity is attached to its datasource's current session.
	 */
	public Boolean _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IClassRunnable	theEntity	= ( IClassRunnable ) arguments.get( ORMKeys.entity );
		String			entityName	= getEntityName( theEntity );
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.getORMApp();

		if ( ormApp == null ) {
			return false;
		}

		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ormContext.getSession( entityRecord.getDatasource() );
		return session.contains( theEntity );
	}
}