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

import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF
public class ORMGetSQLFunctions extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMGetSQLFunctions() {
		super();
		declaredArguments = new Argument[] {};
	}

	/**
	 * The named SQL functions the application registered with the <code>sqlFunctions</code> ORM setting, which HQL and
	 * <code>entityCriteria()</code> paths can call by name.
	 *
	 * <pre>
	 * // Application.bx
	 * this.ormSettings.sqlFunctions = { soundex : "soundex(?1)", jsonGet : { sql : "json_value(?1, ?2)", returns : "string" } };
	 *
	 * ormGetSQLFunctions(); // { soundex : { sql : "soundex(?1)", returns : "" }, jsonGet : { sql : "json_value(?1, ?2)", returns : "string" } }
	 * </pre>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return A struct of function name to { sql, returns } (returns is empty when Hibernate infers the type).
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) ).requireORMApp().getConfig().sqlFunctions.describe();
	}
}
