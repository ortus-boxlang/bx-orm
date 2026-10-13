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
import java.util.Set;

import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.internal.PersisterFactoryImpl;
import org.hibernate.persister.spi.PersisterFactory;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.type.BasicType;

import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.UnsavedValues;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.logging.BoxLangLogger;

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

	/** Hibernate's unsaved-value keywords, which are not parsed as a value. */
	private static final Set<String>		HIBERNATE_KEYWORDS	= Set.of( "null", "none", "any", "undefined", "negative" );

	private final PersisterFactoryImpl		delegate			= new PersisterFactoryImpl();
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
		applyUnsavedValues( entityBinding );
		return delegate.createEntityPersister(
		    entityBinding,
		    entityCacheAccessStrategy,
		    naturalIdCacheAccessStrategy,
		    new BoxRuntimeModelCreationContext( creationContext, resolver )
		);
	}

	/**
	 * Declare the unsaved value of the entity's id and version on the boot model, before Hibernate builds the persister.
	 * <p>
	 * Without one, Hibernate 7 instantiates the entity at boot to read the id and version a new instance starts with. For a
	 * BoxLang entity that runs its pseudo-constructor and {@code init()} during ORM startup, before the application is
	 * ready (for example, before ColdBox and WireBox exist). {@link UnsavedValues} decides the values from the entity's
	 * metadata; they match what Hibernate would read off a fresh instance. Hibernate never instantiates the entity for a
	 * composite id, so those are left alone, as is any id or version Hibernate already has an unsaved value for. The id and
	 * version live on the root entity, so a subclass is handled through its root.
	 *
	 * @param entityBinding The entity's boot model.
	 */
	private void applyUnsavedValues( PersistentClass entityBinding ) {
		RootClass		root	= entityBinding.getRootClass();
		EntityRecord	record	= recordFor( root );
		if ( record == null ) {
			return;
		}
		IEntityMeta	meta		= record.getEntityMeta();

		KeyValue	identifier	= root.getIdentifier();
		if ( identifier instanceof SimpleValue simpleId && ! ( identifier instanceof Component ) && simpleId.getNullValueSemantic() == null ) {
			Set<IPropertyMeta> idProperties = meta.getIdProperties();
			if ( idProperties.size() == 1 ) {
				simpleId.setNullValue( parseableOrNull( UnsavedValues.forId( idProperties.iterator().next() ), simpleId, root ) );
			}
		}

		Property		version			= root.getVersion();
		IPropertyMeta	versionProperty	= meta.getVersionProperty();
		if ( version != null && versionProperty != null && version.getValue() instanceof SimpleValue versionValue
		    && versionValue.getNullValueSemantic() == null ) {
			versionValue.setNullValue( parseableOrNull( UnsavedValues.forVersion( versionProperty ), versionValue, root ) );
		}
	}

	/**
	 * The bx-orm entity record of a boot entity, found by its JPA entity name (the BoxLang entity name) or its Hibernate
	 * entity name (the facade class name).
	 *
	 * @param entityBinding The entity's boot model.
	 *
	 * @return The record, or null when the entity is not a bx-orm entity.
	 */
	private EntityRecord recordFor( PersistentClass entityBinding ) {
		for ( String name : new String[] { entityBinding.getJpaEntityName(), entityBinding.getEntityName() } ) {
			EntityRecord record = name == null ? null : entityRecords.get( name.toLowerCase().trim() );
			if ( record != null ) {
				return record;
			}
		}
		return null;
	}

	/**
	 * Check that a literal unsaved value (not one of Hibernate's keywords) parses as the value's type. Hibernate parses it
	 * while building the persister and fails the whole boot when it cannot, so an unparseable {@code default} or
	 * {@code unsavedValue} falls back to {@value UnsavedValues#NULL} with a warning instead.
	 *
	 * @param unsaved The unsaved value.
	 * @param value   The id or version value it applies to.
	 * @param root    The root entity, for the warning.
	 *
	 * @return The unsaved value, or {@value UnsavedValues#NULL} when it does not parse.
	 */
	private static String parseableOrNull( String unsaved, SimpleValue value, PersistentClass root ) {
		if ( HIBERNATE_KEYWORDS.contains( unsaved ) || ! ( value.getType() instanceof BasicType<?> basicType ) ) {
			return unsaved;
		}
		try {
			basicType.getJavaTypeDescriptor().fromString( unsaved );
			return unsaved;
		} catch ( RuntimeException e ) {
			logger().warn( "The unsaved value [{}] of entity [{}] is not a valid {}; using null instead.", unsaved, root.getJpaEntityName(),
			    basicType.getJavaType().getSimpleName() );
			return UnsavedValues.NULL;
		}
	}

	@Override
	public CollectionPersister createCollectionPersister( Collection collectionBinding, CollectionDataAccess cacheAccessStrategy,
	    RuntimeModelCreationContext creationContext ) {
		return delegate.createCollectionPersister( collectionBinding, cacheAccessStrategy, creationContext );
	}

	/**
	 * @return The ORM logger.
	 */
	private static BoxLangLogger logger() {
		return BoxRuntime.getInstance().getLoggingService().getLogger( "orm" );
	}

}
