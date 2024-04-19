package ortus.boxlang.orm.mapping;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

import ortus.boxlang.orm.config.ORMKeys;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Property;

public class ORMAnnotationInspector {

	private IClassRunnable bxInstance;

	public ORMAnnotationInspector( IClassRunnable bxInstance ) {
		this.bxInstance = bxInstance;
	}

	public Collection<Property> getProperties() {
		return this.bxInstance.getProperties().values();
	}

	public String getPropertyName( Property prop ) {
		return prop.name().toString();
	}

	public String getPropertyType( Property prop ) {
		return prop.annotations().containsKey( ORMKeys.ORMType )
		    ? prop.annotations().getAsString( ORMKeys.ORMType )
		    : "string";
	}

	public String getIdPropertyName() {
		return this.bxInstance.getProperties()
		    .values()
		    .stream()
		    .filter( ( prop ) -> prop.annotations().containsKey( Key.id ) )
		    .findFirst()
		    .get().annotations().get( Key.of( "name" ) ).toString();
	}

	public String getIDPropertyType() {
		// TODO should probably refactor getIdPropertyName : String -> getIdProperty() : Property
		// that way we can reuse the generic properties for id as well
		return "integer";
	}

	public String getIDPropertyGenerator() {
		// TODO see @getIDPropertyType() todo
		return "increment";
	}

	public String getEntityName() {
		String entityAnnotationValue = bxInstance.getAnnotations().getAsString( ORMKeys.entity );

		if ( entityAnnotationValue.length() > 0 ) {
			return entityAnnotationValue;
		}

		return extractName();
	}

	public String getTable() {
		String tableAnnotationValue = bxInstance.getAnnotations().containsKey( ORMKeys.table ) ? bxInstance.getAnnotations().getAsString( ORMKeys.table ) : "";

		if ( tableAnnotationValue.length() > 0 ) {
			return tableAnnotationValue;
		}

		return extractName();
	}

	private String extractName() {
		return StringUtils.substringAfterLast( bxInstance.getName().toString(), "." );
	}

}
