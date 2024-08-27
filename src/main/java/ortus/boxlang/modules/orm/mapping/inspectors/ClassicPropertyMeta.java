package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ClassicPropertyMeta extends AbstractPropertyMeta {

	public ClassicPropertyMeta( IStruct meta ) {
		super( meta );

		// General property annotations
		if ( this.annotations.containsKey( ORMKeys.lazy ) ) {
			this.isLazy = BooleanCaster.cast( this.annotations.get( ORMKeys.lazy ) );
		}
		if ( this.annotations.containsKey( ORMKeys.readOnly ) ) {
			this.isImmutable = BooleanCaster.cast( this.annotations.get( ORMKeys.readOnly ) );
		}
		if ( this.annotations.containsKey( ORMKeys.optimisticLock ) ) {
			this.isOptimisticLock = BooleanCaster.cast( this.annotations.get( ORMKeys.optimisticLock ) );
		}
		if ( this.annotations.containsKey( ORMKeys.formula ) ) {
			this.formula = this.annotations.getAsString( ORMKeys.formula );
		}

		this.types		= parseTypeAnnotations( this.annotations );
		this.column		= parseColumnAnnotations( this.annotations );
		this.generator	= parseGeneratorAnnotations( this.annotations );
	}

	private IStruct parseTypeAnnotations( IStruct annotations ) {
		IStruct typeInfo = new Struct();
		if ( annotations.containsKey( Key.type ) ) {
			typeInfo.put( Key.type, annotations.getAsString( Key.type ) );
		}
		if ( annotations.containsKey( ORMKeys.fieldtype ) ) {
			typeInfo.put( ORMKeys.fieldtype, annotations.getAsString( ORMKeys.fieldtype ) );
		}
		if ( annotations.containsKey( Key.sqltype ) ) {
			typeInfo.put( Key.sqltype, annotations.getAsString( Key.sqltype ) );
		}
		if ( annotations.containsKey( ORMKeys.ORMType ) ) {
			typeInfo.put( ORMKeys.ORMType, annotations.getAsString( ORMKeys.ORMType ) );
		}
		return typeInfo;
	}

	private IStruct parseColumnAnnotations( IStruct annotations ) {
		IStruct columnInfo = new Struct();
		columnInfo.put( "name", this.name );
		if ( annotations.containsKey( Key.length ) ) {
			columnInfo.put( Key.length, annotations.getAsString( Key.length ) );
		}
		if ( annotations.containsKey( ORMKeys.precision ) ) {
			columnInfo.put( ORMKeys.precision, annotations.getAsString( ORMKeys.precision ) );
		}
		if ( annotations.containsKey( ORMKeys.scale ) ) {
			columnInfo.put( ORMKeys.scale, annotations.getAsString( ORMKeys.scale ) );
		}
		if ( annotations.containsKey( ORMKeys.unique ) ) {
			columnInfo.put( ORMKeys.unique, BooleanCaster.cast( annotations.getAsString( ORMKeys.unique ) ) );
		}
		if ( annotations.containsKey( ORMKeys.notNull ) ) {
			columnInfo.put( ORMKeys.nullable, BooleanCaster.cast( annotations.getAsString( ORMKeys.notNull ) ) );
		}
		if ( annotations.containsKey( ORMKeys.insert ) ) {
			columnInfo.put( ORMKeys.insertable, BooleanCaster.cast( annotations.getAsString( ORMKeys.insert ) ) );
		}
		if ( annotations.containsKey( ORMKeys.update ) ) {
			columnInfo.put( ORMKeys.updateable, BooleanCaster.cast( annotations.getAsString( ORMKeys.update ) ) );
		}
		if ( annotations.containsKey( Key.table ) ) {
			columnInfo.put( Key.table, annotations.getAsString( Key.table ) );
		}
		return columnInfo;
	}

	private IStruct parseGeneratorAnnotations( IStruct annotations ) {
		return new Struct();
	}
}
