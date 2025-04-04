package ortus.boxlang.modules.orm.hibernate.converters;

import java.math.BigDecimal;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.BigDecimalCaster;

@Converter( autoApply = true )
public class BigDecimalConverter implements AttributeConverter<Object, BigDecimal> {

	@Override
	public BigDecimal convertToDatabaseColumn( Object attribute ) {
		return attribute != null ? BigDecimalCaster.cast( attribute ) : null;
	}

	@Override
	public Object convertToEntityAttribute( BigDecimal dbData ) {
		return dbData;
	}

}