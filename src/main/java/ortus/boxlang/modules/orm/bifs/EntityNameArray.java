package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.SessionFactoryBuilder;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.util.ListUtil;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
// @TODO: Consider deprecating entityNameList, since we can just use entityNameArray.toList().
@BoxBIF( alias = "EntityNameList" )
public class EntityNameArray extends BIF {

	/**
	 * Constructor
	 */
	public EntityNameArray() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "String", Key.delimiter, Set.of( Validator.NON_EMPTY ) ),
		    new Argument( false, "String", Key.datasource, Set.of( Validator.NON_EMPTY ) ),
		};
	}

	/**
	 * Retrieve an array of entity names for this ORM application.
	 * 
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.delimiter The delimiter to use between entity names. Defaults to a comma.
	 * 
	 * @argument.datasource The name of the datasource to filter on. If provided, only entities configured for this datasource will be returned.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Key		bifMethodKey	= arguments.getAsKey( BIF.__functionName );
		String	delimiter		= ( String ) arguments.getOrDefault( Key.delimiter, "," );
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
		SessionFactory	sessionFactory	= datasource != null
		    ? ORMService.getInstance().getSessionFactoryForContextAndDataSource( context, datasource )
		    : ORMService.getInstance().getSessionFactoryForContext( context );

		Array			entityNames		= Array.fromList( SessionFactoryBuilder.getEntityMap( sessionFactory )
		    .values()
		    .stream()
		    .map( entity -> entity.getEntityName() )
		    // Sort alphabetically to ensure a consistent order. The order of entities in the entity map is not guaranteed, as it is a HashMap and is
		    // populated in order of discovery.
		    .sorted()
		    .toList() );

		return bifMethodKey.equals( ORMKeys.entityNameList ) ? ListUtil.asString( entityNames, delimiter ) : entityNames;
	}
}
