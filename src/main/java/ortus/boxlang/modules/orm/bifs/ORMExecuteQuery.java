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

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import ortus.boxlang.modules.orm.HQLQuery;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.CastAttempt;
import ortus.boxlang.runtime.dynamic.casters.DateTimeCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.dynamic.casters.TimeCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DateTime;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.BLCollector;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class ORMExecuteQuery extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMExecuteQuery() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.hql, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Any", Key.params, Set.of() ),
		    new Argument( false, "Any", ORMKeys.unique, Set.of() ),
		    new Argument( false, "Struct", Key.options, Set.of() )
		};
	}

	/**
	 * Execute an HQL query with (optional) parameters and specific query options.
	 * 
	 * <h2>Parameters</h2>
	 * The <code>parameters</code> argument can be used to bind parameters to the SQL query.
	 * You can use either an array of binding parameters or a struct of named binding parameters.
	 * 
	 * The SQL must have the parameters bound using the syntax <code>?</code> for positional parameters or <code>:name</code> for named parameters.
	 * <p>
	 * Example:
	 *
	 * <pre>
	 * ORMExecuteQuery( hql: "FROM autos WHERE make = ?", params: [ 'Ford' ] );
	 * ORMExecuteQuery( hql: "FROM autos WHERE make = :make", params: { make: 'Ford' } );
	 * </pre>
	 * 
	 * <h2>Options</h2>
	 * 
	 * The options struct can contain any of the following keys:
	 * <ul>
	 * <li><strong><code>unique</code></strong> - Specifies whether to retrieve a single, unique item. Default is false.</li>
	 * <li><strong><code>datasource</code></strong> - The datasource to use for the query. If not specified, the default datasource will be used.</li>
	 * <li><strong><code>offset</code></strong> - Specifies the position from which to retrieve the objects. Default is 0.</li>
	 * <li><strong><code>maxresults</code></strong> - Specifies the maximum number of objects to be retrieved. Default is no limit.</li>
	 * <li><strong><code>readonly</code></strong> - If true, the query will be read-only. Default is false.</li>
	 * </ul>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.hql The HQL query string to execute.
	 * 
	 * @argument.params Optional parameters for the HQL query. Can be a struct of named parameters or an array of positional parameters.
	 * 
	 * @argument.unique Optional boolean indicating whether to return a unique result (true) or a list of results (false). If true, the query will return
	 *                  a single object or null if no results found.
	 * 
	 * @argument.options Optional struct of additional query options.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IStruct	options		= arguments.containsKey( Key.options ) && arguments.get( Key.options ) != null
		    ? StructCaster.cast( arguments.getOrDefault( Key.options, Struct.EMPTY ) )
		    : new Struct();
		Object	params		= null;
		Boolean	isUnique	= false;

		// "params" arg could positionally be either params (an array or struct) or unique.
		Object	paramsArg	= arguments.get( Key.params );
		// "unique" arg could positionally be either unique (boolean or string boolean representation) or a struct of options.
		Object	uniqueArg	= arguments.get( ORMKeys.unique );

		if ( paramsArg != null ) {
			if ( paramsArg instanceof Boolean || paramsArg instanceof String ) {
				isUnique = BooleanCaster.cast( paramsArg );
			} else if ( paramsArg instanceof Array paramsArray ) {
				params = paramsArray.stream().map( param -> castParam( param ) ).collect( BLCollector.toArray() );
			} else if ( paramsArg instanceof Struct paramsStruct ) {
				params = paramsStruct.entrySet().stream()
				    .map( entry -> {
					    entry.setValue( castParam( entry.getValue() ) );
					    return entry;
				    } ).collect( BLCollector.toStruct() );
			} else {
				params = paramsArg;
			}
		}

		if ( uniqueArg != null ) {
			if ( uniqueArg instanceof Struct theRealOptions ) {
				options.putAll( theRealOptions );
			} else {
				isUnique = BooleanCaster.cast( uniqueArg );
				options.put( ORMKeys.unique, isUnique );
			}
		}
		options.putIfAbsent( ORMKeys.unique, isUnique );
		if ( isUnique ) {
			options.put( ORMKeys.maxResults, 1 );
		}
		Object results = new HQLQuery( context, arguments.getAsString( ORMKeys.hql ), params, options ).execute();
		if ( results instanceof List<?> castList ) {
			if ( options.getAsBoolean( ORMKeys.unique ) ) {
				return castList.isEmpty() ? null : castList.getFirst();
			}
			return Array.fromList( castList );
		} else {
			return results;
		}
	}

	private Object castParam( Object param ) {
		if ( param instanceof String ) {
			CastAttempt<LocalTime> timeCastAttempt = TimeCaster.attempt( param );
			if ( timeCastAttempt.wasSuccessful() ) {
				return timeCastAttempt.get();
			}
			CastAttempt<DateTime> dateCastAttempt = DateTimeCaster.attempt( param );
			if ( dateCastAttempt.wasSuccessful() ) {
				return dateCastAttempt.get().toDate();
			}
			return param;
		} else {
			return param;
		}
	}
}
