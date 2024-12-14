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
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Java class responsible for providing datasource connections to Hibernate ORM.
 *
 * @see org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
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
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'unwrap'" );
	}

	@Override
	public Connection getConnection() throws SQLException {
		DataSource	datasource	= getDatasourceForKey( RequestBoxContext.getCurrent(), datasourceName );
		Connection	connection	= datasource.getConnection();
		logger.debug( "Getting connection {} for datasource: {}", connection, datasourceName.getOriginalValue() );
		return connection;
	}

	@Override
	public void closeConnection( Connection conn ) throws SQLException {
		// Just do a regular connection.close(); BoxLang's connection pooling strategy
		// (currently HikariCP) will intercept this and carefully release the
		// connection back into the pool for later reuse.
		conn.close();
	}

	@Override
	public boolean supportsAggressiveRelease() {
		// We probably shouldn't support this, although it may be possible and improve
		// performance.
		// https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/engine/jdbc/connections/spi/ConnectionProvider.html#supportsAggressiveRelease()
		return false;
	}

	@Override
	public boolean isUnwrappableAs( Class unwrapType ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isUnwrappableAs'" );
	}

	/**
	 * Retrieve the datasource for the configured datasource name - either the defined entity datasource, or the default datasource.
	 * 
	 * @param context        JDBC-capable context - i.e. an IBoxContext containing a ConnectionManager.
	 * @param datasourceName Datasource name to look up.
	 */
	private DataSource getDatasourceForKey( IJDBCCapableContext context, Key datasourceName ) {
		ConnectionManager connectionManager = context.getConnectionManager();
		return datasourceName == null || datasourceName.equals( Key.defaultDatasource )
		    ? connectionManager.getDefaultDatasourceOrThrow()
		    : connectionManager.getDatasourceOrThrow( datasourceName );
	}
}
