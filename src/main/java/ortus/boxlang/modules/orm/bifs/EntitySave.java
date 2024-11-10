package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.util.EntityUtil;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
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
		Session			session		= ORMRequestContext.getForContext( context ).getSession();
		IClassRunnable	entity		= ( IClassRunnable ) arguments.get( ORMKeys.entity );
		Boolean			forceInsert	= BooleanCaster.cast( arguments.getOrDefault( ORMKeys.forceinsert, false ) );
		if ( forceInsert ) {
			session.save( EntityUtil.getEntityName( entity ), entity );
		} else {
			session.saveOrUpdate( EntityUtil.getEntityName( entity ), entity );
		}

		return null;
	}

	private String getClassNameFromFQN( String fqn ) {
		return fqn.substring( fqn.lastIndexOf( '.' ) + 1 );
	}

}
