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

import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.type.ManyToOneType;
import org.hibernate.type.Type;

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
public class EntityDelete extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityDelete() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED ) ),
		    new Argument( false, "Struct", ORMKeys.options )
		};
	}

	/**
	 * Delete one entity, or an array of entities, from the database.
	 * <p>
	 * Delete operations will cascade to related entities if `cascade` is enabled on the relationship property. The rows
	 * are deleted when the session flushes (at the end of the <code>transaction{}</code>); pass <code>{ flush : true }</code>
	 * to flush right away.
	 *
	 * <pre>
	 * entityDelete( user );
	 * entityDelete( [ order1, order2 ], { flush : true } );
	 * </pre>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return null.
	 *
	 * @argument.entity The entity to delete, or an array of entities.
	 *
	 * @argument.options Options: <code>flush</code> (boolean) flushes the session after the delete.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.requireORMApp();
		Set<Session>	touched		= new java.util.LinkedHashSet<>();
		for ( Object item : entities( arguments.get( ORMKeys.entity ) ) ) {
			IClassRunnable	entity			= requireEntity( item, "entity", "entityDelete" );
			String			entityName		= getEntityName( entity );
			EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
			Session			session			= ormContext.getSession( entityRecord.getDatasource() );

			// Hibernate manages the generated facade, not the IClassRunnable. Remove the facade (a detached one must be
			// re-associated via merge first, since Hibernate 6+ rejects removing an unmanaged instance).
			String			hbName			= ORMApp.hibernateEntityName( session, entityName );
			Object			facade			= FacadeSupport.wrap( ormContext.getFacadeNamespace(), entityName, entity );
			Object			managed			= session.contains( facade ) ? facade : session.merge( hbName, facade );
			session.remove( managed );
			evictLoadedChildren( session, managed, hbName );
			touched.add( session );
		}
		if ( flushRequested( arguments.get( ORMKeys.options ) ) ) {
			touched.forEach( session -> ORMContext.flush( session, "entityDelete" ) );
		}
		return null;
	}

	/**
	 * Evict the children that are still loaded in the session and point at the entity that was just removed through a
	 * many-to-one, so the delete does not fail at flush.
	 * <p>
	 * Hibernate 7 refuses to flush a managed child that references a removed entity. Hibernate 5 (bx-orm 1.x) tolerated
	 * it, and apps rely on that when the database cascades the delete ({@code FOREIGN KEY ... ON DELETE CASCADE}) and the
	 * parent maps no collection. Evicting those children gives the same outcome: the database removes their rows, and
	 * the session forgets them. When the database does <em>not</em> cascade, its own foreign key constraint still fails the
	 * delete, as before.
	 * <p>
	 * Only children that already exist in the database are evicted. A child that is still waiting for its insert keeps
	 * failing at flush with the {@code orm.transient} error, because inserting it would be a genuine mistake. Children the
	 * delete already cascaded to (or that {@code onDelete="cascade"} removed) are not managed any more and are skipped.
	 *
	 * @param session    The session the entity was removed from.
	 * @param removed    The removed (managed) entity.
	 * @param entityName The Hibernate entity name of the removed entity.
	 */
	private static void evictLoadedChildren( Session session, Object removed, String entityName ) {
		SessionImplementor	impl		= session.unwrap( SessionImplementor.class );
		Object				removedId	= impl.getContextEntityIdentifier( removed );
		if ( removedId == null ) {
			return;
		}
		for ( Map.Entry<Object, EntityEntry> loaded : impl.getPersistenceContextInternal().reentrantSafeEntityEntries() ) {
			EntityEntry entry = loaded.getValue();
			if ( loaded.getKey() == removed || !entry.isExistsInDatabase()
			    || ( entry.getStatus() != Status.MANAGED && entry.getStatus() != Status.READ_ONLY ) ) {
				continue;
			}
			Type[]		types		= entry.getPersister().getPropertyTypes();
			Object[]	values		= entry.getPersister().getValues( loaded.getKey() );
			boolean		referencing	= false;
			for ( int i = 0; i < types.length && !referencing; i++ ) {
				referencing = types[ i ] instanceof ManyToOneType toOne
				    && entityName.equals( toOne.getAssociatedEntityName() )
				    && values[ i ] != null
				    && removedId.equals( impl.getContextEntityIdentifier( values[ i ] ) );
			}
			if ( referencing ) {
				session.evict( loaded.getKey() );
			}
		}
	}
}
