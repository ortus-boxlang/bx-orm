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
package ortus.boxlang.modules.orm.interceptors;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.statics.StaticEntityMethods;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Keeps the static entity methods ({@link StaticEntityMethods}) on entity classes BoxLang recompiles while the application
 * runs. A recompiled class starts with an empty static scope, so the methods added at ORM startup are gone from it.
 * <ul>
 * <li>{@code afterBoxClassCreation} (BoxLang 1.18+): an instance of a recompiled entity class puts the methods back on its
 * class.</li>
 * <li>{@code onRequestStart}: when the runtime's trusted cache is off (development), every entity class is checked once per
 * request, so a static call that is the first use of an edited entity still works. With the trusted cache on, classes never
 * recompile and this check is skipped.</li>
 * </ul>
 *
 * @since 2.0.0
 */
public class StaticMethodsListener extends BaseInterceptor {

	private ORMService ormService;

	/**
	 * Configure the interceptor.
	 *
	 * @param properties The interceptor properties (unused).
	 */
	@Override
	public void configure( IStruct properties ) {
		this.properties	= properties;
		this.logger		= getRuntime().getLoggingService().getLogger( "orm" );
	}

	/**
	 * Put the static methods back on the class of a new entity instance when its class was recompiled since ORM startup.
	 *
	 * @param data The event data: {@code instance}, {@code className}, {@code noInit}, {@code context}.
	 */
	@InterceptionPoint
	public void afterBoxClassCreation( IStruct data ) {
		if ( ! ( data.get( Key.instance ) instanceof IClassRunnable instance ) ) {
			return;
		}
		if ( StaticEntityMethods.isKnown( instance.getClass() ) ) {
			return;
		}
		ORMApp app = staticMethodsApp( data.get( Key.context ) instanceof IBoxContext c ? c : RequestBoxContext.getCurrent() );
		if ( app != null ) {
			StaticEntityMethods.ensureInstalled( instance, app );
		}
	}

	/**
	 * In development (trusted cache off), put the static methods back on any entity class recompiled since the last request.
	 *
	 * @param data The event data, which carries the request {@code context}.
	 */
	@InterceptionPoint
	public void onRequestStart( IStruct data ) {
		if ( Boolean.TRUE.equals( getRuntime().getConfiguration().trustedCache ) ) {
			return;
		}
		IBoxContext	context	= data.get( Key.context ) instanceof IBoxContext c ? c : RequestBoxContext.getCurrent();
		ORMApp		app		= staticMethodsApp( context );
		if ( app != null ) {
			StaticEntityMethods.refresh( context, app );
		}
	}

	/**
	 * The running ORM application of a context, when it has static methods turned on.
	 *
	 * @param context The current context (may be null).
	 *
	 * @return The ORM application, or null when there is none or static methods are off.
	 */
	private ORMApp staticMethodsApp( IBoxContext context ) {
		if ( context == null ) {
			return null;
		}
		if ( this.ormService == null ) {
			this.ormService = ( ORMService ) getRuntime().getGlobalService( ORMKeys.ORMService );
			if ( this.ormService == null ) {
				return null;
			}
		}
		try {
			ORMApp app = this.ormService.getORMAppByContext( context );
			return app != null && app.getConfig().staticMethods ? app : null;
		} catch ( RuntimeException e ) {
			// Not inside an application, or the ORM is not enabled for it.
			return null;
		}
	}
}
