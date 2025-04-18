package ortus.boxlang.modules.orm.hibernate.converters;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.DoubleCaster;

@Converter( autoApply = true )
public class DoubleConverter implements AttributeConverter<Object, Double> {

	@Override
	public Double convertToDatabaseColumn( Object attribute ) {
		return attribute != null ? DoubleCaster.cast( attribute ) : null;
	}

	@Override
	public Object convertToEntityAttribute( Double dbData ) {
		return dbData;
	}

}