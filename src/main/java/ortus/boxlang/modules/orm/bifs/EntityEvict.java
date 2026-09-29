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

import java.util.List;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;

@BoxBIF
public class EntityEvict extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityEvict() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity )
		};
	}

	/**
	 * Remove one entity, or an array of entities, from the ORM session. The session stops tracking them: later changes to
	 * an evicted entity are not saved, and loading it again reads it fresh from the database. Nothing is deleted.
	 * <p>
	 * Unsaved changes to an evicted entity are dropped, so flush first if you want to keep them. Evicting an entity that
	 * is not in the session does nothing. To remove cached data from the second-level cache instead, use
	 * <code>ormEvictEntity()</code>.
	 *
	 * <pre>
	 * entityEvict( user );
	 * entityEvict( [ user, order ] );
	 * </pre>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return null.
	 *
	 * @argument.entity An entity, or an array of entities, to remove from the session.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object			value		= arguments.get( ORMKeys.entity );
		List<Object>	entities	= value instanceof Array array ? array : List.of( value );
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.requireORMApp();
		for ( Object item : entities ) {
			IClassRunnable	entity			= requireEntity( item, "entity", "entityEvict" );
			// A lazy proxy is evicted as is (without loading it); anything else through its facade.
			boolean			isProxy			= item instanceof org.hibernate.proxy.HibernateProxy;
			String			entityName		= isProxy
			    ? ORMErrors.entityName( ( ( org.hibernate.proxy.HibernateProxy ) item ).getHibernateLazyInitializer().getEntityName() )
			    : getEntityName( entity );
			EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
			Session			session			= ormContext.getSession( entityRecord.getDatasource() );
			Object			managed			= isProxy ? item : FacadeSupport.wrap( ormContext.getFacadeNamespace(), entityName, entity );
			if ( session.contains( managed ) ) {
				session.evict( managed );
			}
		}
		return null;
	}
}
