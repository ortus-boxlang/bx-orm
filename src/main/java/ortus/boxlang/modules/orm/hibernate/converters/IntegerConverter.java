package ortus.boxlang.modules.orm.hibernate.converters;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;

@Converter( autoApply = true )
public class IntegerConverter implements AttributeConverter<Object, Integer> {

	@Override
	public Integer convertToDatabaseColumn( Object attribute ) {
		return IntegerCaster.cast( attribute );
	}

	@Override
	public Object convertToEntityAttribute( Integer dbData ) {
		return dbData;
	}

}