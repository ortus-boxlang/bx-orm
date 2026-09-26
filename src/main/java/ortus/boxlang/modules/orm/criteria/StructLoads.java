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

import org.hibernate.engine.spi.SessionFactoryImplementor;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * {@code entityLoadAsStruct()}: an id or filter load that returns structs built like {@code entityToStruct()}, read
 * through a criteria and {@link MementoProjection} so no entity is loaded.
 */
public final class StructLoads {

	/** {@code sortOrder}. */
	private static final Key SORT_ORDER = Key.of( "sortOrder" );

	/**
	 * Not instantiable.
	 */
	private StructLoads() {
	}

	/**
	 * Load by id (a struct or null) or by filter (an array of structs, or one struct with {@code unique}).
	 *
	 * @param app        The ORM application.
	 * @param context    The context.
	 * @param entityName The entity name.
	 * @param idOrFilter An id, a composite key struct, or a struct of property values to match.
	 * @param includes   The includes (list or array), or null for the entity's defaults.
	 * @param options    {@code sortOrder}, {@code maxResults}, {@code offset}, {@code unique}, and the struct options
	 *                   {@code excludes}, {@code mappers}, {@code defaults}, {@code ignoreDefaults}, {@code profile}; may
	 *                   be null.
	 *
	 * @return A struct or null for an id (or a unique filter), else an array of structs.
	 */
	public static Object load( ORMApp app, IBoxContext context, String entityName, Object idOrFilter, Object includes, IStruct options ) {
		EntityRecord				record		= app.lookupEntity( entityName, true );
		SessionFactoryImplementor	factory		= ( SessionFactoryImplementor ) app.getSessionFactoryOrThrow( record.getDatasource(), context );
		CriteriaBuilder				criteria	= new CriteriaBuilder( app, record, factory );
		boolean						single;
		if ( idOrFilter instanceof IStruct filter && !app.isCompositeId( entityName, filter ) ) {
			for ( Key key : filter.keySet() ) {
				Object value = filter.get( key );
				if ( value == null ) {
					criteria.unary( key.getName(), " is null" );
				} else {
					criteria.compare( key.getName(), "=", value );
				}
			}
			single = options != null && BooleanCaster.cast( options.getOrDefault( ORMKeys.unique, false ) );
		} else if ( idOrFilter instanceof IStruct composite ) {
			composite.forEach( ( key, value ) -> criteria.compare( key.getName(), "=", value ) );
			single = true;
		} else {
			if ( idOrFilter == null ) {
				throw new ortus.boxlang.modules.orm.errors.ORMException( ortus.boxlang.modules.orm.errors.ORMErrorType.ARGUMENT,
				    "entityLoadAsStruct() needs an id or a filter struct.", "Pass an id, or a struct such as { active : true }." );
			}
			IPropertyMeta id = record.getEntityMeta().getIdProperties().iterator().next();
			criteria.compare( id.getName(), "=", idOrFilter );
			single = true;
		}
		if ( options != null ) {
			if ( options.get( SORT_ORDER ) != null && !options.get( SORT_ORDER ).toString().isBlank() ) {
				criteria.order( options.get( SORT_ORDER ).toString(), "asc", false );
			}
			if ( options.get( ORMKeys.maxResults ) != null ) {
				criteria.maxResults( options.get( ORMKeys.maxResults ) );
			}
			if ( options.get( Key.offset ) != null ) {
				criteria.firstResult( options.get( Key.offset ) );
			}
		}
		criteria.asStruct( includes == null ? "" : includes, options );
		if ( single ) {
			Array rows = MementoProjection.list( criteria, context, null, 2 );
			if ( rows.size() > 1 ) {
				throw ORMErrors.nonUniqueResult( 0, "entityLoadAsStruct", null );
			}
			return rows.isEmpty() ? null : rows.get( 0 );
		}
		Integer[] paging = criteria.paging();
		return MementoProjection.list( criteria, context, paging[ 0 ], paging[ 1 ] );
	}
}
