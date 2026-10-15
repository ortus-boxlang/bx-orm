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

import org.hibernate.engine.spi.SessionFactoryImplementor;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.criteria.CriteriaBuilder;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

/**
 * {@code entityCriteria()}: start a fluent query on an entity.
 */
@BoxBIF
public class EntityCriteria extends BaseORMBIF {

	/**
	 * Declare the BIF's arguments.
	 */
	public EntityCriteria() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Start a fluent query on an entity. Chain conditions, joins, projections, ordering and options, then run it with a
	 * terminal method such as list(), count(), get(), first(), paginate() or each().
	 *
	 * <pre>
	 * users = entityCriteria( "User" )
	 *     .isEq( "active", true )
	 *     .like( "lastName", "Sm%" )
	 *     .order( "lastName" )
	 *     .list();
	 * </pre>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.entityName The entity to query.
	 *
	 * @return The criteria builder.
	 */
	public CriteriaBuilder _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMContext		ormContext	= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) );
		ORMApp			ormApp		= ormContext.requireORMApp();
		EntityRecord	record		= ormApp.lookupEntity( arguments.getAsString( ORMKeys.entityName ), true );
		var				factory		= ( SessionFactoryImplementor ) ormApp.getSessionFactoryOrThrow( record.getDatasource(), context );
		return new CriteriaBuilder( ormApp, record, factory );
	}
}
