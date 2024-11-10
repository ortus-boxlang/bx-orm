package ortus.boxlang.modules.orm.bifs;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF
public class ORMFlush extends BIF {

	/**
	 * ORM Service, responsible for managing ORM applications.
	 */
	private ORMService			ormService;

	private static final Logger	logger	= LoggerFactory.getLogger( ORMFlush.class );

	/**
	 * Constructor
	 */
	public ORMFlush() {

		super();
		this.ormService		= ( ORMService ) BoxRuntime.getInstance().getGlobalService( ORMKeys.ORMService );
		declaredArguments	= new Argument[] {
		    new Argument( false, "String", ORMKeys.datasource )
		};
	}

	/**
	 * ORMFlush
	 * <p>
	 * Flush the Hibernate session - synchronizing the in-memory state with the database.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session session = ORMRequestContext.getForContext( context ).getSession();
		logger.debug( "Flushing session: {}", session );
		session.flush();

		return null;
	}

}
