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
	 * ORMExecuteQuery
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
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
				params = paramsArray.stream().map( param -> {
					if ( param instanceof String ) {
						CastAttempt<LocalTime> timeCastAttempt = TimeCaster.attempt( param );
						if ( timeCastAttempt.wasSuccessful() ) {
							return timeCastAttempt.get();
						}
						CastAttempt<DateTime> dateCastAttempt = DateTimeCaster.attempt( param );
						if ( dateCastAttempt.wasSuccessful() ) {
							System.out.println( "Original: " + param );
							System.out.println( "Cast date: " + dateCastAttempt.get().toISOString() );
							return dateCastAttempt.get().toDate();
						}
						return param;
					} else {
						return param;
					}
				} ).collect( BLCollector.toArray() );
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
		List<?> results = new HQLQuery( context, arguments.getAsString( ORMKeys.hql ), params, options ).execute();
		if ( options.getAsBoolean( ORMKeys.unique ) ) {
			return results.isEmpty() ? null : results.getFirst();
		}
		return Array.fromList( results );
	}
}
