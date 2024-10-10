package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;
import java.util.Arrays;

import org.hibernate.collection.internal.PersistentBag;
import org.hibernate.collection.internal.PersistentMap;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DynamicFunction;
import ortus.boxlang.runtime.types.Struct;

/**
 * This class is used to instantiate a BoxLang class for a Hibernate entity.
 * <p>
 * In other words, here is where the magic happens to tie a Hibernate entity to a BoxLang class when loading entities from the database via
 * `entityLoadByPK()`, creating new entities via `entityNew()`, etc, etc.
 */
public class BoxClassInstantiator implements Instantiator {

	Logger					logger			= LoggerFactory.getLogger( BoxClassInstantiator.class );

	private EntityMetamodel	entityMetamodel;
	private PersistentClass	mappingInfo;

	private ClassLocator	classLocator	= ClassLocator.getInstance();

	public BoxClassInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		this.entityMetamodel	= entityMetamodel;
		this.mappingInfo		= mappingInfo;
	}

	@Override
	public Object instantiate( Serializable id ) {
		String			bxClassFQN	= SessionFactoryBuilder.lookupEntity( entityMetamodel.getSessionFactory(),
		    entityMetamodel.getName() ).getClassFQN();
		IBoxContext		appContext	= SessionFactoryBuilder
		    .getApplicationContext( entityMetamodel.getSessionFactory() );

		IClassRunnable	theEntity	= ( IClassRunnable ) classLocator.load( appContext, bxClassFQN, "bx" )
		    .invokeConstructor( appContext )
		    .unWrapBoxLangClass();

		Arrays.stream( this.entityMetamodel.getProperties() )
		    .filter( prop -> prop.getType().isAssociationType() )
		    .forEach( prop -> {
			    String associationName = prop.getName();
			    logger.debug( "Adding association methods for property: {} on entity {}", associationName, this.entityMetamodel.getName() );

			    // add has to THIS scope
			    DynamicFunction hasUDF = getHasMethod( associationName );
			    theEntity.put( hasUDF.getName(), hasUDF );

			    logger.error( "Adding addX() method for property: {} on entity {}", associationName, this.entityMetamodel.getName() );
			    if ( prop.getType().isCollectionType() ) {
				    // add add (for to-many associations)
				    DynamicFunction addUDF = getAddMethod( associationName );
				    theEntity.put( addUDF.getName(), addUDF );

				    // add remove (for to-many associations)
				    // theEntity.put( Key.of( "remove" + associationName, removeUDF ) );
			    }
		    } );

		return theEntity;
	}

	@Override
	public Object instantiate() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'instantiate'" );
	}

	@Override
	public boolean isInstance( Object object ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'isInstance'" );
	}

	/**
	 * Create a `has*` method for the entity association, like `hasManufacturer()`, which returns a boolean indicating whether the entity has a value (one
	 * or more ) for the given association.
	 * 
	 * @param associationName The name of the association, like 'manufacturer' or 'vehicles'. Used to construct the method name.
	 * 
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	private DynamicFunction getHasMethod( String associationName ) {
		return new DynamicFunction(
		    Key.of( "has" + associationName ),
		    ( context, function ) -> {
			    // System.out.println( context.getArgumentsScope().toString() );
			    return context.getThisClass().getVariablesScope().get( associationName ) != null;
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
	 * 
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	private DynamicFunction getAddMethod( String associationName ) {
		return new DynamicFunction(
		    Key.of( "add" + associationName ),
		    ( context, function ) -> {
			    // @TODO: Determine the association type (bag or map, i.e. array or struct) so we can create the correct type.
			    boolean		isArrayCollection	= true;

			    Object		itemToAdd			= context.getArgumentsScope().get( 0 );
			    VariablesScope variablesScope	= context.getThisClass().getVariablesScope();

			    // create collection if it doesn't exist
			    if ( !variablesScope.containsKey( associationName ) ) {
				    variablesScope.put( associationName, isArrayCollection ? new Array() : new Struct() );
			    }

			    if ( isArrayCollection ) {
				    ( ( PersistentBag ) variablesScope.get( associationName ) ).add( itemToAdd );
			    } else {
				    // struct collection
				    String structKey = StringCaster.cast( context.getArgumentsScope().get( 1 ) );
				    ( ( PersistentMap ) variablesScope.get( associationName ) ).put( structKey, itemToAdd );
			    }

			    // Return this for chainability.
			    return context.getThisClass();
		    },
		    new Argument[] {},
		    "class",
		    String.format( "Append the provided entity to the {} association, creating it if it does not exist.", associationName ),
		    Struct.EMPTY
		);
	}

}
