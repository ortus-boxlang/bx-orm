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

import java.util.Set;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityLoadByPK extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityLoadByPK() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "String", Key.id, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "String", ORMKeys.unique, Set.of( Validator.NOT_IMPLEMENTED ) )
		};
	}

	/**
	 * Load an array of entities by the primary key.
	 * <p>
	 * <code>
	 * var myAuto = entityLoadByPK( "Automobile", "1HGCM82633A123456" );
	 * </code>
	 * <p>
	 * In Lucee, by default, an array of entities is returned and you must pass a third `unique=true` argument to return only a single entity. In BoxLang,
	 * only a single entity is returned - matching the Adobe ColdFusion behavior - and no `unique` attribute
	 * is supported. To return an array of entities, use the `entityLoad` BIF.
	 * <p>
	 * Composite keys are also supported:
	 * <code>
	 * entityLoadByPK( "VehicleType", { make : "Ford", model: "Fusion" } );
	 * </code>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String	entityName	= arguments.getAsString( ORMKeys.entity );
		Object	keyValue	= arguments.get( Key.id );

		return this.ormApp.loadEntityById( context.getRequestContext(), entityName, keyValue );
	}
}
