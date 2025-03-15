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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * This class is used to get a property on a BoxLang class for a Hibernate entity.
 */
public class BoxPropertyGetter implements Getter {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger			logger;

	private Property				mappedProperty;
	private PersistentClass			mappedEntity;
	@SuppressWarnings( "unused" ) // needed for compilation
	private IBoxContext				context;

	public BoxPropertyGetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
		this.context		= context;
	}

	@Override
	public Object get( Object owner ) {
		logger.trace( "getting property {} on entity {}", mappedProperty.getName(), mappedEntity.getEntityName() );
		// @TODO: I think we should call the getter method on the BoxLang class, and not just return the property from the scope?
		return ( ( IClassRunnable ) owner ).getVariablesScope().get( mappedProperty.getName() );
	}

	@Override
	public Object getForInsert( Object owner, Map mergeMap, SharedSessionContractImplementor session ) {
		return get( owner );
	}

	@Override
	public Class getReturnType() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getReturnType'" );
	}

	@Override
	public Member getMember() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMember'" );
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
