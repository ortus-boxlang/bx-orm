package ortus.boxlang.orm.config;

import java.sql.Connection;
import java.sql.SQLException;

import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import ortus.boxlang.runtime.jdbc.DataSource;

/**
 * Java class responsible for providing datasource connections to Hibernate ORM.
 *
 * @see org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
 */
public class ORMConnectionProvider implements ConnectionProvider {

	/**
	 * The BoxLang DataSource object which manages database connections and
	 * especially connection pooling.
	 */
	private DataSource dataSource;

	public ORMConnectionProvider( DataSource dataSource ) {
		this.dataSource = dataSource;
	}

	@Override
	public <T> @UnknownKeyFor @NonNull @Initialized T unwrap(
	    @UnknownKeyFor @NonNull @Initialized Class<@UnknownKeyFor @NonNull @Initialized T> unwrapType ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'unwrap'" );
	}

	@Override
	public Connection getConnection() throws SQLException {
		return dataSource.getConnection();
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
