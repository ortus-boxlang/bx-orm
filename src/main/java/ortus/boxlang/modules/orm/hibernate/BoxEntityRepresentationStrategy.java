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

import java.util.function.Consumer;

import org.hibernate.EntityNameResolver;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.metamodel.spi.EntityInstantiator;
import org.hibernate.metamodel.spi.EntityRepresentationStrategy;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.type.descriptor.java.JavaType;

import ortus.boxlang.modules.orm.hibernate.facade.FacadePropertyAccess;
import ortus.boxlang.modules.orm.mapping.EntityRecord;

/**
 * Hibernate {@link EntityRepresentationStrategy} that bridges Hibernate's runtime model with BoxLang entity classes.
 * <p>
 * This is the Hibernate 6+/7+ replacement for the Hibernate 5 tuplizer. Hibernate asks the strategy for everything it
 * needs to instantiate, proxy, and read/write a mapped entity, and this implementation hands back BoxLang-aware
 * components: {@link BoxClassInstantiator}, {@link BoxProxyFactory},
 * {@link ortus.boxlang.modules.orm.hibernate.facade.FacadePropertyAccess} and {@link BoxEntityNameResolver}.
 *
 * @since 2.0.0
 */
public class BoxEntityRepresentationStrategy implements EntityRepresentationStrategy {

	private final JavaType<?>			mappedJavaType;
	private final JavaType<?>			proxyJavaType;
	private final EntityInstantiator	instantiator;
	private final ProxyFactory			proxyFactory;

	/**
	 * The generated facade class Hibernate maps as the entity.
	 */
	private final Class<?>				facadeClass;

	public BoxEntityRepresentationStrategy( PersistentClass bootDescriptor, EntityPersister runtimeDescriptor,
	    RuntimeModelCreationContext creationContext, EntityRecord entityRecord ) {
		// Hibernate manages the real generated facade class named in the mapping XML (<entity class=...>), whose typed
		// accessors delegate to the BoxLang instance. Property access is Hibernate's standard reflection access (the facade
		// has real getters/setters), wrapped by FacadePropertyAccess.
		this.facadeClass	= bootDescriptor.getMappedClass();
		this.mappedJavaType	= creationContext.getTypeConfiguration().getJavaTypeRegistry().resolveEntityTypeDescriptor( this.facadeClass );
		this.instantiator	= new BoxFacadeInstantiator( new BoxClassInstantiator( bootDescriptor, entityRecord ), this.facadeClass );
		// Lazy proxying: the proxy is a BoxProxy (an IClassRunnable the developer can navigate), and its loaded
		// implementation - a facade - is unwrapped back to the BoxLang instance by BoxLazyInitializer. Hibernate reads the
		// id/entity-name straight off the proxy without forcing initialization, so a lazy to-one hands the developer a real
		// (lazy) BoxLang instance, never a facade.
		this.proxyJavaType	= creationContext.getTypeConfiguration().getJavaTypeRegistry().resolveDescriptor( BoxProxy.class );
		this.proxyFactory	= runtimeDescriptor.isLazy() ? new BoxProxyFactory( bootDescriptor ) : null;
	}

	@Override
	public RepresentationMode getMode() {
		return RepresentationMode.POJO;
	}

	@Override
	public ReflectionOptimizer getReflectionOptimizer() {
		return null;
	}

	@Override
	public JavaType<?> getMappedJavaType() {
		return mappedJavaType;
	}

	@Override
	public JavaType<?> getProxyJavaType() {
		return proxyJavaType;
	}

	@Override
	public PropertyAccess resolvePropertyAccess( Property bootAttributeDescriptor ) {
		// The facade exposes real getX/setX accessors, so use Hibernate's standard reflection-based property access,
		// wrapped so it also tolerates a raw IClassRunnable owner. Hibernate can resolve an association value (or a
		// persistence-context entity) to the backing BoxLang instance rather than its facade; coercing the owner back
		// to its (memoized) facade before the reflective accessor runs keeps id/property access working in every path.
		return new FacadePropertyAccess(
		    PropertyAccessStrategyBasicImpl.INSTANCE.buildPropertyAccess( facadeClass, bootAttributeDescriptor.getName(), true ) );
	}

	@Override
	public EntityInstantiator getInstantiator() {
		return instantiator;
	}

	@Override
	public ProxyFactory getProxyFactory() {
		return proxyFactory;
	}

	@Override
	public void visitEntityNameResolvers( Consumer<EntityNameResolver> consumer ) {
		consumer.accept( new BoxEntityNameResolver() );
	}

}
