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

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.ArrayCaster;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCasterStrict;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.BLCollector;
import ortus.boxlang.runtime.types.util.ListUtil;

@BoxBIF
public class EntityLoad extends BaseORMBIF {

	/**
	 * Default options for loading entities.
	 */
	private final IStruct DEFAULT_OPTIONS = Struct.of(
	    // Specifies whether to retrieve a single, unique item. Default is false.
	    "unique", Boolean.FALSE,
	    // Ignores the case of sort order when set to true. Use only if you specify the sortorder parameter.
	    "ignorecase", Boolean.FALSE,
	    // Specifies the position from which to retrieve the objects.
	    "offset", 0,
	    // Specifies the maximum number of objects to be retrieved.
	    "maxresults", null,
	    // Whether the result has to be cached in the secondary cache. Default is false.
	    "cacheable", Boolean.FALSE,
	    // Name of the cache in secondary cache.
	    "cachename", null,
	    // Specifies the timeout value (in seconds) for the query.
	    "timeout", null
	);

	/**
	 * Constructor
	 */
	public EntityLoad() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", ORMKeys.entityName ),
		    new Argument( false, "any", ORMKeys.idOrFilter ),
		    new Argument( false, "any", ORMKeys.uniqueOrOrder ),
		    new Argument( false, "struct", ORMKeys.options )
		};
	}

	/**
	 * Load an entity or array of entities from the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( arguments.get( ORMKeys.uniqueOrOrder ) != null && arguments.get( ORMKeys.options ) == null
		    && arguments.get( ORMKeys.uniqueOrOrder ) instanceof IStruct ) {
			// If the uniqueOrOrder is a struct, we need to move it to options
			arguments.put( ORMKeys.options, arguments.get( ORMKeys.uniqueOrOrder ) );
			arguments.remove( ORMKeys.uniqueOrOrder );
		} else if ( arguments.get( ORMKeys.uniqueOrOrder ) != null ) {
			arguments.put( ORMKeys.uniqueOrOrder, StringCaster.cast( arguments.get( ORMKeys.uniqueOrOrder ) ) );
		}
		if ( arguments.containsKey( ORMKeys.idOrFilter ) ) {
			boolean idIsSimpleValue = StringCasterStrict.attempt( arguments.get( ORMKeys.idOrFilter ) ).wasSuccessful();
			if ( idIsSimpleValue ) {
				return loadEntityById( context, arguments );
			}
		}
		// EITHER: No filter or was ID provided, so load all entities as an array...
		// OR a non-simple value was provided (i.e. a struct or array), so load by filter.
		return loadEntitiesByFilter( context, arguments );
	}

	/**
	 * Load an entity or array of entities by ID.
	 *
	 * @param context   Context in which the BIF was invoked.
	 * @param arguments Arguments scope of the BIF.
	 */
	private Object loadEntityById( IBoxContext context, ArgumentsScope arguments ) {
		if ( BooleanCaster.cast( arguments.getOrDefault( ORMKeys.uniqueOrOrder, "false" ) ) ) {
			return ormService.getORMAppByContext( context ).loadEntityById( context.getRequestContext(), arguments.getAsString( ORMKeys.entityName ),
			    arguments.get( ORMKeys.idOrFilter ) );
		}
		var entity = ormService.getORMAppByContext( context ).loadEntityById( context.getRequestContext(), arguments.getAsString( ORMKeys.entityName ),
		    arguments.get( ORMKeys.idOrFilter ) );
		return entity == null ? Array.EMPTY : Array.of( entity );
	}

	/**
	 * Load an array of entities by filter criteria.
	 *
	 * @param context   Context in which the BIF was invoked.
	 * @param arguments Arguments scope of the BIF.
	 */
	private Object loadEntitiesByFilter( IBoxContext context, ArgumentsScope arguments ) {
		IStruct	options	= buildCriteriaOptions( arguments );
		IStruct	filter	= arguments.getAsStruct( ORMKeys.idOrFilter );

		Array	results	= ormService.getORMAppByContext( context ).loadEntitiesByFilter( context.getRequestContext(),
		    arguments.getAsString( ORMKeys.entityName ), filter, options );
		if ( options.getAsBoolean( ORMKeys.unique ) ) {
			return results.isEmpty() ? null : results.getFirst();
		}
		return results;
	}

	private IStruct buildCriteriaOptions( ArgumentsScope arguments ) {
		IStruct options = Struct.of();
		options.putAll( DEFAULT_OPTIONS );
		if ( arguments.containsKey( ORMKeys.options ) && arguments.get( ORMKeys.options ) != null ) {
			options.putAll( arguments.getAsStruct( ORMKeys.options ) );
		}
		if ( arguments.containsKey( ORMKeys.uniqueOrOrder ) ) {
			Boolean unique = BooleanCaster.cast( arguments.get( ORMKeys.uniqueOrOrder ), false );
			if ( unique != null ) {
				options.put( "unique", unique );
				if ( unique ) {
					options.put( "maxresults", 1 );
				}
			} else {
				options.put(
				    ORMKeys.orderBy,
				    ArrayCaster.cast(
				        ListUtil.asList( StringCaster.cast( arguments.get( ORMKeys.uniqueOrOrder ) ), "," )
				            .stream()
				            .map( String::valueOf )
				            .map( String::trim )
				            .map( item -> {
					            var parts = item.split( " " );
					            return Struct.of(
					                "property", parts[ 0 ],
					                "ascending", parts.length == 1 || "asc".equalsIgnoreCase( parts[ 1 ] )
					            );
				            } )
				            .collect( BLCollector.toArray() )
				    )
				);
			}
		}
		return options;
	}
}
