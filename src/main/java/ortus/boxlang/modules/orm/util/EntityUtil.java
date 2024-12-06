package ortus.boxlang.modules.orm.util;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.IStruct;

public class EntityUtil {

	public static String getEntityName( IClassRunnable entity ) {
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

	public static String getClassNameFromFQN( String fqn ) {
		return fqn.substring( fqn.lastIndexOf( '.' ) + 1 );
	}

}
