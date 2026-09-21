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

import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;

import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.query.Query;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF
public class EntityLoadByExample extends BaseORMBIF {

	public EntityLoadByExample() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", ORMKeys.sampleEntity ),
		    new Argument( false, "boolean", ORMKeys.unique, false )
		};
	}

	/**
	 * Load entities matching an example entity.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.sampleEntity Instance of an ORM entity to use as an example for the query.
	 * 
	 * @argument.unique Whether to return a single unique result (true) or an array of results (false).
	 */
	/**
	 * Whether a property should be excluded from a query-by-example predicate. Ids, the version, and associations are
	 * excluded: example queries match on regular property values, and Hibernate 7 rejects an entity/PK value bound as a
	 * simple equality predicate for an association.
	 *
	 * @param entityMeta The entity's metadata.
	 * @param property   The property to test.
	 *
	 * @return {@code true} when the property is an id, the version, or an association.
	 */
	private static boolean isExcludedFromExample( IEntityMeta entityMeta,
	    IPropertyMeta property ) {
		if ( property.isAssociationType() ) {
			return true;
		}
		IPropertyMeta version = entityMeta.getVersionProperty();
		if ( version != null && version.getName().equals( property.getName() ) ) {
			return true;
		}
		return entityMeta.getIdProperties().stream().anyMatch( id -> id.getName().equals( property.getName() ) );
	}

	@SuppressWarnings( { "deprecation", "unchecked" } )
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IBoxContext	jdbcBoxContext	= context.getParentOfType( IJDBCCapableContext.class );
		ORMContext	ormContext		= ORMContext.getForContext( jdbcBoxContext );
		ORMApp		ormApp			= ormContext.getORMApp();
		Object		sampleEntity	= arguments.get( ORMKeys.sampleEntity );
		Boolean		unique			= arguments.getAsBoolean( ORMKeys.unique );

		if ( ! ( sampleEntity instanceof IClassRunnable ) ) {
			throw new BoxRuntimeException( "Sample entity must be a valid entity" );
		}

		IClassRunnable		workingEntity	= ( IClassRunnable ) sampleEntity;
		String				entityName		= getEntityName( workingEntity );
		EntityRecord		entityRecord	= ormApp.lookupEntity( entityName, true );
		Session				session			= ormContext.getSession( entityRecord.getDatasource() );

		// Hibernate 6+ removed the legacy Criteria/Example API, so build an equivalent "query by example" HQL statement:
		// every non-null simple (non-association) property of the sample entity becomes an equality predicate.
		StringBuilder		hql				= new StringBuilder( "select e from " ).append( entityRecord.getEntityName() ).append( " e" );
		Map<String, Object>	params			= new java.util.HashMap<>();
		int					index			= 0;
		for ( Object propertyMeta : entityRecord.getEntityMeta().getAllPersistentProperties() ) {
			String propertyName = ( ( IPropertyMeta ) propertyMeta ).getName();
			// Skip ids, the version, and associations: example queries match on regular property values, and an
			// association value is an entity/PK that Hibernate 7 rejects as a simple equality predicate.
			if ( isExcludedFromExample( entityRecord.getEntityMeta(), ( IPropertyMeta ) propertyMeta ) ) {
				continue;
			}
			Object value = workingEntity.getVariablesScope().get( Key.of( propertyName ) );
			if ( value == null ) {
				continue;
			}
			String paramName = "p" + index++;
			hql.append( index == 1 ? " where" : " and" ).append( " e." ).append( propertyName ).append( " = :" ).append( paramName );
			params.put( paramName, value );
		}

		Query<?> query = session.createQuery( hql.toString(), Object.class );
		params.forEach( query::setParameter );
		if ( unique ) {
			query.setMaxResults( 1 );
		}
		// Hibernate returns POJO facades; unwrap each to its BoxLang instance so callers only ever see IClassRunnables.
		List<Object> results = query.list()
		    .stream()
		    .map( FacadeSupport::unwrapIfFacade )
		    .collect( java.util.stream.Collectors.toList() );

		if ( unique ) {
			return results.isEmpty() ? null : results.get( 0 );
		} else {
			return Array.fromList( results );
		}
	}

}
