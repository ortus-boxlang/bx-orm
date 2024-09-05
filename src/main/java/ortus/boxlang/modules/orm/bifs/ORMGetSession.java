package ortus.boxlang.modules.orm.bifs;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

@BoxBIF
public class ORMGetSession extends BIF {

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Session _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return ORMService.getInstance().getSessionForContext( context );
	}

}
