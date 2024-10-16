package ortus.boxlang.modules.orm.bifs;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.DataSource;
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
		    new Argument( false, "String", Key.datasource, Set.of( Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Retrieve a comma-separated list of entity names for this ORM application.
	 * 
	 * @argument.delimiter The delimiter to use between entity names. Defaults to a comma.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		CharSequence delimiter = ( String ) arguments.getOrDefault( Key.delimiter, "," );
		if ( delimiter == null ) {
			delimiter = ",";
		}
		String		datasourceName	= ( String ) arguments.getAsString( Key.datasource );
		DataSource	datasource		= null;
		if ( datasourceName != null && !datasourceName.isBlank() ) {
			datasource = context.getParentOfType( IJDBCCapableContext.class )
			    .getConnectionManager()
			    .getDatasourceOrThrow( Key.of( datasourceName ) );
		}
		// @TODO: This technically only works for a SINGLE datasource. We need to rearchitect the way ORMService stores and retrieves session factories, so
		// we can retrieve ALL session factories for the given RequestBoxContext, or for the specific RequestBoxContext/datasource name pair.
		SessionFactory sessionFactory = datasource != null
		    ? ORMService.getInstance().getSessionFactoryForContextAndDataSource( context, datasource )
		    : ORMService.getInstance().getSessionFactoryForContext( context );
		return SessionFactoryBuilder.getEntityMap( sessionFactory )
		    .values()
		    .stream()
		    .map( entity -> entity.getEntityName() )
		    .collect( Collectors.joining( delimiter ) );
	}
}
