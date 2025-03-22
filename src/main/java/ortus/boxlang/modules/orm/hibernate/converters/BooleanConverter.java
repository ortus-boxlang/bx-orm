package ortus.boxlang.modules.orm.hibernate.converters;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;

@Converter( autoApply = true )
public class BooleanConverter implements AttributeConverter<String, Boolean> {

	@Override
	public Boolean convertToDatabaseColumn( String attribute ) {
		return BooleanCaster.cast( attribute );
	}

	@Override
	public String convertToEntityAttribute( Boolean dbData ) {
		return StringCaster.cast( dbData );
	}

}