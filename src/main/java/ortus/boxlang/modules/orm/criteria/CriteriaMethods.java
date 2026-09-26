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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.criteria.CriteriaBuilder.JoinKind;
import ortus.boxlang.modules.orm.criteria.CriteriaBuilder.Shape;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Function;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Every method of {@code entityCriteria()} as BoxLang sees it: its names (aliases included), its argument names, and
 * the {@link CriteriaBuilder} code that runs it.
 * <p>
 * Method names are case-insensitive. Arguments can be positional or named (named arguments accept the documented name
 * or its alternates, e.g. {@code property} or {@code propertyName}). Two name patterns are handled dynamically:
 * {@code not<Condition>(...)} negates any condition ({@code notLike}, {@code notIn}, {@code notBetween}, ...) and
 * {@code with<Association>(...)} joins an association and scopes the following conditions to it. An unknown method
 * fails with the closest known name.
 */
final class CriteriaMethods {

	/**
	 * The code behind a method.
	 */
	@FunctionalInterface
	interface Body {

		/**
		 * Run the method.
		 *
		 * @param c   The builder.
		 * @param ctx The calling context.
		 * @param a   The arguments, one per declared parameter (missing ones are null; a variadic last parameter holds
		 *            an {@code Object[]}).
		 *
		 * @return The result (the builder itself for chaining methods).
		 */
		Object run( CriteriaBuilder c, IBoxContext ctx, Object[] a );
	}

	/**
	 * A method definition.
	 *
	 * @param name      The documented name.
	 * @param params    Per parameter, its accepted names (the first is the documented one).
	 * @param variadic  Whether the last parameter takes all remaining positional arguments.
	 * @param condition Whether the method adds a condition (negatable with {@code not}, announced with
	 *                  {@code onCriteriaBuilderAddition}).
	 * @param body      The code.
	 */
	record Spec( String name, String[][] params, boolean variadic, boolean condition, Body body ) {
	}

	/** Methods by lower-cased name and alias. */
	private static final Map<String, Spec>		METHODS			= new LinkedHashMap<>();

	/** The documented method names (for "Did you mean"). */
	private static final List<String>			NAMES			= new ArrayList<>();

	/** Java methods callable from BoxLang even though they are not criteria methods. */
	private static final java.util.Set<String>	JAVA_METHODS	= java.util.Set.of( "getclass", "hashcode", "equals", "getentityname" );

	/** The property argument and its alternates. */
	private static final String					P				= "property|propertyName";
	/** The value argument and its alternates. */
	private static final String					V				= "value|propertyValue";

	/**
	 * Not instantiable.
	 */
	private CriteriaMethods() {
	}

	/**
	 * Register a method.
	 *
	 * @param names     The name and its aliases, separated by {@code |}.
	 * @param params    The parameters, comma-separated, each with {@code |}-separated alternate names; a trailing
	 *                  {@code ...} marks a variadic last parameter.
	 * @param condition Whether it adds a condition.
	 * @param body      The code.
	 */
	private static void def( String names, String params, boolean condition, Body body ) {
		boolean		variadic	= params.endsWith( "..." );
		String		list		= variadic ? params.substring( 0, params.length() - 3 ) : params;
		String[][]	parsed		= list.isBlank() ? new String[ 0 ][]
		    : Arrays.stream( list.split( "," ) ).map( p -> p.trim().split( "\\|" ) )
		        .toArray( String[][]::new );
		String[]	all			= names.split( "\\|" );
		Spec		spec		= new Spec( all[ 0 ], parsed, variadic, condition, body );
		for ( String n : all ) {
			METHODS.put( n.toLowerCase(), spec );
			NAMES.add( n );
		}
	}

	/**
	 * A string argument.
	 *
	 * @param value The argument.
	 *
	 * @return The string, or null.
	 */
	private static String str( Object value ) {
		return value == null ? null : StringCaster.cast( value );
	}

	/**
	 * A boolean argument with a default.
	 *
	 * @param value The argument.
	 * @param def   The default when missing.
	 *
	 * @return The boolean.
	 */
	private static boolean bool( Object value, boolean def ) {
		return value == null || value.toString().isBlank() ? def : BooleanCaster.cast( value );
	}

	/**
	 * A struct argument.
	 *
	 * @param value  The argument.
	 * @param method The criteria method, for the error.
	 *
	 * @return The struct.
	 *
	 * @throws ORMException {@code orm.argument} when the value is not a struct.
	 */
	private static IStruct struct( Object value, String method ) {
		if ( value instanceof IStruct struct ) {
			return struct;
		}
		throw new ORMException( ORMErrorType.ARGUMENT,
		    "entityCriteria." + method + "() expected a struct but received " + ( value == null ? "nothing" : value.getClass().getSimpleName() ) + ".",
		    "Pass a struct, e.g. " + ( method.equals( "lock" ) ? "lock( \"write\", { timeout : 5 } )" : "updateAll( { status : \"archived\" } )" ) + "." );
	}

	static {
		/* ------------------------------------------------------------------------------------------------------- */
		/* Conditions */
		/* ------------------------------------------------------------------------------------------------------- */
		def( "isEq|eq", P + "," + V, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "=", a[ 1 ] ) );
		def( "ne|isNe|isNotEq|notEqual", P + "," + V, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "<>", a[ 1 ] ) );
		def( "isGt|gt", P + "," + V, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), ">", a[ 1 ] ) );
		def( "isGe|ge|isGte|gte", P + "," + V, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), ">=", a[ 1 ] ) );
		def( "isLt|lt", P + "," + V, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "<", a[ 1 ] ) );
		def( "isLe|le|isLte|lte", P + "," + V, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "<=", a[ 1 ] ) );
		def( "like|whereLike|isLike", P + "," + V, true, ( c, x, a ) -> c.like( str( a[ 0 ] ), a[ 1 ], false ) );
		def( "ilike|whereIlike|isIlike", P + "," + V, true, ( c, x, a ) -> c.like( str( a[ 0 ] ), a[ 1 ], true ) );
		def( "between|whereBetween|isBetween", P + ",min|minValue,max|maxValue", true, ( c, x, a ) -> c.between( str( a[ 0 ] ), a[ 1 ], a[ 2 ] ) );
		def( "isIn|in|whereIn", P + ",values|" + V, true, ( c, x, a ) -> c.in( str( a[ 0 ] ), a[ 1 ], false ) );
		def( "isNotIn|whereNotIn", P + ",values|" + V, true, ( c, x, a ) -> c.in( str( a[ 0 ] ), a[ 1 ], true ) );
		def( "isNull|whereNull", P, true, ( c, x, a ) -> c.unary( str( a[ 0 ] ), " is null" ) );
		def( "isNotNull|whereNotNull", P, true, ( c, x, a ) -> c.unary( str( a[ 0 ] ), " is not null" ) );
		def( "isTrue", P, true, ( c, x, a ) -> c.unary( str( a[ 0 ] ), " = true" ) );
		def( "isFalse", P, true, ( c, x, a ) -> c.unary( str( a[ 0 ] ), " = false" ) );
		def( "isEmpty", P, true, ( c, x, a ) -> c.empty( str( a[ 0 ] ), true ) );
		def( "isNotEmpty", P, true, ( c, x, a ) -> c.empty( str( a[ 0 ] ), false ) );
		String S = P + ",size|" + V;
		def( "sizeEq", S, true, ( c, x, a ) -> c.size( str( a[ 0 ] ), "=", a[ 1 ] ) );
		def( "sizeNe", S, true, ( c, x, a ) -> c.size( str( a[ 0 ] ), "<>", a[ 1 ] ) );
		def( "sizeGt", S, true, ( c, x, a ) -> c.size( str( a[ 0 ] ), ">", a[ 1 ] ) );
		def( "sizeGe", S, true, ( c, x, a ) -> c.size( str( a[ 0 ] ), ">=", a[ 1 ] ) );
		def( "sizeLt", S, true, ( c, x, a ) -> c.size( str( a[ 0 ] ), "<", a[ 1 ] ) );
		def( "sizeLe", S, true, ( c, x, a ) -> c.size( str( a[ 0 ] ), "<=", a[ 1 ] ) );
		String PP = P + ",otherProperty|otherPropertyName";
		def( "eqProperty", PP, true, ( c, x, a ) -> c.compareProperties( str( a[ 0 ] ), "=", str( a[ 1 ] ) ) );
		def( "neProperty", PP, true, ( c, x, a ) -> c.compareProperties( str( a[ 0 ] ), "<>", str( a[ 1 ] ) ) );
		def( "gtProperty", PP, true, ( c, x, a ) -> c.compareProperties( str( a[ 0 ] ), ">", str( a[ 1 ] ) ) );
		def( "geProperty", PP, true, ( c, x, a ) -> c.compareProperties( str( a[ 0 ] ), ">=", str( a[ 1 ] ) ) );
		def( "ltProperty", PP, true, ( c, x, a ) -> c.compareProperties( str( a[ 0 ] ), "<", str( a[ 1 ] ) ) );
		def( "leProperty", PP, true, ( c, x, a ) -> c.compareProperties( str( a[ 0 ] ), "<=", str( a[ 1 ] ) ) );
		def( "idEq", "id|" + V, true, ( c, x, a ) -> c.idEq( a[ 0 ] ) );
		def( "sql|sqlRestriction", "sql|fragment,params|values", true, ( c, x, a ) -> c.sql( str( a[ 0 ] ), a[ 1 ] ) );
		def( "where", P + "|filter,operator|" + V + ",value", true, ( c, x, a ) -> {
			if ( a[ 0 ] instanceof IStruct filter && a[ 1 ] == null ) {
				return c.whereStruct( filter );
			}
			if ( a[ 0 ] instanceof Function ) {
				return c.group( x, false, a[ 0 ] );
			}
			if ( a.length > 2 && a[ 2 ] != null ) {
				return c.whereOperator( str( a[ 0 ] ), str( a[ 1 ] ), a[ 2 ] );
			}
			return c.compare( str( a[ 0 ] ), "=", a[ 1 ] );
		} );
		def( "not|isNot", "callback|fn", false, ( c, x, a ) -> c.not( x, a[ 0 ] ) );
		def( "anyOf|$or|or|orWhere|disjunction", "callbacks...", false, ( c, x, a ) -> c.group( x, true, ( Object[] ) a[ 0 ] ) );
		def( "allOf|$and|and|conjunction", "callbacks...", false, ( c, x, a ) -> c.group( x, false, ( Object[] ) a[ 0 ] ) );
		def( "exists", "subquery|criteria", true, ( c, x, a ) -> a[ 0 ] == null ? c.exists( x ) : c.exists( CriteriaBuilder.requireSub( a[ 0 ] ), false ) );
		def( "notExists", "subquery|criteria", true, ( c, x, a ) -> c.exists( CriteriaBuilder.requireSub( a[ 0 ] ), true ) );
		String PS = P + ",subquery|criteria";
		def( "propertyEq", PS, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "=", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "propertyNe", PS, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "<>", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "propertyGt", PS, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), ">", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "propertyGe", PS, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), ">=", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "propertyLt", PS, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "<", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "propertyLe", PS, true, ( c, x, a ) -> c.compare( str( a[ 0 ] ), "<=", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "propertyIn", PS, true, ( c, x, a ) -> c.in( str( a[ 0 ] ), CriteriaBuilder.requireSub( a[ 1 ] ), false ) );
		def( "propertyNotIn", PS, true, ( c, x, a ) -> c.in( str( a[ 0 ] ), CriteriaBuilder.requireSub( a[ 1 ] ), true ) );
		String VS = V + ",subquery|criteria";
		def( "subEq", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], "=", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subNe", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], "<>", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subGt", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], ">", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subGe", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], ">=", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subLt", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], "<", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subLe", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], "<=", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subIn", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], "in", CriteriaBuilder.requireSub( a[ 1 ] ) ) );
		def( "subNotIn", VS, true, ( c, x, a ) -> c.valueVsSubquery( a[ 0 ], "not in", CriteriaBuilder.requireSub( a[ 1 ] ) ) );

		/* ------------------------------------------------------------------------------------------------------- */
		/* Joins */
		/* ------------------------------------------------------------------------------------------------------- */
		String A = "association|associationName";
		def( "joinTo|createAlias", A + ",alias,joinType", false,
		    ( c, x, a ) -> c.joinTo( str( a[ 0 ] ), str( a[ 1 ] ), JoinKind.parse( a[ 2 ], JoinKind.INNER ) ) );
		def( "innerJoin", A + ",alias", false, ( c, x, a ) -> c.joinTo( str( a[ 0 ] ), str( a[ 1 ] ), JoinKind.INNER ) );
		def( "leftJoin", A + ",alias", false, ( c, x, a ) -> c.joinTo( str( a[ 0 ] ), str( a[ 1 ] ), JoinKind.LEFT ) );
		def( "rightJoin", A + ",alias", false, ( c, x, a ) -> c.joinTo( str( a[ 0 ] ), str( a[ 1 ] ), JoinKind.RIGHT ) );
		def( "fullJoin", A + ",alias", false, ( c, x, a ) -> c.joinTo( str( a[ 0 ] ), str( a[ 1 ] ), JoinKind.FULL ) );
		def( "fetch|joinFetch", A, false, ( c, x, a ) -> c.fetch( str( a[ 0 ] ) ) );
		def( "createCriteria", A + ",joinType|callback,callback", false, ( c, x, a ) -> withCall( c, x, str( a[ 0 ] ), a[ 1 ], a[ 2 ] ) );
		def( "end|endAssociation|resetCriteria", "", false, ( c, x, a ) -> c.end() );

		/* ------------------------------------------------------------------------------------------------------- */
		/* Flow */
		/* ------------------------------------------------------------------------------------------------------- */
		def( "when", "test|condition,callback|apply,otherwise|else", false, ( c, x, a ) -> c.when( x, a[ 0 ], a[ 1 ], a[ 2 ] ) );
		def( "unless", "test|condition,callback|apply,otherwise|else", false,
		    ( c, x, a ) -> c.when( x, a[ 0 ] instanceof Function ? a[ 0 ] : !bool( a[ 0 ], false ), a[ 0 ] instanceof Function ? a[ 2 ] : a[ 1 ],
		        a[ 0 ] instanceof Function ? a[ 1 ] : a[ 2 ] ) );
		def( "apply|scope", "callback|fragment", false, ( c, x, a ) -> {
			CriteriaBuilder.call( x, a[ 0 ], c );
			return c;
		} );
		def( "peek|tap", "callback", false, ( c, x, a ) -> {
			CriteriaBuilder.call( x, a[ 0 ], c );
			return c;
		} );
		def( "copy|clone", "", false, ( c, x, a ) -> c.copy() );
		def( "subquery|createSubcriteria|detachedCriteria", "entityName|entity,alias", false, ( c, x, a ) -> c.subquery( str( a[ 0 ] ), str( a[ 1 ] ) ) );

		/* ------------------------------------------------------------------------------------------------------- */
		/* Shape */
		/* ------------------------------------------------------------------------------------------------------- */
		def( "project|projections", "callback", false, ( c, x, a ) -> {
			CriteriaBuilder.call( x, a[ 0 ], new CriteriaProjections( c ) );
			return c;
		} );
		def( "withProjections", "projections", false, ( c, x, a ) -> c.withProjections( ( IStruct ) a[ 0 ] ) );
		def( "asDistinct|distinct", "", false, ( c, x, a ) -> c.asDistinct() );
		def( "asStruct|asStructs", "", false, ( c, x, a ) -> c.shape( Shape.STRUCT ) );
		def( "asQuery", "", false, ( c, x, a ) -> c.shape( Shape.QUERY ) );
		def( "asStream", "", false, ( c, x, a ) -> c.shape( Shape.STREAM ) );
		def( "asEntities|asArray", "", false, ( c, x, a ) -> c.shape( Shape.ENTITY ) );
		def( "order|orderBy|sort", P + "|sortOrder,direction|sortDir|order,ignoreCase", false,
		    ( c, x, a ) -> c.order( str( a[ 0 ] ), str( a[ 1 ] ), bool( a[ 2 ], false ) ) );
		def( "orderByDesc|sortDesc", P, false, ( c, x, a ) -> c.order( str( a[ 0 ] ), "desc", false ) );
		def( "firstResult|offset|skip", "offset|firstResult", false, ( c, x, a ) -> c.firstResult( a[ 0 ] ) );
		def( "maxResults|limit|take", "max|maxResults", false, ( c, x, a ) -> c.maxResults( a[ 0 ] ) );

		/* ------------------------------------------------------------------------------------------------------- */
		/* Options */
		/* ------------------------------------------------------------------------------------------------------- */
		def( "cache|cacheable", "cache|cacheable|region,region|cacheRegion", false, ( c, x, a ) -> {
			Object first = a[ 0 ];
			if ( first != null && ! ( first instanceof Boolean ) && !isBooleanText( first ) ) {
				c.option( ORMKeys.cacheable, true );
				return c.option( ORMKeys.cacheName, str( first ) );
			}
			c.option( ORMKeys.cacheable, bool( first, true ) );
			if ( a[ 1 ] != null && !str( a[ 1 ] ).isBlank() ) {
				c.option( ORMKeys.cacheName, str( a[ 1 ] ) );
			}
			return c;
		} );
		def( "readOnly", "readOnly", false, ( c, x, a ) -> c.option( ORMKeys.readOnly, bool( a[ 0 ], true ) ) );
		def( "timeout", "timeout|seconds", false, ( c, x, a ) -> c.option( Key.timeout, IntegerCaster.cast( a[ 0 ] ) ) );
		def( "lock", "mode,options", false, ( c, x, a ) -> c.lock( a[ 0 ], a[ 1 ] == null ? null : struct( a[ 1 ], "lock" ) ) );
		def( "fetchSize", "fetchSize|size", false, ( c, x, a ) -> c.option( Key.fetchSize, IntegerCaster.cast( a[ 0 ] ) ) );
		def( "comment", "comment|text", false, ( c, x, a ) -> c.option( ORMKeys.comment, str( a[ 0 ] ) ) );
		def( "queryHint|hint", "name|hint,value", false, ( c, x, a ) -> c.hint( str( a[ 0 ] ), a[ 1 ] ) );

		/* ------------------------------------------------------------------------------------------------------- */
		/* SQL */
		/* ------------------------------------------------------------------------------------------------------- */
		def( "getSQL|toSQL", "executable|returnExecutableSQL,format|formatSQL", false,
		    ( c, x, a ) -> c.getSQL( x, bool( a[ 0 ], false ), bool( a[ 1 ], true ) ) );
		def( "getHQL|toHQL", "", false, ( c, x, a ) -> c.getHQL() );
		def( "peekSQL", "callback,executable|returnExecutableSQL", false, ( c, x, a ) -> {
			CriteriaBuilder.call( x, a[ 0 ], c.getSQL( x, bool( a[ 1 ], false ), true ) );
			return c;
		} );
		def( "logSQL", "label,executable|returnExecutableSQL", false, ( c, x, a ) -> {
			String label = a[ 0 ] == null ? "entityCriteria" : str( a[ 0 ] );
			( ( ortus.boxlang.modules.orm.ORMService ) ortus.boxlang.runtime.BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService ) )
			    .getLogger().info( "[{}] {}", label, c.getSQL( x, bool( a[ 1 ], true ), true ) );
			return c;
		} );
		def( "toString", "", false, ( c, x, a ) -> c.toString() );

		/* ------------------------------------------------------------------------------------------------------- */
		/* Terminals */
		/* ------------------------------------------------------------------------------------------------------- */
		def( "list", "max,offset,timeout,sortOrder,ignoreCase,asQuery", false, ( c, x, a ) -> c.list( x, a[ 0 ], a[ 1 ], a[ 2 ], a[ 3 ], a[ 4 ], a[ 5 ] ) );
		def( "count", P, false, ( c, x, a ) -> c.count( x, str( a[ 0 ] ) ) );
		def( "updateAll", "values", false, ( c, x, a ) -> c.updateAll( x, struct( a[ 0 ], "updateAll" ) ) );
		def( "deleteAll", "", false, ( c, x, a ) -> c.deleteAll( x ) );
		def( "get", "uniqueFirst", false, ( c, x, a ) -> c.get( x, bool( a[ 0 ], false ) ) );
		def( "getOrFail", "uniqueFirst", false, ( c, x, a ) -> c.getOrFail( x, bool( a[ 0 ], false ) ) );
		def( "first", "", false, ( c, x, a ) -> c.first( x ) );
		def( "firstOrFail", "", false, ( c, x, a ) -> c.firstOrFail( x ) );
		def( "paginate", "page,maxRows|perPage|pageSize", false, ( c, x, a ) -> c.paginate( x, a[ 0 ] == null ? 1 : a[ 0 ], a[ 1 ] == null ? 25 : a[ 1 ] ) );
		def( "simplePaginate", "page,maxRows|perPage|pageSize", false,
		    ( c, x, a ) -> c.simplePaginate( x, a[ 0 ] == null ? 1 : a[ 0 ], a[ 1 ] == null ? 25 : a[ 1 ] ) );
		def( "pluck", P, false, ( c, x, a ) -> c.pluck( x, str( a[ 0 ] ) ) );
		def( "sum", P, false, ( c, x, a ) -> c.aggregate( x, "sum", str( a[ 0 ] ) ) );
		def( "avg", P, false, ( c, x, a ) -> c.aggregate( x, "avg", str( a[ 0 ] ) ) );
		def( "min", P, false, ( c, x, a ) -> c.aggregate( x, "min", str( a[ 0 ] ) ) );
		def( "max", P, false, ( c, x, a ) -> c.aggregate( x, "max", str( a[ 0 ] ) ) );
		def( "each", "callback,size|chunkSize", false, ( c, x, a ) -> c.each( x, a[ 0 ], a[ 1 ] ) );
		def( "chunk", "size|chunkSize,callback", false, ( c, x, a ) -> c.chunk( x, a[ 0 ], a[ 1 ] ) );
	}

	/**
	 * Whether a value is boolean text ({@code true}, {@code false}, {@code yes}, {@code no}).
	 *
	 * @param value The value.
	 *
	 * @return True for boolean text.
	 */
	private static boolean isBooleanText( Object value ) {
		String text = value.toString().trim().toLowerCase();
		return text.equals( "true" ) || text.equals( "false" ) || text.equals( "yes" ) || text.equals( "no" );
	}

	/**
	 * {@code with{Association}( [joinType], [callback] )} and {@code createCriteria( association, joinType )}.
	 *
	 * @param c           The builder.
	 * @param ctx         The context.
	 * @param association The association path.
	 * @param first       A join type or a closure.
	 * @param second      A closure when the first argument is a join type.
	 *
	 * @return The builder.
	 */
	private static Object withCall( CriteriaBuilder c, IBoxContext ctx, String association, Object first, Object second ) {
		if ( first instanceof Function ) {
			return c.withAssociation( ctx, association, JoinKind.INNER, first );
		}
		return c.withAssociation( ctx, association, JoinKind.parse( first, JoinKind.INNER ), second );
	}

	/**
	 * Run a method called from BoxLang.
	 *
	 * @param c          The builder.
	 * @param ctx        The calling context.
	 * @param name       The method name.
	 * @param positional Positional arguments (or null).
	 * @param named      Named arguments (or null).
	 *
	 * @return The method's result.
	 */
	static Object invoke( CriteriaBuilder c, IBoxContext ctx, String name, Object[] positional, Map<Key, Object> named ) {
		c.rememberContext( ctx );
		String	lower	= name.toLowerCase();
		Spec	spec	= METHODS.get( lower );
		if ( spec == null && lower.startsWith( "with" ) && lower.length() > 4 ) {
			String		association	= name.substring( 4 );
			Object[]	args		= positional != null ? positional : namedToArray( named, new String[][] { { "joinType", "callback" }, { "callback" } } );
			int			before		= c.stepCount();
			c.enterStep();
			try {
				withCall( c, ctx, association, args.length > 0 ? args[ 0 ] : null, args.length > 1 ? args[ 1 ] : null );
			} finally {
				c.leaveStep();
			}
			c.insertStep( before, "." + name + "(" + formatArgs( args ) + ")" );
			return c;
		}
		if ( spec == null && lower.startsWith( "not" ) && lower.length() > 3 ) {
			Spec inner = METHODS.get( lower.substring( 3 ) );
			if ( inner == null ) {
				inner = METHODS.get( "is" + lower.substring( 3 ) );
			}
			if ( inner != null && inner.condition() ) {
				Spec		target	= inner;
				Object[]	args	= bind( target, positional, named );
				int			before	= c.stepCount();
				c.enterStep();
				try {
					c.negate( ctx, () -> target.body().run( c, ctx, args ) );
				} finally {
					c.leaveStep();
				}
				c.insertStep( before, "." + name + "(" + formatArgs( positional != null ? positional : args ) + ")" );
				c.announceAddition( name );
				return c;
			}
		}
		if ( spec == null && positional != null && JAVA_METHODS.contains( lower ) ) {
			// Plain Java methods (getClass, hashCode, equals, getEntityName, ...) used by writeDump() and Java interop.
			return ortus.boxlang.runtime.interop.DynamicInteropService.invoke( ctx, c, name, false, positional );
		}
		if ( spec == null ) {
			throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria has no method [" + name + "()]." + suggest( name ),
			    "See the entityCriteria() documentation for the list of methods." );
		}
		Object[]	args	= bind( spec, positional, named );
		int			before	= c.stepCount();
		Object		result;
		c.enterStep();
		try {
			result = spec.body().run( c, ctx, args );
		} finally {
			c.leaveStep();
		}
		if ( result == c ) {
			c.insertStep( before, "." + name + "(" + formatArgs( positional != null ? positional : args ) + ")" );
			if ( spec.condition() ) {
				c.announceAddition( spec.name() );
			}
		}
		return result;
	}

	/**
	 * A "Did you mean" for an unknown method: the closest name by spelling, else the longest known name the input
	 * starts with (e.g. {@code isEqual} suggests {@code isEq}).
	 *
	 * @param name The unknown method name.
	 *
	 * @return The suggestion sentence, or empty.
	 */
	private static String suggest( String name ) {
		String close = ORMErrors.suggestion( name, NAMES );
		if ( !close.isEmpty() ) {
			return close;
		}
		String	lower	= name.toLowerCase();
		String	best	= null;
		for ( String known : NAMES ) {
			String k = known.toLowerCase();
			if ( ( lower.startsWith( k ) || k.startsWith( lower ) ) && k.length() > 2 && ( best == null || known.length() > best.length() ) ) {
				best = known;
			}
		}
		return best == null ? "" : " Did you mean [" + best + "]?";
	}

	/**
	 * Map call arguments to a method's parameters.
	 *
	 * @param spec       The method.
	 * @param positional Positional arguments (or null).
	 * @param named      Named arguments (or null).
	 *
	 * @return One value per parameter.
	 */
	private static Object[] bind( Spec spec, Object[] positional, Map<Key, Object> named ) {
		int			count	= spec.params().length;
		Object[]	args	= new Object[ count ];
		if ( positional != null ) {
			if ( spec.variadic() ) {
				for ( int i = 0; i < count - 1 && i < positional.length; i++ ) {
					args[ i ] = positional[ i ];
				}
				args[ count - 1 ] = positional.length >= count ? Arrays.copyOfRange( positional, count - 1, positional.length ) : new Object[ 0 ];
				return args;
			}
			if ( positional.length > count && !spec.name().equals( "withProjections" ) ) {
				throw new ORMException( ORMErrorType.ARGUMENT,
				    spec.name() + "() takes at most " + count + " argument(s) but received " + positional.length + ".",
				    "Arguments: " + describeParams( spec ) + "." );
			}
			System.arraycopy( positional, 0, args, 0, Math.min( positional.length, count ) );
			return args;
		}
		Map<Key, Object> given = new LinkedHashMap<>( named == null ? Map.of() : named );
		if ( given.get( Key.argumentCollection ) instanceof IStruct collection ) {
			given.remove( Key.argumentCollection );
			collection.forEach( given::putIfAbsent );
		}
		if ( spec.name().equals( "withProjections" ) ) {
			// Named projections: withProjections( property = "a,b", sum = "price:total" ).
			IStruct projections = new Struct( IStruct.TYPES.LINKED );
			given.forEach( ( k, v ) -> {
				if ( v instanceof IStruct s && k.getName().equalsIgnoreCase( "projections" ) ) {
					projections.putAll( s );
				} else {
					projections.put( k, v );
				}
			} );
			args[ 0 ] = projections;
			return args;
		}
		for ( Map.Entry<Key, Object> entry : given.entrySet() ) {
			int index = paramIndex( spec, entry.getKey().getName() );
			if ( index < 0 ) {
				throw new ORMException( ORMErrorType.ARGUMENT, spec.name() + "() has no argument named [" + entry.getKey().getName() + "].",
				    "Arguments: " + describeParams( spec ) + "." );
			}
			args[ index ] = spec.variadic() && index == count - 1 ? new Object[] { entry.getValue() } : entry.getValue();
		}
		if ( spec.variadic() && args[ count - 1 ] == null ) {
			args[ count - 1 ] = new Object[ 0 ];
		}
		return args;
	}

	/**
	 * Find a parameter by any of its names.
	 *
	 * @param spec The method.
	 * @param name The argument name.
	 *
	 * @return The parameter index, or -1.
	 */
	private static int paramIndex( Spec spec, String name ) {
		for ( int i = 0; i < spec.params().length; i++ ) {
			for ( String alt : spec.params()[ i ] ) {
				if ( alt.equalsIgnoreCase( name ) ) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Map named arguments to an ad-hoc parameter list.
	 *
	 * @param named  The named arguments.
	 * @param params The parameters with their names.
	 *
	 * @return One value per parameter.
	 */
	private static Object[] namedToArray( Map<Key, Object> named, String[][] params ) {
		Object[] args = new Object[ params.length ];
		if ( named != null ) {
			named.forEach( ( k, v ) -> {
				for ( int i = 0; i < params.length; i++ ) {
					for ( String alt : params[ i ] ) {
						if ( alt.equalsIgnoreCase( k.getName() ) ) {
							args[ i ] = v;
						}
					}
				}
			} );
		}
		return args;
	}

	/**
	 * A method's parameter names for error messages.
	 *
	 * @param spec The method.
	 *
	 * @return E.g. {@code property, value}.
	 */
	private static String describeParams( Spec spec ) {
		if ( spec.params().length == 0 ) {
			return "none";
		}
		List<String> names = new ArrayList<>();
		for ( String[] p : spec.params() ) {
			names.add( p[ 0 ] );
		}
		return String.join( ", ", names ) + ( spec.variadic() ? "..." : "" );
	}

	/**
	 * Every documented method name, sorted (for documentation and tests).
	 *
	 * @return The names.
	 */
	static List<String> methodNames() {
		return new ArrayList<>( new TreeSet<>( NAMES ) );
	}

	/**
	 * Arguments as they appear in {@code toString()}.
	 *
	 * @param args The arguments.
	 *
	 * @return E.g. {@code  "make", "Ford" }.
	 */
	static String formatArgs( Object[] args ) {
		if ( args == null || args.length == 0 ) {
			return "";
		}
		int last = args.length - 1;
		while ( last >= 0 && args[ last ] == null ) {
			last--;
		}
		if ( last < 0 ) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		for ( int i = 0; i <= last; i++ ) {
			parts.add( format( args[ i ] ) );
		}
		return " " + String.join( ", ", parts ) + " ";
	}

	/**
	 * A value as it appears in {@code toString()}.
	 *
	 * @param value The value.
	 *
	 * @return A short readable form.
	 */
	static String format( Object value ) {
		if ( value == null ) {
			return "null";
		}
		if ( value instanceof String s ) {
			return "\"" + s + "\"";
		}
		if ( value instanceof Function ) {
			return "( c ) => {...}";
		}
		if ( value instanceof CriteriaBuilder sub ) {
			return "subquery( " + format( sub.getEntityName() ) + " )";
		}
		if ( value instanceof Object[] array ) {
			return Arrays.stream( array ).map( CriteriaMethods::format ).toList().toString();
		}
		if ( value instanceof Array array ) {
			return array.stream().map( CriteriaMethods::format ).toList().toString();
		}
		Object unwrapped = ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport.unwrapIfFacade( value );
		if ( unwrapped instanceof IClassRunnable entity ) {
			return entity.bxGetName().getName() + " instance";
		}
		return String.valueOf( value );
	}
}
