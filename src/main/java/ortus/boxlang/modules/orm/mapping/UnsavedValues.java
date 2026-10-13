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
package ortus.boxlang.modules.orm.mapping;

import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * The unsaved values bx-orm declares for an entity's id and version, so Hibernate never instantiates an entity at boot.
 * <p>
 * Hibernate tells a new entity from an existing one by comparing its id (or version) to the <em>unsaved value</em>: the
 * value a brand-new instance starts with. When the mapping does not declare it, Hibernate 7 creates one throwaway instance
 * of the entity while it builds the SessionFactory and reads the id and version off it. For a BoxLang entity that runs the
 * class's pseudo-constructor and {@code init()} during ORM startup, before the application (ColdBox, WireBox, ...) is
 * ready, which breaks entities whose {@code init()} needs the application. Hibernate 5 never did this for bx-orm 1.x's
 * dynamic-map entities, so declaring the value restores the 1.x boot behavior.
 * <p>
 * The values below are the ones Hibernate would infer from a fresh BoxLang instance, so saving and updating behave the
 * same: a new instance's id and version are null unless the property declares a {@code default}.
 *
 * @since 2.0.0
 */
public final class UnsavedValues {

	/** Hibernate's keyword for "a null id or version marks a new entity". */
	public static final String	NULL		= "null";

	/** Hibernate's keyword for "the version alone cannot tell; check the identifier (and the database)". */
	public static final String	UNDEFINED	= "undefined";

	private UnsavedValues() {
	}

	/**
	 * The unsaved value of a single (non-composite) id: the property's {@code unsavedValue} annotation when set, else its
	 * {@code default} when it has one, else {@value #NULL}.
	 *
	 * @param prop The id property.
	 *
	 * @return The unsaved value; never null.
	 */
	public static String forId( IPropertyMeta prop ) {
		if ( prop.getUnsavedValue() != null ) {
			return prop.getUnsavedValue();
		}
		String defaultValue = defaultOf( prop );
		return defaultValue == null ? NULL : defaultValue;
	}

	/**
	 * The unsaved value of a version property: the property's {@code unsavedValue} annotation when set, else
	 * {@value #UNDEFINED} when it has a {@code default} (a new instance already carries a version), else {@value #NULL}.
	 *
	 * @param prop The version property.
	 *
	 * @return The unsaved value; never null.
	 */
	public static String forVersion( IPropertyMeta prop ) {
		if ( prop.getUnsavedValue() != null ) {
			return prop.getUnsavedValue();
		}
		return defaultOf( prop ) == null ? NULL : UNDEFINED;
	}

	/**
	 * The property's {@code default} annotation as text, or null when it has none. BoxLang metadata always carries a
	 * {@code default} annotation, null when the property declares none. A blank default is kept: a new instance's id is
	 * then {@code ""} (the common {@code default=""} on a uuid id), so {@code ""} is what marks it unsaved.
	 *
	 * @param prop The property.
	 *
	 * @return The default as text, or null.
	 */
	static String defaultOf( IPropertyMeta prop ) {
		IStruct annotations = prop.getAnnotations();
		if ( annotations == null || annotations.get( Key._DEFAULT ) == null ) {
			return null;
		}
		return StringCaster.attempt( annotations.get( Key._DEFAULT ) ).getOrDefault( "" );
	}
}
