package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.SessionFactory;

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
public class ORMGetSessionFactory extends BIF {

	/**
	 * Constructor
	 */
	public ORMGetSessionFactory() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource, Set.of( Validator.NON_EMPTY ) )
		};
	}

	/**
	 * Retrieve the Hibernate SessionFactory configured for this datasource or default datasource.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.datasource The name of the datasource to retrieve the SessionFactory for. If not specified, the Application's default datasource is used.
	 */
	public SessionFactory _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String datasourceName = StringCaster.attempt( arguments.get( ORMKeys.datasource ) ).getOrDefault( "" );
		if ( !datasourceName.isBlank() ) {
			return ORMService.getInstance().getSessionFactoryForContext( context, Key.of( datasourceName ) );
		}
		return ORMService.getInstance().getSessionFactoryForContext( context );
	}

}
