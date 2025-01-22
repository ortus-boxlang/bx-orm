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

import java.io.Serializable;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.GenericCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class ORMEvictCollection extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMEvictCollection() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "String", ORMKeys.collectionName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "String", ORMKeys.primaryKey )
		};
	}

	/**
	 * Evict all entity data for a given collection on a given entity type from the second-level cache.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String			entityName		= arguments.getAsString( ORMKeys.entityName );
		String			primaryKey		= arguments.getAsString( ORMKeys.primaryKey );
		ORMApp			ormApp			= ORMRequestContext.getForContext( context.getRequestContext() ).getORMApp();
		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ORMRequestContext.getForContext( context.getRequestContext() ).getSession( entityRecord.getDatasource() );
		SessionFactory	factory			= session.getSessionFactory();
		// Fix casing.
		entityName = entityRecord.getEntityName();
		String collection = entityName + "." + arguments.getAsString( ORMKeys.collectionName );

		if ( primaryKey == null ) {
			factory.getCache().evictCollectionData( collection );
		} else {
			String			keyType	= ormApp.getKeyJavaType( session, entityName ).getSimpleName();
			Serializable	id		= ( Serializable ) GenericCaster.cast( context, primaryKey, keyType );
			factory.getCache().evictCollectionData( collection, id );
		}

		return null;
	}

}
