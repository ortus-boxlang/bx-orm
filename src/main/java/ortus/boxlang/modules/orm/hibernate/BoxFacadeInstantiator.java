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
package ortus.boxlang.modules.orm.hibernate;

import java.lang.reflect.Constructor;

import org.hibernate.metamodel.spi.EntityInstantiator;

import ortus.boxlang.modules.orm.hibernate.facade.BoxEntityFacade;
import ortus.boxlang.modules.orm.hibernate.facade.BoxEntityState;
import ortus.boxlang.modules.orm.hibernate.facade.BoxIClassRunnableState;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * {@link EntityInstantiator} used in facade (POJO) representation mode.
 * <p>
 * Hibernate manages a generated POJO facade as the entity, but the real state lives in a BoxLang instance. This
 * instantiator delegates the actual BoxLang class creation (including association helper-method injection) to the
 * standard {@link BoxClassInstantiator}, then wraps the resulting {@link IClassRunnable} in a fresh facade whose
 * backing state points straight at it. Hibernate therefore gets a real class instance while the BoxLang instance
 * remains the single source of truth for every mapped property.
 *
 * @since 2.0.0
 */
public class BoxFacadeInstantiator implements EntityInstantiator {

	private final BoxClassInstantiator	delegate;
	private final Class<?>				facadeClass;
	private final Constructor<?>		facadeConstructor;
	private final String				namespace;

	/**
	 * @param delegate    The standard BoxLang instantiator that builds the underlying {@link IClassRunnable}.
	 * @param facadeClass The generated facade class Hibernate maps as the entity.
	 */
	public BoxFacadeInstantiator( BoxClassInstantiator delegate, Class<?> facadeClass ) {
		this.delegate		= delegate;
		this.facadeClass	= facadeClass;
		this.namespace		= ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming.namespaceOf( facadeClass.getName() );
		try {
			this.facadeConstructor = facadeClass.getConstructor( BoxEntityState.class );
		} catch ( NoSuchMethodException e ) {
			throw new BoxRuntimeException( "Generated facade [" + facadeClass.getName() + "] is missing its BoxEntityState constructor", e );
		}
	}

	@Override
	public Object instantiate() {
		IClassRunnable instance = ( IClassRunnable ) delegate.instantiate();
		try {
			Object facade = facadeConstructor.newInstance( new BoxIClassRunnableState( instance ) );
			// Memoize the facade on the instance so bx-orm's wrap/unwrap layer reuses this exact pairing, and stamp the
			// owning application's namespace so a later wrapInstance() resolves the right facade class.
			FacadeSupport.memoize( instance, facade );
			FacadeSupport.stampNamespace( instance, namespace );
			return facade;
		} catch ( ReflectiveOperationException e ) {
			throw new BoxRuntimeException( "Unable to instantiate facade [" + facadeClass.getName() + "]", e );
		}
	}

	@Override
	public boolean isInstance( Object object ) {
		return delegate.isInstance( FacadeSupport.unwrapIfFacade( object ) );
	}

	@Override
	public boolean isSameClass( Object object ) {
		return delegate.isSameClass( FacadeSupport.unwrapIfFacade( object ) );
	}
}
