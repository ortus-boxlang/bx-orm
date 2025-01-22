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

import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * BIF to evict all queries from the named or default cache.
 */
@BoxBIF
public class ORMEvictQueries extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMEvictQueries() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.cacheName ),
		    new Argument( false, "String", ORMKeys.datasource )
		};
	}

	/**
	 * Evict all queries from the named or default cache on the named or default datasource.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String				cacheName			= arguments.getAsString( ORMKeys.cacheName );
		String				datasourceName		= arguments.getAsString( ORMKeys.datasource );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		SessionFactory		factory				= null;
		if ( datasourceName == null ) {
			factory = ormRequestContext.getSession().getSessionFactory();
		} else {
			factory = ormRequestContext.getSession( Key.of( datasourceName ) ).getSessionFactory();
		}

		if ( cacheName == null ) {
			factory.getCache().evictDefaultQueryRegion();
		} else {
			factory.getCache().evictQueryRegion( cacheName );
		}

		return null;
	}
}
