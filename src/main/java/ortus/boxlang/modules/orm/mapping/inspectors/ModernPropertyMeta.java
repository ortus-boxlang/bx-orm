package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ModernPropertyMeta extends AbstractPropertyMeta {

	public ModernPropertyMeta( IStruct meta ) {
		super( meta );

		this.types		= Struct.EMPTY;
		this.generator	= Struct.EMPTY;
		if ( this.annotations.containsKey( Key.column ) ) {
			this.column = this.annotations.getAsStruct( Key.column );
		} else {
			this.column = new Struct();
		}
		if ( this.annotations.containsKey( ORMKeys.notNull ) ) {
			this.column.computeIfAbsent( ORMKeys.nullable,
			    key -> Boolean.FALSE.equals( BooleanCaster.cast( this.annotations.get( ORMKeys.notNull ) ) ) );
		}
		this.column.computeIfAbsent( Key._NAME, key -> this.name );
	}
}
