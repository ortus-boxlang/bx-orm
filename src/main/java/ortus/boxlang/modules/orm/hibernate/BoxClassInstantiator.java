package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
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

	public static final Logger	logger				= LoggerFactory.getLogger( BoxClassInstantiator.class );

	private EntityMetamodel		entityMetamodel;
	private PersistentClass		mappingInfo;
	private String				entityName;
	private EntityRecord		entityRecord;
	private List<String>		subclassClassNames	= new ArrayList<>();

	public static IClassRunnable instantiate( ApplicationBoxContext context, EntityRecord entityRecord, IStruct properties ) {
		IClassRunnable theEntity = ( IClassRunnable ) ClassLocator.getInstance().load( context, entityRecord.getClassFQN(), "bx" )
		    .invokeConstructor( context )
		    .unWrapBoxLangClass();

		entityRecord.getEntityMeta().getAssociations().stream()
		    .forEach( prop -> {
			    IStruct association	= prop.getAssociation();
			    String methodName	= association.getAsString( Key._NAME );
			    String collectionType = association.getAsString( ORMKeys.collectionType );
			    if ( association.containsKey( ORMKeys.singularName ) ) {
				    methodName = association.getAsString( ORMKeys.singularName );
			    }
			    methodName = methodName.substring( 0, 1 ).toUpperCase() + methodName.substring( 1 );
			    BoxClassInstantiator.logger.error( "Adding 'has{}' methods for property '{}' on entity '{}", methodName, prop.getName(),
			        entityRecord.getEntityName() );

			    // add has to THIS scope
			    DynamicFunction hasUDF = BoxClassInstantiator.getHasMethod( methodName, collectionType );
			    theEntity.put( hasUDF.getName(), hasUDF );

			    if ( association.containsKey( ORMKeys.collectionType ) ) {
				    BoxClassInstantiator.logger.error( "Adding 'add{}' method for property '{}' on entity '{}", methodName, prop.getName(),
				        entityRecord.getEntityName() );
				    // add add (for to-many associations)
				    DynamicFunction addUDF = BoxClassInstantiator.getAddMethod( methodName, collectionType, association );
				    theEntity.put( addUDF.getName(), addUDF );

				    // add remove (for to-many associations)
				    BoxClassInstantiator.logger.error( "Adding 'remove{}' method for property '{}' on entity '{}", methodName, prop.getName(),
				        entityRecord.getEntityName() );
				    DynamicFunction removeUDF = BoxClassInstantiator.getRemoveMethod( methodName, collectionType, association );
				    theEntity.put( removeUDF.getName(), removeUDF );
			    }
		    } );

		if ( properties != null && !properties.isEmpty() ) {
			theEntity.getVariablesScope().putAll( properties );
		}

		return theEntity;
	}

	public BoxClassInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		this.entityMetamodel	= entityMetamodel;
		this.mappingInfo		= mappingInfo;
		this.entityRecord		= SessionFactoryBuilder.lookupEntity( entityMetamodel.getSessionFactory(),
		    entityMetamodel.getName() );
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

	@Override
	public Object instantiate( Serializable id ) {
		return BoxClassInstantiator.instantiate( SessionFactoryBuilder
		    .getApplicationContext( entityMetamodel.getSessionFactory() ), entityRecord, null );
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
	 * @param associationName The name of the association, like 'manufacturer' or 'vehicles'. Used to construct the method name.
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * 
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public static DynamicFunction getHasMethod( String associationName, String collectionType ) {
		return new DynamicFunction(
		    Key.of( "has" + associationName ),
		    ( context, function ) -> {
			    VariablesScope scope = context.getThisClass().getVariablesScope();
			    return scope.get( associationName ) != null && ( collectionType == "bag"
			        ? scope.getAsArray( Key.of( associationName ) ).size() > 0
			        : scope.getAsStruct( Key.of( associationName ) ).size() > 0 );
		    },
		    new Argument[] {},
		    "boolean",
		    "Returns true if the entity has a value for the association " + associationName,
		    Struct.EMPTY
		);
	}

	/**
	 * Create an `add*` method for the entity association, like `addManufacturer()`, which appends the provided entity to the association.
	 * <p>
	 * Supports both array and struct associations, or, in the Hibernate vernacular, "bag" and "map" collections.
	 * 
	 * @param associationName The name of the association, like 'manufacturer' or 'vehicles'. Used to construct the method name.
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 * 
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public static DynamicFunction getAddMethod( String associationName, String collectionType, IStruct associationMeta ) {
		Key collectionKey = Key.of( associationName );
		return new DynamicFunction(
		    Key.of( "add" + associationName ),
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
				    variablesScope.getAsArray( collectionKey ).append( itemToAdd );
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
		    String.format( "Append the provided entity to the {} collection, creating it if it does not exist.", associationName ),
		    Struct.EMPTY
		);
	}

	/**
	 * Create an `remove*` method for the entity association, like `removeManufacturer()`, which removes the provided entity from the association, if
	 * found.
	 * <p>
	 * Supports both array and struct associations, or, in the Hibernate vernacular, "bag" and "map" collections.
	 * 
	 * @param associationName The name of the association, like 'manufacturer' or 'vehicles'. Used to construct the method name.
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 * 
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public static DynamicFunction getRemoveMethod( String associationName, String collectionType, IStruct associationMeta ) {
		Key collectionKey = Key.of( associationName );
		return new DynamicFunction(
		    Key.of( "remove" + associationName ),
		    ( context, function ) -> {
			    boolean		isArrayCollection	= collectionType == "bag";

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
				    Array collection = variablesScope.getAsArray( collectionKey );
				    collection.stream()
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
				        .ifPresent( collection::remove );
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
		    String.format( "Remove the provided entity from the {} collection.", associationName ),
		    Struct.EMPTY
		);
	}

}
