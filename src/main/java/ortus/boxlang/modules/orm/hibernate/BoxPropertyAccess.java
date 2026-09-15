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
package ortus.boxlang.modules.orm.hibernate;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.property.access.spi.Setter;

/**
 * Hibernate {@link PropertyAccess} for a single mapped property on a BoxLang entity.
 * <p>
 * Pairs a {@link BoxPropertyGetter} and {@link BoxPropertySetter} so that Hibernate reads and writes
 * entity state through the BoxLang variables/this scopes instead of Java reflection.
 *
 * @since 2.0.0
 */
public class BoxPropertyAccess implements PropertyAccess {

	/**
	 * Marker strategy. Hibernate only uses this for identity/equality of access strategies, so a single
	 * shared instance is sufficient.
	 */
	public static final PropertyAccessStrategy	STRATEGY	= ( containerJavaType, propertyName, setterRequired ) -> {
																throw new UnsupportedOperationException(
																    "BoxPropertyAccess must be built from a boot Property, not a property name" );
															};

	private final Getter						getter;
	private final Setter						setter;

	public BoxPropertyAccess( Property mappedProperty, PersistentClass mappedEntity ) {
		this.getter	= new BoxPropertyGetter( mappedProperty, mappedEntity );
		this.setter	= new BoxPropertySetter( mappedProperty, mappedEntity );
	}

	@Override
	public PropertyAccessStrategy getPropertyAccessStrategy() {
		return STRATEGY;
	}

	@Override
	public Getter getGetter() {
		return getter;
	}

	@Override
	public Setter getSetter() {
		return setter;
	}

}
