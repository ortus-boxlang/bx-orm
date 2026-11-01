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
package ortus.boxlang.modules.orm;

import java.math.BigDecimal;
import java.util.Map;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BigDecimalCaster;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.DateTimeCaster;
import ortus.boxlang.runtime.dynamic.casters.DoubleCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.dynamic.casters.LongCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.ListUtil;

/**
 * Support for HQL run through the query component and BIF: {@code <bx:query dbtype="hql">} (and {@code <cfquery>}) and
 * {@code queryExecute( hql, params, { dbtype : "hql" } )}. As in Adobe ColdFusion and Lucee, these run the HQL like
 * {@code ormExecuteQuery()} and return the same result, an array, rather than a query.
 */
public final class HQLDbType {

	private HQLDbType() {
	}

	/**
	 * Whether a {@code dbtype} asks for HQL.
	 *
	 * @param dbtype The dbtype attribute or option.
	 *
	 * @return true for "hql"
	 */
	public static boolean isHQL( Object dbtype ) {
		return dbtype != null && "hql".equalsIgnoreCase( StringCaster.cast( dbtype ).trim() );
	}

	/**
	 * The ORM query options for a query run with {@code dbtype="hql"}: {@code datasource}, {@code timeout} and
	 * {@code maxrows} carry over, and the {@code ormoptions} struct (offset, maxResults, cacheable, cacheName, readOnly,
	 * unique...) is applied on top, as in Adobe ColdFusion.
	 *
	 * @param source The query component attributes or the queryExecute options.
	 *
	 * @return The options for the HQL query.
	 */
	public static IStruct ormOptions( IStruct source ) {
		IStruct options = new Struct();
		if ( source == null ) {
			return options;
		}
		if ( source.get( Key.datasource ) != null ) {
			options.put( Key.datasource, source.get( Key.datasource ) );
		}
		if ( source.get( Key.timeout ) != null ) {
			options.put( Key.timeout, source.get( Key.timeout ) );
		}
		if ( source.get( Key.maxRows ) != null ) {
			int maxRows = IntegerCaster.cast( source.get( Key.maxRows ) );
			if ( maxRows >= 0 ) {
				options.put( ORMKeys.maxResults, maxRows );
			}
		}
		Object ormOptions = source.get( ORMKeys.ormOptions );
		if ( ormOptions != null ) {
			options.putAll( StructCaster.cast( ormOptions ) );
		}
		return options;
	}

	/**
	 * Casts the value of each typed parameter ({@code <cfqueryparam cfsqltype="...">}, or a {@code { value, sqltype }}
	 * struct) to the Java type HQL compares it with. Hibernate binds a parameter as is, so an id compared with the string
	 * "5" from a form is a type mismatch; a typed query param casts it, as it does for SQL.
	 *
	 * @param params  An array or struct of parameters, or null.
	 * @param context The context, for date casting.
	 *
	 * @return The parameters with typed values cast.
	 */
	public static Object typeParams( Object params, IBoxContext context ) {
		if ( params instanceof Array array ) {
			Array typed = new Array();
			for ( Object param : array ) {
				typed.add( typeParam( param, context ) );
			}
			return typed;
		}
		if ( params instanceof IStruct struct ) {
			IStruct typed = new Struct( IStruct.TYPES.LINKED );
			for ( Map.Entry<Key, Object> entry : struct.entrySet() ) {
				typed.put( entry.getKey(), typeParam( entry.getValue(), context ) );
			}
			return typed;
		}
		return params;
	}

	/**
	 * The {@code result} struct of a query run with {@code dbtype="hql"}.
	 *
	 * @param hql           The HQL.
	 * @param result        The query result.
	 * @param executionTime The execution time in milliseconds.
	 *
	 * @return The result struct.
	 */
	public static IStruct resultInfo( String hql, Object result, long executionTime ) {
		int recordCount = result instanceof Array array ? array.size() : ( result == null ? 0 : 1 );
		return Struct.of(
		    Key.sql, hql,
		    Key.recordCount, recordCount,
		    Key.executionTime, executionTime,
		    Key.cached, false
		);
	}

	private static Object typeParam( Object param, IBoxContext context ) {
		if ( ! ( param instanceof IStruct struct ) || !struct.containsKey( Key.value ) ) {
			return param;
		}
		Object sqltype = struct.get( Key.sqltype );
		if ( sqltype == null ) {
			sqltype = struct.get( Key.type );
		}
		boolean isNull = BooleanCaster.cast( struct.getOrDefault( Key.nulls, struct.getOrDefault( Key.nulls2, false ) ) );
		if ( sqltype == null || isNull ) {
			return param;
		}
		String	type	= StringCaster.cast( sqltype ).toLowerCase().replaceFirst( "^cf_sql_", "" ).trim();
		Object	value	= struct.get( Key.value );
		IStruct	typed	= new Struct( IStruct.TYPES.LINKED );
		typed.putAll( struct );
		if ( BooleanCaster.cast( struct.getOrDefault( Key.list, false ) ) ) {
			Array	values		= value instanceof Array array ? array
			    : ListUtil.asList( StringCaster.cast( value ), StringCaster.cast( struct.getOrDefault( Key.separator, "," ) ) );
			Array	castValues	= new Array();
			for ( Object item : values ) {
				castValues.add( castValue( type, item, context ) );
			}
			typed.put( Key.value, castValues );
		} else {
			typed.put( Key.value, castValue( type, value, context ) );
		}
		return typed;
	}

	private static Object castValue( String type, Object value, IBoxContext context ) {
		if ( value == null ) {
			return null;
		}
		return switch ( type ) {
			case "integer", "int", "smallint", "tinyint" -> IntegerCaster.cast( value );
			case "bigint" -> LongCaster.cast( value );
			case "double", "float", "real" -> DoubleCaster.cast( value );
			case "decimal", "numeric", "money", "money4" -> ( BigDecimal ) BigDecimalCaster.cast( value );
			case "bit", "boolean" -> BooleanCaster.cast( value );
			case "date", "time", "timestamp", "datetime" -> DateTimeCaster.cast( value, context ).toDate();
			case "varchar", "char", "longvarchar", "nvarchar", "nchar", "longnvarchar", "clob", "nclob" -> StringCaster.cast( value );
			default -> value;
		};
	}
}
