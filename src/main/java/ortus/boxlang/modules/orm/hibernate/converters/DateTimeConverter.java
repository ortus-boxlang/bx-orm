/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.hibernate.converters;

import java.util.Date;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import ortus.boxlang.runtime.dynamic.casters.DateTimeCaster;

@Converter( autoApply = true )
public class DateTimeConverter implements AttributeConverter<Object, Date> {

	@Override
	public Date convertToDatabaseColumn( Object attribute ) {
		return DateTimeCaster.cast( attribute ).toDate();
	}

	@Override
	public Object convertToEntityAttribute( Date dbData ) {
		return dbData != null ? DateTimeCaster.cast( dbData ) : dbData;
	}

}