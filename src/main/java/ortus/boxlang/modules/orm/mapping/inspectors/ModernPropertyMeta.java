package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ModernPropertyMeta extends AbstractPropertyMeta {

	public ModernPropertyMeta( String entityName, IStruct meta ) {
		super( entityName, meta );
	}

	/**
	 * Get all association metadata.
	 * <p>
	 * If none of these keys are present, the property is not an association and an empty struct will be returned:
	 * 
	 * <ul>
	 * <li>&#064;OneToOne</li>
	 * <li>&#064;OneToMany</li>
	 * <li>&#064;ManyToOne</li>
	 * <li>&#064;ManyToMany</li>
	 * </ul>
	 * 
	 * Here's a quick example of an ORM property defined as a one-to-many relationship:
	 * 
	 * <pre>
	 * <code>
	&#064;OneToMany {
	mappedBy : "customer",
	fetchMode : "select",
	batchSize : 25
	}
	property invoices;
	 * </code>
	 * </pre>
	 */
	protected IStruct parseAssociation( IStruct annotations ) {
		// @TODO: For *-to-many associations, lazy should default to true. (lazy fetch)
		// @TODO: For *-to-one associations, lazy should default to true. (lazy fetch)
		// @TODO: For one-to-one associations, lazy should default to false. (eager fetch)
		// Note: fetch mode (select vs join) is NOT the same as fetch type (lazy vs eager).
		// batch size is only applicable when fetch mode = select.
		return Struct.EMPTY;
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
			    key -> BooleanCaster.cast( annotations.get( ORMKeys.notNull ) ) );
		}
		// important to note that column.name defines the column name, whereas property.getName() defines the property name.
		// column.computeIfAbsent( Key._NAME, key -> this.name );
		// type coercion
		column.computeIfPresent( ORMKeys.insertable, ( key, object ) -> BooleanCaster.cast( column.get( ORMKeys.insertable ) ) );
		column.computeIfPresent( ORMKeys.updateable, ( key, object ) -> BooleanCaster.cast( column.get( ORMKeys.updateable ) ) );
		return column;
	}

	// @TODO: Implement this method.
	protected IStruct parseGeneratorAnnotations( IStruct annotations ) {
		IStruct generator = new Struct();
		if ( annotations.containsKey( ORMKeys.generatedValue ) ) {
			IStruct generatedValue = annotations.getAsStruct( ORMKeys.generatedValue );
			generator.putAll( generatedValue );
			if ( generatedValue.containsKey( ORMKeys.strategy ) ) {
				generator.put( Key._CLASS, generatedValue.getAsString( ORMKeys.strategy ) );
			}
			// @TODO: Implement 'tableGenerator' and 'sequenceGenerator' annotations.
			generator.putIfAbsent( Key.params, new Struct() );
		}
		return generator;
	}
}
