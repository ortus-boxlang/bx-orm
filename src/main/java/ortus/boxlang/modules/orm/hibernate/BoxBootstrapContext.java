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

import java.util.Collection;
import java.util.Map;

import org.hibernate.boot.CacheRegionDefinition;
import org.hibernate.boot.archive.scan.spi.ScanEnvironment;
import org.hibernate.boot.archive.scan.spi.ScanOptions;
import org.hibernate.boot.archive.spi.ArchiveDescriptorFactory;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.relational.AuxiliaryDatabaseObject;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.ClassLoaderAccess;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.jpa.spi.MutableJpaCompliance;
import org.hibernate.metamodel.spi.ManagedTypeRepresentationResolver;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.resource.beans.spi.BeanInstanceProducer;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Delegating {@link BootstrapContext} whose only override is {@link #getRepresentationStrategySelector()}, returning
 * {@link BoxRepresentationResolver} so BoxLang classes are used as Hibernate entities.
 *
 * @since 2.0.0
 */
@SuppressWarnings( "deprecation" )
public class BoxBootstrapContext implements BootstrapContext {

	private final BootstrapContext					delegate;
	private final ManagedTypeRepresentationResolver	resolver;

	public BoxBootstrapContext( BootstrapContext delegate, ManagedTypeRepresentationResolver resolver ) {
		this.delegate	= delegate;
		this.resolver	= resolver;
	}

	@Override
	public ManagedTypeRepresentationResolver getRepresentationStrategySelector() {
		return resolver;
	}

	@Override
	public StandardServiceRegistry getServiceRegistry() {
		return delegate.getServiceRegistry();
	}

	@Override
	public MutableJpaCompliance getJpaCompliance() {
		return delegate.getJpaCompliance();
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return delegate.getTypeConfiguration();
	}

	@Override
	public ModelsContext getModelsContext() {
		return delegate.getModelsContext();
	}

	@Override
	public SqmFunctionRegistry getFunctionRegistry() {
		return delegate.getFunctionRegistry();
	}

	@Override
	public BeanInstanceProducer getCustomTypeProducer() {
		return delegate.getCustomTypeProducer();
	}

	@Override
	public MetadataBuildingOptions getMetadataBuildingOptions() {
		return delegate.getMetadataBuildingOptions();
	}

	@Override
	public ClassLoaderService getClassLoaderService() {
		return delegate.getClassLoaderService();
	}

	@Override
	public ManagedBeanRegistry getManagedBeanRegistry() {
		return delegate.getManagedBeanRegistry();
	}

	@Override
	public ConfigurationService getConfigurationService() {
		return delegate.getConfigurationService();
	}

	@Override
	public boolean isJpaBootstrap() {
		return delegate.isJpaBootstrap();
	}

	@Override
	public void markAsJpaBootstrap() {
		delegate.markAsJpaBootstrap();
	}

	@Override
	public ClassLoader getJpaTempClassLoader() {
		return delegate.getJpaTempClassLoader();
	}

	@Override
	public ClassLoaderAccess getClassLoaderAccess() {
		return delegate.getClassLoaderAccess();
	}

	@Override
	public ArchiveDescriptorFactory getArchiveDescriptorFactory() {
		return delegate.getArchiveDescriptorFactory();
	}

	@Override
	public ScanOptions getScanOptions() {
		return delegate.getScanOptions();
	}

	@Override
	public ScanEnvironment getScanEnvironment() {
		return delegate.getScanEnvironment();
	}

	@Override
	public Object getScanner() {
		return delegate.getScanner();
	}

	@Override
	public Object getJandexView() {
		return delegate.getJandexView();
	}

	@Override
	public Map<String, SqmFunctionDescriptor> getSqlFunctions() {
		return delegate.getSqlFunctions();
	}

	@Override
	public Collection<AuxiliaryDatabaseObject> getAuxiliaryDatabaseObjectList() {
		return delegate.getAuxiliaryDatabaseObjectList();
	}

	@Override
	public Collection<ConverterDescriptor<?, ?>> getAttributeConverters() {
		return delegate.getAttributeConverters();
	}

	@Override
	public Collection<CacheRegionDefinition> getCacheRegionDefinitions() {
		return delegate.getCacheRegionDefinitions();
	}

	@Override
	public void release() {
		delegate.release();
	}

	@Override
	public void registerAdHocBasicType( BasicType<?> basicType ) {
		delegate.registerAdHocBasicType( basicType );
	}

	@Override
	public <T> BasicType<T> resolveAdHocBasicType( String key ) {
		return delegate.resolveAdHocBasicType( key );
	}

	@Override
	public <T> BasicType<T> findAdHocBasicType( JavaType<T> javaType, JdbcType jdbcType ) {
		return delegate.findAdHocBasicType( javaType, jdbcType );
	}

}
