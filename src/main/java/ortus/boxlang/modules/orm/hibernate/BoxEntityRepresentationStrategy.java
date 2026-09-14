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
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.type.descriptor.java.JavaType;

import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * Hibernate {@link EntityRepresentationStrategy} that bridges Hibernate's runtime model with BoxLang entity classes.
 * <p>
 * This is the Hibernate 6+/7+ replacement for the Hibernate 5 tuplizer. Hibernate asks the strategy for everything it
 * needs to instantiate, proxy, and read/write a mapped entity, and this implementation hands back BoxLang-aware
 * components: {@link BoxClassInstantiator}, {@link BoxProxyFactory}, {@link BoxPropertyAccess} and
 * {@link BoxEntityNameResolver}.
 *
 * @since 2.0.0
 */
public class BoxEntityRepresentationStrategy implements EntityRepresentationStrategy {

	private final PersistentClass		bootDescriptor;
	private final JavaType<?>			mappedJavaType;
	private final JavaType<?>			proxyJavaType;
	private final BoxClassInstantiator	instantiator;
	private final ProxyFactory			proxyFactory;

	public BoxEntityRepresentationStrategy( PersistentClass bootDescriptor, EntityPersister runtimeDescriptor,
	    RuntimeModelCreationContext creationContext, EntityRecord entityRecord ) {
		this.bootDescriptor	= bootDescriptor;
		this.mappedJavaType	= creationContext.getTypeConfiguration().getJavaTypeRegistry().resolveEntityTypeDescriptor( IClassRunnable.class );
		this.proxyJavaType	= creationContext.getTypeConfiguration().getJavaTypeRegistry().resolveDescriptor( BoxProxy.class );
		this.instantiator	= new BoxClassInstantiator( bootDescriptor, entityRecord );
		this.proxyFactory	= runtimeDescriptor.isLazy() ? new BoxProxyFactory( bootDescriptor ) : null;
	}

	@Override
	public RepresentationMode getMode() {
		return RepresentationMode.MAP;
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
		return new BoxPropertyAccess( bootAttributeDescriptor, bootDescriptor );
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
