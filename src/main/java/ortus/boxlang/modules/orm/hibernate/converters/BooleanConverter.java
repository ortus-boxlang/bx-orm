package ortus.boxlang.modules.orm.hibernate.converters;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;

@Converter( autoApply = true )
public class BooleanConverter implements AttributeConverter<Object, Boolean> {

	@Override
	public Boolean convertToDatabaseColumn( Object attribute ) {
		return BooleanCaster.cast( attribute );
	}

	@Override
	public Object convertToEntityAttribute( Boolean dbData ) {
		return dbData != null ? BooleanCaster.cast( dbData ) : null;
	}

}