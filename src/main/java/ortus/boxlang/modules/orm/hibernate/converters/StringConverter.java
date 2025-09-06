package ortus.boxlang.modules.orm.hibernate.converters;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.StringCaster;

@Converter( autoApply = true )
public class StringConverter implements AttributeConverter<Object, String> {

	@Override
	public String convertToDatabaseColumn( Object attribute ) {
		return attribute != null ? StringCaster.cast( attribute ) : null;
	}

	@Override
	public Object convertToEntityAttribute( String dbData ) {
		return dbData;
	}

}