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
package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.Set;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Reads the ORM annotations that are switches (true or false).
 * <p>
 * BoxLang lets a switch be written without a value: {@code @persistent}, {@code @unique}, {@code @notNull}. Such an
 * annotation is stored with an empty value, and it means {@code true}, the same as {@code @persistent( true )} or
 * {@code persistent="true"}. An explicit value still wins: {@code @persistent( false )} turns the switch off.
 */
public final class AnnotationFlags {

	/**
	 * The entity (class) annotations that are switches.
	 */
	public static final Set<Key>	ENTITY_FLAGS	= Set.of(
	    ORMKeys.persistent,
	    ORMKeys.readOnly,
	    ORMKeys.dynamicInsert,
	    ORMKeys.dynamicUpdate,
	    ORMKeys.selectBeforeUpdate,
	    ORMKeys.mappedSuperClass,
	    // also takes "active" or "timestamp"; a valueless one is the default, "true"
	    ORMKeys.softDelete
	);

	/**
	 * The property annotations that are switches.
	 */
	public static final Set<Key>	PROPERTY_FLAGS	= Set.of(
	    ORMKeys.persistent,
	    ORMKeys.unique,
	    ORMKeys.notNull,
	    ORMKeys.insert,
	    ORMKeys.update,
	    ORMKeys.readOnly,
	    ORMKeys.optimisticLock,
	    ORMKeys.inverse,
	    ORMKeys.constrained,
	    ORMKeys.missingRowIgnored,
	    // also takes "extra", "proxy" or "no-proxy"; a valueless one means "true"
	    ORMKeys.lazy
	);

	private AnnotationFlags() {
	}

	/**
	 * Whether an annotation value is empty: the annotation was written without a value.
	 *
	 * @param value The annotation value.
	 *
	 * @return True for null or blank text.
	 */
	public static boolean isValueless( Object value ) {
		return value == null || ( value instanceof CharSequence text && text.toString().isBlank() );
	}

	/**
	 * Whether a switch annotation is on.
	 *
	 * @param annotations The annotations to read.
	 * @param key         The annotation.
	 * @param whenAbsent  The answer when the annotation is not there.
	 *
	 * @return True when the annotation is valueless or true; {@code whenAbsent} when it is missing.
	 */
	public static boolean isTrue( IStruct annotations, Key key, boolean whenAbsent ) {
		if ( annotations == null || !annotations.containsKey( key ) ) {
			return whenAbsent;
		}
		Object value = annotations.get( key );
		return isValueless( value ) || BooleanCaster.cast( value );
	}

	/**
	 * Store {@code true} for every switch annotation written without a value, so the rest of the mapping, the boot
	 * cache and the class metadata all see the same value.
	 *
	 * @param annotations The annotations to normalize (changed in place). May be null.
	 * @param flags       The switch annotations, {@link #ENTITY_FLAGS} or {@link #PROPERTY_FLAGS}.
	 *
	 * @return The same annotations.
	 */
	public static IStruct normalize( IStruct annotations, Set<Key> flags ) {
		if ( annotations == null ) {
			return null;
		}
		for ( Key flag : flags ) {
			if ( annotations.containsKey( flag ) && isValueless( annotations.get( flag ) ) ) {
				annotations.put( flag, Boolean.TRUE );
			}
		}
		return annotations;
	}
}
