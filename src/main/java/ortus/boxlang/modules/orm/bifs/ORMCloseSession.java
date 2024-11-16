package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
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
	 * ORM Service, responsible for managing ORM applications.
	 */
	private ORMService ormService;

	/**
	 * Constructor
	 */
	public ORMCloseSession() {

		super();
		this.ormService		= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		declaredArguments	= new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Close the Hibernate session for the current context and provided (or default) datasource
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.datasource The datasource on which to close the current session. If not provided, the default datasource will be used.
	 * 
	 * @return null.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session	session;
		String	datasourceName	= StringCaster.attempt( arguments.get( ORMKeys.datasource ) ).getOrDefault( "" );
		if ( !datasourceName.isBlank() ) {
			session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession( Key.of( datasourceName ) );
		} else {
			session = ORMRequestContext.getForContext( context.getRequestContext() ).getSession();
		}
		session.close();

		return null;
	}

}
