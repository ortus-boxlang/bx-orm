package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;
import java.util.Arrays;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
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

			    // // add add (for to-many associations)
			    // theEntity.put( Key.of( "add" + associationName, addUDF )

			    // // add remove (for to-many associations)
			    // theEntity.put( Key.of( "remove" + associationName, removeUDF ) );
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

}
