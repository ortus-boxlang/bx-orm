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
package ortus.boxlang.modules.orm.criteria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.model.domain.EmbeddableDomainType;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PluralPersistentAttribute;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

/**
 * The shape of one entity (or embedded component) as Hibernate mapped it: its attributes, which of them are
 * associations or components, and its id. {@code entityCriteria()} uses it to validate property paths while the
 * criteria is built ("Did you mean"), to fix their casing (HQL is case-sensitive, BoxLang is not), and to know where a
 * path needs a join.
 */
final class EntityModel {

	/**
	 * What kind of attribute a property is.
	 */
	enum Kind {
		/** A plain value (string, number, date, ...). */
		BASIC,
		/** A single entity reference (many-to-one, one-to-one). */
		TO_ONE,
		/** A collection of entities (one-to-many, many-to-many). */
		TO_MANY,
		/** A collection of values (element collection). */
		VALUES,
		/** An embedded component (composite id or embeddable). */
		COMPONENT
	}

	/**
	 * One attribute of the model.
	 *
	 * @param name     The declared attribute name (declared casing).
	 * @param kind     What kind of attribute it is.
	 * @param target   The model of the associated entity or component; null for basic values.
	 * @param javaType The Java type of a basic value (used to know text properties); null otherwise.
	 */
	record Attr( String name, Kind kind, EntityModel target, Class<?> javaType ) {

		/**
		 * Whether this is a text value (sorted case-insensitively by {@code ignoreCase}).
		 *
		 * @return True for a String or character value.
		 */
		boolean isText() {
			return javaType == String.class || javaType == Character.class || javaType == char.class;
		}

		/**
		 * Whether a path can continue through this attribute with a join.
		 *
		 * @return True for to-one and to-many associations.
		 */
		boolean isAssociation() {
			return kind == Kind.TO_ONE || kind == Kind.TO_MANY;
		}
	}

	/** The JPA (BoxLang) entity name, or the component's Java name. */
	private final String					name;
	/** Whether this is an entity (false for a component). */
	private final boolean					entity;
	/** The Hibernate domain type. */
	private final ManagedDomainType<?>		type;
	/** The session factory, to resolve associated models lazily. */
	private final SessionFactoryImplementor	factory;
	/** Attributes by lower-cased name, built on first use. */
	private Map<String, Attr>				attributes;

	/**
	 * Wrap a Hibernate domain type.
	 *
	 * @param factory The session factory.
	 * @param type    The entity or embeddable domain type.
	 */
	private EntityModel( SessionFactoryImplementor factory, ManagedDomainType<?> type ) {
		this.factory	= factory;
		this.type		= type;
		this.entity		= type instanceof EntityDomainType<?>;
		this.name		= type instanceof EntityDomainType<?> e ? e.getName() : type.getTypeName();
	}

	/**
	 * The model of an entity, by its BoxLang (JPA) name.
	 *
	 * @param factory    The session factory the entity is mapped in.
	 * @param entityName The entity name.
	 *
	 * @return The model.
	 */
	static EntityModel of( SessionFactoryImplementor factory, String entityName ) {
		return new EntityModel( factory, factory.getJpaMetamodel().entity( entityName ) );
	}

	/**
	 * The entity name (or component type name).
	 *
	 * @return The name.
	 */
	String name() {
		return name;
	}

	/**
	 * Whether this model is an entity (false for an embedded component).
	 *
	 * @return True for an entity.
	 */
	boolean isEntity() {
		return entity;
	}

	/**
	 * Find an attribute by name, ignoring case.
	 *
	 * @param attributeName The attribute name as the developer wrote it.
	 *
	 * @return The attribute, or null when the model has none by that name.
	 */
	Attr find( String attributeName ) {
		return attributes().get( attributeName.toLowerCase() );
	}

	/**
	 * Every attribute name, in declared casing (for "Did you mean" and error messages).
	 *
	 * @return The names.
	 */
	List<String> attributeNames() {
		List<String> names = new ArrayList<>();
		attributes().values().forEach( a -> names.add( a.name() ) );
		return names;
	}

	/**
	 * The plain-value attributes, id first: the columns {@code asStruct()} and {@code asQuery()} return when there is no
	 * projection.
	 *
	 * @return The attribute names.
	 */
	List<String> basicAttributeNames() {
		List<String>	names	= new ArrayList<>();
		String			id		= singleIdName();
		if ( id != null ) {
			names.add( id );
		}
		attributes().values().forEach( a -> {
			if ( a.kind() == Kind.BASIC && !a.name().equals( id ) ) {
				names.add( a.name() );
			}
		} );
		return names;
	}

	/**
	 * The name of the id attribute, when the entity has a single (non-composite) id.
	 *
	 * @return The id attribute name, or null for a component or a composite id.
	 */
	String singleIdName() {
		if ( type instanceof EntityDomainType<?> e && e.hasSingleIdAttribute() ) {
			SingularAttribute<?, ?> id = e.getId( e.getIdType().getJavaType() );
			return id == null ? null : id.getName();
		}
		return null;
	}

	/**
	 * Build the attribute map on first use: every attribute, including inherited ones.
	 *
	 * @return The attributes by lower-cased name, in declaration order.
	 */
	private Map<String, Attr> attributes() {
		if ( attributes == null ) {
			Map<String, Attr> map = new LinkedHashMap<>();
			for ( Attribute<?, ?> attribute : type.getAttributes() ) {
				map.put( attribute.getName().toLowerCase(), toAttr( attribute ) );
			}
			attributes = map;
		}
		return attributes;
	}

	/**
	 * Classify one attribute.
	 *
	 * @param attribute The JPA attribute.
	 *
	 * @return The classified attribute (its target model is built now; models are small).
	 */
	private Attr toAttr( Attribute<?, ?> attribute ) {
		if ( attribute instanceof PluralPersistentAttribute<?, ?, ?> plural ) {
			Type<?> element = plural.getElementType();
			if ( element instanceof EntityDomainType<?> target ) {
				return new Attr( attribute.getName(), Kind.TO_MANY, new EntityModel( factory, target ), null );
			}
			return new Attr( attribute.getName(), Kind.VALUES, null, null );
		}
		if ( attribute instanceof SingularAttribute<?, ?> singular ) {
			Type<?> valueType = singular.getType();
			if ( valueType instanceof EntityDomainType<?> target ) {
				return new Attr( attribute.getName(), Kind.TO_ONE, new EntityModel( factory, target ), null );
			}
			if ( valueType instanceof EmbeddableDomainType<?> component ) {
				return new Attr( attribute.getName(), Kind.COMPONENT, new EntityModel( factory, component ), null );
			}
			// The mapped (Hibernate) type, not the facade field type, which is Object for untyped BoxLang properties.
			return new Attr( attribute.getName(), Kind.BASIC, null, valueType.getJavaType() );
		}
		return new Attr( attribute.getName(), Kind.BASIC, null, attribute.getJavaType() );
	}
}
