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
package ortus.boxlang.modules.orm.hibernate.facade;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.hibernate.Cache;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * Facade-aware wrappers for the raw Hibernate {@link Session} and {@link SessionFactory} handed to BoxLang developers by
 * {@code ormGetSession()} / {@code ormGetSessionFactory()}.
 * <p>
 * In facade (POJO) mode Hibernate manages a generated facade class, and the modern {@code mapping.xml} format makes the
 * Hibernate entity-name the facade's fully-qualified class name (the BoxLang name is only the JPA/HQL import). The ORM BIFs
 * translate at that boundary, but code that reaches <em>through</em> to the raw Session/SessionFactory (for example
 * {@code ormGetSession().detach(entity)}, {@code getCache().containsEntity("User")},
 * {@code getMappingMetamodel().getEntityDescriptor("User")}) would otherwise fail with {@code Unknown entity type} or
 * {@code Non-entity object instance passed to ...}.
 * <p>
 * Each wrapper is a JDK dynamic proxy over the target's full interface set (so casts to Hibernate SPI types such as
 * {@code SessionFactoryImplementor} still work). Per call it (1) rewrites any {@code String} argument that is a registered
 * BoxLang entity name to that entity's Hibernate entity-name (non-entity strings - HQL, property names - are left alone),
 * (2) wraps any {@code IClassRunnable} argument to its managed facade, and (3) unwraps facade return values back to the
 * BoxLang instance and re-wraps returned Session/SessionFactory/Cache/Metamodel objects so chained calls stay translated.
 * <p>
 * Returned {@code Query}/{@code SelectionQuery}/{@code NativeQuery} objects (from HQL/JPQL, named queries, native queries,
 * and criteria queries built via {@code createQuery(CriteriaQuery)}) are wrapped too, so their terminal results -
 * {@code list()} / {@code getResultList()} / {@code getSingleResult()} / {@code uniqueResult()} /
 * {@code uniqueResultOptional()} / {@code getResultStream()} - come back as {@code IClassRunnable}s rather than generated
 * facades, and any {@code IClassRunnable} bound via {@code setParameter} is wrapped to its facade. Facades held in a
 * returned collection / optional / stream are unwrapped element-by-element.
 *
 * @since 1.5.0
 */
public final class FacadeAwareHibernate {

	/**
	 * Cache of target class -> the full interface set the proxy must implement. The interface set of a class is invariant,
	 * and {@code wrap()} runs again for every Session/SessionFactory/Query returned through a call chain, so the reflective
	 * hierarchy walk is done once per class and reused thereafter.
	 */
	private static final Map<Class<?>, Class<?>[]> INTERFACE_CACHE = new ConcurrentHashMap<>();

	private FacadeAwareHibernate() {
	}

	/**
	 * Wrap a Hibernate {@link Session} so BoxLang entity names and instances work against its raw API in facade mode.
	 *
	 * @param session The real Hibernate session (may be {@code null}).
	 *
	 * @return A facade-aware proxy, or the original session if it is {@code null} or cannot be proxied.
	 */
	public static Session wrap( Session session ) {
		if ( session == null ) {
			return session;
		}
		Object proxy = proxy( session, resolver( ( SessionFactoryImplementor ) session.getSessionFactory() ) );
		return proxy instanceof Session s ? s : session;
	}

	/**
	 * Wrap a Hibernate {@link SessionFactory} so BoxLang entity names work against its raw API (and its {@code getCache()} /
	 * {@code getMetamodel()} / {@code getMappingMetamodel()}) in facade mode.
	 *
	 * @param factory The real Hibernate session factory (may be {@code null}).
	 *
	 * @return A facade-aware proxy, or the original factory if it is {@code null} or cannot be proxied.
	 */
	public static SessionFactory wrap( SessionFactory factory ) {
		if ( factory == null ) {
			return factory;
		}
		Object proxy = proxy( factory, resolver( ( SessionFactoryImplementor ) factory ) );
		return proxy instanceof SessionFactory f ? f : factory;
	}

	/**
	 * Build a BoxLang-name -> Hibernate entity-name resolver from a session factory's imported (JPA/HQL) names. A name that
	 * is not a registered import is returned unchanged, so non-entity strings (HQL text, property names) pass through.
	 */
	private static UnaryOperator<String> resolver( SessionFactoryImplementor factory ) {
		return name -> {
			if ( name == null ) {
				return null;
			}
			try {
				String imported = factory.getMappingMetamodel().getImportedName( name );
				return ( imported != null && !imported.equals( name ) ) ? imported : name;
			} catch ( RuntimeException e ) {
				return name;
			}
		};
	}

	/**
	 * Create a dynamic proxy over the target's full interface set, or return the raw target if it cannot be proxied.
	 */
	private static Object proxy( Object target, UnaryOperator<String> resolver ) {
		try {
			Class<?>[] interfaces = INTERFACE_CACHE.computeIfAbsent( target.getClass(), FacadeAwareHibernate::allInterfaces );
			if ( interfaces.length == 0 ) {
				return target;
			}
			return Proxy.newProxyInstance( target.getClass().getClassLoader(), interfaces, new Handler( target, resolver ) );
		} catch ( RuntimeException e ) {
			// Never let wrapping break ormGetSession()/ormGetSessionFactory(); fall back to the raw object.
			return target;
		}
	}

	/**
	 * Collect every interface implemented by a class and its superclasses (including super-interfaces), so the proxy is
	 * assignable to the same types as the target (e.g. {@code SessionFactoryImplementor}, not just {@code SessionFactory}).
	 */
	private static Class<?>[] allInterfaces( Class<?> type ) {
		Set<Class<?>> interfaces = new LinkedHashSet<>();
		for ( Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass() ) {
			collectInterfaces( c, interfaces );
		}
		return interfaces.toArray( new Class<?>[ 0 ] );
	}

	private static void collectInterfaces( Class<?> type, Set<Class<?>> collected ) {
		for ( Class<?> iface : type.getInterfaces() ) {
			if ( collected.add( iface ) ) {
				collectInterfaces( iface, collected );
			}
		}
	}

	/**
	 * The invocation handler that performs the argument/return translation described on {@link FacadeAwareHibernate}.
	 */
	private static final class Handler implements InvocationHandler {

		private final Object				target;
		private final UnaryOperator<String>	resolver;

		private Handler( Object target, UnaryOperator<String> resolver ) {
			this.target		= target;
			this.resolver	= resolver;
		}

		@Override
		public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
			// Most proxied calls carry no entity-name String and no IClassRunnable, so nothing is rewritten. Only clone the
			// argument array when a translation actually changes an element, otherwise pass the original array through.
			Object[] translated = args;
			if ( args != null ) {
				for ( int i = 0; i < args.length; i++ ) {
					Object rewritten = translateArgument( args[ i ] );
					if ( rewritten != args[ i ] ) {
						if ( translated == args ) {
							translated = args.clone();
						}
						translated[ i ] = rewritten;
					}
				}
			}
			Object result;
			try {
				result = method.invoke( target, translated );
			} catch ( InvocationTargetException e ) {
				throw e.getCause();
			}
			return translateResult( result );
		}

		/**
		 * Rewrite a BoxLang entity name to its Hibernate entity-name, wrap an {@link IClassRunnable} to its facade, else
		 * return the argument unchanged.
		 */
		private Object translateArgument( Object arg ) {
			if ( arg instanceof String name ) {
				return resolver.apply( name );
			}
			if ( arg instanceof IClassRunnable instance ) {
				try {
					return FacadeSupport.wrapInstance( instance );
				} catch ( RuntimeException e ) {
					// Not an ORM-managed instance we can wrap; let Hibernate handle (or reject) it as before.
					return instance;
				}
			}
			return arg;
		}

		/**
		 * Unwrap a facade result to its BoxLang instance, and re-wrap a returned Session/SessionFactory/Cache/Metamodel so
		 * chained calls keep translating.
		 */
		private Object translateResult( Object result ) {
			if ( result == null ) {
				return null;
			}
			// A managed facade -> the backing BoxLang instance.
			Object unwrapped = FacadeSupport.unwrapIfFacade( result );
			if ( unwrapped != result ) {
				return unwrapped;
			}
			// Chainable Hibernate objects: re-wrap so their calls keep translating.
			if ( result instanceof Session session ) {
				return wrap( session );
			}
			if ( result instanceof SessionFactory factory ) {
				return wrap( factory );
			}
			// Query objects (HQL/JPQL, named, native, and criteria queries built via createQuery(CriteriaQuery)) yield facades
			// from list()/getResultList()/getSingleResult()/uniqueResult()/getResultStream(). Wrapping the query routes those
			// terminal results back through this translator (so callers only ever see IClassRunnables) and wraps any
			// IClassRunnable bound via setParameter to its facade. CommonQueryContract covers Query/SelectionQuery/
			// MutationQuery/NativeQuery; jakarta types cover TypedQuery and stored-procedure queries.
			if ( result instanceof org.hibernate.query.CommonQueryContract || result instanceof jakarta.persistence.Query
			    || result instanceof jakarta.persistence.StoredProcedureQuery ) {
				return proxy( result, resolver );
			}
			if ( result instanceof Cache || result instanceof jakarta.persistence.metamodel.Metamodel
			    || result instanceof org.hibernate.metamodel.MappingMetamodel ) {
				return proxy( result, resolver );
			}
			// Query result shapes: unwrap facades held in a returned collection / optional / stream.
			if ( result instanceof java.util.Collection<?> collection ) {
				return unwrapCollection( collection );
			}
			if ( result instanceof java.util.Optional<?> optional ) {
				return optional.map( FacadeSupport::unwrapIfFacade );
			}
			if ( result instanceof java.util.stream.Stream<?> stream ) {
				return stream.map( FacadeSupport::unwrapIfFacade );
			}
			return result;
		}

		/**
		 * Unwrap any managed facades held in a query-result collection back to their BoxLang instances. Returns the original
		 * collection untouched when it holds no facades, so scalar/projection results (and their identity) are preserved.
		 */
		private Object unwrapCollection( java.util.Collection<?> collection ) {
			boolean hasFacade = false;
			for ( Object element : collection ) {
				if ( FacadeSupport.unwrapIfFacade( element ) != element ) {
					hasFacade = true;
					break;
				}
			}
			if ( !hasFacade ) {
				return collection;
			}
			java.util.Collection<Object> unwrapped = ( collection instanceof java.util.Set )
			    ? new java.util.LinkedHashSet<>()
			    : new java.util.ArrayList<>( collection.size() );
			for ( Object element : collection ) {
				unwrapped.add( FacadeSupport.unwrapIfFacade( element ) );
			}
			return unwrapped;
		}
	}
}
