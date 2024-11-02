package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntitySave extends BIF {

	/**
	 * ORM Service, responsible for managing ORM applications.
	 */
	private ORMService	ormService;

	Logger				logger	= LoggerFactory.getLogger( EntitySave.class );

	/**
	 * Constructor
	 */
	public EntitySave() {

		super();
		this.ormService		= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		declaredArguments	= new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Boolean", ORMKeys.forceinsert )
		};
	}

	/**
	 * Save the provided entity to the persistence context
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @return
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session			session		= this.ormService.getORMApp( context ).getSession( context );
		// @TODO: Implement forceinsert
		IClassRunnable	entity		= ( IClassRunnable ) arguments.get( ORMKeys.entity );
		// @TODO: Should we look up the EntityRecord and use that to grab the class name?
		String			entityName	= getClassNameFromFQN( entity.getName().getName() );
		IStruct			annotations	= entity.getAnnotations();
		if ( annotations.containsKey( ORMKeys.entity ) && !annotations.getAsString( ORMKeys.entity ).isBlank() ) {
			entityName = annotations.getAsString( ORMKeys.entity );
		} else if ( annotations.containsKey( ORMKeys.entityName ) && !annotations.getAsString( ORMKeys.entityName ).isBlank() ) {
			entityName = annotations.getAsString( ORMKeys.entityName );
		}
		session.save( entityName, entity );

		return null;
	}

	private String getClassNameFromFQN( String fqn ) {
		return fqn.substring( fqn.lastIndexOf( '.' ) + 1 );
	}

}
