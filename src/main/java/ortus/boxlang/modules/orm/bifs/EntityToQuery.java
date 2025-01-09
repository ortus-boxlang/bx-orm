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

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Query;
import ortus.boxlang.runtime.types.QueryColumnType;

@BoxBIF
public class EntityToQuery extends BaseORMBIF {

	/**
	 * Constructor
	 */
	public EntityToQuery() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity ),
		    new Argument( false, "Any", Key._name )
		};
	}

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String	entityName	= arguments.containsKey( Key._name )
		    ? arguments.getAsString( Key._name )
		    : null;

		Object	item		= arguments.get( ORMKeys.entity );
		if ( item instanceof Array entities ) {
			if ( entityName == null ) {
				entityName = getEntityNameOrThrow( entities.getFirst() );
			}
			return populateQuery( entities, entityName );
		}
		if ( entityName == null ) {
			entityName = getEntityNameOrThrow( item );
		}

		return populateQuery( Array.of( item ), entityName );
	}

	private String getEntityNameOrThrow( Object item ) {
		if ( ! ( item instanceof IClassRunnable ) ) {
			throw new IllegalArgumentException( "Entity must be an Boxlang class or array of classes" );
		}
		return getEntityName( ( IClassRunnable ) item );
	}

	private Query populateQuery( Array entities, String entityName ) {
		EntityRecord		entityRecord	= ormApp.lookupEntity( entityName, true );
		List<IPropertyMeta>	props			= entityRecord.getEntityMeta().getAllPersistentProperties();
		Query				result			= new Query();
		for ( IPropertyMeta prop : props ) {
			result.addColumn( Key.of( prop.getName() ), QueryColumnType.fromString( prop.getORMType() ) );
		}
		for ( Object item : entities ) {
			IClassRunnable	entity	= ( IClassRunnable ) item;
			Object[]		row		= new Object[ props.size() ];
			int				i		= 0;
			for ( IPropertyMeta prop : props ) {
				row[ i ] = prop.isAssociationType() ? null : entity.get( prop.getName() );
				i++;
			}
			result.addRow( row );
		}
		return result;
	}

}
