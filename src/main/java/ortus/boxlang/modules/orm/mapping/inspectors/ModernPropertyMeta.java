package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ModernPropertyMeta extends AbstractPropertyMeta {

	public ModernPropertyMeta( String entityName, IStruct meta ) {
		super( entityName, meta );

		if ( this.annotations.containsKey( Key.version ) ) {
			this.fieldType = FIELDTYPE.VERSION;
		} else if ( this.annotations.containsKey( ORMKeys.timestamp ) ) {
			this.fieldType = FIELDTYPE.TIMESTAMP;
		} else if ( this.annotations.containsKey( Key.id ) ) {
			this.fieldType = FIELDTYPE.ID;
		} else if ( this.annotations.containsKey( Key.collection ) ) {
			this.fieldType = FIELDTYPE.COLLECTION;
		} else if ( this.annotations.containsKey( ORMKeys.OneToOne ) ) {
			if ( ( getAssociation().containsKey( ORMKeys.fkcolumn ) || getAssociation().containsKey( ORMKeys.linkTable ) ) ) {
				// behaves as many-to-one
				this.fieldType = FIELDTYPE.MANY_TO_ONE;
				this.getAssociation().put( Key.type, "many-to-one" );
				this.getAssociation().put( ORMKeys.unique, true );
			} else {
				this.fieldType = FIELDTYPE.ONE_TO_ONE;
			}
		} else if ( this.annotations.containsKey( ORMKeys.OneToMany ) ) {
			if ( getAssociation().containsKey( ORMKeys.linkTable ) ) {
				// behaves as many-to-many
				this.fieldType = FIELDTYPE.MANY_TO_MANY;
				this.getAssociation().put( Key.type, "many-to-many" );
				this.getAssociation().put( ORMKeys.unique, true );
			} else {
				this.fieldType = FIELDTYPE.ONE_TO_MANY;
			}
		} else if ( this.annotations.containsKey( ORMKeys.ManyToOne ) ) {
			this.fieldType = FIELDTYPE.MANY_TO_ONE;
		} else if ( this.annotations.containsKey( ORMKeys.ManyToMany ) ) {
			this.fieldType = FIELDTYPE.MANY_TO_MANY;
		} else {
			this.fieldType = FIELDTYPE.COLUMN;
		}
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
		IStruct association;
		if ( annotations.containsKey( ORMKeys.OneToOne ) ) {
			association = annotations.getAsStruct( ORMKeys.OneToOne );
			association.put( Key.type, "one-to-one" );
		} else if ( annotations.containsKey( ORMKeys.OneToMany ) ) {
			association = annotations.getAsStruct( ORMKeys.OneToMany );
			association.put( Key.type, "one-to-many" );
		} else if ( annotations.containsKey( ORMKeys.ManyToOne ) ) {
			association = annotations.getAsStruct( ORMKeys.ManyToOne );
			association.put( Key.type, "many-to-one" );
		} else if ( annotations.containsKey( ORMKeys.ManyToMany ) ) {
			association = annotations.getAsStruct( ORMKeys.ManyToMany );
			association.put( Key.type, "many-to-many" );
		} else {
			return Struct.EMPTY;
		}

		association.putIfAbsent( Key._NAME, this.name );
		association.computeIfPresent( ORMKeys.inverseJoinColumn, ( key, value ) -> translateColumnName( ( String ) value ) );
		association.computeIfPresent( ORMKeys.fkcolumn, ( key, value ) -> translateColumnName( ( String ) value ) );
		if ( association.containsKey( ORMKeys.fkcolumn ) ) {
			association.put( Key.column, association.getAsString( ORMKeys.fkcolumn ) );
		}
		return association;
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

		if ( column.containsKey( ORMKeys.formula ) ) {
			this.formula = column.getAsString( ORMKeys.formula );
		}
		// Column should ALWAYS have a name.
		column.putIfAbsent( Key._NAME, translateColumnName( this.name ) );
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
