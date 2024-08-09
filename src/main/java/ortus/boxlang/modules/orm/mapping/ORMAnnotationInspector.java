package ortus.boxlang.modules.orm.mapping;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

public class ORMAnnotationInspector {

	private IStruct	meta;
	private IStruct	annotations;
	private Array	properties;

	// public static boolean isIDProperty( Property prop ) {
	// return prop.annotations().containsKey( Key.id );
	// }

	// public static boolean isMappableProperty( Property prop ) {
	// return !prop.annotations().containsKey( ORMKeys.persistent )
	// || prop.annotations().getAsBoolean( ORMKeys.persistent );
	// }

	public ORMAnnotationInspector( IStruct entityMeta ) {
		this.meta			= entityMeta;
		this.annotations	= entityMeta.getAsStruct( Key.annotations );
		this.properties		= entityMeta.getAsArray( Key.properties );

		// set sane defaults
		this.annotations.computeIfAbsent( ORMKeys.entity, ( key ) -> this.meta.getAsString( Key._name ) );
		this.annotations.computeIfAbsent( Key.table, ( key ) -> this.meta.getAsString( Key.table ) );
	}

	// public Collection<Property> getProperties() {
	// return this.bxInstance.getProperties().values();
	// }

	// public String getPropertyName( Property prop ) {
	// return prop.name().toString();
	// }

	// public String getPropertyType( Property prop ) {
	// return prop.annotations().containsKey( ORMKeys.ORMType )
	// ? prop.annotations().getAsString( ORMKeys.ORMType )
	// : "string";
	// }

	// public String getPropertyColumn( Property prop ) {
	// return prop.annotations().containsKey( Key.column )
	// ? prop.annotations().getAsString( Key.column )
	// : null;
	// }

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

	// public String getTable() {
	// String tableAnnotationValue = bxInstance.getAnnotations().containsKey( ORMKeys.table ) ? bxInstance.getAnnotations().getAsString( ORMKeys.table ) :
	// "";

	// if ( tableAnnotationValue.length() > 0 ) {
	// return tableAnnotationValue;
	// }

	// return extractName();
	// }

	// private String extractName() {
	// return StringUtils.substringAfterLast( bxInstance.getName().toString(), "." );
	// }

}
