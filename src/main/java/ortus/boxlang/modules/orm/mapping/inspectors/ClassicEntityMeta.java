package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.stream.Collectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ClassicEntityMeta extends AbstractEntityMeta {

	public ClassicEntityMeta( IStruct entityMeta ) {
		super( entityMeta );

		this.annotations.computeIfAbsent( ORMKeys.entityName, key -> this.meta.getAsString( Key._name ) );

		this.entityName = this.annotations.getAsString( ORMKeys.entityName );

		this.annotations.computeIfAbsent( ORMKeys.table, key -> this.getEntityName() );
		this.tableName = this.annotations.getAsString( Key.table );

		if ( this.annotations.containsKey( ORMKeys.schema ) ) {
			this.schemaName = this.annotations.getAsString( ORMKeys.schema );
		}

		if ( this.annotations.containsKey( ORMKeys.catalog ) ) {
			this.catalogName = this.annotations.getAsString( ORMKeys.catalog );
		}
		this.isImmutable = this.annotations.containsKey( ORMKeys.readOnly )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.readOnly, "false" ), false );

		if ( this.annotations.containsKey( ORMKeys.discriminatorColumn ) || this.annotations.containsKey( ORMKeys.discriminatorValue ) ) {
			this.discriminator = new Struct();
			// copy the old-school discriminatorColumn and discriminatorValue annotations into the new struct
			this.discriminator.computeIfAbsent( Key._name, key -> this.annotations.getAsString( ORMKeys.discriminatorColumn ) );
			this.discriminator.computeIfAbsent( Key.value, key -> this.annotations.getAsString( ORMKeys.discriminatorValue ) );
		}

		this.properties		= entityMeta.getAsArray( Key.properties ).stream()
		    .map( IStruct.class::cast )
		    .filter( ( IStruct prop ) -> {
								    var annotations = prop.getAsStruct( Key.annotations );
								    return !annotations.containsKey( ORMKeys.persistent ) || BooleanCaster.cast( annotations.get( ORMKeys.persistent ), false );
							    } )
		    .filter( ( IStruct prop ) -> {
			    var annotations = prop.getAsStruct( Key.annotations );
			    return !annotations.containsKey( ORMKeys.fieldtype ) || annotations.getAsString( ORMKeys.fieldtype ).equals( "column" );
		    } )
		    .map( ClassicPropertyMeta::new )
		    .collect( Collectors.toList() );

		this.idProperties	= entityMeta.getAsArray( Key.properties ).stream()
		    .map( IStruct.class::cast )
		    .filter( ( IStruct prop ) -> {
								    var annotations = prop.getAsStruct( Key.annotations );
								    return annotations.containsKey( ORMKeys.fieldtype ) && annotations.getAsString( ORMKeys.fieldtype ).equals( "id" );
							    } )
		    .map( ClassicPropertyMeta::new )
		    .collect( Collectors.toList() );

	}
}