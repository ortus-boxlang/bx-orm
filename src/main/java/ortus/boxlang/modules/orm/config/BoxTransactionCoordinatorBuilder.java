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

import java.util.function.Supplier;

import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.jpa.spi.JpaCompliance;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.transaction.spi.IsolationDelegate;
import org.hibernate.resource.transaction.spi.SynchronizationRegistry;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorOwner;
import org.hibernate.resource.transaction.spi.TransactionObserver;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;

/**
 * Hibernate's default JDBC transaction coordinator, with one addition: while bx-orm runs a locking query inside a
 * BoxLang {@code transaction{}}, it reports a transaction as active.
 * <p>
 * bx-orm rides the BoxLang transaction's connection and never begins a Hibernate transaction (see
 * {@code ORMConnectionProvider}), so Hibernate believes no transaction is active and refuses pessimistic-lock queries
 * ("No active transaction"). {@link #lockScope(Supplier)} marks the few calls that run such a query, after bx-orm has
 * checked that a BoxLang transaction is active, so the query runs with Hibernate's own locking SQL. Everything else is
 * delegated unchanged to Hibernate's JDBC builder, looked up by its {@code jdbc} short name through the public
 * {@link StrategySelector} SPI.
 */
public final class BoxTransactionCoordinatorBuilder implements TransactionCoordinatorBuilder, ServiceRegistryAwareService {

	private static final long						serialVersionUID	= 1L;

	/** How many lock scopes are open on the current thread. */
	private static final ThreadLocal<int[]>			LOCK_SCOPE			= ThreadLocal.withInitial( () -> new int[ 1 ] );

	/** Hibernate's default JDBC builder, which does all the real work; set when the service registry starts. */
	private transient TransactionCoordinatorBuilder	delegate;

	/**
	 * Create a builder; each session factory gets its own (its service registry injects the delegate).
	 */
	public BoxTransactionCoordinatorBuilder() {
	}

	/**
	 * Look up Hibernate's default JDBC coordinator builder.
	 *
	 * @param serviceRegistry The session factory's service registry.
	 */
	@Override
	public void injectServices( ServiceRegistryImplementor serviceRegistry ) {
		this.delegate = serviceRegistry.requireService( StrategySelector.class ).resolveStrategy( TransactionCoordinatorBuilder.class, "jdbc" );
	}

	/**
	 * Run a locking query. Callers must first check that a BoxLang transaction is active.
	 *
	 * @param work The query run.
	 * @param <T>  Its result type.
	 *
	 * @return What the work returned.
	 */
	public static <T> T lockScope( Supplier<T> work ) {
		int[] depth = LOCK_SCOPE.get();
		depth[ 0 ]++;
		try {
			return work.get();
		} finally {
			depth[ 0 ]--;
		}
	}

	/**
	 * Whether a lock scope is open on the current thread.
	 *
	 * @return True inside {@link #lockScope(Supplier)}.
	 */
	static boolean inLockScope() {
		return LOCK_SCOPE.get()[ 0 ] > 0;
	}

	/**
	 * Build the coordinator for a session: the default one, wrapped.
	 *
	 * @param owner   The session's coordinator owner.
	 * @param options The coordinator options.
	 *
	 * @return The coordinator.
	 */
	@Override
	public TransactionCoordinator buildTransactionCoordinator( TransactionCoordinatorOwner owner, Options options ) {
		return new Coordinator( this, delegate.buildTransactionCoordinator( owner, options ) );
	}

	/**
	 * Whether this is a JTA coordinator: no.
	 *
	 * @return False.
	 */
	@Override
	public boolean isJta() {
		return delegate.isJta();
	}

	/**
	 * The default connection handling of the JDBC coordinator.
	 *
	 * @return The connection handling mode.
	 */
	@Override
	public PhysicalConnectionHandlingMode getDefaultConnectionHandlingMode() {
		return delegate.getDefaultConnectionHandlingMode();
	}

	/**
	 * The default JDBC coordinator, reporting an active transaction inside a lock scope.
	 *
	 * @param builder  The bx-orm builder that made it.
	 * @param delegate The default coordinator.
	 */
	private record Coordinator( BoxTransactionCoordinatorBuilder builder, TransactionCoordinator delegate ) implements TransactionCoordinator {

		/**
		 * The builder that made this coordinator.
		 *
		 * @return The bx-orm builder.
		 */
		@Override
		public TransactionCoordinatorBuilder getTransactionCoordinatorBuilder() {
			return builder;
		}

		/**
		 * The transaction driver.
		 *
		 * @return The delegate's driver.
		 */
		@Override
		public TransactionDriver getTransactionDriverControl() {
			return delegate.getTransactionDriverControl();
		}

		/**
		 * The local synchronizations.
		 *
		 * @return The delegate's registry.
		 */
		@Override
		public SynchronizationRegistry getLocalSynchronizations() {
			return delegate.getLocalSynchronizations();
		}

		/**
		 * The JPA compliance settings.
		 *
		 * @return The delegate's settings.
		 */
		@Override
		public JpaCompliance getJpaCompliance() {
			return delegate.getJpaCompliance();
		}

		/**
		 * Join the transaction explicitly.
		 */
		@Override
		public void explicitJoin() {
			delegate.explicitJoin();
		}

		/**
		 * Whether the coordinator is joined to a transaction.
		 *
		 * @return The delegate's answer.
		 */
		@Override
		public boolean isJoined() {
			return delegate.isJoined();
		}

		/**
		 * Pulse the coordinator.
		 */
		@Override
		public void pulse() {
			delegate.pulse();
		}

		/**
		 * Whether the coordinator's owner is active.
		 *
		 * @return The delegate's answer.
		 */
		@Override
		public boolean isActive() {
			return delegate.isActive();
		}

		/**
		 * The isolation delegate.
		 *
		 * @return The delegate's isolation delegate.
		 */
		@Override
		public IsolationDelegate createIsolationDelegate() {
			return delegate.createIsolationDelegate();
		}

		/**
		 * Add a transaction observer.
		 *
		 * @param observer The observer.
		 */
		@Override
		public void addObserver( TransactionObserver observer ) {
			delegate.addObserver( observer );
		}

		/**
		 * Remove a transaction observer.
		 *
		 * @param observer The observer.
		 */
		@Override
		public void removeObserver( TransactionObserver observer ) {
			delegate.removeObserver( observer );
		}

		/**
		 * Set the transaction timeout.
		 *
		 * @param seconds The timeout.
		 */
		@Override
		public void setTimeOut( int seconds ) {
			delegate.setTimeOut( seconds );
		}

		/**
		 * The transaction timeout.
		 *
		 * @return The delegate's timeout.
		 */
		@Override
		public int getTimeOut() {
			return delegate.getTimeOut();
		}

		/**
		 * Whether a transaction is active: a Hibernate one, or a BoxLang one while a locking query runs.
		 *
		 * @return True when active.
		 */
		@Override
		public boolean isTransactionActive() {
			return inLockScope() || delegate.isTransactionActive();
		}

		/**
		 * Invalidate the coordinator.
		 */
		@Override
		public void invalidate() {
			delegate.invalidate();
		}
	}
}
