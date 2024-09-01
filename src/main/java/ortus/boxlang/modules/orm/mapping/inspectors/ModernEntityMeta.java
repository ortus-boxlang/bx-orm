package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.stream.Collectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public class ModernEntityMeta extends AbstractEntityMeta {

	public ModernEntityMeta( IStruct entityMeta ) {
		super( entityMeta );

		// @TODO: Implement extended/join/discriminator check
		this.isSimpleEntity = true;

		// @TODO: Attempt to support @Entity.foo syntax
		this.annotations.computeIfAbsent( ORMKeys.entity, key -> this.meta.getAsString( Key._name ) );
		this.entityName = ( String ) this.annotations.getAsString( ORMKeys.entity );
		if ( this.entityName == null || this.entityName.isEmpty() ) {
			this.entityName = this.meta.getAsString( Key._name );
		}

		/**
		 * All properties set here MUST be specific to the modern entity metadata. If they are applicable to both modern and classic metadata, they should be
		 * set in the AbstractEntityMeta constructor.
		 */
		this.isImmutable			= this.annotations.containsKey( ORMKeys.immutable );

		this.isDynamicInsert		= this.annotations.containsKey( ORMKeys.dynamicInsert )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicInsert, false ) );

		this.isDynamicUpdate		= this.annotations.containsKey( ORMKeys.dynamicUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicUpdate, false ) );

		this.isSelectBeforeUpdate	= this.annotations.containsKey( ORMKeys.selectBeforeUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.selectBeforeUpdate, false ) );

		if ( this.annotations.containsKey( ORMKeys.discriminator ) ) {
			this.discriminator = this.annotations.getAsStruct( ORMKeys.discriminator );
		}

		if ( this.annotations.containsKey( ORMKeys.lazy ) ) {
			this.isLazy = BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.lazy, true ) );
		}

		if ( this.annotations.containsKey( ORMKeys.batchsize ) ) {
			this.batchsize = IntegerCaster.cast( this.annotations.get( ORMKeys.batchsize ) );
		}

		if ( this.annotations.containsKey( ORMKeys.optimisticLock ) ) {
			this.optimisticLock = this.annotations.getAsString( ORMKeys.optimisticLock );
		}

		if ( this.annotations.containsKey( ORMKeys.rowid ) ) {
			this.rowid = this.annotations.getAsString( ORMKeys.rowid );
		}

		if ( this.annotations.containsKey( ORMKeys.where ) ) {
			this.where = this.annotations.getAsString( ORMKeys.where );
		}

		// https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0#table-annotation
		if ( this.annotations.containsKey( Key.table ) ) {
			var table = this.annotations.getAsStruct( Key.table );
			this.tableName = table.getAsString( Key._NAME );

			if ( table.containsKey( ORMKeys.schema ) ) {
				this.schemaName = table.getAsString( ORMKeys.schema );
			}

			if ( table.containsKey( ORMKeys.catalog ) ) {
				this.catalogName = table.getAsString( ORMKeys.catalog );
			}
		}

		this.properties		= entityMeta.getAsArray( Key.properties ).stream()
		    .map( IStruct.class::cast )
		    // Filter out transient properties, aka @Transient
		    .filter( ( prop ) -> !prop.getAsStruct( Key.annotations ).containsKey( ORMKeys._transient ) )
		    .filter( ( prop ) -> prop.getAsStruct( Key.annotations ).containsKey( Key.column ) )
		    .map( ModernPropertyMeta::new )
		    .collect( Collectors.toList() );

		this.idProperties	= entityMeta.getAsArray( Key.properties ).stream()
		    .map( IStruct.class::cast )
		    .filter( ( prop ) -> !prop.getAsStruct( Key.annotations ).containsKey( ORMKeys._transient ) )
		    .filter( ( prop ) -> prop.getAsStruct( Key.annotations ).containsKey( Key.id ) )
		    .map( ModernPropertyMeta::new )
		    .collect( Collectors.toList() );
	}
}
