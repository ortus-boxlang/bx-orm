package ortus.boxlang.modules.orm.bifs;

import java.util.Set;
import java.util.stream.Collectors;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityNameList extends BIF {

	/**
	 * Constructor
	 */
	public EntityNameList() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", Key.delimiter, Set.of( Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		CharSequence delimiter = ( String ) arguments.getOrDefault( Key.delimiter, "," );
		if ( delimiter == null ) {
			delimiter = ",";
		}
		return SessionFactoryBuilder.getEntityMap(
		    ORMService.getInstance().getSessionFactoryForContext( context )
		)
		    .values()
		    .stream()
		    .map( entity -> entity.getEntityName() )
		    .collect( Collectors.joining( delimiter ) );
	}

}
