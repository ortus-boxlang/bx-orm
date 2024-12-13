package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.IStruct;

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

	/**
	 * Pull the entity name from the provided boxlang class
	 * 
	 * @param entity Instance of IClassRunnable, aka the compiled/parsed entity.
	 */
	protected String getEntityName( IClassRunnable entity ) {
		// @TODO: Should we look up the EntityRecord and use that to grab the class name?
		IStruct annotations = entity.getAnnotations();
		if ( annotations.containsKey( ORMKeys.entity ) && !annotations.getAsString( ORMKeys.entity ).isBlank() ) {
			return annotations.getAsString( ORMKeys.entity );
		} else if ( annotations.containsKey( ORMKeys.entityName ) && !annotations.getAsString( ORMKeys.entityName ).isBlank() ) {
			return annotations.getAsString( ORMKeys.entityName );
		} else {
			return getClassNameFromFQN( entity.bxGetName().getName() );
		}
	}

	/**
	 * Retrieve the last portion of the FQN as the class name.
	 * 
	 * @param fqn Boxlang class FQN, like models.orm.foo
	 */
	protected String getClassNameFromFQN( String fqn ) {
		return fqn.substring( fqn.lastIndexOf( '.' ) + 1 );
	}
}
