package ortus.boxlang.modules.orm.mapping;

import java.util.List;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class ORMAnnotationInspector {

	private IStruct	meta;
	private IStruct	annotations;
	private Array	properties;
	private Array	idProperties;

	public static boolean isIDFieldType( IStruct prop ) {
		var propAnnotations = prop.getAsStruct( Key.annotations );
		// JPA style, i.e. `@Id`
		return propAnnotations.containsKey( Key.id )
		    // CFML syntax, i.e. `fieldtype="id"`
		    || ( propAnnotations.containsKey( ORMKeys.fieldtype ) && propAnnotations.getAsString( ORMKeys.fieldtype ).equals( "id" ) );
	}

	public static boolean isColumnFieldType( IStruct prop ) {
		var propAnnotations = prop.getAsStruct( Key.annotations );
		if ( propAnnotations.containsKey( Key.column ) ) {
			// JPA style, i.e. `@Column`
			return true;
		} else if ( propAnnotations.containsKey( ORMKeys.fieldtype ) ) {
			// CFML verbose, i.e. `fieldtype="column"`
			return propAnnotations.getAsString( ORMKeys.fieldtype ).equals( "column" );
		} else {
			// default to column if it's not an id fieldtype
			return !propAnnotations.containsKey( Key.id );
		}
	}

	/**
	 * Check if the given metadata represents a persistent entity
	 * 
	 * @param entityMeta metadata of a given boxlang class
	 * 
	 * @return true if the entity is a persistent entity
	 */
	public static boolean isPersistentEntity( IStruct entityMeta ) {
		var entityAnnotations = entityMeta.getAsStruct( Key.annotations );
		entityAnnotations.computeIfAbsent( ORMKeys.persistent, ( key ) -> true );

		// if it's in a CFC location, it's persistent by default
		if ( !entityAnnotations.containsKey( ORMKeys.persistent )
		    // JPA style, i.e. `@Entity`
		    || entityAnnotations.containsKey( ORMKeys.entity ) ) {
			return true;
		}
		// verbose CFML syntax, i.e. `persistent="true"`
		Object isPersistent = BooleanCaster.cast( entityAnnotations.getOrDefault( ORMKeys.persistent, true ), false );
		return !Boolean.FALSE.equals( isPersistent );
	}

	/**
	 * Check if the given metadata represents a persistent property
	 * 
	 * @param entityMeta metadata of a given boxlang class property
	 * 
	 * @return true if the property is persistent property
	 */
	public static boolean isPersistentProperty( IStruct prop ) {
		var propAnnotations = prop.getAsStruct( Key.annotations );
		// ommitted persistent defaults to true
		if ( !propAnnotations.containsKey( ORMKeys.persistent )
		    // JPA style, i.e. `@Column`
		    || propAnnotations.containsKey( Key.column ) ) {
			return true;
		}
		// verbose CFML syntax, i.e. `persistent="true"`
		Object isPersistent = BooleanCaster.cast( propAnnotations.getOrDefault( ORMKeys.persistent, true ), false );
		return !Boolean.FALSE.equals( isPersistent );
	}

	public ORMAnnotationInspector( IStruct entityMeta ) {
		this.meta			= entityMeta;
		this.annotations	= entityMeta.getAsStruct( Key.annotations );

		this.properties		= entityMeta.getAsArray( Key.properties ).stream()
		    .map( IStruct.class::cast )
		    .filter( ORMAnnotationInspector::isPersistentProperty )
		    .filter( ORMAnnotationInspector::isColumnFieldType )
		    .collect( Array::new, Array::add, Array::addAll );

		this.idProperties	= entityMeta.getAsArray( Key.properties ).stream()
		    .map( IStruct.class::cast )
		    .filter( ORMAnnotationInspector::isIDFieldType )
		    .collect( Array::new, Array::add, Array::addAll );

		// set sane defaults
		this.annotations.computeIfAbsent( ORMKeys.entityName, key -> this.meta.getAsString( Key._name ) );
		this.annotations.computeIfAbsent( ORMKeys.table, key -> this.getEntityName() );
		// @TODO: Default these values using ORMConfig.catalog and ORMConfig.schema
		// this.annotations.computeIfAbsent( Key.table, ( key ) -> config.catalog );
		// this.annotations.computeIfAbsent( ORMKeys.schema, ( key ) -> config.schema );
	}

	public Array getProperties() {
		return this.properties;
	}

	public String getPropertyName( IStruct prop ) {
		return prop.getAsString( Key._name );
	}

	public boolean hasPropertyAnnotation( IStruct prop, Key annotation ) {
		return prop.getAsStruct( Key.annotations ).containsKey( annotation );
	}

	public String getPropertyAnnotation( IStruct prop, Key annotation ) {
		var propAnnotations = prop.getAsStruct( Key.annotations );
		return propAnnotations.containsKey( annotation )
		    ? propAnnotations.getAsString( annotation )
		    : null;
	}

	public String getPropertyType( IStruct prop ) {
		var propAnnotations = prop.getAsStruct( Key.annotations );
		return propAnnotations.containsKey( ORMKeys.ORMType )
		    ? propAnnotations.getAsString( ORMKeys.ORMType )
		    : "string";
	}

	public String getPropertyColumn( IStruct prop ) {
		// @TODO: Use the naming strategy to generate or massage the column name
		var propAnnotations = prop.getAsStruct( Key.annotations );
		return propAnnotations.containsKey( Key.column )
		    ? propAnnotations.getAsString( Key.column )
		    : propAnnotations.getAsString( Key._name );
	}

	public Array getIdProperties() {
		return this.idProperties;
	}

	public String getEntityName() {
		String entityName = this.annotations.getAsString( ORMKeys.entityName );
		if ( entityName == null || entityName.isEmpty() ) {
			entityName = this.annotations.getAsString( ORMKeys.entity );
			if ( entityName == null || entityName.isEmpty() ) {
				entityName = this.meta.getAsString( Key._name );
			}
		}
		return entityName;
	}

	public String getTableName() {
		// @TODO: Use the naming strategy to generate or massage the table name
		return this.annotations.getAsString( ORMKeys.table );
	}

	public String getSchema() {
		return this.annotations.getAsString( ORMKeys.schema );
	}

	public String getCatalog() {
		return this.annotations.getAsString( ORMKeys.catalog );
	}

	public boolean hasEntityAnnotation( Key annotation ) {
		return this.annotations.containsKey( annotation );
	}

	public String getEntityAnnotation( Key annotation ) {
		return this.annotations.containsKey( annotation )
		    ? this.annotations.getAsString( annotation )
		    : null;
	}

	public boolean isReadOnly() {
		return this.annotations.containsKey( ORMKeys.readOnly )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.readOnly, "false" ), false )
		    || this.annotations.containsKey( ORMKeys.immutable );
	}

	public boolean isDynamicInsert() {
		return this.annotations.containsKey( ORMKeys.dynamicInsert )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicInsert, false ) );
	}

	public boolean isDynamicUpdate() {
		return this.annotations.containsKey( ORMKeys.dynamicUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.dynamicUpdate, false ) );
	}

	public boolean isSelectBeforeUpdate() {
		return this.annotations.containsKey( ORMKeys.selectBeforeUpdate )
		    && BooleanCaster.cast( this.annotations.getOrDefault( ORMKeys.selectBeforeUpdate, false ) );
	}

	/**
	 * Get the discriminator info for this entity.
	 * 
	 * @return A struct of discriminator info - EMPTY if none defined, else a struct with the following keys (any of which may be null):
	 *         <ul>
	 *         <li>{@link Key#value} - The value of the discriminator column</li>
	 *         <li>{@link Key#_name} - The name of the discriminator column</li>
	 *         <li>{@link Key#type} - The type of the discriminator column</li>
	 *         <li>{@link Key#force} - Whether to force the discriminator column to be created</li>
	 *         <li>{@link ORMKeys#insert} - Whether to insert the discriminator column</li>
	 *         <li>{@link ORMKeys#formula} - A formula to use for the discriminator column</li>
	 *         </ul>
	 */
	public IStruct getDiscriminatorData() {
		if ( this.annotations.containsKey( ORMKeys.discriminator ) ) {
			return this.annotations.getAsStruct( ORMKeys.discriminator );
		}
		IStruct newStruct = new Struct();
		// copy the old-school discriminatorColumn and discriminatorValue annotations into the new struct
		newStruct.computeIfAbsent( Key._name, key -> this.annotations.getAsString( ORMKeys.discriminatorColumn ) );
		newStruct.computeIfAbsent( Key.value, key -> this.annotations.getAsString( ORMKeys.discriminatorValue ) );
		// support and cast keys from the @DiscriminatorColumn {} struct
		newStruct.computeIfPresent( Key.force, ( key, object ) -> BooleanCaster.cast( object, false ) );
		newStruct.computeIfPresent( ORMKeys.insert, ( key, object ) -> BooleanCaster.cast( object, false ) );
		// If 'formula' is set, just pass it through - no casting needed.
		return newStruct;
	}

	/**
	 * Determining whether thisEntity is "direct" or "derived". Aka, is the table name explicitly defined in the entity
	 * metadata, or is it derived from a parent class plus discriminator metadata.
	 * 
	 * @return true if the entity is simple and NOT derived from a parent class, a join, or discriminator metadata; else false.
	 */
	public boolean isSimpleEntity() {
		return !this.isExtended() || ( !this.hasEntityAnnotation( ORMKeys.discriminatorColumn ) && !this.hasEntityAnnotation( ORMKeys.discriminatorValue )
		    && !this.hasEntityAnnotation( ORMKeys.joinColumn ) );
	}

	public boolean isExtended() {
		return this.meta.containsKey( Key._EXTENDS )
		    && !this.meta.getAsStruct( Key._EXTENDS ).isEmpty();
	}

	public String getPropertySqlType( IStruct prop ) {
		var				sqlType			= this.getPropertyAnnotation( prop, Key.sqltype );
		List<String>	varcharTypes	= List.of( "varchar", "nvarchar" );
		if ( varcharTypes.contains( sqlType ) && this.hasPropertyAnnotation( prop, Key.length ) ) {
			sqlType += "(" + this.getPropertyAnnotation( prop, ORMKeys.length ) + ")";
		}
		return sqlType;
	}

	public boolean isPropertyUnique( IStruct prop ) {
		return BooleanCaster.cast(
		    prop.getAsStruct( Key.annotations ).getOrDefault( ORMKeys.unique, false ),
		    false
		);
	}

	public boolean isPropertyInsertable( IStruct prop ) {
		return BooleanCaster.cast(
		    prop.getAsStruct( Key.annotations ).getOrDefault( ORMKeys.insert, true ),
		    true
		);
	}

	public boolean isPropertyUpdatable( IStruct prop ) {
		return BooleanCaster.cast(
		    prop.getAsStruct( Key.annotations ).getOrDefault( ORMKeys.update, true ),
		    false
		);
	}

	public boolean isPropertyNotNull( IStruct prop ) {
		return BooleanCaster.cast(
		    prop.getAsStruct( Key.annotations ).getOrDefault( ORMKeys.notNull, false ),
		    false
		);
	}

	public boolean isPropertyLazy( IStruct prop ) {
		return BooleanCaster.cast(
		    prop.getAsStruct( Key.annotations ).getOrDefault( ORMKeys.lazy, false ),
		    false
		);
	}

	public boolean isPropertyLockable( IStruct prop ) {
		return BooleanCaster.cast(
		    prop.getAsStruct( Key.annotations ).getOrDefault( ORMKeys.optimisticLock, true ),
		    false
		);
	}

	public String getPropertyUniqueKey( IStruct prop ) {
		String uniqueKey = "";
		if ( this.hasPropertyAnnotation( prop, ORMKeys.uniqueKey ) ) {
			uniqueKey = this.getPropertyAnnotation( prop, ORMKeys.uniqueKey );
		} else {
			uniqueKey = this.annotations.getAsString( ORMKeys.uniqueKeyName );
		}
		return uniqueKey == null || uniqueKey.isBlank() ? null : uniqueKey.trim();
	}

}
