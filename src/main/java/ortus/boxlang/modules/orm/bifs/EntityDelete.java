package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.util.EntityUtil;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityDelete extends BIF {

	/**
	 * ORM Service, responsible for managing ORM applications.
	 */
	private ORMService ormService;

	/**
	 * Constructor
	 */
	public EntityDelete() {
		super();
		this.ormService		= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		declaredArguments	= new Argument[] {
		    new Argument( true, "class", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Delete an entity from the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session			session	= ORMRequestContext.getForContext( context ).getSession();
		IClassRunnable	entity	= ( IClassRunnable ) arguments.get( ORMKeys.entity );
		session.delete( EntityUtil.getEntityName( entity ), entity );
		return null;
	}
}
