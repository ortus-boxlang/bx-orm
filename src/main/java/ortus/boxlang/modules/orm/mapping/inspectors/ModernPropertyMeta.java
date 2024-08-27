package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ModernPropertyMeta extends AbstractPropertyMeta {

	public ModernPropertyMeta( IStruct meta ) {
		super( meta );

		this.column		= this.annotations.getAsStruct( Key.column );
		this.types		= Struct.EMPTY;
		this.generator	= Struct.EMPTY;
	}
}
