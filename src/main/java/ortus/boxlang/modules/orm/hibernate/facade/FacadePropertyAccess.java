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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.property.access.spi.Setter;

import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * Facade-mode {@link PropertyAccess} that wraps Hibernate's standard reflection-based access and coerces the target to a
 * generated facade before every read/write.
 * <p>
 * Hibernate manages the generated facade, whose reflective getters/setters delegate to the backing BoxLang instance.
 * Most of the time Hibernate holds the facade, but some internal paths - association FK extraction,
 * {@code isTransient}/{@code getIdentifier} on an association target resolved from the persistence context - can hand
 * the reflective accessor the raw {@link IClassRunnable} instead. A reflective getter/setter bound to the facade class
 * throws {@link IllegalArgumentException} on a raw {@code IClassRunnable} owner. This wrapper coerces any raw
 * {@code IClassRunnable} back to its (memoized) facade first, so id and property access succeeds regardless of which
 * representation Hibernate happens to be holding. A value that is already a facade (or anything else) passes through
 * unchanged, so the normal facade path is unaffected.
 *
 * @since 2.0.0
 */
public class FacadePropertyAccess implements PropertyAccess {

	private final PropertyAccess	delegate;
	private final Getter			getter;
	private final Setter			setter;

	/**
	 * @param delegate The reflection-based property access to wrap (built against the generated facade class).
	 */
	public FacadePropertyAccess( PropertyAccess delegate ) {
		this.delegate	= delegate;
		this.getter		= new FacadeGetter( delegate.getGetter() );
		this.setter		= new FacadeSetter( delegate.getSetter() );
	}

	/**
	 * Coerce a raw BoxLang instance to its facade so the wrapped reflective accessor (bound to the facade class) can run;
	 * a facade or any other value passes through unchanged.
	 *
	 * @param target The owner Hibernate handed us.
	 *
	 * @return The facade to run the reflective accessor against.
	 */
	private static Object coerce( Object target ) {
		if ( target instanceof IClassRunnable runnable && ! ( target instanceof BoxEntityFacade ) ) {
			return FacadeSupport.wrapInstance( runnable );
		}
		return target;
	}

	@Override
	public PropertyAccessStrategy getPropertyAccessStrategy() {
		return delegate.getPropertyAccessStrategy();
	}

	@Override
	public Getter getGetter() {
		return getter;
	}

	@Override
	public Setter getSetter() {
		return setter;
	}

	/**
	 * A {@link Getter} that coerces the owner to its facade before delegating to the reflective getter.
	 */
	private static final class FacadeGetter implements Getter {

		private final Getter delegate;

		private FacadeGetter( Getter delegate ) {
			this.delegate = delegate;
		}

		@Override
		public Object get( Object owner ) {
			return delegate.get( coerce( owner ) );
		}

		@Override
		public Object getForInsert( Object owner, Map<Object, Object> mergeMap, SharedSessionContractImplementor session ) {
			return delegate.getForInsert( coerce( owner ), mergeMap, session );
		}

		@Override
		public Class<?> getReturnTypeClass() {
			return delegate.getReturnTypeClass();
		}

		@Override
		public Type getReturnType() {
			return delegate.getReturnType();
		}

		@Override
		public Member getMember() {
			return delegate.getMember();
		}

		@Override
		public String getMethodName() {
			return delegate.getMethodName();
		}

		@Override
		public Method getMethod() {
			return delegate.getMethod();
		}
	}

	/**
	 * A {@link Setter} that coerces the target to its facade before delegating to the reflective setter.
	 */
	private static final class FacadeSetter implements Setter {

		private final Setter delegate;

		private FacadeSetter( Setter delegate ) {
			this.delegate = delegate;
		}

		@Override
		public void set( Object target, Object value ) {
			delegate.set( coerce( target ), value );
		}

		@Override
		public String getMethodName() {
			return delegate.getMethodName();
		}

		@Override
		public Method getMethod() {
			return delegate.getMethod();
		}
	}
}
