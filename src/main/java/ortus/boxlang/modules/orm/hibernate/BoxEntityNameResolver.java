package ortus.boxlang.modules.orm.hibernate;

import org.hibernate.EntityNameResolver;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.IStruct;

public class BoxEntityNameResolver implements EntityNameResolver {

	@Override
	public String resolveEntityName( Object entity ) {
		if ( entity instanceof IClassRunnable boxClass ) {
			IStruct	annotations	= boxClass.getAnnotations();
			String	result		= null;
			if ( annotations.containsKey( ORMKeys.entity ) ) {
				result = StringCaster.cast( annotations.get( ORMKeys.entity ) );
			} else if ( annotations.containsKey( ORMKeys.entity ) ) {
				result = StringCaster.cast( annotations.get( ORMKeys.entityName ) );
			}
			if ( result == null || result.isBlank() ) {
				result = boxClass.getClass().getSimpleName().replace( "$bx", "" ).replace( "$cfc", "" );
			}
			return result.trim();
		}
		return null;
	}

}
