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

import ortus.boxlang.modules.orm.hibernate.facade.BoxIClassRunnableState;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;

import java.util.Set;

import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IBoxContext.ScopeSearchResult;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
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
		ORMContext ormContext = ORMContext.getForContext( jdbcBoxContext );
		if ( ! ( entity instanceof IClassRunnable runnable ) ) {
			ormContext.getSession().refresh( entity );
			return entity;
		}
		ORMApp	ormApp		= ormContext.getORMApp();
		String	entityName	= getEntityName( runnable );
		String	namespace	= ormContext.getFacadeNamespace();
		Session	session		= ormContext.getSession( ormApp.lookupEntity( entityName, true ).getDatasource() );
		String	hbName		= ORMApp.hibernateEntityName( session, entityName );
		// Hibernate manages the generated facade, not the IClassRunnable; the facade's state delegates to this instance.
		Object	facade		= FacadeSupport.wrap( namespace, entityName, runnable );
		if ( session.contains( hbName, facade ) ) {
			session.refresh( facade );
			return entity;
		}

		// Detached (e.g. after ormClearSession() or a rolled-back transaction): Hibernate 7 refuses to refresh or reattach it,
		// but Hibernate 5 re-read it and made it managed again. Do the same: load the row, then either rebind that managed
		// entry to the caller's object or, if another variable already holds the managed copy, copy its values over.
		EntityPersister	persister	= ormApp.getEntityPersister( session, entityName );
		Object			id			= persister.getIdentifier( facade, ( SharedSessionContractImplementor ) session );
		if ( id == null ) {
			throw new BoxRuntimeException( "Cannot reload entity [" + entityName + "]: it has no identifier (it was never saved)." );
		}
		SharedSessionContractImplementor	source	= ( SharedSessionContractImplementor ) session;
		boolean								tracked	= source.getPersistenceContextInternal()
		    .getEntity( source.generateEntityKey( id, persister ) ) != null;
		Object								loaded	= session.get( hbName, id );
		if ( loaded == null ) {
			throw new BoxRuntimeException( "Cannot reload entity [" + entityName + "] with id [" + id + "]: the row no longer exists." );
		}
		if ( tracked ) {
			// Only one managed instance per row: refresh the existing one and give the caller its values.
			session.refresh( loaded );
			IClassRunnable current = FacadeSupport.unwrap( loaded );
			if ( current != null && current != runnable ) {
				BoxIClassRunnableState	from	= new BoxIClassRunnableState( current );
				BoxIClassRunnableState	to		= new BoxIClassRunnableState( runnable );
				for ( String property : persister.getPropertyNames() ) {
					to.set( property, from.get( property ) );
				}
			}
			return entity;
		}
		// The row was loaded just now for this call: make the caller's object the managed instance and re-read into it.
		FacadeSupport.rebind( loaded, runnable, namespace );
		session.refresh( loaded );
		return entity;
	}

}
