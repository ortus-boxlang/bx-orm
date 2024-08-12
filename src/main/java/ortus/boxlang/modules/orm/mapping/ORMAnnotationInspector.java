package ortus.boxlang.modules.orm.mapping;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

public class ORMAnnotationInspector {

	private IStruct	meta;
	private IStruct	annotations;
	private Array	properties;

	public static boolean isIDProperty( IStruct prop ) {
		return prop.getAsStruct( Key.annotations ).containsKey( Key.id );
	}

	public static boolean isMappableProperty( IStruct prop ) {
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
		    .filter( ( prop ) -> ( ( IStruct ) prop ).getAsStruct( Key.annotations ).containsKey( Key.id ) )
		    .collect( Array::new, Array::add, Array::addAll );
	}

	/**
	 * @TODO: Convert this to support muiltiple ID properties.
	 *        And... shouldn't we also be looking at the `fieldtype` annotation?
	 * 
	 * @return
	 */
	public String getIdPropertyName() {
		IStruct firstIDProperty = ( IStruct ) this.properties
		    .stream()
		    .filter( ( prop ) -> ( ( IStruct ) prop ).getAsStruct( Key.annotations ).containsKey( Key.id ) )
		    .findFirst()
		    .get();
		return firstIDProperty
		    .getAsStruct( Key.annotations )
		    .get( Key.of( "name" ) )
		    .toString();
	}

	// public String getIDPropertyType() {
	// // TODO should probably refactor getIdPropertyName : String -> getIdProperty() : Property
	// // that way we can reuse the generic properties for id as well
	// return "integer";
	// }

	// public String getIDPropertyGenerator() {
	// // TODO see @getIDPropertyType() todo
	// return "increment";
	// }

	public String getEntityName() {
		return this.annotations.getAsString( ORMKeys.entity );
	}

	public String getTableName() {
		return this.annotations.getAsString( ORMKeys.table );
	}

}
