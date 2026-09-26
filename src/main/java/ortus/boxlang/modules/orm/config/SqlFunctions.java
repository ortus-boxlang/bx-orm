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
package ortus.boxlang.modules.orm.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.spi.TypeConfiguration;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * The application's named SQL functions ({@code ormSettings.sqlFunctions}): SQL templates registered with Hibernate so HQL
 * and {@code entityCriteria()} paths can call them by name.
 *
 * <pre>
 * this.ormSettings.sqlFunctions = {
 *     soundex : "soundex(?1)",
 *     jsonGet : { sql : "json_value(?1, ?2)", returns : "string" }
 * };
 * </pre>
 * <p>
 * {@code ?1}, {@code ?2}, ... are the function's arguments. {@code returns} is the result type ({@code string},
 * {@code integer}, {@code long}, {@code double}, {@code decimal}, {@code boolean}, {@code date}, {@code time},
 * {@code timestamp}); without it Hibernate infers one. Registered through Hibernate's public
 * {@link FunctionContributor} SPI.
 */
public final class SqlFunctions {

	/** A function name: letters, digits and underscores, not starting with a digit. */
	private static final Pattern							NAME	= Pattern.compile( "[A-Za-z_][A-Za-z0-9_]*" );

	/** The {@code sql} key of a function struct. */
	private static final Key								SQL		= Key.of( "sql" );

	/** The {@code returns} key of a function struct. */
	private static final Key								RETURNS	= Key.of( "returns" );

	/** The supported {@code returns} values. */
	private static final Map<String, BasicTypeReference<?>>	TYPES	= Map.ofEntries(
	    Map.entry( "string", StandardBasicTypes.STRING ),
	    Map.entry( "text", StandardBasicTypes.STRING ),
	    Map.entry( "integer", StandardBasicTypes.INTEGER ),
	    Map.entry( "int", StandardBasicTypes.INTEGER ),
	    Map.entry( "long", StandardBasicTypes.LONG ),
	    Map.entry( "bigint", StandardBasicTypes.LONG ),
	    Map.entry( "double", StandardBasicTypes.DOUBLE ),
	    Map.entry( "float", StandardBasicTypes.DOUBLE ),
	    Map.entry( "decimal", StandardBasicTypes.BIG_DECIMAL ),
	    Map.entry( "numeric", StandardBasicTypes.BIG_DECIMAL ),
	    Map.entry( "boolean", StandardBasicTypes.BOOLEAN ),
	    Map.entry( "date", StandardBasicTypes.LOCAL_DATE ),
	    Map.entry( "time", StandardBasicTypes.LOCAL_TIME ),
	    Map.entry( "timestamp", StandardBasicTypes.LOCAL_DATE_TIME ),
	    Map.entry( "datetime", StandardBasicTypes.LOCAL_DATE_TIME )
	);

	/**
	 * One function.
	 *
	 * @param name    The name HQL calls it by.
	 * @param sql     The SQL template, with {@code ?1..?n} for the arguments.
	 * @param returns The result type name (lower-cased), or null to let Hibernate infer it.
	 */
	public record Function( String name, String sql, String returns ) {
	}

	/** The functions by name, in declaration order. */
	private final Map<String, Function> functions;

	/**
	 * Wrap parsed functions.
	 *
	 * @param functions The functions by name.
	 */
	private SqlFunctions( Map<String, Function> functions ) {
		this.functions = Collections.unmodifiableMap( functions );
	}

	/**
	 * No functions.
	 *
	 * @return An empty set of functions.
	 */
	public static SqlFunctions empty() {
		return new SqlFunctions( new LinkedHashMap<>() );
	}

	/**
	 * Parse the {@code sqlFunctions} setting.
	 *
	 * @param setting The setting: a struct of name to SQL template, or to a struct { sql, returns }; null for none.
	 *
	 * @return The functions.
	 *
	 * @throws ORMException {@code orm.config} for a bad name, template or return type.
	 */
	public static SqlFunctions parse( Object setting ) {
		Map<String, Function> result = new LinkedHashMap<>();
		if ( setting == null || ( setting instanceof String s && s.isBlank() ) ) {
			return new SqlFunctions( result );
		}
		if ( ! ( setting instanceof IStruct struct ) ) {
			throw new ORMException( ORMErrorType.CONFIG, "The ORM setting sqlFunctions must be a struct.",
			    "Use sqlFunctions : { soundex : \"soundex(?1)\" }." );
		}
		for ( Key key : struct.keySet() ) {
			String	name	= key.getName();
			Object	value	= struct.get( key );
			String	sql;
			String	returns	= null;
			if ( value instanceof IStruct spec ) {
				sql = spec.get( SQL ) == null ? null : spec.get( SQL ).toString();
				if ( spec.get( RETURNS ) != null && !spec.get( RETURNS ).toString().isBlank() ) {
					returns = spec.get( RETURNS ).toString().trim().toLowerCase();
				}
			} else {
				sql = value == null ? null : value.toString();
			}
			if ( !NAME.matcher( name ).matches() ) {
				throw new ORMException( ORMErrorType.CONFIG, "The SQL function name [" + name + "] is not valid.",
				    "Use letters, digits and underscores, starting with a letter." );
			}
			if ( sql == null || sql.isBlank() ) {
				throw new ORMException( ORMErrorType.CONFIG, "The SQL function [" + name + "] has no SQL.",
				    "Give it a template, e.g. " + name + " : \"soundex(?1)\", or { sql : \"...\", returns : \"string\" }." );
			}
			if ( returns != null && !TYPES.containsKey( returns ) ) {
				throw new ORMException( ORMErrorType.CONFIG,
				    "The SQL function [" + name + "] returns [" + returns + "], which is not a known type.",
				    "Use one of: " + String.join( ", ", new java.util.TreeSet<>( TYPES.keySet() ) ) + ", or leave returns out." );
			}
			result.put( name, new Function( name, sql.trim(), returns ) );
		}
		return new SqlFunctions( result );
	}

	/**
	 * Whether there are no functions.
	 *
	 * @return True when none are configured.
	 */
	public boolean isEmpty() {
		return functions.isEmpty();
	}

	/**
	 * The functions by name.
	 *
	 * @return The functions (unmodifiable).
	 */
	public Map<String, Function> all() {
		return functions;
	}

	/**
	 * The Hibernate function contributor that registers these functions.
	 *
	 * @return The contributor.
	 */
	public FunctionContributor contributor() {
		return contributions -> {
			TypeConfiguration types = contributions.getTypeConfiguration();
			functions.values().forEach( f -> {
				if ( f.returns() == null ) {
					contributions.getFunctionRegistry().registerPattern( f.name(), f.sql() );
				} else {
					BasicType<?> type = types.getBasicTypeRegistry().resolve( TYPES.get( f.returns() ) );
					contributions.getFunctionRegistry().registerPattern( f.name(), f.sql(), type );
				}
			} );
		};
	}

	/**
	 * The functions as a BoxLang struct, for {@code ormGetSQLFunctions()}: name to { sql, returns }.
	 *
	 * @return The struct (a new copy).
	 */
	public IStruct describe() {
		IStruct result = new Struct( IStruct.TYPES.LINKED );
		functions.values().forEach( f -> result.put( Key.of( f.name() ), Struct.of( SQL, f.sql(), RETURNS, f.returns() == null ? "" : f.returns() ) ) );
		return result;
	}
}
