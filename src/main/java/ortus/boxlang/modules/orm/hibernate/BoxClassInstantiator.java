package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;

import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.loader.ClassLocator;

/**
 * This class is used to instantiate a BoxLang class for a Hibernate entity.
 * <p>
 * In other words, here is where the magic happens to tie a Hibernate entity to a BoxLang class when loading entities from the database via
 * `entityLoadByPK()`, creating new entities via `entityNew()`, etc, etc.
 */
public class BoxClassInstantiator implements Instantiator {

	private EntityMetamodel	entityMetamodel;
	private PersistentClass	mappingInfo;

	private ClassLocator	classLocator	= ClassLocator.getInstance();

	public BoxClassInstantiator( EntityMetamodel entityMetamodel, PersistentClass mappingInfo ) {
		this.entityMetamodel	= entityMetamodel;
		this.mappingInfo		= mappingInfo;
	}

	@Override
	public Object instantiate( Serializable id ) {
		String		bxClassFQN	= SessionFactoryBuilder.lookupBoxLangClass( entityMetamodel.getSessionFactory(),
		    entityMetamodel.getName() );
		IBoxContext	context		= SessionFactoryBuilder
		    .getApplicationContext( entityMetamodel.getSessionFactory() );

		Object		theEntity	= classLocator.load( context, bxClassFQN, "bx" )
		    .invokeConstructor( context )
		    .unWrapBoxLangClass();

		// @TODO: Get this working!
		// Arrays.stream( this.entityMetamodel.getPropertyTypes() )
		// .filter( propertyType -> propertyType.isAssociationType() )
		// .forEach( propertyType -> {
		// String associationName = this.entityMetamodel.getPropertyNames()[propertyType.getPropertyIndex()];
		// // add has
		// theEntity.getThisScope().put( Key.of( "has" + associationName, hasUDF )
		// // add add (for to-many associations)
		// theEntity.getThisScope().put( Key.of( "add" + associationName, addUDF )
		// // add remove (for to-many associations)
		// theEntity.getThisScope().put( Key.of( "remove" + associationName, removeUDF ) );

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

}
