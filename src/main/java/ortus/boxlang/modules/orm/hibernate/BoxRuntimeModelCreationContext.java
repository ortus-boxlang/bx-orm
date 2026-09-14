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

import java.util.Map;

import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.generator.Generator;
import org.hibernate.mapping.GeneratorSettings;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.spi.MappingMetamodelImplementor;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Delegating {@link RuntimeModelCreationContext} whose only override is {@link #getBootstrapContext()}, which returns a
 * {@link BoxBootstrapContext} wrapper so the entity persister resolves its representation strategy through
 * {@link BoxRepresentationResolver}.
 *
 * @since 2.0.0
 */
public class BoxRuntimeModelCreationContext implements RuntimeModelCreationContext {

	private final RuntimeModelCreationContext	delegate;
	private final BootstrapContext				bootstrapContext;

	public BoxRuntimeModelCreationContext( RuntimeModelCreationContext delegate, BoxRepresentationResolver resolver ) {
		this.delegate			= delegate;
		this.bootstrapContext	= new BoxBootstrapContext( delegate.getBootstrapContext(), resolver );
	}

	@Override
	public BootstrapContext getBootstrapContext() {
		return bootstrapContext;
	}

	@Override
	public SessionFactoryImplementor getSessionFactory() {
		return delegate.getSessionFactory();
	}

	@Override
	public MetadataImplementor getBootModel() {
		return delegate.getBootModel();
	}

	@Override
	public MappingMetamodelImplementor getDomainModel() {
		return delegate.getDomainModel();
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return delegate.getTypeConfiguration();
	}

	@Override
	public SqmFunctionRegistry getFunctionRegistry() {
		return delegate.getFunctionRegistry();
	}

	@Override
	public Map<String, Object> getSettings() {
		return delegate.getSettings();
	}

	@Override
	public Dialect getDialect() {
		return delegate.getDialect();
	}

	@Override
	public CacheImplementor getCache() {
		return delegate.getCache();
	}

	@Override
	public SessionFactoryOptions getSessionFactoryOptions() {
		return delegate.getSessionFactoryOptions();
	}

	@Override
	public JdbcServices getJdbcServices() {
		return delegate.getJdbcServices();
	}

	@Override
	public SqlStringGenerationContext getSqlStringGenerationContext() {
		return delegate.getSqlStringGenerationContext();
	}

	@Override
	public ServiceRegistry getServiceRegistry() {
		return delegate.getServiceRegistry();
	}

	@Override
	public Map<String, Generator> getGenerators() {
		return delegate.getGenerators();
	}

	@Override
	public GeneratorSettings getGeneratorSettings() {
		return delegate.getGeneratorSettings();
	}

	@Override
	public Generator getOrCreateIdGenerator( String rootName, PersistentClass persistentClass ) {
		return delegate.getOrCreateIdGenerator( rootName, persistentClass );
	}

}
