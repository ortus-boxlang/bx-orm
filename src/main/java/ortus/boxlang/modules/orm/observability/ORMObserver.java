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
package ortus.boxlang.modules.orm.observability;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

import org.hibernate.BaseSessionEventListener;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.services.InterceptorService;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Announces ORM observability events (<code>onORMQuery</code>, <code>onORMFlush</code>, <code>onORMException</code>) to BoxLang interceptors.
 * <p>
 * JDBC objects are only wrapped when somebody is listening to one of these events at the moment a connection is acquired, so there is no
 * wrapping cost when nothing is listening. Event payloads are built lazily, inside the interceptor service's own state check.
 */
public final class ORMObserver {

	private static final BoxRuntime					runtime			= BoxRuntime.getInstance();

	/**
	 * Tracks the statements executed while a Hibernate flush is in progress on this thread.
	 */
	private static final ThreadLocal<FlushTally>	FLUSH			= new ThreadLocal<>();

	/**
	 * Optional hints, set by ORM code which knows more about the statement being run than the JDBC layer does.
	 */
	private static final ThreadLocal<String>		HQL_HINT		= new ThreadLocal<>();
	private static final ThreadLocal<String>		ENTITY_HINT		= new ThreadLocal<>();

	private static final Set<String>				PARAM_SETTERS	= Set.of(
	    "setObject", "setString", "setNString", "setInt", "setLong", "setShort", "setByte", "setBoolean", "setFloat", "setDouble",
	    "setBigDecimal", "setBytes", "setDate", "setTime", "setTimestamp", "setNull", "setURL", "setBlob", "setClob", "setNClob", "setArray",
	    "setSQLXML", "setRef", "setRowId", "setAsciiStream", "setBinaryStream", "setCharacterStream", "setNCharacterStream" );

	private static final Set<String>				EXECUTORS		= Set.of(
	    "execute", "executeQuery", "executeUpdate", "executeBatch", "executeLargeUpdate", "executeLargeBatch" );

	private ORMObserver() {
	}

	/**
	 * Does the event have at least one registered listener? Note <code>InterceptorService.hasState()</code> only says the interception point
	 * exists, which is always true for ORM events once the ORM service starts.
	 */
	private static boolean hasListeners( Key event ) {
		InterceptorService	service	= runtime.getInterceptorService();
		var					state	= service.hasState( event ) ? service.getState( event ) : null;
		return state != null && state.size() > 0;
	}

	/**
	 * Is anybody listening to an ORM observability event right now?
	 */
	public static boolean isObserving() {
		return hasListeners( ORMKeys.EVENT_ORM_QUERY )
		    || hasListeners( ORMKeys.EVENT_ORM_EXCEPTION )
		    || hasListeners( ORMKeys.EVENT_ORM_FLUSH );
	}

	/**
	 * Wrap a connection so statements run through it are announced. Returns the connection untouched when nobody is listening.
	 */
	public static Connection wrap( Connection connection, Key datasource, String appName, boolean captureParams ) {
		if ( connection == null || !isObserving() ) {
			return connection;
		}
		Origin origin = new Origin( datasource, appName, captureParams );
		return ( Connection ) Proxy.newProxyInstance(
		    ORMObserver.class.getClassLoader(),
		    new Class<?>[] { Connection.class },
		    new ConnectionHandler( connection, origin )
		);
	}

	/**
	 * The connection behind an observed connection, or the connection itself when it was not wrapped.
	 */
	public static Connection unwrap( Connection connection ) {
		if ( connection != null && Proxy.isProxyClass( connection.getClass() )
		    && Proxy.getInvocationHandler( connection ) instanceof ConnectionHandler handler ) {
			return handler.delegate;
		}
		return connection;
	}

	/**
	 * Hint the HQL behind the statements run on this thread until {@link #clearHints()} is called.
	 */
	public static void hintHql( String hql ) {
		if ( isObserving() ) {
			HQL_HINT.set( hql );
		}
	}

	/**
	 * Hint the entity behind the statements run on this thread until {@link #clearHints()} is called.
	 */
	public static void hintEntity( String entityName ) {
		if ( isObserving() ) {
			ENTITY_HINT.set( entityName );
		}
	}

	public static void clearHints() {
		HQL_HINT.remove();
		ENTITY_HINT.remove();
	}

	/**
	 * Classify a SQL string as select, insert, update, delete, ddl or other.
	 */
	public static String classify( String sql ) {
		if ( sql == null ) {
			return "other";
		}
		int	i	= 0;
		int	len	= sql.length();
		while ( i < len ) {
			char c = sql.charAt( i );
			if ( Character.isWhitespace( c ) || c == '(' ) {
				i++;
			} else if ( sql.startsWith( "/*", i ) ) {
				int end = sql.indexOf( "*/", i + 2 );
				i = end < 0 ? len : end + 2;
			} else if ( sql.startsWith( "--", i ) ) {
				int end = sql.indexOf( '\n', i );
				i = end < 0 ? len : end + 1;
			} else {
				break;
			}
		}
		int j = i;
		while ( j < len && Character.isLetter( sql.charAt( j ) ) ) {
			j++;
		}
		return switch ( sql.substring( i, j ).toLowerCase( Locale.ROOT ) ) {
			case "select", "with", "show", "pragma" -> "select";
			case "insert" -> "insert";
			case "update", "merge", "upsert" -> "update";
			case "delete" -> "delete";
			case "create", "alter", "drop", "truncate", "comment", "rename" -> "ddl";
			default -> "other";
		};
	}

	/**
	 * Hibernate session listener which brackets a flush so the statements it runs can be counted for <code>onORMFlush</code>.
	 */
	public static class FlushListener extends BaseSessionEventListener {

		private static final long	serialVersionUID	= 1L;
		private final String		datasource;
		private final String		appName;

		public FlushListener( String datasource, String appName ) {
			this.datasource	= datasource;
			this.appName	= appName;
		}

		@Override
		public void flushStart() {
			if ( hasListeners( ORMKeys.EVENT_ORM_FLUSH ) ) {
				FLUSH.set( new FlushTally() );
			}
		}

		@Override
		public void flushEnd( int numberOfEntities, int numberOfCollections ) {
			FlushTally tally = FLUSH.get();
			FLUSH.remove();
			if ( tally == null ) {
				return;
			}
			long elapsed = System.nanoTime() - tally.start;
			runtime.getInterceptorService().announce(
			    ORMKeys.EVENT_ORM_FLUSH,
			    () -> Struct.of(
			        ORMKeys.inserts, tally.inserts,
			        ORMKeys.updates, tally.updates,
			        ORMKeys.deletes, tally.deletes,
			        ORMKeys.elapsedNanos, elapsed,
			        ORMKeys.datasource, datasource,
			        ORMKeys.appName, appName
			    )
			);
		}
	}

	private static class FlushTally {

		final long	start	= System.nanoTime();
		int			inserts, updates, deletes;

		void count( String kind, int n ) {
			switch ( kind ) {
				case "insert" -> inserts += n;
				case "update" -> updates += n;
				case "delete" -> deletes += n;
				default -> {
				}
			}
		}
	}

	private record Origin( Key datasource, String appName, boolean captureParams ) {
	}

	/**
	 * Build and announce <code>onORMQuery</code>, plus <code>onORMException</code> on failure.
	 */
	private static void announceQuery( Origin origin, String sql, long elapsed, long rows, Throwable error, List<Object> params ) {
		String		kind	= classify( sql );

		FlushTally	tally	= FLUSH.get();
		if ( tally != null && error == null ) {
			tally.count( kind, rows > 0 && !"select".equals( kind ) ? ( int ) rows : 1 );
		}

		InterceptorService service = runtime.getInterceptorService();
		if ( hasListeners( ORMKeys.EVENT_ORM_QUERY ) ) {
			final String	hql		= HQL_HINT.get();
			final String	entity	= ENTITY_HINT.get();
			service.announce( ORMKeys.EVENT_ORM_QUERY, () -> {
				IStruct data = Struct.of(
				    ORMKeys.sql, sql,
				    ORMKeys.kind, kind,
				    ORMKeys.elapsedNanos, elapsed,
				    ORMKeys.rows, rows,
				    ORMKeys.datasource, origin.datasource().getOriginalValue(),
				    ORMKeys.appName, origin.appName(),
				    ORMKeys.hql, hql,
				    ORMKeys.entityName, entity,
				    ORMKeys.error, error
				);
				if ( origin.captureParams() ) {
					data.put( ORMKeys.params, params == null ? new Array() : Array.fromList( params ) );
				}
				return data;
			} );
		}
		if ( error != null && hasListeners( ORMKeys.EVENT_ORM_EXCEPTION ) ) {
			service.announce( ORMKeys.EVENT_ORM_EXCEPTION, () -> Struct.of(
			    ORMKeys.error, error,
			    ORMKeys.sql, sql,
			    ORMKeys.datasource, origin.datasource().getOriginalValue(),
			    ORMKeys.appName, origin.appName()
			) );
		}
	}

	private static Throwable unwrapCause( InvocationTargetException e ) {
		return e.getCause() != null ? e.getCause() : e;
	}

	private static Object invoke( Object target, Method method, Object[] args ) throws Throwable {
		try {
			return method.invoke( target, args );
		} catch ( InvocationTargetException e ) {
			throw unwrapCause( e );
		}
	}

	/**
	 * Wraps connections so created statements are observed.
	 */
	private static class ConnectionHandler implements InvocationHandler {

		private final Connection	delegate;
		private final Origin		origin;

		ConnectionHandler( Connection delegate, Origin origin ) {
			this.delegate	= delegate;
			this.origin		= origin;
		}

		@Override
		public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
			String	name	= method.getName();
			boolean	prepare	= name.equals( "prepareStatement" ) || name.equals( "prepareCall" );
			Object	result;
			long	start	= System.nanoTime();
			try {
				result = ORMObserver.invoke( delegate, method, args );
			} catch ( Throwable t ) {
				if ( prepare ) {
					// Invalid SQL is usually rejected when the statement is prepared, before it can be executed
					announceQuery( origin, args != null && args.length > 0 && args[ 0 ] instanceof String s ? s : null, System.nanoTime() - start, -1, t,
					    null );
				}
				throw t;
			}
			if ( result instanceof Statement statement
			    && ( name.equals( "prepareStatement" ) || name.equals( "prepareCall" ) || name.equals( "createStatement" ) ) ) {
				String sql = args != null && args.length > 0 && args[ 0 ] instanceof String s ? s : null;
				return Proxy.newProxyInstance(
				    ORMObserver.class.getClassLoader(),
				    new Class<?>[] { method.getReturnType() },
				    new StatementHandler( statement, sql, origin )
				);
			}
			return result;
		}
	}

	/**
	 * Times statement execution and counts rows.
	 */
	private static class StatementHandler implements InvocationHandler {

		private final Statement					delegate;
		private final String					preparedSql;
		private final Origin					origin;
		private final TreeMap<Integer, Object>	params	= new TreeMap<>();
		private final List<List<Object>>		batches	= new ArrayList<>();
		private ResultSetHandler				openResultSet;

		StatementHandler( Statement delegate, String preparedSql, Origin origin ) {
			this.delegate		= delegate;
			this.preparedSql	= preparedSql;
			this.origin			= origin;
		}

		private List<Object> snapshot() {
			return origin.captureParams() ? new ArrayList<>( params.values() ) : null;
		}

		@Override
		public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
			String name = method.getName();

			if ( origin.captureParams() ) {
				if ( PARAM_SETTERS.contains( name ) && args != null && args.length >= 2 && args[ 0 ] instanceof Integer index ) {
					Object value = args[ 1 ];
					params.put( index, value instanceof java.io.InputStream || value instanceof java.io.Reader ? "[stream]" : value );
				} else if ( name.equals( "clearParameters" ) ) {
					params.clear();
				}
			}

			if ( name.equals( "addBatch" ) && ( args == null || args.length == 0 ) && origin.captureParams() ) {
				batches.add( snapshot() );
			}

			if ( name.equals( "close" ) && openResultSet != null ) {
				openResultSet.finish();
			}

			if ( !EXECUTORS.contains( name ) ) {
				return ORMObserver.invoke( delegate, method, args );
			}

			String	sql		= args != null && args.length > 0 && args[ 0 ] instanceof String s ? s : preparedSql;
			long	start	= System.nanoTime();
			Object	result;
			try {
				result = ORMObserver.invoke( delegate, method, args );
			} catch ( Throwable t ) {
				announceQuery( origin, sql, System.nanoTime() - start, -1, t, snapshot() );
				throw t;
			}
			long elapsed = System.nanoTime() - start;

			switch ( name ) {
				case "executeQuery" -> {
					openResultSet = new ResultSetHandler( ( ResultSet ) result, this, sql, elapsed, snapshot() );
					return Proxy.newProxyInstance( ORMObserver.class.getClassLoader(), new Class<?>[] { ResultSet.class }, openResultSet );
				}
				case "executeUpdate", "executeLargeUpdate" -> announceQuery( origin, sql, elapsed, ( ( Number ) result ).longValue(), null, snapshot() );
				case "executeBatch", "executeLargeBatch" -> {
					long	total	= 0;
					int		size	= 0;
					if ( result instanceof int[] counts ) {
						size = counts.length;
						for ( int c : counts ) {
							total += Math.max( c, 0 );
						}
					} else if ( result instanceof long[] counts ) {
						size = counts.length;
						for ( long c : counts ) {
							total += Math.max( c, 0 );
						}
					}
					final long	rows	= total;
					final int	entries	= size;
					announceQuery( origin, sql, elapsed, rows, null, batches.isEmpty() ? snapshot() : batches.get( batches.size() - 1 ) );
					FlushTally tally = FLUSH.get();
					if ( tally != null && entries > 1 ) {
						// announceQuery counted one; account for the rest of the batch
						tally.count( classify( sql ), entries - 1 );
					}
					batches.clear();
				}
				default -> {
					// Statement.execute(): row count is only cheap to know for non-result-set statements
					boolean	hasResultSet	= result instanceof Boolean b && b;
					long	rows			= -1;
					if ( !hasResultSet ) {
						try {
							rows = delegate.getUpdateCount();
						} catch ( SQLException ignored ) {
							// unknown
						}
					}
					announceQuery( origin, sql, elapsed, rows, null, snapshot() );
				}
			}
			return result;
		}
	}

	/**
	 * Counts rows as they are read and announces the select once the result set is closed.
	 */
	private static class ResultSetHandler implements InvocationHandler {

		private final ResultSet			delegate;
		private final StatementHandler	owner;
		private final String			sql;
		private final long				elapsed;
		private final List<Object>		params;
		private long					rows		= 0;
		private boolean					announced	= false;

		ResultSetHandler( ResultSet delegate, StatementHandler owner, String sql, long elapsed, List<Object> params ) {
			this.delegate	= delegate;
			this.owner		= owner;
			this.sql		= sql;
			this.elapsed	= elapsed;
			this.params		= params;
		}

		void finish() {
			if ( !announced ) {
				announced = true;
				announceQuery( owner.origin, sql, elapsed, rows, null, params );
			}
			if ( owner.openResultSet == this ) {
				owner.openResultSet = null;
			}
		}

		@Override
		public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
			if ( method.getName().equals( "close" ) ) {
				try {
					return ORMObserver.invoke( delegate, method, args );
				} finally {
					finish();
				}
			}
			Object result = ORMObserver.invoke( delegate, method, args );
			if ( method.getName().equals( "next" ) && Boolean.TRUE.equals( result ) ) {
				rows++;
			}
			return result;
		}
	}
}
