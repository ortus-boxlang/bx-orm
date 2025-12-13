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

import java.io.Serializable;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.proxy.AbstractLazyInitializer;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.runnables.IBoxRunnable;
import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * Lazy initializer for BoxLang entities, which allows for lazy loading of entities
 * in a way that is compatible with the BoxLang runtime and ORM system.
 *
 * @since 1.0.0
 */
public class BoxLazyInitializer extends AbstractLazyInitializer implements Serializable {

	private final Serializable	id;
	private final String		entityName;

	public BoxLazyInitializer( String entityName, Serializable id, SharedSessionContractImplementor session ) {
		super( entityName, id, session );
		this.id			= id;
		this.entityName	= entityName;
	}

	public IBoxRunnable getEntity() {
		IBoxContext	context	= RequestBoxContext.getCurrent();
		ORMApp		ormApp	= ORMContext.getForContext( context ).getORMApp();
		return ormApp.loadEntityById( context, this.entityName, this.id );
	}

	@SuppressWarnings( "rawtypes" )
	@Override
	public Class getPersistentClass() {
		return BoxProxy.class;
	}

	/**
	 * Convenience method to get the actual instantiated target
	 *
	 * @return
	 */
	public IClassRunnable getInstantiatedEntity() {
		initializeWithoutLoadIfPossible();
		return ( IClassRunnable ) getImplementation();
	}

}
