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
import java.util.Set;
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
 * {@code Non-entity object instance passed to ...}. In MAP mode the entity-name IS the BoxLang name and the managed object IS
 * the {@code IClassRunnable}, so no translation is needed (this class is only used when {@code entityFacades} is enabled).
 * <p>
 * Each wrapper is a JDK dynamic proxy over the target's full interface set (so casts to Hibernate SPI types such as
 * {@code SessionFactoryImplementor} still work). Per call it (1) rewrites any {@code String} argument that is a registered
 * BoxLang entity name to that entity's Hibernate entity-name (non-entity strings - HQL, property names - are left alone),
 * (2) wraps any {@code IClassRunnable} argument to its managed facade, and (3) unwraps facade return values back to the
 * BoxLang instance and re-wraps returned Session/SessionFactory/Cache/Metamodel objects so chained calls stay translated.
 *
 * @since 1.5.0
 */
public final class FacadeAwareHibernate {

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
			Class<?>[] interfaces = allInterfaces( target.getClass() );
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
			Object[] translated = args;
			if ( args != null ) {
				translated = new Object[ args.length ];
				for ( int i = 0; i < args.length; i++ ) {
					translated[ i ] = translateArgument( args[ i ] );
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
			Object unwrapped = FacadeSupport.unwrapIfFacade( result );
			if ( unwrapped != result ) {
				return unwrapped;
			}
			if ( result instanceof Session session ) {
				return wrap( session );
			}
			if ( result instanceof SessionFactory factory ) {
				return wrap( factory );
			}
			if ( result instanceof Cache || result instanceof jakarta.persistence.metamodel.Metamodel
			    || result instanceof org.hibernate.metamodel.MappingMetamodel ) {
				return proxy( result, resolver );
			}
			return result;
		}
	}
}
