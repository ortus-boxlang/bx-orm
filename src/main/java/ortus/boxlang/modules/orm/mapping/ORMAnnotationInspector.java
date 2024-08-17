package ortus.boxlang.modules.orm.mapping;

import java.util.List;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

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
		return !entityAnnotations.containsKey( ORMKeys.persistent )
		    // JPA style, i.e. `@Entity`
		    || entityAnnotations.containsKey( ORMKeys.entity )
			// verbose CFML syntax, i.e. `persistent="true"`
		    || BooleanCaster.cast( entityAnnotations.getOrDefault( ORMKeys.persistent, true ), false );
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
		return !propAnnotations.containsKey( ORMKeys.persistent )
		    // JPA style, i.e. `@Column`
		    || propAnnotations.containsKey( Key.column )
			// verbose CFML syntax, i.e. `persistent="true"`
		    || propAnnotations.getAsBoolean( ORMKeys.persistent );
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
		this.annotations.computeIfAbsent( ORMKeys.entity, ( key ) -> this.meta.getAsString( Key._name ) );
		// @TODO: Pass in the ORMConfig object to get defaults from
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
		String entityName = this.annotations.getAsString( ORMKeys.entity );
		if ( entityName == null || entityName.isEmpty() ) {
			entityName = this.meta.getAsString( Key._name );
		}
		return entityName;
	}

	public String getTableName() {
		// @TODO: Use the naming strategy to generate or massage the table name
		String tableName = this.annotations.getAsString( ORMKeys.table );
		if ( tableName == null || tableName.isEmpty() ) {
			tableName = this.meta.getAsString( Key._name );
		}
		return tableName;
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
		    && this.annotations.getAsBoolean( ORMKeys.readOnly )
		    || ( this.annotations.containsKey( ORMKeys.immutable )
		        && this.annotations.getAsBoolean( ORMKeys.immutable ) );
	}

	public boolean isDynamicInsert() {
		return this.annotations.containsKey( ORMKeys.dynamicInsert )
		    && this.annotations.getAsBoolean( ORMKeys.dynamicInsert );
	}

	public boolean isDynamicUpdate() {
		return this.annotations.containsKey( ORMKeys.dynamicUpdate )
		    && this.annotations.getAsBoolean( ORMKeys.dynamicUpdate );
	}

	public boolean isSelectBeforeUpdate() {
		return this.annotations.containsKey( ORMKeys.selectBeforeUpdate )
		    && this.annotations.getAsBoolean( ORMKeys.selectBeforeUpdate );
	}

	public boolean needsTableCatalogSchema() {
		return !this.isExtended() || ( !this.hasEntityAnnotation( ORMKeys.discriminatorValue ) && !this.hasEntityAnnotation( ORMKeys.joinColumn ) );
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
