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

import ortus.boxlang.modules.orm.HQLDbType;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.ExpressionInterpreter;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * {@code queryExecute()} with HQL support. With {@code options.dbtype = "hql"} it runs the HQL like
 * {@code ormExecuteQuery()} and returns the same result, an array of entities or of projection rows, as in Adobe
 * ColdFusion and Lucee. Every other query runs through the core {@code queryExecute()} unchanged.
 */
@BoxBIF( description = "Execute a SQL query, or an HQL query with dbtype=\"hql\"" )
public class QueryExecute extends ortus.boxlang.runtime.bifs.global.jdbc.QueryExecute {

	/**
	 * Runs the query: HQL for {@code options.dbtype = "hql"}, otherwise the core queryExecute().
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.sql The SQL, or the HQL with {@code dbtype: "hql"}.
	 *
	 * @argument.params Positional (array) or named (struct) parameters.
	 *
	 * @argument.options The query options. With {@code dbtype: "hql"}: {@code datasource}, {@code maxrows},
	 *                   {@code timeout}, {@code result} and {@code ormoptions} (the {@code ormExecuteQuery()} options).
	 */
	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IStruct options = arguments.getAsStruct( Key.options );
		if ( options == null || !HQLDbType.isHQL( options.get( Key.dbtype ) ) ) {
			return super._invoke( context, arguments );
		}
		String	hql		= arguments.getAsString( Key.sql ).trim();
		long	start	= System.currentTimeMillis();
		Object	result	= ORMExecuteQuery.execute( context, hql, HQLDbType.typeParams( arguments.get( Key.params ), context ), null,
		    HQLDbType.ormOptions( options ) );
		if ( options.get( Key.result ) != null ) {
			ExpressionInterpreter.setVariable( context, StringCaster.cast( options.get( Key.result ) ),
			    HQLDbType.resultInfo( hql, result, System.currentTimeMillis() - start ) );
		}
		return result;
	}
}
