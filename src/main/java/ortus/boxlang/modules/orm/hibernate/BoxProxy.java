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
import org.hibernate.mapping.PersistentClass;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import ortus.boxlang.runtime.types.Struct;

/**
 * Boxlang class proxy.
 */
public class BoxProxy extends Struct implements HibernateProxy {

	private BoxLazyInitializer lazyInitializer;

	/**
	 * Constructor.
	 * 
	 * @param entityName
	 * @param id
	 * @param session
	 */
	public BoxProxy( String entityName, Serializable id, SharedSessionContractImplementor session, PersistentClass mappingInfo ) {
		this.lazyInitializer = new BoxLazyInitializer( entityName, id, session, mappingInfo );
	}

	/**
	 * Perform serialization-time write-replacement of this proxy.
	 *
	 * @return The serializable proxy replacement.
	 */
	@Override
	public Object writeReplace() {
		return this;
	}

	/**
	 * Get the underlying lazy initialization handler.
	 *
	 * @return The lazy initializer.
	 */
	@Override
	public LazyInitializer getHibernateLazyInitializer() {
		return this.lazyInitializer;
	}

}
