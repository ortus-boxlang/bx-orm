package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ModernPropertyMeta extends AbstractPropertyMeta {

	public ModernPropertyMeta( IStruct meta ) {
		super( meta );
	}

	protected IStruct parseColumnAnnotations( IStruct annotations ) {
		IStruct column;
		if ( annotations.containsKey( Key.column ) && annotations.get( Key.column ) instanceof IStruct ) {
			column = annotations.getAsStruct( Key.column );
		} else {
			column = new Struct();
		}
		// @Immutable annotation shall not override the more specific @Column(insertable=true, updatable=true) annotation.
		if ( annotations.containsKey( ORMKeys.immutable ) ) {
			column.computeIfAbsent( ORMKeys.insertable, key -> Boolean.FALSE );
			column.computeIfAbsent( ORMKeys.updateable, key -> Boolean.FALSE );
		}
		if ( annotations.containsKey( ORMKeys.notNull ) ) {
			column.computeIfAbsent( ORMKeys.nullable,
			    key -> Boolean.FALSE.equals( BooleanCaster.cast( annotations.get( ORMKeys.notNull ) ) ) );
		}
		column.computeIfAbsent( Key._NAME, key -> this.name );
		return column;
	}

	// @TODO: Implement this method.
	protected IStruct parseGeneratorAnnotations( IStruct annotations ) {
		IStruct generator = new Struct();
		if ( annotations.containsKey( ORMKeys.generatedValue ) ) {
			IStruct generatedValue = annotations.getAsStruct( ORMKeys.generatedValue );
			// @TODO: Implement this.
			// generator.put( ORMKeys.generated, "never|insert|always" );
			if ( generatedValue.containsKey( ORMKeys.strategy ) ) {
				generator.put( Key._CLASS, generatedValue.getAsString( ORMKeys.strategy ) );
			}
		}
		if ( annotations.containsKey( ORMKeys.tableGenerator ) ) {
			// IStruct tableGenerator = annotations.getAsStruct( ORMKeys.generatedValue );
			// if ( tableGenerator.containsKey( ORMKeys.strategy ) ) {
			// generator.put( ORMKeys.strategy, tableGenerator.getAsString( ORMKeys.strategy ) );
			// }
		}
		return generator;
	}
}
