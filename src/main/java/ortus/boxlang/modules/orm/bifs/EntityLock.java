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

import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.proxy.HibernateProxy;

import jakarta.persistence.Timeout;
import ortus.boxlang.modules.orm.EntityLocking;
import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;

@BoxBIF
public class EntityLock extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLock() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity ),
		    new Argument( false, "String", ORMKeys.mode, "write" ),
		    new Argument( false, "Struct", ORMKeys.options )
		};
	}

	/**
	 * Lock an entity's row in the database until the current transaction ends.
	 * <p>
	 * Modes:
	 * <ul>
	 * <li><code>write</code> (default): an exclusive lock (<code>select ... for update</code>). Other transactions wait to
	 * lock or change the row.</li>
	 * <li><code>read</code>: a shared lock. Others can read and share-lock the row, nobody can change it.</li>
	 * <li><code>force</code>: an exclusive lock that also increments the entity's version (versioned entities only).</li>
	 * </ul>
	 *
	 * <pre>
	 * 
	 * transaction {
	 *     account = entityLoadByPK( "Account", id );
	 *     entityLock( account );
	 *     account.setBalance( account.getBalance() - amount );
	 * }
	 * </pre>
	 * <p>
	 * The entity must be in the session (loaded in this request and not evicted), and the call must run inside
	 * <code>transaction{}</code>; the lock is released when the transaction ends. To load and lock in one step, use
	 * <code>entityLoadByPK( name, id, { lock : "write" } )</code>.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return null.
	 *
	 * @argument.entity The entity to lock.
	 *
	 * @argument.mode The lock mode: write (default), read or force.
	 *
	 * @argument.options Lock options: timeout (seconds to wait; 0 means do not wait) and skipLocked.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object			value			= arguments.get( ORMKeys.entity );
		IClassRunnable	entity			= requireEntity( value, "entity", "entityLock" );
		boolean			isProxy			= value instanceof HibernateProxy;
		String			entityName		= isProxy ? ORMErrors.entityName( ( ( HibernateProxy ) value ).getHibernateLazyInitializer().getEntityName() )
		    : getEntityName( entity );
		ORMContext		ormContext		= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp			= ormContext.requireORMApp();
		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ormContext.getSession( entityRecord.getDatasource() );
		Object			managed			= isProxy ? value : FacadeSupport.wrap( ormContext.getFacadeNamespace(), entityName, entity );
		if ( !session.contains( managed ) ) {
			throw new ORMException( ORMErrorType.TRANSIENT,
			    "entityLock() needs an entity that is in the session, but this [" + entityRecord.getEntityName()
			        + "] is new, evicted or from another request.",
			    "Load it first (or load and lock in one step with entityLoadByPK( name, id, { lock : \"write\" } ))." );
		}
		LockMode mode = EntityLocking.mode( arguments.get( ORMKeys.mode ), "entityLock" );
		EntityLocking.requireTransaction( ormContext, "entityLock" );
		EntityLocking.checkForce( mode, ormApp.getEntityPersister( session, entityRecord.getEntityName() ), entityRecord.getEntityName(),
		    "entityLock" );
		Timeout timeout = EntityLocking.timeout( ( IStruct ) arguments.get( ORMKeys.options ) );
		if ( timeout == null ) {
			session.lock( managed, mode );
		} else {
			session.lock( managed, mode, timeout );
		}
		return null;
	}
}
