package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class ORMCloseSession extends BIF {

	/**
	 * Constructor
	 */
	public ORMCloseSession() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Close the Hibernate session for the current context and provided (or default) datasource
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.datasource The datasource to close the session for. If not provided, the default datasource will be used.
	 * 
	 * @return null.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session	session;
		String	datasourceName	= StringCaster.attempt( arguments.get( ORMKeys.datasource ) ).getOrDefault( "" );
		if ( !datasourceName.isBlank() ) {
			session = ORMService.getInstance().getSessionForContext( context, Key.of( datasourceName ) );
		} else {
			session = ORMService.getInstance().getSessionForContext( context );
		}
		session.close();

		return null;
	}

}
