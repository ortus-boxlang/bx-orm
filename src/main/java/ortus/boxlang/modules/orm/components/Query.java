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
package ortus.boxlang.modules.orm.components;

import java.util.Arrays;

import ortus.boxlang.modules.orm.HQLDbType;
import ortus.boxlang.modules.orm.bifs.ORMExecuteQuery;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.ExpressionInterpreter;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * The query component with HQL support. {@code <bx:query dbtype="hql">} (and {@code <cfquery dbtype="hql">}) runs its
 * body as HQL, like {@code ormExecuteQuery()}, and sets the variable to the same result: an array of entities, or an
 * array of rows for a projection, as in Adobe ColdFusion and Lucee. {@code <bx:queryparam>} in the body binds positional
 * parameters, and {@code ormoptions} takes the {@code ormExecuteQuery()} options. Every other query runs through the core
 * query component unchanged.
 */
@BoxComponent( description = "Execute SQL queries against databases, or HQL with dbtype=\"hql\"", requiresBody = true, ignoreEnableOutputOnly = true, autoEvaluateBodyExpressions = true )
public class Query extends ortus.boxlang.runtime.components.jdbc.Query {

	/**
	 * Constructor: the core attributes plus {@code ormoptions}.
	 */
	public Query() {
		super();
		Attribute[] attributes = Arrays.copyOf( declaredAttributes, declaredAttributes.length + 1 );
		attributes[ attributes.length - 1 ]	= new Attribute( ORMKeys.ormOptions, "struct" );
		declaredAttributes					= attributes;
	}

	/**
	 * Runs the query: HQL for {@code dbtype="hql"}, otherwise the core query component.
	 *
	 * @param context        The context in which the Component is being invoked
	 * @param attributes     The attributes to the Component
	 * @param body           The body of the Component
	 * @param executionState The execution state of the Component
	 *
	 * @attribute.ormoptions With {@code dbtype="hql"}: the {@code ormExecuteQuery()} options, such as {@code offset},
	 *                       {@code maxResults}, {@code cacheable}, {@code cacheName}, {@code readOnly} and {@code unique}.
	 */
	@Override
	public BodyResult _invoke( IBoxContext context, IStruct attributes, ComponentBody body, IStruct executionState ) {
		if ( !HQLDbType.isHQL( attributes.get( Key.dbtype ) ) ) {
			return super._invoke( context, attributes, body, executionState );
		}

		executionState.put( Key.queryParams, new Array() );
		StringBuffer	buffer		= new StringBuffer();
		BodyResult		bodyResult	= processBody( context, body, buffer );
		if ( bodyResult.isEarlyExit() ) {
			return bodyResult;
		}

		String	hql		= buffer.toString().trim();
		Object	params	= executionState.getAsArray( Key.queryParams );
		if ( attributes.get( Key.params ) != null ) {
			if ( ( ( Array ) params ).size() > 0 ) {
				throw new IllegalArgumentException( "Cannot specify both query parameters in the body and as an attribute." );
			}
			params = attributes.get( Key.params );
		}

		long	start	= System.currentTimeMillis();
		Object	result	= ORMExecuteQuery.execute( context, hql, HQLDbType.typeParams( params, context ), null, HQLDbType.ormOptions( attributes ),
		    "query" );
		long	elapsed	= System.currentTimeMillis() - start;

		if ( attributes.get( Key.result ) != null ) {
			ExpressionInterpreter.setVariable( context, StringCaster.cast( attributes.get( Key.result ) ), HQLDbType.resultInfo( hql, result, elapsed ) );
		}
		String variableName = StringCaster.cast( attributes.getOrDefault( Key._NAME, "bxquery" ) );
		ExpressionInterpreter.setVariable( context, variableName, result );
		return DEFAULT_RETURN;
	}
}
