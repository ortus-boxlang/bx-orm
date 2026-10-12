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
package ortus.boxlang.modules.orm.hibernate.cache;

import java.util.Locale;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.config.spi.StandardConverters;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Caches collections that declare <code>cacheUse</code> (and optionally <code>cacheName</code>) in the second-level cache.
 * <p>
 * Hibernate 7's <code>mapping.xml</code> has no caching element for collections, and its
 * <code>hibernate.collectioncache.*</code> settings are only read by the JPA bootstrap. So this integrator sets the cache
 * strategy and region on each collection's boot model. Hibernate calls integrators before it creates the cache regions, so
 * the collection gets its region like an annotated one. It does nothing when the second-level cache is off.
 * <p>
 * The entity records come from the {@value #ENTITY_RECORDS_SETTING} setting, which the session factory builder sets to its
 * map of entity records (keyed by lower-cased entity name).
 */
public final class CollectionCacheIntegrator implements Integrator {

	/**
	 * The Hibernate setting that carries the session factory's entity records.
	 */
	public static final String ENTITY_RECORDS_SETTING = "bxorm.entity_records";

	/**
	 * Set the cache strategy and region on every collection whose property declares <code>cacheUse</code>.
	 *
	 * @param metadata         The boot model.
	 * @param bootstrapContext The bootstrap context, used to read the settings.
	 * @param sessionFactory   The session factory being built.
	 */
	@Override
	public void integrate( Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory ) {
		ConfigurationService	settings	= bootstrapContext.getServiceRegistry().requireService( ConfigurationService.class );
		boolean					enabled		= settings.getSetting( "hibernate.cache.use_second_level_cache", StandardConverters.BOOLEAN, false );
		if ( !enabled || ! ( settings.getSettings().get( ENTITY_RECORDS_SETTING ) instanceof Map<?, ?> records ) ) {
			return;
		}
		for ( Collection collection : metadata.getCollectionBindings() ) {
			apply( collection, records );
		}
	}

	/**
	 * Nothing to undo: the boot model is discarded with the session factory.
	 *
	 * @param sessionFactory  The session factory.
	 * @param serviceRegistry The session factory's service registry.
	 */
	@Override
	public void disintegrate( SessionFactoryImplementor sessionFactory, org.hibernate.service.spi.SessionFactoryServiceRegistry serviceRegistry ) {
		// nothing to clean up
	}

	/**
	 * Set the cache strategy and region of one collection from its property's <code>cacheUse</code> and
	 * <code>cacheName</code>.
	 *
	 * @param collection The collection binding.
	 * @param records    The entity records, keyed by lower-cased entity name.
	 */
	static void apply( Collection collection, Map<?, ?> records ) {
		PersistentClass	owner	= collection.getOwner();
		EntityRecord	record	= recordFor( owner, records );
		if ( record == null ) {
			return;
		}
		IPropertyMeta property = propertyFor( record, propertyName( collection.getRole(), owner.getEntityName() ) );
		if ( property == null || property.getCache() == null || property.getCache().isEmpty() ) {
			return;
		}
		IStruct	cache		= property.getCache();
		String	strategy	= cache.getAsString( ORMKeys.strategy );
		if ( strategy == null || strategy.isBlank() ) {
			return;
		}
		collection.setCacheConcurrencyStrategy( strategy.trim().toLowerCase( Locale.ROOT ) );
		String region = cache.getAsString( Key.region );
		if ( region != null && !region.isBlank() ) {
			collection.setCacheRegionName( region.trim() );
		}
	}

	/**
	 * The entity record of a collection's owner, found by its JPA entity name or its Hibernate entity name.
	 *
	 * @param owner   The owning entity binding.
	 * @param records The entity records, keyed by lower-cased entity name.
	 *
	 * @return The record, or null when the owner is not a bx-orm entity.
	 */
	private static EntityRecord recordFor( PersistentClass owner, Map<?, ?> records ) {
		for ( String name : new String[] { owner.getJpaEntityName(), owner.getEntityName() } ) {
			if ( name != null && records.get( name.toLowerCase( Locale.ROOT ).trim() ) instanceof EntityRecord record ) {
				return record;
			}
		}
		return null;
	}

	/**
	 * The property path of a collection role: the role without its owner's entity name.
	 *
	 * @param role            The collection role, such as <code>bxorm.app.User.orders</code>.
	 * @param ownerEntityName The owner's Hibernate entity name.
	 *
	 * @return The property name, such as <code>orders</code>.
	 */
	static String propertyName( String role, String ownerEntityName ) {
		if ( ownerEntityName != null && role.startsWith( ownerEntityName + "." ) ) {
			return role.substring( ownerEntityName.length() + 1 );
		}
		return role.substring( role.lastIndexOf( '.' ) + 1 );
	}

	/**
	 * A persistent property of an entity (including inherited ones), matched without regard to case.
	 *
	 * @param record The entity record.
	 * @param name   The property name.
	 *
	 * @return The property, or null when the entity has none by that name.
	 */
	private static IPropertyMeta propertyFor( EntityRecord record, String name ) {
		for ( IPropertyMeta property : record.getEntityMeta().getAllPersistentProperties() ) {
			if ( property.getName().equalsIgnoreCase( name ) ) {
				return property;
			}
		}
		return null;
	}
}
