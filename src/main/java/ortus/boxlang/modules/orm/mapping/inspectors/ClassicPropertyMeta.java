package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.Map;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.JSONUtil;

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
	}

	protected IStruct parseColumnAnnotations( IStruct annotations ) {
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

	protected IStruct parseGeneratorAnnotations( IStruct annotations ) {
		IStruct generatorInfo = new Struct();
		if ( annotations.containsKey( ORMKeys.generator ) ) {
			generatorInfo.put( Key._CLASS, annotations.getAsString( ORMKeys.generator ) );
		}
		if ( annotations.containsKey( ORMKeys.property ) ) {
			generatorInfo.put( ORMKeys.property, annotations.getAsString( ORMKeys.property ) );
		}
		if ( annotations.containsKey( ORMKeys.generator ) ) {
			generatorInfo.put( Key._CLASS, annotations.getAsString( ORMKeys.generator ) );
		}
		if ( annotations.containsKey( ORMKeys.selectKey ) ) {
			generatorInfo.put( ORMKeys.selectKey, annotations.getAsString( ORMKeys.selectKey ) );
		}
		if ( annotations.containsKey( ORMKeys.generated ) ) {
			generatorInfo.put( ORMKeys.generated, annotations.getAsString( ORMKeys.generated ) );
		}
		if ( annotations.containsKey( ORMKeys.sequence ) ) {
			generatorInfo.put( ORMKeys.sequence, annotations.getAsString( ORMKeys.sequence ) );
		}
		if ( annotations.containsKey( Key.params ) ) {
			Object	paramValue	= annotations.getAsString( Key.params );
			IStruct	theParams	= Struct.fromMap( ( Map ) JSONUtil.fromJSON( paramValue ) );
			if ( theParams == null ) {
				// logger.warn( "Property '{}' has a 'params' annotation that could not be cast to a struct: {}", propName, paramValue );
			} else {
				generatorInfo.put( Key.params, theParams );
			}
		}
		return generatorInfo;
	}
}
