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

import java.util.List;
import java.util.Set;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.util.ListUtil;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
// @TODO: Consider deprecating entityNameList, since we can just use entityNameArray.toList().
@BoxBIF( alias = "EntityNameList" )
public class EntityNameArray extends BIF {

	/**
	 * ORM Service, responsible for managing ORM applications.
	 */
	private ORMService ormService;

	/**
	 * Constructor
	 */
	public EntityNameArray() {

		super();
		this.ormService		= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		declaredArguments	= new Argument[] {
		    new Argument( false, "String", Key.delimiter, Set.of( Validator.NON_EMPTY ) ),
		    new Argument( false, "String", Key.datasource, Set.of( Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Retrieve an array of entity names for this ORM application.
	 * 
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.delimiter The delimiter to use between entity names. Defaults to a comma.
	 * 
	 * @argument.datasource The name of the datasource to filter on. If provided, only entities configured for this datasource will be returned.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Key		bifMethodKey	= arguments.getAsKey( BIF.__functionName );
		String	delimiter		= ( String ) arguments.getOrDefault( Key.delimiter, "," );
		if ( delimiter == null ) {
			delimiter = ",";
		}
		String				datasourceName	= ( String ) arguments.getAsString( Key.datasource );

		ORMApp				ormApp			= this.ormService.getORMApp( context );
		List<EntityRecord>	entityList		= datasourceName != null
		    ? ormApp.getEntityRecords( datasourceName )
		    : ormApp.getEntityRecords();

		Array				entityNames		= Array.fromList(
		    entityList
		        .stream()
		        .map( entity -> entity.getEntityName() )
		        // Sort alphabetically to ensure a consistent order. The order of entities in the entity map is not guaranteed, as it is a HashMap and is
		        // populated in order of discovery.
		        .sorted()
		        .toList()
		);

		return bifMethodKey.equals( ORMKeys.entityNameList ) ? ListUtil.asString( entityNames, delimiter ) : entityNames;
	}
}
