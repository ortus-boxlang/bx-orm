package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityReload extends BIF {

	/**
	 * Constructor
	 */
	public EntityReload() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "Any", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Reload an entity from the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object entity = arguments.get( ORMKeys.entity );
		if ( entity instanceof String ) {
			// @TODO: Get this working.
			// ScopeSearchResult entityLookup = context.scopeFindNearby( Key.of( ( String ) entity ), null, true );
			// if ( entityLookup == null ) {
			// throw new IllegalArgumentException( "Entity variable not found: " + arguments.get( ORMKeys.entity ) );
			// }
			// entity = entityLookup.value();
		}
		ORMRequestContext.getForContext( context.getRequestContext() ).getSession().refresh( arguments.get( ORMKeys.entity ) );
		return null;
	}

}
