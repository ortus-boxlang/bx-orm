package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.RequestBoxContext;

public abstract class BaseORMBIF extends BIF {

	/**
	 * BoxLang runtime
	 */
	protected BoxRuntime	runtime		= BoxRuntime.getInstance();

	/**
	 * ORM service
	 */
	protected ORMService	ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );

	protected ORMApp		ormApp;

	/**
	 * Constructor
	 */
	public BaseORMBIF() {
		super();
		this.ormApp = ormService.getORMApp( RequestBoxContext.getCurrent() );
		// Do we need a logger in the BIFs?
		// this.logger = runtime.getLoggingService().getLogger( "orm" );
	}
}
