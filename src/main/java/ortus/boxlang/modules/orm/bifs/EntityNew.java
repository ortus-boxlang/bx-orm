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

import org.hibernate.engine.spi.SessionFactoryImplementor;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityNew extends BaseORMBIF {

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
	 * 
	 * @argument.entityName The name of the entity to create.
	 * 
	 * @argument.properties A struct of properties to populate on the new entity.
	 * 
	 * @argument.ignoreExtras If false, an error will be thrown if properties are provided that do not exist on the entity. Not implemented.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext					ormContext			= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp						ormApp				= ormContext.getORMApp();
		String						entityName			= arguments.getAsString( ORMKeys.entityName );
		EntityRecord				entityRecord		= ormApp.lookupEntity( entityName, true );
		IStruct						properties			= arguments.containsKey( Key.properties ) ? arguments.getAsStruct( Key.properties ) : Struct.EMPTY;

		SessionFactoryImplementor	sessionFactoryImpl	= ( SessionFactoryImplementor ) ormApp.getSessionFactoryOrThrow( entityRecord.getDatasource() );
		IClassRunnable				entity				= ( IClassRunnable ) sessionFactoryImpl.getMetamodel().entityPersister( entityRecord.getEntityName() )
		    .getEntityMetamodel().getTuplizer().instantiate();

		// @TODO: Find a more correct location for the entity population logic. Surely we repeat this somewhere else?
		if ( properties != null && !properties.isEmpty() ) {
			entity.getVariablesScope().putAll( properties );
		}

		interceptorService.announce( ORMKeys.EVENT_POST_NEW, Struct.of(
		    ORMKeys.entityName, entityRecord.getEntityName(),
		    ORMKeys.entity, entity,
		    "context", context
		) );

		return entity;
	}

}
