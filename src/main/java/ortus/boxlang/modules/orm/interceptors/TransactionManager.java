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
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * ORM transaction lifecycle management.
 * <p>
 * Listens to boxlang transaction events to manage the Hibernate transaction lifecycles (start,end,commit,rollback, etc.)
 *
 * @since 1.0.0
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
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );
		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			logger.warn(
			    "No ORM application found during transaction request.  Either the ORM service is not properly configured or the application has not yet started." );
			return;
		}
		IJDBCCapableContext	jdbcContext	= context.getParentOfType( IJDBCCapableContext.class );
		ORMContext			ormContext	= ORMContext.getForContext( jdbcContext );
		ORMConfig			config		= ormContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormContext.getSession( datasource );
			// Ensure any pending operations are flushed before starting the transaction
			ormSession.flush();
			// We should never hit this conditional as long as BoxLang does not support nested transactions
			if ( ormSession.isJoinedToTransaction() ) {
				if ( logger.isDebugEnabled() ) {
					logger.debug(
					    "Session [{}] is for datasource [{}] already joined to a transaction",
					    ormSession,
					    datasource.getName()
					);
				}
				return;
			}

			if ( config.autoManageSession ) {

				if ( logger.isDebugEnabled() ) {
					logger.debug(
					    "'autoManageSession' is enabled; flushing ORM session [{}] for datasource [{}] prior to transaction begin.",
					    ormSession,
					    datasource.getName()
					);
				}

				ormSession.flush();
			}

			logger.debug(
			    "Starting ORM transaction on session [{}] for datasource: [{}]",
			    ormSession,
			    datasource.getName()
			);

			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionSetSavepoint( IStruct args ) {
		IBoxContext	context			= args.getAs( IBoxContext.class, Key.context );
		String		savepointName	= args.getAsString( Key.savepoint );
		ORMApp		ormApp			= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			// Just return as we would already have warned during transaction begin
			return;
		}
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ormApp.getDatasources().forEach( datasource -> {
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Setting ORM transaction savepoint [{}] on session [{}] for datasource [{}]",
				    savepointName,
				    ormSession,
				    datasource.getName()
				);
			}

			ormSession.flush();
		} );
	}

	@InterceptionPoint
	public void onTransactionCommit( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );

		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			// Just return as we would already have warned during transaction begin
			return;
		}
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );

		ormApp.getDatasources().forEach( datasource -> {
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Committing ORM transaction and beginning NEW transaction on session [{}] for datasource [{}]",
				    ormSession,
				    datasource.getName()
				);
			}

			ormSession.getTransaction().commit();
			ormSession.flush();
			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionRollback( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );

		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			// Just return as we would already have warned during transaction begin
			return;
		}
		ORMContext	ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMConfig	config		= ormContext.getConfig();

		ormApp.getDatasources().forEach( ( datasource ) -> {
			// FYI: Lucee's implementation actually waits until transaction END to rollback and clear the session.
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Rolling back ORM transaction on session [{}] for datasource [{}]",
				    ormSession,
				    datasource.getName()
				);
			}

			ormSession.getTransaction().rollback();
			if ( config.autoManageSession ) {
				if ( logger.isDebugEnabled() ) {
					logger.debug(
					    "'autoManageSession' is enabled; clearing ORM session [{}] for datasource [{}] after transaction rollback.",
					    ormSession,
					    datasource.getName()
					);
				}
				ormSession.clear();
			}

			logger.debug(
			    "Beginning new ORM transaction on session [{}] for datasource [{}]",
			    ormSession,
			    datasource.getName()
			);

			ormSession.beginTransaction();
		} );
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );

		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			// Just return as we would already have warned during transaction begin
			return;
		}
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Ending ORM transaction on session [{}] for datasource [{}]",
				    ormSession,
				    datasource.getName()
				);
			}

			ormSession.flush();
			ormSession.getTransaction().commit();
		} );
	}
}
