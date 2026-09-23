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
package ortus.boxlang.modules.orm.config;

import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.jdbc.ITransaction;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Hibernate ConnectionProvider implementation for retrieving JDBC connections on a specific datasource from BoxLang's connection manager.
 *
 * Built once at ORM startup for each datasource/session factory.
 *
 * @see org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
 *
 * @since 1.0.0
 */
public class ORMConnectionProvider implements ConnectionProvider {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger			logger;

	/**
	 * The BoxLang DataSource object which manages database connections and
	 * especially connection pooling.
	 */
	private Key						datasourceName;

	public ORMConnectionProvider( Key datasourceName ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.datasourceName	= datasourceName;
	}

	@Override
	public <T> T unwrap( Class<T> unwrapType ) {
		throw new UnsupportedOperationException( "Unimplemented method 'unwrap'" );
	}

	/**
	 * Acquire a JDBC connection from BoxLang's connection manager for the configured datasource.
	 * <p>
	 * This is <em>transaction-aware</em>: when the current request is inside a BoxLang
	 * {@code transaction{}} block bound to this datasource, the connection manager returns the
	 * transaction's shared connection. Otherwise it returns a fresh pooled connection. Riding the
	 * BoxLang transaction connection lets Hibernate writes flow through the same JDBC connection
	 * BoxLang controls, so BoxLang's single-unit (savepoint-based) commit/rollback governs the
	 * rows the ORM flushes.
	 */
	@Override
	public Connection getConnection() throws SQLException {
		ConnectionManager	connectionManager	= getConnectionManager();
		DataSource			datasource			= resolveDatasource( connectionManager );
		Connection			connection			= connectionManager.getBoxConnection( datasource );
		logger.trace( "Getting connection {} for datasource: {}", connection, datasourceName.getOriginalValue() );
		return connection;
	}

	/**
	 * Close the JDBC connection, thus releasing it back into the pool for later reuse.
	 * <p>
	 * When the connection belongs to an active BoxLang transaction, BoxLang owns its lifecycle
	 * (commit, rollback, and eventual release at transaction end). Closing it here would sever the
	 * transaction mid-flight, so we leave it untouched. Any other (fresh pooled) connection is
	 * closed normally, which BoxLang's pooling strategy (currently HikariCP) intercepts to return
	 * it to the pool.
	 */
	@Override
	public void closeConnection( Connection conn ) throws SQLException {
		if ( isActiveTransactionConnection( conn ) ) {
			logger.trace( "Skipping close of transaction-owned connection {} for datasource: {}", conn, datasourceName.getOriginalValue() );
			return;
		}
		logger.trace( "closing connection {} for datasource: {}", conn, datasourceName.getOriginalValue() );
		conn.close();
	}

	@Override
	public boolean supportsAggressiveRelease() {
		// Report aggressive-release support so Hibernate honors DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT
		// (see SessionFactoryBuilder). Without this, Hibernate falls back to holding a single connection for the
		// session, which would keep a stale reference to a BoxLang transaction connection after BoxLang closes it
		// at transaction end. Per-statement acquire/release is what lets each statement ride the current
		// transaction connection (or a fresh pooled one outside a transaction).
		return true;
	}

	@Override
	public boolean isUnwrappableAs( Class unwrapType ) {
		throw new UnsupportedOperationException( "Unimplemented method 'isUnwrappableAs'" );
	}

	/**
	 * Retrieve the BoxLang connection manager from the current request context.
	 */
	private ConnectionManager getConnectionManager() {
		IBoxContext context = RequestBoxContext.getCurrent();
		if ( context == null ) {
			throw new IllegalStateException( "No BoxLang request context is available to retrieve the datasource." );
		}
		IJDBCCapableContext jdbcContext = context.getParentOfType( IJDBCCapableContext.class );
		if ( jdbcContext == null ) {
			throw new IllegalStateException( "No JDBC-capable BoxLang context is available to retrieve the datasource." );
		}
		return jdbcContext.getConnectionManager();
	}

	/**
	 * Resolve the datasource for the configured datasource name - either the defined entity datasource, or the default datasource.
	 *
	 * @param connectionManager The connection manager to resolve the datasource against.
	 */
	private DataSource resolveDatasource( ConnectionManager connectionManager ) {
		return datasourceName == null || datasourceName.equals( Key.defaultDatasource )
		    ? connectionManager.getDefaultDatasourceOrThrow()
		    : connectionManager.getDatasourceOrThrow( datasourceName );
	}

	/**
	 * Determine whether the given connection is the connection owned by an active BoxLang
	 * transaction. Used by {@link #closeConnection(Connection)} to avoid closing a connection whose
	 * lifecycle BoxLang controls.
	 * <p>
	 * We only compare once the transaction has actually bound a datasource (and therefore a
	 * connection); this avoids forcing lazy creation of a transaction connection just to compare.
	 *
	 * @param conn The connection Hibernate is asking to close.
	 */
	private boolean isActiveTransactionConnection( Connection conn ) {
		try {
			ConnectionManager connectionManager = getConnectionManager();
			if ( !connectionManager.isInTransaction() ) {
				return false;
			}
			ITransaction transaction = connectionManager.getTransaction();
			if ( transaction == null || transaction.getDataSource() == null ) {
				return false;
			}
			return conn == transaction.getBoxConnection();
		} catch ( RuntimeException e ) {
			// If we cannot determine transaction state, fall back to closing the connection normally.
			return false;
		}
	}
}
