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
package ortus.boxlang.modules.orm.criteria;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Captures the SQL Hibernate generates for a query without running it, for {@code getSQL()}, {@code peekSQL()} and
 * {@code logSQL()}.
 * <p>
 * Registered as every ORM session factory's {@link StatementInspector} (a public Hibernate SPI). Normally it returns the
 * SQL unchanged. While {@link #capture(Runnable)} runs on the current thread, the first statement Hibernate is about to
 * prepare is recorded and the query is stopped with {@link Captured} before a connection is used, so nothing reaches the
 * database.
 */
public final class SqlCapture implements StatementInspector {

	private static final long					serialVersionUID	= 1L;

	/** The shared inspector registered on every session factory. */
	public static final SqlCapture				INSTANCE			= new SqlCapture();

	/** Whether the current thread is capturing. */
	private static final ThreadLocal<Boolean>	CAPTURING			= ThreadLocal.withInitial( () -> false );

	/**
	 * Use {@link #INSTANCE}.
	 */
	private SqlCapture() {
	}

	/**
	 * Stops a query once its SQL is known.
	 */
	static final class Captured extends RuntimeException {

		private static final long	serialVersionUID	= 1L;

		/** The SQL Hibernate generated. */
		final String				sql;

		/**
		 * Record the SQL.
		 *
		 * @param sql The SQL.
		 */
		Captured( String sql ) {
			super( "SQL captured", null, false, false );
			this.sql = sql;
		}
	}

	/**
	 * Return the SQL unchanged, or, while capturing on this thread, record it and stop the query.
	 *
	 * @param sql The SQL Hibernate is about to prepare.
	 *
	 * @return The SQL, unchanged.
	 */
	@Override
	public String inspect( String sql ) {
		if ( CAPTURING.get() ) {
			CAPTURING.set( false );
			throw new Captured( sql );
		}
		return sql;
	}

	/**
	 * Run a query and return the SQL of its first statement instead of executing it.
	 *
	 * @param query Runs the prepared query (e.g. {@code query::list}).
	 *
	 * @return The SQL, or null when the query issued no statement.
	 */
	static String capture( Runnable query ) {
		CAPTURING.set( true );
		try {
			query.run();
			return null;
		} catch ( RuntimeException e ) {
			for ( Throwable t = e; t != null; t = t.getCause() ) {
				if ( t instanceof Captured captured ) {
					return captured.sql;
				}
			}
			throw e;
		} finally {
			CAPTURING.set( false );
		}
	}
}
