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
import java.util.function.Supplier;

import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.internal.ManagedTypeRepresentationResolverStandard;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.spi.EmbeddableRepresentationStrategy;
import org.hibernate.metamodel.spi.EntityRepresentationStrategy;
import org.hibernate.metamodel.spi.ManagedTypeRepresentationResolver;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.entity.EntityPersister;

import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * {@link ManagedTypeRepresentationResolver} that routes every entity to {@link BoxEntityRepresentationStrategy} and
 * delegates embeddables (components, composite ids) to Hibernate's standard resolver.
 *
 * @since 2.0.0
 */
public class BoxRepresentationResolver implements ManagedTypeRepresentationResolver {

	private final Map<String, EntityRecord>	entityRecords;

	/**
	 * Secondary index of the same records keyed by lower-cased facade FQN, so an entity whose Hibernate entity-name is its
	 * facade class (as the modern mapping.xml format reports it) still resolves. The primary by-entity-name lookup always
	 * takes precedence.
	 */
	private final Map<String, EntityRecord>	recordsByFacadeFQN;

	/**
	 * @param entityRecords   The discovered BoxLang entities for this session factory, keyed by lower-cased entity name.
	 * @param facadeNamespace The owning application's facade namespace, used to build the facade-FQN fallback index.
	 */
	public BoxRepresentationResolver( Map<String, EntityRecord> entityRecords, String facadeNamespace ) {
		this.entityRecords		= entityRecords;
		this.recordsByFacadeFQN	= new java.util.HashMap<>();
		for ( EntityRecord record : entityRecords.values() ) {
			this.recordsByFacadeFQN.put(
			    ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming.facadeClassName( facadeNamespace, record.getEntityName() ).toLowerCase()
			        .trim(),
			    record );
		}
	}

	@Override
	public EntityRepresentationStrategy resolveStrategy( PersistentClass bootDescriptor, EntityPersister runtimeDescriptor,
	    RuntimeModelCreationContext creationContext ) {
		String			hibernateName	= bootDescriptor.getEntityName().toLowerCase().trim();
		// Primary: by entity-name. Fallback: by facade FQN (the modern mapping.xml reports the mapped facade class as the
		// Hibernate entity-name, so the by-name lookup misses and this resolves it).
		EntityRecord	entityRecord	= entityRecords.get( hibernateName );
		if ( entityRecord == null ) {
			entityRecord = recordsByFacadeFQN.get( hibernateName );
		}
		if ( entityRecord == null ) {
			throw new BoxRuntimeException( "No BoxLang entity record found for Hibernate entity [" + bootDescriptor.getEntityName() + "]" );
		}
		return new BoxEntityRepresentationStrategy( bootDescriptor, runtimeDescriptor, creationContext, entityRecord );
	}

	/**
	 * Resolve the representation strategy for an embeddable (a component, or a composite primary key). bx-orm only
	 * provides a custom strategy for entities; embeddables use Hibernate's built-in handling.
	 * <p>
	 * This is the single place where the bridge touches an {@code internal} Hibernate type
	 * ({@link ManagedTypeRepresentationResolverStandard}). Hibernate exposes no public factory for the default
	 * embeddable strategy, and this static {@code INSTANCE} has been stable since Hibernate 6. If a future Hibernate
	 * release relocates or changes it, this one delegation is the only thing to update - see the
	 * bx-orm-hibernate-bridge skill and AGENTS.md.
	 */
	@Override
	public EmbeddableRepresentationStrategy resolveStrategy( Component bootDescriptor, Supplier<EmbeddableMappingType> runtimeDescriptor,
	    RuntimeModelCreationContext creationContext ) {
		return ManagedTypeRepresentationResolverStandard.INSTANCE.resolveStrategy( bootDescriptor, runtimeDescriptor, creationContext );
	}

}
