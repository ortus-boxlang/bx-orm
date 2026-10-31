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
package ortus.boxlang.modules.orm.hibernate.facade;

import java.util.Collection;
import java.util.Map;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.logging.BoxLangLogger;

/**
 * Generates the entity facade classes ({@link EntityFacadeFactory}) for a set of discovered entities.
 * <p>
 * Facades are built from entity metadata alone: no Hibernate session factory and no database are involved. A session
 * factory build calls this just before Hibernate parses the mappings, and the {@code module:orm generate} CLI calls it
 * to write {@code facades-{app}.jar} for {@code trust}-mode boots.
 *
 * @since 2.0.0
 */
public final class EntityFacadeGenerator {

	private static final BoxLangLogger LOGGER = BoxRuntime.getInstance().getLoggingService().getLogger( "orm" );

	private EntityFacadeGenerator() {
	}

	/**
	 * Generate a real POJO facade class for each entity and register it, so Hibernate can map the entity to a real class
	 * with a real id member (enabling {@code uuid} and other id generation).
	 * <p>
	 * The facade's fully-qualified name and id Java type are computed by {@link EntityFacadeNaming}, so they match the
	 * {@code <class name=...>} and id {@code type} the mapping XML writer emits. The facades are defined in
	 * {@code ormConfig.facadeClassLoader} when set (one loader per ORM application build), else in the ORM module
	 * classloader.
	 *
	 * @param ormConfig The ORM configuration (facade namespace, facade classloader, ignoreParseErrors).
	 * @param entities  The discovered entity records.
	 */
	public static void generate( ORMConfig ormConfig, Collection<EntityRecord> entities ) {
		ClassLoader						loader		= ormConfig.facadeClassLoader != null ? ormConfig.facadeClassLoader
		    : ORMApp.moduleClassLoader();

		// Facades must be generated parents-first: a subclass facade extends its parent facade, so the parent class must
		// already exist. Order by inheritance depth (roots first), reusing the same depth calculation the mapping-file
		// ordering uses.
		Map<String, EntityRecord>		entityMap	= entities
		    .stream()
		    .filter( entity -> entity.getEntityName() != null )
		    .collect( java.util.stream.Collectors.toMap( entity -> entity.getEntityName().toLowerCase().trim(), entity -> entity, ( a, b ) -> a ) );
		java.util.List<EntityRecord>	ordered		= entities
		    .stream()
		    .sorted( java.util.Comparator.comparingInt( e -> SessionFactoryBuilder.inheritanceDepth( e, entityMap, new java.util.HashSet<>() ) ) )
		    .toList();

		for ( EntityRecord entity : ordered ) {
			IEntityMeta meta = entity.getEntityMeta();
			if ( meta == null ) {
				continue;
			}

			// A subclass facade extends its (already-generated) parent facade and declares only its own local properties;
			// its id is inherited from the hierarchy root. A root facade extends Object and owns the id accessor(s) - one
			// for a simple key, one per key property for a composite key.
			Class<?>											superClass	= Object.class;
			java.util.List<EntityFacadeFactory.PropertySpec>	idSpecs		= new java.util.ArrayList<>();
			if ( meta.isSubclass() ) {
				String parentName = parentEntityName( meta );
				superClass = parentName == null ? null : FacadeSupport.facadeClassFor( ormConfig.facadeNamespace, parentName );
				if ( superClass == null ) {
					String message = "Entity facade for subclass [" + entity.getEntityName() + "] cannot resolve its parent facade"
					    + ( parentName == null ? "." : " for parent entity [" + parentName + "]." );
					if ( ormConfig.ignoreParseErrors ) {
						LOGGER.error( message );
						continue;
					}
					throw new ortus.boxlang.runtime.types.exceptions.BoxRuntimeException( message );
				}
			} else {
				for ( IPropertyMeta idProp : meta.getIdProperties() ) {
					idSpecs.add( new EntityFacadeFactory.PropertySpec( idProp.getName(), EntityFacadeNaming.idJavaType( idProp.getORMType() ) ) );
				}
			}

			java.util.Set<String>								idNames		= meta.getIdProperties().stream()
			    .map( p -> p.getName().toLowerCase() )
			    .collect( java.util.stream.Collectors.toSet() );

			// Every other persistent property (normal columns, version/timestamp, associations) gets an Object accessor;
			// bx-orm's JPA AttributeConverters are declared AttributeConverter<Object, ?>, matching an Object attribute type.
			// Associations additionally carry an AssocKind so the facade's accessor translates at the Hibernate/BoxLang
			// boundary (facade<->IClassRunnable for to-one, managed collection<->view for to-many). For a subclass, only its
			// LOCAL properties are declared (the parent facade already carries the inherited ones).
			java.util.Set<IPropertyMeta>						sourceProps	= meta.isSubclass() ? meta.getLocalPersistentProperties()
			    : meta.getAllPersistentProperties();
			java.util.List<EntityFacadeFactory.PropertySpec>	propSpecs	= new java.util.ArrayList<>();
			for ( IPropertyMeta prop : sourceProps ) {
				if ( idNames.contains( prop.getName().toLowerCase() ) ) {
					continue;
				}
				// Accessor Java type by property kind:
				// - binary/byte[]: a concrete byte[] accessor (not Object). The modern mapping format has no AttributeConverter
				// for byte[], so Hibernate must infer the column's SQL type (VARBINARY/BLOB) from the accessor's Java type. An
				// Object accessor would resolve to JAVA_OBJECT, which has no SQL type and breaks schema export.
				// - version (optimistic-lock): a concrete numeric/temporal accessor. A <version> attribute carries no converter,
				// so Hibernate resolves the version's Java type from the accessor; an Object accessor is not a legal version
				// type and fails SessionFactory build. Match the version's ormType (Integer/Long/Short/Instant).
				// - everything else: an Object accessor (bx-orm's converters are declared AttributeConverter<Object, ?>).
				Class<?> accessorType;
				if ( prop.getFieldType() == IPropertyMeta.FIELDTYPE.VERSION || prop.getFieldType() == IPropertyMeta.FIELDTYPE.TIMESTAMP ) {
					// fieldtype="timestamp" is a timestamp-based version whatever its (often absent, defaulting to string)
					// ormType; fieldtype="version" follows its ormType.
					accessorType = prop.getFieldType() == IPropertyMeta.FIELDTYPE.TIMESTAMP ? java.time.Instant.class
					    : versionJavaType( prop.getORMType() );
				} else if ( "binary".equals( ortus.boxlang.modules.orm.mapping.MappingXMLWriter.toHibernateType( prop.getORMType() ) ) ) {
					accessorType = byte[].class;
				} else {
					accessorType = Object.class;
				}
				propSpecs.add( new EntityFacadeFactory.PropertySpec( prop.getName(), accessorType, facadeAssocKind( prop ),
				    FacadeAnnotations.forProperty( prop ) ) );
			}

			// Use the IEntityMeta entity name so the FQN matches, byte-for-byte, the <class name=...> the mapping writer emits.
			String		facadeFQN	= EntityFacadeNaming.facadeClassName( ormConfig.facadeNamespace, meta.getEntityName() );
			Class<?>	facadeClass	= EntityFacadeFactory.generate( facadeFQN, idSpecs, propSpecs, superClass, loader,
			    FacadeAnnotations.forEntity( meta ) );
			FacadeSupport.register( ormConfig.facadeNamespace, meta.getEntityName(), facadeClass );
			FacadeSupport.register( ormConfig.facadeNamespace, entity.getEntityName(), facadeClass );
			LOGGER.trace( "Generated entity facade [{}] for entity [{}]", facadeFQN, meta.getEntityName() );
		}
	}

	/**
	 * Resolve a subclass entity's parent entity name the same way the mapping XML writer does: prefer the parent's
	 * {@code entityName} annotation, falling back to its simple class name.
	 *
	 * @param meta The subclass entity metadata.
	 *
	 * @return The parent entity name, or {@code null} if it cannot be resolved.
	 */
	private static String parentEntityName( IEntityMeta meta ) {
		var parentMeta = meta.getParentMeta();
		if ( parentMeta == null || parentMeta.isEmpty() ) {
			return null;
		}
		var		parentAnnotations	= parentMeta.getAsStruct( ortus.boxlang.runtime.scopes.Key.annotations );
		String	parentName			= parentAnnotations != null ? parentAnnotations.getAsString( ortus.boxlang.modules.orm.config.ORMKeys.entityName ) : null;
		if ( parentName == null || parentName.isBlank() ) {
			parentName = parentMeta.getAsString( ortus.boxlang.runtime.scopes.Key.simpleName );
		}
		return parentName == null || parentName.isBlank() ? null : parentName;
	}

	/**
	 * Map a property's field type to the facade accessor's association-translation kind. Associations need the facade's
	 * getter/setter to translate between Hibernate's representation (facades / managed collections) and the BoxLang
	 * instance's ({@code IClassRunnable} / a developer-facing collection view); everything else passes straight through.
	 *
	 * @param prop The property metadata.
	 *
	 * @return The {@link EntityFacadeFactory.AssocKind} for the generated accessor.
	 */
	private static EntityFacadeFactory.AssocKind facadeAssocKind( IPropertyMeta prop ) {
		switch ( prop.getFieldType() ) {
			case ONE_TO_ONE, MANY_TO_ONE :
				return EntityFacadeFactory.AssocKind.TO_ONE;
			case COLLECTION :
				// A value/element collection (fieldtype="collection"). An array maps to a List accessor; a struct maps to a
				// Map accessor. Its keys/values are scalars, so the accessor's contents pass through unwrapped (facade
				// wrapping only ever applies to IClassRunnable entity elements).
				return "map".equalsIgnoreCase( prop.getAssociation().getAsString( ortus.boxlang.modules.orm.config.ORMKeys.collectionType ) )
				    ? EntityFacadeFactory.AssocKind.TO_MANY_MAP
				    : EntityFacadeFactory.AssocKind.TO_MANY;
			case ONE_TO_MANY, MANY_TO_MANY :
				// A struct-typed entity collection is a MAP of entities (keyed by structKeyColumn); an array is a List.
				return "map".equalsIgnoreCase( prop.getAssociation().getAsString( ortus.boxlang.modules.orm.config.ORMKeys.collectionType ) )
				    ? EntityFacadeFactory.AssocKind.TO_MANY_ENTITY_MAP
				    : EntityFacadeFactory.AssocKind.TO_MANY;
			default :
				return EntityFacadeFactory.AssocKind.NONE;
		}
	}

	/**
	 * Resolve the concrete Java type for an optimistic-lock {@code <version>} property's facade accessor. A version
	 * attribute carries no {@code AttributeConverter}, so Hibernate infers the version's Java type from the accessor; it
	 * must be a legal version type (a numeric or a temporal), never {@code Object}. Numeric ormTypes map to Integer/Long/
	 * Short and temporal ormTypes to {@link java.time.Instant}; anything else defaults to Integer (the common CFML case).
	 *
	 * @param ormType The version property's ORM type.
	 *
	 * @return The concrete accessor Java type for the version.
	 */
	private static Class<?> versionJavaType( String ormType ) {
		String type = ormType == null ? "" : ormType.trim().toLowerCase();
		return switch ( type ) {
			case "long", "biginteger", "big_integer", "bigint" -> Long.class;
			case "short", "tinyint", "tinyinteger" -> Short.class;
			case "timestamp", "datetime", "date", "eurodate", "usdate", "time" -> java.time.Instant.class;
			default -> Integer.class;
		};
	}
}
