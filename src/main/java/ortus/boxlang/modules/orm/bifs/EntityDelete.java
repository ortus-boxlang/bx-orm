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
			session.remove( session.contains( hbName, facade ) ? facade : session.merge( hbName, facade ) );
			touched.add( session );
		}
		if ( flushRequested( arguments.get( ORMKeys.options ) ) ) {
			touched.forEach( session -> ORMContext.flush( session, "entityDelete" ) );
		}
		return null;
	}
}
