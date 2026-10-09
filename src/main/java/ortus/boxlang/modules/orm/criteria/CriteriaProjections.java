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

import java.util.Map;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.IReferenceable;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.Key;

/**
 * The object {@code project( ( p ) => ... )} passes to its closure. Each method adds one selected column and returns
 * the same object, so calls chain:
 *
 * <pre>
 * c.project( ( p ) => p.group( "make" ).count( "vin", "total" ).avg( "price" ) )
 * </pre>
 *
 * Methods: {@code property}, {@code group} (alias {@code groupProperty}), {@code sum}, {@code avg}, {@code min},
 * {@code max}, {@code count}, {@code countDistinct} (each takes a property and an optional alias), {@code rowCount}
 * and {@code id} (each takes an optional alias). The alias names the column in {@code asStruct()} and
 * {@code asQuery()} results; it defaults to the property name.
 */
public final class CriteriaProjections implements IReferenceable {

	/** The criteria the columns are added to. */
	private final CriteriaBuilder criteria;

	/**
	 * Create the projection object for a criteria.
	 *
	 * @param criteria The criteria.
	 */
	CriteriaProjections( CriteriaBuilder criteria ) {
		this.criteria = criteria;
	}

	/**
	 * Add a column.
	 *
	 * @param method The method name.
	 * @param args   The arguments: property and alias (or only an alias for rowCount/id).
	 *
	 * @return This object.
	 */
	private Object add( String method, Object[] args ) {
		String	first	= args.length > 0 && args[ 0 ] != null ? StringCaster.cast( args[ 0 ] ) : null;
		String	second	= args.length > 1 && args[ 1 ] != null ? StringCaster.cast( args[ 1 ] ) : null;
		String	function;
		switch ( method.toLowerCase() ) {
			case "property" -> function = "property";
			case "group", "groupproperty" -> function = "group";
			case "sum", "avg", "min", "max", "count" -> function = method.toLowerCase();
			case "countdistinct" -> function = "countDistinct";
			case "rowcount" -> {
				criteria.project( "rowCount", null, first );
				return this;
			}
			case "id" -> {
				criteria.project( "id", null, first );
				return this;
			}
			default -> throw new ORMException( ORMErrorType.ARGUMENT, "Projections have no method [" + method + "()]."
			    + ORMErrors.suggestion( method, java.util.List.of( "property", "group", "groupProperty", "sum", "avg", "min", "max", "count",
			        "countDistinct", "rowCount", "id" ) ),
			    "Use property, group, sum, avg, min, max, count, countDistinct, rowCount or id." );
		}
		if ( first == null || first.isBlank() ) {
			throw new ORMException( ORMErrorType.ARGUMENT, method + "() needs a property name.", "Example: p." + method + "( \"price\" )." );
		}
		criteria.project( function, first, second );
		return this;
	}

	/**
	 * Projections have no readable properties.
	 *
	 * @param context The context.
	 * @param name    The property name.
	 * @param safe    Whether to return null instead of failing.
	 *
	 * @return Null when safe.
	 */
	@Override
	public Object dereference( IBoxContext context, Key name, Boolean safe ) {
		if ( Boolean.TRUE.equals( safe ) ) {
			return null;
		}
		throw new ORMException( ORMErrorType.ARGUMENT, "Projections have no property [" + name.getName() + "].", "Call a method, e.g. p.sum( \"price\" )." );
	}

	/**
	 * Call a projection method with positional arguments.
	 *
	 * @param context   The context.
	 * @param name      The method name.
	 * @param arguments The arguments.
	 * @param safe      Unused.
	 *
	 * @return This object.
	 */
	@Override
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Object[] arguments, Boolean safe ) {
		return add( name.getName(), arguments );
	}

	/**
	 * Call a projection method with named arguments ({@code property}, {@code alias}).
	 *
	 * @param context   The context.
	 * @param name      The method name.
	 * @param arguments The arguments by name.
	 * @param safe      Unused.
	 *
	 * @return This object.
	 */
	@Override
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Map<Key, Object> arguments, Boolean safe ) {
		Object	property	= arguments.getOrDefault( Key.of( "property" ), arguments.get( Key.of( "propertyName" ) ) );
		Object	alias		= arguments.get( Key.of( "alias" ) );
		boolean	aliasOnly	= name.getName().equalsIgnoreCase( "rowCount" ) || name.getName().equalsIgnoreCase( "id" );
		return add( name.getName(), aliasOnly ? new Object[] { alias } : new Object[] { property, alias } );
	}

	/**
	 * Projections cannot be assigned.
	 *
	 * @param context The context.
	 * @param name    The property name.
	 * @param value   The value.
	 *
	 * @return Never returns.
	 */
	@Override
	public Object assign( IBoxContext context, Key name, Object value ) {
		throw new ORMException( ORMErrorType.ARGUMENT, "Projections cannot be assigned.", "Call a method, e.g. p.sum( \"price\" )." );
	}

	/**
	 * A short description for dumps.
	 *
	 * @return The description.
	 */
	@Override
	public String toString() {
		return "Projections of " + criteria.getEntityName();
	}
}
