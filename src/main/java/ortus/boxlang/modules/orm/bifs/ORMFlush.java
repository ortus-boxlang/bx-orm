package ortus.boxlang.modules.orm.bifs;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF
public class ORMFlush extends BIF {

	/**
	 * Constructor
	 */
	public ORMFlush() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource )
		};
	}

	/**
	 * ORMFlush
	 * <p>
	 * Flush the Hibernate session - synchronizing the in-memory state with the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session session = ORMService.getInstance().getORMApp( context ).getSession( context );
		session.flush();

		return null;
	}

}
