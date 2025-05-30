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
	private IBoxContext				context;

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger			logger;

	public BoxPropertySetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
		this.context		= context;
	}

	@Override
	public void set( Object target, Object value, SessionFactoryImplementor factory ) {
		logger.trace( "Setting property {} on entity {} to value {}", mappedProperty.getName(), mappedEntity.getEntityName(), value );
		if ( target instanceof IClassRunnable instance ) {
			instance.getThisScope().put( mappedProperty.getName(), value );
			instance.getVariablesScope().put( mappedProperty.getName(), value );
		}
	}

	@Override
	public String getMethodName() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMethodName'" );
	}

	@Override
	public Method getMethod() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMethod'" );
	}

}
