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

import ortus.boxlang.modules.orm.EntityInspector;
import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;
import ortus.boxlang.runtime.types.IStruct;

/**
 * {@code entityGetMetadata()}: The mapping metadata of an entity, as a struct. Built once per entity and cached; each call returns a copy.
 */
@BoxBIF
public class EntityGetMetadata extends BaseORMBIF {

	/**
	 * Declare the BIF's arguments.
	 */
	public EntityGetMetadata() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", ORMKeys.entity, Set.of( Validator.REQUIRED ) )
		};
	}

	/**
	 * The mapping metadata of an entity, as a struct. Built once per entity and cached; each call returns a copy.
	 *
	 * Keys: entityName, className, datasource, tableName, schema, catalog, parent, readOnly, discriminator { column, value },
	 * idProperties, idType, version, properties (one struct per column-backed property: name, column, ormtype, fieldtype,
	 * nullable, unique, length, precision, scale, formula, insertable, updatable), associations (name, kind, target, cascade,
	 * lazy, inverse, fkcolumn, mappedBy, linkTable, orderBy) and propertyNames (every non-id property name).
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.entity An entity instance (loaded, new or a lazy reference) or an entity name.
	 *
	 * @return The metadata struct.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.requireORMApp();
		Object			entity		= arguments.get( ORMKeys.entity );
		EntityRecord	record		= EntityInspector.resolve( ormApp, entity, "entityGetMetadata" );
		return ormApp.getEntityMetadata( record.getEntityName() );
	}
}
