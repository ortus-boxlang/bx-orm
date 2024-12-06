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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.runtime.jdbc.DataSource;

/**
 * Java class responsible for providing datasource connections to Hibernate ORM.
 *
 * @see org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
 */
public class ORMConnectionProvider implements ConnectionProvider {

	private Logger		logger	= LoggerFactory.getLogger( ORMConnectionProvider.class );

	/**
	 * The BoxLang DataSource object which manages database connections and
	 * especially connection pooling.
	 */
	private DataSource	dataSource;

	public ORMConnectionProvider( DataSource dataSource ) {
		this.dataSource = dataSource;
	}

	@Override
	public <T> T unwrap( Class<T> unwrapType ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'unwrap'" );
	}

	@Override
	public Connection getConnection() throws SQLException {
		Connection connection = dataSource.getConnection();
		logger.debug( "Getting connection {} for datasource: {}", connection, dataSource.getOriginalName() );
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

}
