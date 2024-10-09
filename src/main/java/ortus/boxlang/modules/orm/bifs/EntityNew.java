package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityNew extends BIF {

	private ClassLocator classLocator = ClassLocator.getInstance();

	/**
	 * Constructor
	 */
	public EntityNew() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entityName, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( false, "Struct", Key.properties )
		};
	}

	/**
	 * Instantiate a new entity, optionally with a struct of properties.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session			session		= ORMService.getInstance().getSessionForContext( context );
		String			bxClassFQN	= SessionFactoryBuilder.lookupBoxLangClass( session.getSessionFactory(), arguments.getAsString( ORMKeys.entityName ) );

		IClassRunnable	entity		= ( IClassRunnable ) classLocator.load( context, bxClassFQN, "bx" )
		    .invokeConstructor( context )
		    .unWrapBoxLangClass();

		if ( arguments.containsKey( Key.properties ) ) {
			IStruct properties = arguments.getAsStruct( Key.properties );
			if ( properties != null && properties.size() > 0 ) {
				entity.getVariablesScope().putAll( properties );
			}
		}

		return entity;
	}

}
