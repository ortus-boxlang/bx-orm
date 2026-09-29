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

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

import net.bytebuddy.description.annotation.AnnotationDescription;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Turns BoxLang entity and property annotations into the Hibernate Java annotations the generated facade carries.
 * <p>
 * Some Hibernate features have no element in the {@code mapping.xml} format bx-orm generates (soft delete, creation and
 * update timestamps). Hibernate still reads Java annotations on the mapped class alongside the XML, so the facade class
 * (generated with ByteBuddy) carries them. Each supported BoxLang annotation is one entry here:
 * <ul>
 * <li>entity {@code softDelete="true|active|timestamp"} (+ {@code softDeleteColumn}): {@code @SoftDelete} on the class</li>
 * <li>property {@code autoTimestamp="create|update"}: {@code @CreationTimestamp} / {@code @UpdateTimestamp} on the getter</li>
 * </ul>
 */
public final class FacadeAnnotations {

	/** Entity annotation: soft delete strategy. */
	public static final Key	SOFT_DELETE			= ORMKeys.softDelete;
	/** Entity annotation: soft delete column name. */
	public static final Key	SOFT_DELETE_COLUMN	= ORMKeys.softDeleteColumn;
	/** Property annotation: automatic timestamp. */
	public static final Key	AUTO_TIMESTAMP		= ORMKeys.autoTimestamp;

	/**
	 * Not instantiable.
	 */
	private FacadeAnnotations() {
	}

	/**
	 * The Java annotations for an entity's facade class.
	 *
	 * @param meta The entity metadata.
	 *
	 * @return The annotations (empty when none apply).
	 */
	public static List<AnnotationDescription> forEntity( IEntityMeta meta ) {
		List<AnnotationDescription>	result		= new ArrayList<>();
		IStruct						annotations	= annotations( meta );
		Object						softDelete	= annotations.get( SOFT_DELETE );
		if ( softDelete != null && !softDelete.toString().isBlank() && !"false".equalsIgnoreCase( softDelete.toString().trim() )
		    && !"no".equalsIgnoreCase( softDelete.toString().trim() ) ) {
			if ( meta.isSubclass() ) {
				throw new ORMException( ORMErrorType.CONFIG,
				    "[" + meta.getEntityName() + "] declares softDelete, but it is a subclass entity.",
				    "Declare softDelete on the root entity of the hierarchy; its subclasses inherit it." );
			}
			AnnotationDescription.Builder	builder	= AnnotationDescription.Builder.ofType( SoftDelete.class )
			    .define( "strategy", softDeleteStrategy( meta.getEntityName(), softDelete.toString() ) );
			Object							column	= annotations.get( SOFT_DELETE_COLUMN );
			if ( column != null && !column.toString().isBlank() ) {
				builder = builder.define( "columnName", column.toString().trim() );
			}
			result.add( builder.build() );
		}
		return result;
	}

	/**
	 * The Java annotations for a property's facade getter.
	 *
	 * @param prop The property metadata.
	 *
	 * @return The annotations (empty when none apply).
	 */
	public static List<AnnotationDescription> forProperty( IPropertyMeta prop ) {
		List<AnnotationDescription> result = new ArrayList<>();
		if ( prop.getAnnotations() == null ) {
			return result;
		}
		Object autoTimestamp = prop.getAnnotations().get( AUTO_TIMESTAMP );
		if ( autoTimestamp != null && !autoTimestamp.toString().isBlank() ) {
			switch ( autoTimestamp.toString().trim().toLowerCase() ) {
				case "create", "created", "insert" -> result.add( AnnotationDescription.Builder.ofType( CreationTimestamp.class ).build() );
				case "update", "updated" -> result.add( AnnotationDescription.Builder.ofType( UpdateTimestamp.class ).build() );
				default -> throw new ORMException( ORMErrorType.CONFIG,
				    "Property [" + prop.getName() + "] has autoTimestamp=\"" + autoTimestamp + "\", which is not a known value.",
				    "Use autoTimestamp=\"create\" (set once on insert) or autoTimestamp=\"update\" (set on every insert and update)." );
			}
		}
		return result;
	}

	/**
	 * Whether a property carries an automatic timestamp.
	 *
	 * @param prop The property metadata.
	 *
	 * @return True when {@code autoTimestamp} is set.
	 */
	public static boolean isAutoTimestamp( IPropertyMeta prop ) {
		return prop.getAnnotations() != null && prop.getAnnotations().get( AUTO_TIMESTAMP ) != null
		    && !prop.getAnnotations().get( AUTO_TIMESTAMP ).toString().isBlank();
	}

	/**
	 * The entity's annotations struct.
	 *
	 * @param meta The entity metadata.
	 *
	 * @return The annotations (never null).
	 */
	private static IStruct annotations( IEntityMeta meta ) {
		IStruct annotations = meta.getMeta() == null ? null : meta.getMeta().getAsStruct( Key.annotations );
		return annotations == null ? new ortus.boxlang.runtime.types.Struct() : annotations;
	}

	/**
	 * Map a {@code softDelete} value to Hibernate's strategy.
	 *
	 * @param entityName The entity name (for the error).
	 * @param value      The annotation value.
	 *
	 * @return The strategy.
	 */
	private static SoftDeleteType softDeleteStrategy( String entityName, String value ) {
		return switch ( value.trim().toLowerCase() ) {
			case "true", "yes", "deleted" -> SoftDeleteType.DELETED;
			case "active" -> SoftDeleteType.ACTIVE;
			case "timestamp" -> SoftDeleteType.TIMESTAMP;
			default -> throw new ORMException( ORMErrorType.CONFIG,
			    "[" + entityName + "] has softDelete=\"" + value + "\", which is not a known value.",
			    "Use softDelete=\"true\" (a boolean deleted column), \"active\" (an inverted active column) or \"timestamp\" (a deleted date column)." );
		};
	}
}
