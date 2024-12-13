package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.RequestBoxContext;

public abstract class BaseORMBIF extends BIF {

	/**
	 * ORM service
	 */
	protected ORMService	ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );

	/**
	 * ORM application
	 */
	protected ORMApp		ormApp;

	/**
	 * Constructor
	 */
	protected BaseORMBIF() {
		super();
		this.ormApp = ormService.getORMAppByContext( RequestBoxContext.getCurrent() );
		// Do we need a logger in the BIFs?
		// this.logger = runtime.getLoggingService().getLogger( "orm" );
	}
}
