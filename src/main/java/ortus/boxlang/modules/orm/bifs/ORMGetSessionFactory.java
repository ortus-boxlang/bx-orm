package ortus.boxlang.modules.orm.bifs;

import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

@BoxBIF
public class ORMGetSessionFactory extends BIF {

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public SessionFactory _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return ORMService.getInstance().getSessionFactoryForContext( context );
	}

}
