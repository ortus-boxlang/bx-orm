package ortus.boxlang.modules.orm.hibernate.converters;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.ShortCaster;

@Converter( autoApply = true )
public class ShortConverter implements AttributeConverter<Object, Short> {

	@Override
	public Short convertToDatabaseColumn( Object attribute ) {
		return attribute != null ? ShortCaster.cast( attribute ) : null;
	}

	@Override
	public Object convertToEntityAttribute( Short dbData ) {
		return dbData;
	}

}