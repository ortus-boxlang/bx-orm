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

import java.lang.reflect.Method;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Setter;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;

/**
 * This class is used to set a property on a BoxLang class for a Hibernate entity.
 * <p>
 * In other words, this class takes care of populating the boxlang class properties by calling the appropriate setter method when a Hibernate entity
 * is populated - whether by loading from the database or any other method.
 *
 * @since 1.0.0
 */
public class BoxPropertySetter implements Setter {

	private Property				mappedProperty;
	private PersistentClass			mappedEntity;

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger			logger;

	/**
	 * Constructor for the BoxPropertySetter.
	 *
	 * @param context        The BoxLang context for the application.
	 * @param mappedProperty The Hibernate property mapping that this setter will set values for.
	 * @param mappedEntity   The Hibernate entity mapping that this property belongs to, used for logging purposes to provide context about which entity
	 *                       the property belongs to when logging.
	 */
	public BoxPropertySetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
	}

	@Override
	public void set( Object target, Object value, SessionFactoryImplementor factory ) {
		Key propertyName = Key.of( mappedProperty.getName() );

		if ( logger.isTraceEnabled() ) {
			logger.trace(
			    "BoxPropertySetter - setting property [{}] on entity [{}] to value: {}",
			    propertyName.getName(),
			    mappedEntity.getEntityName(),
			    value
			);
		}

		if ( target instanceof IClassRunnable instance ) {
			instance.getThisScope().put( propertyName, value );
			instance.getVariablesScope().put( propertyName, value );
		}
	}

	@Override
	public String getMethodName() {
		return null;
	}

	@Override
	public Method getMethod() {
		return null;
	}

}
