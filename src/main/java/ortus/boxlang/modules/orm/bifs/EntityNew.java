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

import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.config.ORMEventDispatcher;

import java.util.Set;

import org.hibernate.engine.spi.SessionFactoryImplementor;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
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
		IStruct properties = arguments.containsKey( Key.properties ) ? arguments.getAsStruct( Key.properties ) : Struct.EMPTY;
		return create( context, arguments.getAsString( ORMKeys.entityName ), properties );
	}

	/**
	 * Instantiate a new, unsaved entity, fill it with properties and fire {@code postNew}. Shared by {@code entityNew()} and
	 * the {@code entityLoadOrNew()} / {@code entityLoadOrSave()} BIFs.
	 *
	 * @param context    The context in which the BIF is being invoked.
	 * @param entityName The name of the entity to create.
	 * @param properties Property values to set on the new entity; may be empty.
	 *
	 * @return The new entity.
	 */
	public static IClassRunnable create( IBoxContext context, String entityName, IStruct properties ) {
		ORMContext					ormContext			= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp						ormApp				= ormContext.requireORMApp();

		EntityRecord				entityRecord		= ormApp.lookupEntity( entityName, true );

		SessionFactoryImplementor	sessionFactoryImpl	= ( SessionFactoryImplementor ) ormApp.getSessionFactoryOrThrow(
		    entityRecord.getDatasource(),
		    context
		);
		// The instantiator returns a POJO facade; unwrap it to the BoxLang instance the developer expects.
		IClassRunnable				entity				= ( IClassRunnable ) FacadeSupport.unwrapIfFacade(
		    sessionFactoryImpl.getMappingMetamodel()
		        .getEntityDescriptor( ORMApp.hibernateEntityName( sessionFactoryImpl, entityRecord.getEntityName() ) )
		        .getRepresentationStrategy()
		        .getInstantiator()
		        .instantiate()
		);

		if ( properties != null && !properties.isEmpty() ) {
			entity.getVariablesScope().putAll( properties );
		}

		// Fire the postNew event on the entity itself and on the global event-handler class. Hibernate has no
		// "instantiate/new" event, so entityNew() is the single place this fires - for developer-initiated creation only,
		// not for hydration during a load (that is what postLoad is for). Dispatched through the same ORMEventDispatcher
		// the Hibernate events use, so the global handler resolves identically. eventHandling=false turns it off with the
		// Hibernate events.
		if ( ormApp.getConfig().eventHandling ) {
			IStruct eventArgs = Struct.of(
			    ORMKeys.entity, entity,
			    ORMKeys.entityName, entityRecord.getEntityName(),
			    Key.context, context
			);
			ORMEventDispatcher.announceEntity( entity, ORMKeys.postNew, eventArgs );
			ormApp.getConfig().getEventDispatcher().announceGlobal( ORMKeys.postNew, eventArgs );
		}

		// Also announce the post_new interception point so any registered BoxLang interceptors can observe it.
		InterceptorService interceptors = BoxRuntime.getInstance().getInterceptorService();
		if ( interceptors.hasState( ORMKeys.EVENT_POST_NEW ) ) {
			interceptors.announce(
			    ORMKeys.EVENT_POST_NEW,
			    () -> Struct.of(
			        ORMKeys.entityName, entityRecord.getEntityName(),
			        ORMKeys.entity, entity,
			        Key.context, context
			    )
			);
		}

		return entity;
	}

}
