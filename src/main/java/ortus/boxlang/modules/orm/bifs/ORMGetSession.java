package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class ORMGetSession extends BIF {

	/**
	 * Constructor
	 */
	public ORMGetSession() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Retrieve the Hibernate Session configured for this datasource or default datasource.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.datasource The name of the datasource to retrieve the Session for. If not specified, the Application's default datasource is used.
	 */
	public Session _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( arguments.containsKey( ORMKeys.datasource ) ) {
			// @TODO: Implement
			// return ORMService.getInstance().getSessionForContext( context, arguments.getAsString( ORMKeys.datasource ) );
		}
		return ORMService.getInstance().getSessionForContext( context );
	}

}
