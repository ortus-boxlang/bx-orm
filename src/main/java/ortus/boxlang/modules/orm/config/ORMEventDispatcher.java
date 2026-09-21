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
package ortus.boxlang.modules.orm.config;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Dispatches BoxLang ORM events to the global event-handler class and to an entity's own event method.
 * <p>
 * This is the single dispatch implementation shared by the Hibernate-driven events ({@link EventListener}) and the
 * {@code entityNew()}-driven {@code postNew} event, so the two channels resolve the global handler and invoke methods
 * identically. An event fires on a target only when that target (the global handler class, or the entity class) actually
 * declares a method of the event's name; the {@code eventType} {@link Key}'s name IS the method name to invoke (for
 * example {@code postLoad}, {@code postNew}).
 *
 * @since 1.5.0
 */
public class ORMEventDispatcher {

	private static final BoxRuntime	runtime			= BoxRuntime.getInstance();

	private final BoxLangLogger		logger;

	/**
	 * The global event-handler class, or {@code null} when no {@code eventHandler} is configured.
	 */
	private final DynamicObject		globalListener;

	/**
	 * Flag so that we lazily instantiate the global listener on first use, inside a request context.
	 */
	private boolean					listenerReady	= false;

	/**
	 * Constructor.
	 *
	 * @param globalListener The global event-handler class to fire on each global event, or {@code null} if none.
	 */
	public ORMEventDispatcher( DynamicObject globalListener ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.globalListener	= globalListener;
	}

	/**
	 * The global event-handler class, or {@code null} when no {@code eventHandler} is configured.
	 */
	public DynamicObject getGlobalListener() {
		return this.globalListener;
	}

	/**
	 * Fire the given event on the global event-handler class, if one is configured and declares a method of that name.
	 *
	 * @param eventType The event name; its name is the handler method to invoke (e.g. {@code postLoad}).
	 * @param args      The named arguments to pass to the handler method.
	 */
	public void announceGlobal( Key eventType, IStruct args ) {
		if ( globalListener == null ) {
			return;
		}
		if ( !listenerReady ) {
			RequestBoxContext.runInContext( ( ctx ) -> globalListener.invokeConstructor( ctx ) );
			listenerReady = true;
		}

		boolean hasMethod = false;

		if ( IClassRunnable.class.isAssignableFrom( globalListener.getTargetClass() ) ) {
			hasMethod = ( ( IClassRunnable ) globalListener.unWrapBoxLangClass() ).getThisScope().containsKey( eventType );
		} else {
			hasMethod = globalListener.hasMethodNoCase( eventType.getNameNoCase() );
		}

		if ( hasMethod ) {
			if ( logger.isTraceEnabled() ) {
				logger.trace( "Ready to invoke {} on global EventHandler with args {}", eventType.getName(), args.toString() );
			}
			// Fire the method on the global event handler
			RequestBoxContext.runInContext( ( ctx ) -> this.globalListener.dereferenceAndInvoke( ctx, eventType, args, false ) );
		}
	}

	/**
	 * Fire the given event on the entity itself, if the entity declares a method of that name.
	 *
	 * @param entity    The entity instance.
	 * @param eventType The event name; its name is the entity method to invoke (e.g. {@code postNew}).
	 * @param args      The named arguments to pass to the entity method.
	 */
	public static void announceEntity( IClassRunnable entity, Key eventType, IStruct args ) {
		if ( entity.containsKey( eventType ) ) {
			// Fire the method on the entity itself
			RequestBoxContext.runInContext( ( ctx ) -> entity.dereferenceAndInvoke( ctx, eventType, args, false ) );
		}
	}

}
