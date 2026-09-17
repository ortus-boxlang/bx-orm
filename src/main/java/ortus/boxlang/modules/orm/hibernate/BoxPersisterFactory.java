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

import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.internal.PersisterFactoryImpl;
import org.hibernate.persister.spi.PersisterFactory;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import ortus.boxlang.modules.orm.mapping.EntityRecord;

/**
 * Hibernate {@link PersisterFactory} service that injects the BoxLang {@link BoxRepresentationResolver} into every entity
 * persister.
 * <p>
 * Hibernate 6+ hard-codes its {@code ManagedTypeRepresentationResolver} inside the bootstrap context, with no setting to
 * replace it. The persister factory, however, is a pluggable service ({@code hibernate.persister.factory}) and is the
 * one place that hands the {@link RuntimeModelCreationContext} to the persister constructor. This factory wraps that
 * context so {@code getBootstrapContext().getRepresentationStrategySelector()} returns our resolver, then delegates the
 * actual persister construction to Hibernate's own {@link PersisterFactoryImpl}.
 *
 * @since 2.0.0
 */
public class BoxPersisterFactory implements PersisterFactory, ServiceRegistryAwareService {

	private final PersisterFactoryImpl		delegate	= new PersisterFactoryImpl();
	private final BoxRepresentationResolver	resolver;

	/**
	 * @param entityRecords The discovered BoxLang entities for this session factory, keyed by lower-cased entity name.
	 * @param entityFacades Whether to use the POJO-facade representation (true) or the MAP representation (false).
	 */
	public BoxPersisterFactory( Map<String, EntityRecord> entityRecords, boolean entityFacades ) {
		this.resolver = new BoxRepresentationResolver( entityRecords, entityFacades );
	}

	@Override
	public void injectServices( ServiceRegistryImplementor serviceRegistry ) {
		delegate.injectServices( serviceRegistry );
	}

	@Override
	public EntityPersister createEntityPersister( PersistentClass entityBinding, EntityDataAccess entityCacheAccessStrategy,
	    NaturalIdDataAccess naturalIdCacheAccessStrategy, RuntimeModelCreationContext creationContext ) {
		return delegate.createEntityPersister(
		    entityBinding,
		    entityCacheAccessStrategy,
		    naturalIdCacheAccessStrategy,
		    new BoxRuntimeModelCreationContext( creationContext, resolver )
		);
	}

	@Override
	public CollectionPersister createCollectionPersister( Collection collectionBinding, CollectionDataAccess cacheAccessStrategy,
	    RuntimeModelCreationContext creationContext ) {
		return delegate.createCollectionPersister( collectionBinding, cacheAccessStrategy, creationContext );
	}

}
