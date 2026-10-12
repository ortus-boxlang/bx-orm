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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
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

	/** The {@code <unsaved-value>} inside a mapping's {@code <id>} element. */
	private static final Pattern			UNSAVED_VALUE	= Pattern.compile( "<unsaved-value>([^<]*)</unsaved-value>" );

	private final PersisterFactoryImpl		delegate		= new PersisterFactoryImpl();
	private final BoxRepresentationResolver	resolver;
	private final Map<String, EntityRecord>	entityRecords;

	/**
	 * @param entityRecords   The discovered BoxLang entities for this session factory, keyed by lower-cased entity name.
	 * @param facadeNamespace The owning application's facade namespace.
	 */
	public BoxPersisterFactory( Map<String, EntityRecord> entityRecords, String facadeNamespace ) {
		this.resolver		= new BoxRepresentationResolver( entityRecords, facadeNamespace );
		this.entityRecords	= entityRecords;
	}

	@Override
	public void injectServices( ServiceRegistryImplementor serviceRegistry ) {
		delegate.injectServices( serviceRegistry );
	}

	@Override
	public EntityPersister createEntityPersister( PersistentClass entityBinding, EntityDataAccess entityCacheAccessStrategy,
	    NaturalIdDataAccess naturalIdCacheAccessStrategy, RuntimeModelCreationContext creationContext ) {
		applyUnsavedValue( entityBinding );
		return delegate.createEntityPersister(
		    entityBinding,
		    entityCacheAccessStrategy,
		    naturalIdCacheAccessStrategy,
		    new BoxRuntimeModelCreationContext( creationContext, resolver )
		);
	}

	/**
	 * Give the entity's id the unsaved value its mapping declares, before Hibernate builds the persister.
	 * <p>
	 * Hibernate 7 reads {@code <unsaved-value>} only from legacy {@code hbm.xml}; in {@code mapping.xml} it is parsed and
	 * then ignored. Without an unsaved value, Hibernate creates one throwaway instance of the entity while building the
	 * persister, only to read the id a new entity starts with. For a BoxLang entity that loads, compiles and instantiates
	 * the entity class at boot. The mapping writer decides the value (see {@code MappingXMLWriter}); this applies it to
	 * the boot model, so Hibernate skips that instance. An id Hibernate has already given an unsaved value is left alone.
	 *
	 * @param entityBinding The entity's boot model.
	 */
	private void applyUnsavedValue( PersistentClass entityBinding ) {
		KeyValue identifier = entityBinding.getIdentifier();
		if ( ! ( identifier instanceof SimpleValue simpleId ) || simpleId.getNullValueSemantic() != null ) {
			return;
		}
		// The JPA entity name is the BoxLang entity name; Hibernate's own entity name is the facade class name
		EntityRecord record = null;
		for ( String name : new String[] { entityBinding.getJpaEntityName(), entityBinding.getEntityName() } ) {
			if ( record == null && name != null ) {
				record = entityRecords.get( name.toLowerCase().trim() );
			}
		}
		String unsaved = record == null ? null : unsavedValueOf( record.getXmlMapping() );
		if ( unsaved != null ) {
			simpleId.setNullValue( unsaved );
		}
	}

	/**
	 * The {@code <unsaved-value>} of the {@code <id>} element in an entity's mapping, or null when there is none.
	 *
	 * @param xmlMapping The entity's generated mapping.
	 *
	 * @return The unsaved value text, or null.
	 */
	static String unsavedValueOf( String xmlMapping ) {
		if ( xmlMapping == null ) {
			return null;
		}
		int	start	= xmlMapping.indexOf( "<id " );
		int	end		= start < 0 ? -1 : xmlMapping.indexOf( "</id>", start );
		if ( end < 0 ) {
			return null;
		}
		Matcher matcher = UNSAVED_VALUE.matcher( xmlMapping.substring( start, end ) );
		return matcher.find() ? matcher.group( 1 ).trim() : null;
	}

	@Override
	public CollectionPersister createCollectionPersister( Collection collectionBinding, CollectionDataAccess cacheAccessStrategy,
	    RuntimeModelCreationContext creationContext ) {
		return delegate.createCollectionPersister( collectionBinding, cacheAccessStrategy, creationContext );
	}

}
