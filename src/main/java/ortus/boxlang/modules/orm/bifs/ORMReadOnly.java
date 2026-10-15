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
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Function;

@BoxBIF
public class ORMReadOnly extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public ORMReadOnly() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Function", Key.callback )
		};
	}

	/**
	 * Run a closure with every entity it loads read-only, and return what the closure returns.
	 * <p>
	 * Read-only entities are not dirty-checked, so reports and exports over many entities use less memory and flush
	 * faster, and changes made to them by mistake are never saved.
	 *
	 * <pre>
	 * total = ormReadOnly( () => {
	 *     return entityLoad( "Order", { status : "shipped" } ).reduce( ( sum, o ) => sum + o.getTotal(), 0 );
	 * } );
	 * </pre>
	 * <p>
	 * Only entities loaded inside the closure are read-only: entities already in the session keep their state, and new
	 * entities can still be saved. Blocks can nest.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return What the closure returned.
	 *
	 * @argument.callback The closure to run.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Function	callback	= ( Function ) arguments.get( Key.callback );
		ORMContext	ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ormContext.requireORMApp();
		return ormContext.readOnly( () -> context.invokeFunction( callback ) );
	}
}
