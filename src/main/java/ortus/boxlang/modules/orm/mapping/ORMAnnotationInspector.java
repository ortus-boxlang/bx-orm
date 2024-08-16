package ortus.boxlang.modules.orm.mapping;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

public class ORMAnnotationInspector {

	private IStruct	meta;
	private IStruct	annotations;
	private Array	properties;

	public static boolean isIDProperty( IStruct prop ) {
		var propAnnotations = prop.getAsStruct( Key.annotations );
		// JPA syntax, i.e. `@Id`
		return propAnnotations.containsKey( Key.id )
		    // CFML syntax, i.e. `fieldtype="id"`
		    || propAnnotations.containsKey( ORMKeys.fieldtype ) && propAnnotations.getAsString( ORMKeys.fieldtype ).equals( "id" );
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
		    // JPA syntax, i.e. `@Entity`
		    || entityAnnotations.containsKey( ORMKeys.entity )
			// explicit CFML syntax, i.e. `persistent="true"`
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
		return !propAnnotations.containsKey( ORMKeys.persistent )
		    || propAnnotations.getAsBoolean( ORMKeys.persistent );
	}

	public ORMAnnotationInspector( IStruct entityMeta ) {
		this.meta			= entityMeta;
		this.annotations	= entityMeta.getAsStruct( Key.annotations );
		this.properties		= entityMeta.getAsArray( Key.properties );

		// set sane defaults
		this.annotations.computeIfAbsent( ORMKeys.entity, ( key ) -> this.meta.getAsString( Key._name ) );
		this.annotations.computeIfAbsent( Key.table, ( key ) -> this.meta.getAsString( Key._name ) );
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
		var propAnnotations = prop.getAsStruct( Key.annotations );
		return propAnnotations.containsKey( Key.column )
		    ? propAnnotations.getAsString( Key.column )
		    : null;
	}

	public Array getPrimaryKeyProperties() {
		return this.properties
		    .stream()
		    .map( IStruct.class::cast )
		    .filter( ORMAnnotationInspector::isIDProperty )
		    .collect( Array::new, Array::add, Array::addAll );
	}

	public String getEntityName() {
		String entityName = this.annotations.getAsString( ORMKeys.entity );
		if ( entityName == null || entityName.isEmpty() ) {
			entityName = this.meta.getAsString( Key._name );
		}
		return entityName;
	}

	public String getTableName() {
		return this.annotations.getAsString( ORMKeys.table );
	}

}
