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
package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.collection.internal.PersistentBag;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DynamicFunction;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.validation.Validator;

/**
 * This class is used to instantiate a BoxLang class for a Hibernate entity.
 * <p>
 * In other words, here is where the magic happens to tie a Hibernate entity to a BoxLang class when loading entities from the database via
 * `entityLoadByPK()`, creating new entities via `entityNew()`, etc, etc.
 */
public class BoxClassInstantiator implements Instantiator {

	public static final Logger			logger				= LoggerFactory.getLogger( BoxClassInstantiator.class );

	private ORMApp						ormApp;
	private EntityMetamodel				entityMetamodel;
	private PersistentClass				mappingInfo;
	private String						entityName;
	private EntityRecord				entityRecord;
	private List<String>				subclassClassNames	= new ArrayList<>();

	private static final ClassLocator	CLASS_LOCATOR		= BoxRuntime.getInstance().getClassLocator();

	/**
	 * --------------------------------------------------------------------------
	 * Constructor
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Constructor
	 *
	 * @param entityMetamodel The entity metamodel
	 * @param mappingInfo
	 */
	public BoxClassInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		this.entityMetamodel	= entityMetamodel;
		this.mappingInfo		= mappingInfo;
		this.entityName			= mappingInfo.getEntityName();

		if ( mappingInfo.hasSubclasses() ) {
			@SuppressWarnings( "unchecked" )
			Iterator<PersistentClass> itr = mappingInfo.getSubclassClosureIterator();
			while ( itr.hasNext() ) {
				final PersistentClass subclassInfo = itr.next();
				subclassClassNames.add( subclassInfo.getClassName() );
			}
		}
	}

	/**
	 * --------------------------------------------------------------------------
	 * All Methods
	 * --------------------------------------------------------------------------
	 */

	public static IClassRunnable instantiateByFQN( IBoxContext context, String fqn ) {
		return ( IClassRunnable ) CLASS_LOCATOR.load(
		    context,
		    fqn,
		    context.getCurrentImports()
		)
		    .invokeConstructor( context )
		    .unWrapBoxLangClass();
	}

	public static IClassRunnable instantiateByFQN( IBoxContext context, String fqn, String resolverPrefix ) {
		return ( IClassRunnable ) CLASS_LOCATOR.load(
		    context,
		    fqn,
		    ClassLocator.BX_PREFIX,
		    true,
		    context.getCurrentImports()
		)
		    .invokeConstructor( context )
		    .unWrapBoxLangClass();
	}

	public static IClassRunnable instantiate( IBoxContext context, EntityRecord entityRecord, IStruct properties ) {
		IClassRunnable theEntity = instantiateByFQN( context, entityRecord.getClassFQN(), entityRecord.getResolverPrefix() );

		entityRecord.getEntityMeta().getAssociations().stream()
		    .forEach( prop -> {
			    IStruct		association		= prop.getAssociation();
			    String		collectionType	= association.getAsString( ORMKeys.collectionType );

			    // add has to THIS scope
			    DynamicFunction hasUDF		= BoxClassInstantiator.getHasMethod( collectionType, association );
			    BoxClassInstantiator.logger.trace( "Adding '{}' method for property '{}' on entity '{}", hasUDF.getName().getName(),
			        prop.getName(),
			        entityRecord.getEntityName() );
			    theEntity.put( hasUDF.getName(), hasUDF );

			    if ( association.containsKey( ORMKeys.collectionType ) ) {
				    // add add (for to-many associations)
				    DynamicFunction addUDF = BoxClassInstantiator.getAddMethod( collectionType, association );
				    BoxClassInstantiator.logger.trace( "Adding '{}' method for property '{}' on entity '{}", addUDF.getName().getName(), prop.getName(),
				        entityRecord.getEntityName() );
				    theEntity.put( addUDF.getName(), addUDF );

				    // add remove (for to-many associations)
				    DynamicFunction removeUDF = BoxClassInstantiator.getRemoveMethod( collectionType, association );
				    BoxClassInstantiator.logger.trace( "Adding '{}' method for property '{}' on entity '{}", removeUDF.getName().getName(),
				        prop.getName(),
				        entityRecord.getEntityName() );
				    theEntity.put( removeUDF.getName(), removeUDF );
			    }
		    } );

		if ( properties != null && !properties.isEmpty() ) {
			theEntity.getVariablesScope().putAll( properties );
		}

		return theEntity;
	}

	private static String getMethodName( IStruct associationMeta ) {
		String methodName = associationMeta.getAsString( Key._NAME );
		if ( associationMeta.containsKey( ORMKeys.singularName ) ) {
			methodName = associationMeta.getAsString( ORMKeys.singularName );
		}
		return methodName.substring( 0, 1 ).toUpperCase() + methodName.substring( 1 );
	}

	@Override
	public Object instantiate( Serializable id ) {
		ORMApp			ormApp			= ORMRequestContext.getForContext( RequestBoxContext.getCurrent() ).getORMApp();
		EntityRecord	entityRecord	= ormApp.lookupEntity( this.entityName, true );
		return BoxClassInstantiator.instantiate(
		    SessionFactoryBuilder
		        .getApplicationContext( entityMetamodel.getSessionFactory() )
		        .getRequestContext(),
		    entityRecord,
		    null
		);
	}

	@Override
	public Object instantiate() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'instantiate'" );
	}

	@Override
	public boolean isInstance( Object object ) {
		if ( object instanceof IClassRunnable theClass ) {
			logger.trace( "Checking to see if {} is an instance of {}", theClass.getClass().getName(), this.entityName );
			IStruct	annotations			= theClass.getAnnotations();
			String	objectEntityName	= annotations.containsKey( ORMKeys.entityName )
			    ? annotations.getAsString( ORMKeys.entityName )
			    : annotations.getAsString( ORMKeys.entity );
			logger.trace( "Looking at annotations, found entity name {}", objectEntityName );
			return this.entityName.equals( objectEntityName )
			    || subclassClassNames.contains( objectEntityName );
		}
		return false;

	}

	/**
	 * Create a `has*` method for the entity association, like `hasManufacturer()`, which returns a boolean indicating whether the entity has a value (one
	 * or more ) for the given association.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public static DynamicFunction getHasMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= Key.of( "has" + BoxClassInstantiator.getMethodName( associationMeta ) );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    VariablesScope scope	= context.getThisClass().getVariablesScope();
			    Object		collection	= scope.get( collectionKey );
			    if ( collection == null ) {
				    return false;
			    }
			    if ( collectionType == "bag" ) {
				    if ( collection instanceof PersistentBag bagCollection ) {
					    return bagCollection.size() > 0;
				    }
				    return ( ( Array ) collection ).size() > 0;
			    }
			    return scope.getAsStruct( collectionKey ).size() > 0;
		    },
		    new Argument[] {},
		    "boolean",
		    "Returns true if the entity has a value for the association " + collectionKey.getName(),
		    Struct.EMPTY
		);
	}

	/**
	 * Create an `add*` method for the entity association, like `addManufacturer()`, which appends the provided entity to the association.
	 * <p>
	 * Supports both array and struct associations, or, in the Hibernate vernacular, "bag" and "map" collections.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public static DynamicFunction getAddMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= Key.of( "add" + BoxClassInstantiator.getMethodName( associationMeta ) );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    boolean		isArrayCollection	= collectionType == "bag";

			    Object		itemToAdd			= context.getArgumentsScope().get( collectionKey );
			    VariablesScope variablesScope	= context.getThisClass().getVariablesScope();

			    if ( itemToAdd == null ) {
				    throw new BoxRuntimeException( "Cannot add a null entity to the collection." );
			    }

			    // create collection if it doesn't exist
			    if ( !variablesScope.containsKey( collectionKey ) ) {
				    variablesScope.put( collectionKey, isArrayCollection ? new Array() : new Struct() );
			    }

			    if ( isArrayCollection ) {
				    Object bag = variablesScope.get( collectionKey );
				    if ( bag instanceof PersistentBag bagCollection ) {
					    bagCollection.add( itemToAdd );
				    } else {
					    ( ( Array ) bag ).append( itemToAdd );
				    }
			    } else {
				    // @TODO: implement/test this
				    String structKey = StringCaster.cast( context.getArgumentsScope().get( Key.key ) );
				    variablesScope.getAsStruct( collectionKey ).put( structKey, itemToAdd );
			    }

			    // Return this for chainability.
			    return context.getThisClass();
		    },
		    new Argument[] {
		        new Argument( true, "any", collectionKey, Set.of( Validator.REQUIRED ) ),
		    },
		    "class",
		    String.format( "Append the provided entity to the {} collection, creating it if it does not exist.", collectionKey.getName() ),
		    Struct.EMPTY
		);
	}

	/**
	 * Create an `remove*` method for the entity association, like `removeManufacturer()`, which removes the provided entity from the association, if
	 * found.
	 * <p>
	 * Supports both array and struct associations, or, in the Hibernate vernacular, "bag" and "map" collections.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public static DynamicFunction getRemoveMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= Key.of( "remove" + BoxClassInstantiator.getMethodName( associationMeta ) );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    boolean		isArrayCollection	= collectionType == "bag";

			    // @TODO: We just need the SessionFactory to get this working.
			    // List<Key> keys = SessionFactoryBuilder.lookupEntityByClassName( sessionFactory,
			    // associationMeta.getAsString( Key._CLASS ) )
			    // .getEntityMeta()
			    // .getIdProperties()
			    // .stream()
			    // .map( prop -> Key.of( prop.getName() ) )
			    // .toList();
			    // @TODO: Pull this from the associated entity's getIdProperties().getName().
			    List<Key>	keys				= associationMeta.getAsString( Key._CLASS ).equals( "Vehicle" )
			        ? List.of( Key.of( "vin" ) )
			        : List.of( Key.id );
			    IClassRunnable itemToRemove		= ( IClassRunnable ) context.getArgumentsScope().get( collectionKey );
			    VariablesScope variablesScope	= context.getThisClass().getVariablesScope();

			    // return early if the collection doesn't exist - possibly throw an error for compat?
			    if ( !variablesScope.containsKey( collectionKey ) ) {
				    return context.getThisClass();
			    }
			    if ( itemToRemove == null ) {
				    throw new BoxRuntimeException( "Cannot remove a null entity from the collection." );
			    }

			    if ( isArrayCollection ) {
				    Object collection = variablesScope.get( collectionKey );
				    if ( collection instanceof PersistentBag bagCollection ) {
					    bagCollection.remove( itemToRemove );
				    } else {
					    Array arrayCollection = ( Array ) collection;
					    arrayCollection.stream()
					        .map( item -> ( IClassRunnable ) item )
					        .filter( item -> {
						        VariablesScope itemVariablesScope	= item.getVariablesScope();
						        VariablesScope itemToRemoveVariablesScope = itemToRemove.getVariablesScope();
						        for ( Key key : keys ) {
							        if ( !itemVariablesScope.containsKey( key ) || !itemToRemoveVariablesScope.containsKey( key ) ) {
								        return false;
							        }
							        if ( !itemVariablesScope.get( key ).equals( itemToRemoveVariablesScope.get( key ) ) ) {
								        return false;
							        }
						        }
						        return true;
					        } )
					        .findFirst()
					        .ifPresent( arrayCollection::remove );
				    }
			    } else {
				    // @TODO: test this!
				    String structKey = StringCaster.cast( context.getArgumentsScope().get( Key.key ) );
				    variablesScope.getAsStruct( collectionKey ).remove( structKey );
			    }

			    // Return this for chainability.
			    return context.getThisClass();
		    },
		    new Argument[] {
		        new Argument( true, "any", collectionKey, Set.of( Validator.REQUIRED ) ),
		    },
		    "class",
		    String.format( "Remove the provided entity from the {} collection.", collectionKey.getName() ),
		    Struct.EMPTY
		);
	}

}
