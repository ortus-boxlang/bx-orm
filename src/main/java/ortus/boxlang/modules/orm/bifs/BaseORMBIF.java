package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIF;

public abstract class BaseORMBIF extends BIF {

	/**
	 * BoxLang runtime
	 */
	BoxRuntime	runtime		= BoxRuntime.getInstance();

	/**
	 * ORM service
	 */
	ORMService	ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );

	/**
	 * Constructor
	 */
	public BaseORMBIF() {
		super();
		// Do we need a logger in the BIFs?
		// this.logger = runtime.getLoggingService().getLogger( "orm" );
	}
}
