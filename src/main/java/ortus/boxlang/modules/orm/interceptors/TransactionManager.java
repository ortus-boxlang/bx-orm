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

	/*
	 * Design note: the ORM does NOT run its own Hibernate transaction. Instead, ORMConnectionProvider
	 * rides the BoxLang transaction connection (see that class) so BoxLang owns the real JDBC
	 * commit/rollback, and this interceptor only synchronizes the Hibernate session with the BoxLang
	 * transaction lifecycle:
	 * <ul>
	 * <li>commit/end: flush pending writes so they are emitted on the shared connection before BoxLang
	 * commits them.</li>
	 * <li>rollback: clear the session so it does not retain state that BoxLang rolls back at the JDBC
	 * level.</li>
	 * </ul>
	 * Read-your-writes for in-transaction ORM queries is handled at the query choke points via
	 * {@link ORMContext#flushForQuery(Session)} (Hibernate suppresses auto-flush-before-query when no
	 * Hibernate transaction is in progress, and we deliberately run none).
	 * <p>
	 * Nested transactions are governed entirely by BoxLang (currently flattened into a single
	 * demarcation unit unless the runtime's experimental enableNestedTransactions is on); this
	 * interceptor treats every event uniformly and lets BoxLang decide what actually commits.
	 */

	@InterceptionPoint
	public void onTransactionBegin( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );
		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			return;
		}
		IJDBCCapableContext	jdbcContext	= context.getParentOfType( IJDBCCapableContext.class );
		ORMContext			ormContext	= ORMContext.getForContext( jdbcContext );
		ORMConfig			config		= ormContext.getConfig();

		if ( !config.autoManageSession ) {
			return;
		}

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormContext.getSession( datasource );
			// Flush any pending pre-transaction work before the transaction boundary (Lucee compat).
			if ( ormSession.isOpen() ) {
				ORMContext.flush( ormSession, "transaction commit" );
			}
		} );
	}

	@InterceptionPoint
	public void onTransactionSetSavepoint( IStruct args ) {
		IBoxContext	context			= args.getAs( IBoxContext.class, Key.context );
		String		savepointName	= args.getAsString( Key.savepoint );
		ORMApp		ormApp			= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			return;
		}
		ORMContext	ormContext						= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		// A CHILD_*_END savepoint marks the end of a nested transaction unit; flush so the nested
		// unit's writes are emitted on the shared connection before the parent proceeds.
		boolean		isChildTransactionEndSavepoint	= savepointName.startsWith( "CHILD_" ) && savepointName.endsWith( "_END" );
		if ( !isChildTransactionEndSavepoint ) {
			return;
		}
		ormApp.getDatasources().forEach( datasource -> {
			Session ormSession = ormContext.getSession( datasource );
			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Flushing ORM session [{}] for datasource [{}] at child transaction savepoint [{}]",
				    ormSession,
				    datasource.getName(),
				    savepointName
				);
			}
			if ( ormSession.isOpen() ) {
				ORMContext.flush( ormSession, "transaction commit" );
			}
		} );
	}

	@InterceptionPoint
	public void onTransactionCommit( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );

		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			return;
		}
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );

		ormApp.getDatasources().forEach( datasource -> {
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Flushing ORM session [{}] for datasource [{}] on transaction commit; BoxLang owns the JDBC commit.",
				    ormSession,
				    datasource.getName()
				);
			}
			// Emit pending SQL on the shared transaction connection. BoxLang performs the real JDBC
			// commit; in a single-unit nested transaction a child commit is a no-op governed by the
			// outermost block.
			if ( ormSession.isOpen() ) {
				ORMContext.flush( ormSession, "transaction commit" );
			}
		} );
		// The flushed writes are part of this commit: their postCommit events fire when the transaction ends.
		ormContext.getPostCommits().markCommitted();
	}

	@InterceptionPoint
	public void onTransactionRollback( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );

		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			return;
		}
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Clearing ORM session [{}] for datasource [{}] on transaction rollback; BoxLang owns the JDBC rollback.",
				    ormSession,
				    datasource.getName()
				);
			}
			// BoxLang rolls back the shared connection (or a child savepoint) immediately after this event.
			// Clearing the session is mandatory (independent of autoManageSession): it discards pending writes
			// so they are never re-flushed at transaction end, and drops the first-level cache so the session
			// does not retain entities whose rows were rolled back at the JDBC level.
			if ( ormSession.isOpen() ) {
				ormSession.clear();
			}
		} );
		// Rolled-back writes never get a postCommit event.
		ormContext.getPostCommits().dropUncommitted();
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct args ) {
		IBoxContext	context	= args.getAs( IBoxContext.class, Key.context );

		ORMApp		ormApp	= ormService.getORMAppByContext( context );
		if ( ormApp == null ) {
			return;
		}
		ORMContext ormContext = ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );

		ormApp.getDatasources().forEach( ( datasource ) -> {
			Session ormSession = ormContext.getSession( datasource );

			if ( logger.isDebugEnabled() ) {
				logger.debug(
				    "Flushing ORM session [{}] for datasource [{}] at transaction end.",
				    ormSession,
				    datasource.getName()
				);
			}
			// Final flush so any pending writes (e.g. a transaction{} with no explicit commit) are
			// emitted before BoxLang commits at the end of the demarcation unit. If the transaction was
			// already rolled back, the session was cleared and this is a no-op.
			if ( ormSession.isOpen() ) {
				ORMContext.flush( ormSession, "transaction commit" );
			}
		} );
		// BoxLang announces the end after the JDBC commit: fire the committed writes' postCommit events.
		ormContext.getPostCommits().fire();
	}
}
