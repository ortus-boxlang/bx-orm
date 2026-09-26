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

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.internal.ForeignKeys;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;

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
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED ) ),
		    new Argument( false, "Any", ORMKeys.forceinsert ),
		    new Argument( false, "Struct", ORMKeys.options )
		};
	}

	/**
	 * Save one entity, or an array of entities: a new entity is inserted, a detached one merged, and a managed one needs
	 * nothing (its changes are written when the session flushes, at the end of the <code>transaction{}</code>). Pass
	 * <code>{ flush : true }</code> to flush right away.
	 *
	 * <pre>
	 * entitySave( user );
	 * entitySave( [ order, invoice ], { flush : true } );
	 * entitySave( user, true, { flush : true } ); // force an insert
	 * </pre>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return null.
	 *
	 * @argument.entity The entity to save, or an array of entities.
	 *
	 * @argument.forceinsert If true, always insert. May also be the options struct (<code>entitySave( e, { flush : true } )</code>).
	 *
	 * @argument.options Options: <code>flush</code> (boolean) flushes the session after the save.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object	force	= arguments.get( ORMKeys.forceinsert );
		Object	options	= arguments.get( ORMKeys.options );
		if ( force instanceof ortus.boxlang.runtime.types.IStruct && options == null ) {
			// entitySave( entity, { flush : true } )
			options	= force;
			force	= null;
		}
		boolean					forceInsert	= force != null && BooleanCaster.cast( force );
		ORMContext				ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		java.util.Set<Session>	touched		= new java.util.LinkedHashSet<>();
		for ( Object item : entities( arguments.get( ORMKeys.entity ) ) ) {
			IClassRunnable entity = requireEntity( item, "entity", "entitySave" );
			touched.add( save( context, entity, forceInsert ) );
		}
		if ( flushRequested( options ) ) {
			touched.forEach( session -> ORMContext.flush( session, "entitySave" ) );
		}
		return null;
	}

	/**
	 * Save an entity: persist a new one, merge a detached one, nothing for a managed one. Shared by {@code entitySave()}
	 * and {@code entityLoadOrSave()}.
	 *
	 * @param context     The context in which the BIF is being invoked.
	 * @param entity      The entity to save.
	 * @param forceInsert Always insert, even when the entity looks persisted.
	 *
	 * @return The session the entity was saved in.
	 */
	public static Session save( IBoxContext context, IClassRunnable entity, boolean forceInsert ) {
		String			entityName		= ortus.boxlang.modules.orm.ORMService.getEntityName( entity );
		ORMContext		ormContext		= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp			= ormContext.requireORMApp();

		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ormContext.getSession( entityRecord.getDatasource() );
		// The Hibernate entity-name a by-name Session call needs: the BoxLang name for hbm/MAP, the facade class for the
		// modern mapping.xml facade representation. See ORMApp.hibernateEntityName.
		String			hbName			= ORMApp.hibernateEntityName( session, entityName );

		// bx-orm is the ORM abstraction, so entitySave() must behave as it did on Hibernate 5's saveOrUpdate(): the object the
		// caller passed in stays live afterward and carries any generated identifier and event changes. Hibernate 7 removed
		// saveOrUpdate(), leaving persist() for new entities and merge() for detached ones.
		//
		// Hibernate manages the generated facade, not the IClassRunnable. Persist the facade (which delegates its state to -
		// and writes generated ids straight back onto - the caller's BoxLang instance). The same instance always maps to the
		// same facade via FacadeSupport's per-instance memoization. On a detached merge we copy the managed state back onto
		// the caller's instance so it stays live and carries generated ids/event changes.
		Object			facade			= FacadeSupport.wrap( ormContext.getFacadeNamespace(), entityName,
		    entity );
		if ( session.contains( hbName, facade ) ) {
			// Already managed: nothing to do; the flush will persist any changes.
		} else if ( forceInsert || isTransient( session, hbName, facade ) ) {
			session.persist( hbName, facade );
		} else {
			Object			managed			= session.merge( hbName, facade );
			IClassRunnable	managedRunnable	= FacadeSupport
			    .unwrap( managed );
			if ( managedRunnable != null && managedRunnable != entity ) {
				entity.getThisScope().putAll( managedRunnable.getThisScope() );
				entity.getVariablesScope().putAll( managedRunnable.getVariablesScope() );
			}
		}
		return session;
	}

	/**
	 * Determine whether an entity has never been persisted, using the same unsaved-value/version/snapshot rules Hibernate's
	 * former <code>saveOrUpdate()</code> used to decide between an insert and a re-attach.
	 *
	 * @param session    The session.
	 * @param entityName The Hibernate entity name.
	 * @param entity     The entity's facade.
	 *
	 * @return True when the entity is new.
	 */
	private static boolean isTransient( Session session, String entityName, Object entity ) {
		return ForeignKeys.isTransient(
		    entityName,
		    entity,
		    null,
		    ( SharedSessionContractImplementor ) session
		);
	}

}
