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
package ortus.boxlang.modules.orm.interceptors;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM transaction lifecycle management.
 * <p>
 * Listens to boxlang transaction events to manage the Hibernate transaction lifecycles (start,end,commit,rollback, etc.)
 */
public class TransactionManager extends BaseInterceptor {

	// The properties to configure the interceptor with
	private ORMService ormService;

	/**
	 * This method is called by the BoxLang runtime to configure the interceptor
	 * with a Struct of properties
	 *
	 * @param properties The properties to configure the interceptor with (if any)
	 */
	@Override
	public void configure( IStruct properties ) {
		this.properties	= properties;
		this.logger		= getRuntime().getLoggingService().getLogger( "orm" );
		this.ormService	= ( ( ORMService ) getRuntime().getGlobalService( ORMKeys.ORMService ) );
	}

	@InterceptionPoint
	public void onTransactionBegin( IStruct args ) {
		logger.debug( "onTransactionBegin fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMAppByContext( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );
			if ( config.autoManageSession ) {

				logger.debug(
				    "'autoManageSession' is enabled; flushing ORM session [{}] for datasource [{}] prior to transaction begin.",
				    ormSession,
				    datasource.getOriginalValue()
				);

				ormSession.flush();
			}

			logger.debug(
			    "Starting ORM transaction on session [{}] for datasource: [{}]",
			    ormSession,
			    datasource.getOriginalValue()
			);

			if ( ormSession.isJoinedToTransaction() ) {
				// May want to put this behind some kind of compatibility flag...
				logger.debug(
				    "Session [{}] is already joined to a transaction, closing transaction and beginning anew",
				    ormSession
				);
				ormSession.getTransaction().commit();
			}
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionCommit( IStruct args ) {
		logger.debug( "onTransactionCommit fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMAppByContext( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( datasource -> {
			Session ormSession = ormRequestContext.getSession( datasource );

			logger.debug(
			    "Committing ORM transaction on session [{}] for datasource [{}]",
			    ormSession,
			    datasource.getOriginalValue()
			);

			ormSession.getTransaction().commit();

			logger.debug(
			    "Beginning new ORM transaction on session [{}] for datasource [{}]",
			    ormSession,
			    datasource.getOriginalValue()
			);

			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionRollback( IStruct args ) {
		logger.debug( "onTransactionRollback fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMAppByContext( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			// FYI: Lucee's implementation actually waits until transaction END to rollback and clear the session.
			Session ormSession = ormRequestContext.getSession( datasource );

			logger.debug(
			    "Rolling back ORM transaction on session [{}] for datasource [{}]",
			    ormSession,
			    datasource.getOriginalValue()
			);

			ormSession.getTransaction().rollback();
			if ( config.autoManageSession ) {
				if ( logger.isDebugEnabled() ) {

					logger.debug(
					    "'autoManageSession' is enabled; clearing ORM session [{}] for datasource [{}] after transaction rollback.",
					    ormSession,
					    datasource.getOriginalValue() );
				}
				ormSession.clear();
			}

			logger.debug(
			    "Beginning new ORM transaction on session [{}] for datasource [{}]",
			    ormSession,
			    datasource.getOriginalValue()
			);

			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct args ) {
		logger.debug( "onTransactionEnd fired" );

		IBoxContext			context				= args.getAs( IBoxContext.class, Key.context );
		ORMApp				ormApp				= ormService.getORMAppByContext( context );
		ORMRequestContext	ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		ORMConfig			config				= ormRequestContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormRequestContext.getSession( datasource );

			logger.debug(
			    "Ending ORM transaction on session [{}] for datasource [{}]",
			    ormSession,
			    datasource.getOriginalValue()
			);

			ormSession.getTransaction().commit();
		} );
	}
}
