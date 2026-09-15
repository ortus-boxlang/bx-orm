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
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

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
	 * @arguments.entity The entity instance to save.
	 * 
	 * @arguments.forceinsert If true, will force an insert operation. Otherwise, a saveOrUpdate operation will be performed.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IClassRunnable	entity		= ( IClassRunnable ) arguments.get( ORMKeys.entity );
		String			entityName	= getEntityName( entity );
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.getORMApp();
		if ( ormApp == null ) {
			throw new BoxRuntimeException( "ORM application is not initialized." );
		}

		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ormContext.getSession( entityRecord.getDatasource() );
		Boolean			forceInsert		= BooleanCaster.cast( arguments.getOrDefault( ORMKeys.forceinsert, false ) );

		// bx-orm is the ORM abstraction, so entitySave() must behave as it did on Hibernate 5's saveOrUpdate(): the object the
		// caller passed in stays live afterward and carries any generated identifier and event changes. Hibernate 7 removed
		// saveOrUpdate(), leaving persist() for new entities and merge() for detached ones. persist() attaches the passed
		// instance directly (so it is already live), but merge() copies state into a separate managed instance and leaves the
		// caller's object detached. To keep the old contract we copy the managed state back into the caller's instance.
		if ( session.contains( entityName, entity ) ) {
			// Already managed: nothing to do; the flush will persist any changes.
		} else if ( forceInsert || isTransient( session, entityName, entity ) ) {
			session.persist( entityName, entity );
		} else {
			// Detached instance: merge returns the managed copy; copy its state back so the caller's object stays live.
			Object managed = session.merge( entityName, entity );
			if ( managed != entity && managed instanceof IClassRunnable managedEntity ) {
				// BoxPropertySetter writes mapped properties to both the `this` and variables scopes, so sync both
				// back onto the caller's detached instance. Otherwise generated identifiers or event-updated values
				// held in the managed `this` scope stay stale on the returned object, breaking the compatibility
				// contract described above.
				entity.getThisScope().putAll( managedEntity.getThisScope() );
				entity.getVariablesScope().putAll( managedEntity.getVariablesScope() );
			}
		}

		return null;
	}

	/**
	 * Determine whether an entity has never been persisted, using the same unsaved-value/version/snapshot rules Hibernate's
	 * former <code>saveOrUpdate()</code> used to decide between an insert and a re-attach.
	 */
	private boolean isTransient( Session session, String entityName, IClassRunnable entity ) {
		return org.hibernate.engine.internal.ForeignKeys.isTransient(
		    entityName,
		    entity,
		    null,
		    ( org.hibernate.engine.spi.SharedSessionContractImplementor ) session
		);
	}

}
