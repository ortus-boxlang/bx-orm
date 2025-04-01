package ortus.boxlang.modules.orm.hibernate.converters;

import java.math.BigInteger;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.BigIntegerCaster;

@Converter( autoApply = true )
public class BigIntegerConverter implements AttributeConverter<Object, BigInteger> {

	@Override
	public BigInteger convertToDatabaseColumn( Object attribute ) {
		return attribute != null ? BigIntegerCaster.cast( attribute ) : null;
	}

	@Override
	public Object convertToEntityAttribute( BigInteger dbData ) {
		return dbData;
	}

}