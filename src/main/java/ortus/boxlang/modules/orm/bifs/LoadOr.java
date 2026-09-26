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
package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * The shared steps of {@code entityLoadOrNew()}, {@code entityLoadOrSave()}, {@code entityLoadOrFail()} and
 * {@code entityLoadByPKOrFail()}.
 */
final class LoadOr {

	/**
	 * Not instantiable.
	 */
	private LoadOr() {
	}

	/**
	 * The ORM app for a BIF call.
	 *
	 * @param context The BIF's context.
	 *
	 * @return The ORM app.
	 */
	static ORMApp app( IBoxContext context ) {
		return ORMContext.getForContext( jdbc( context ) ).requireORMApp();
	}

	/**
	 * The JDBC-capable context a BIF runs in.
	 *
	 * @param context The BIF's context.
	 *
	 * @return The JDBC-capable context.
	 */
	static IBoxContext jdbc( IBoxContext context ) {
		return context.getParentOfType( IJDBCCapableContext.class );
	}

	/**
	 * Load at most one entity by id or filter.
	 *
	 * @param context    The BIF's context.
	 * @param entityName The entity name.
	 * @param idOrFilter An id, a composite key struct or a filter struct.
	 * @param operation  The BIF name, for errors.
	 *
	 * @return The entity, or null when none matched.
	 */
	static IClassRunnable find( IBoxContext context, String entityName, Object idOrFilter, String operation ) {
		return app( context ).loadOne( jdbc( context ), entityName, idOrFilter, operation );
	}

	/**
	 * A new, unsaved entity for a load that found nothing: a filter's values (or an assigned id) plus the given
	 * properties, which win.
	 *
	 * @param context    The BIF's context.
	 * @param entityName The entity name.
	 * @param idOrFilter The id or filter that found nothing.
	 * @param properties Extra property values for the new entity; may be null.
	 *
	 * @return The new entity.
	 */
	static IClassRunnable newEntity( IBoxContext context, String entityName, Object idOrFilter, IStruct properties ) {
		IStruct values = new Struct();
		if ( idOrFilter instanceof IStruct filter ) {
			values.putAll( filter );
		} else if ( idOrFilter != null ) {
			// A simple id is only copied when the application assigns ids; a generated id comes from the database.
			IPropertyMeta assignedId = assignedId( app( context ).lookupEntity( entityName, true ) );
			if ( assignedId != null ) {
				values.put( Key.of( assignedId.getName() ), idOrFilter );
			}
		}
		if ( properties != null ) {
			values.putAll( properties );
		}
		return EntityNew.create( context, entityName, values );
	}

	/**
	 * The id property when the entity has a single, application-assigned id (no generator, or generator="assigned").
	 *
	 * @param record The entity.
	 *
	 * @return The id property, or null for a generated or composite id.
	 */
	private static IPropertyMeta assignedId( EntityRecord record ) {
		if ( record.getEntityMeta() == null || record.getEntityMeta().getIdProperties().size() != 1 ) {
			return null;
		}
		IPropertyMeta	id			= record.getEntityMeta().getIdProperties().iterator().next();
		Object			generator	= id.getGenerator() == null ? null : id.getGenerator().get( Key._CLASS );
		return generator == null || "assigned".equalsIgnoreCase( generator.toString().trim() ) ? id : null;
	}

	/**
	 * The error for a load that must find an entity but did not.
	 *
	 * @param entityName The entity name.
	 * @param idOrFilter The id or filter used.
	 * @param operation  The BIF name.
	 *
	 * @return The {@code orm.notFound} error.
	 */
	static ORMException notFound( String entityName, Object idOrFilter, String operation ) {
		String	by		= idOrFilter instanceof IStruct ? "matching the filter " + idOrFilter.toString()
		    : "with id [" + idOrFilter + "]";
		IStruct	info	= Struct.of( "entityName", entityName, "operation", operation );
		if ( idOrFilter != null ) {
			info.put( idOrFilter instanceof IStruct ? "filter" : "id", idOrFilter );
		}
		return new ORMException( ORMErrorType.NOT_FOUND, "No [" + entityName + "] " + by + " was found.",
		    "Check the id or filter, or use entityLoadOrNew() / entityLoad() when a missing row is expected.", info, null );
	}
}
