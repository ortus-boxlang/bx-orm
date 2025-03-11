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

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Example;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF
public class EntityLoadByExample extends BaseORMBIF {

	public EntityLoadByExample() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", ORMKeys.sampleEntity ),
		    new Argument( false, "boolean", ORMKeys.unique, false )
		};
	}

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	@SuppressWarnings( { "deprecation", "unchecked" } )
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		RequestBoxContext	requestContext	= context.getRequestContext();
		ORMApp				ormApp			= ORMRequestContext.getForContext( requestContext ).getORMApp();
		Object				sampleEntity	= arguments.get( ORMKeys.sampleEntity );
		Boolean				unique			= arguments.getAsBoolean( ORMKeys.unique );
		if ( ! ( sampleEntity instanceof IClassRunnable ) ) {
			throw new BoxRuntimeException( "Sample entity must be a valid entity" );
		}
		IClassRunnable	workingEntity	= ( IClassRunnable ) sampleEntity;
		String			entityName		= getEntityName( workingEntity );
		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		Session			session			= ORMRequestContext.getForContext( requestContext ).getSession( entityRecord.getDatasource() );
		Criteria		criteria		= session.createCriteria( entityName );
		Example			example			= Example.create( workingEntity );
		criteria.add( example );

		if ( unique ) {
			criteria.setMaxResults( 1 );
		}
		List<? extends IClassRunnable> results = criteria.list();

		if ( unique ) {
			return results.isEmpty() ? null : results.get( 0 );
		} else {
			return Array.fromList( results );
		}
	}

}
