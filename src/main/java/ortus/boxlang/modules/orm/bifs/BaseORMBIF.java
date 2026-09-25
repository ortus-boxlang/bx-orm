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
package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Abstract, parent BIF utility class which all ORM bifs should extend for reuse.
 */
public abstract class BaseORMBIF extends BIF {

	/**
	 * ORM service
	 */
	protected ORMService ormService = ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );

	/**
	 * Constructor
	 */
	protected BaseORMBIF() {
		super();
		// Do we need a logger in the BIFs?
		// this.logger = runtime.getLoggingService().getLogger( "orm" );
	}

	/**
	 * Pull the entity name from the provided boxlang class
	 *
	 * @param entity Instance of IClassRunnable, aka the compiled/parsed entity.
	 */
	/**
	 * Run the BIF and translate any Hibernate / JPA / JDBC failure into a clear {@link ORMException} (type {@code orm.*},
	 * BoxLang entity names, a fix in the detail, context in extendedInfo). Non-ORM errors, such as an exception thrown by
	 * the developer's own event handler, pass through unchanged.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The BIF's result.
	 */
	@Override
	public Object invoke( IBoxContext context, ArgumentsScope arguments ) {
		try {
			return super.invoke( context, arguments );
		} catch ( ORMException e ) {
			throw e;
		} catch ( RuntimeException e ) {
			throw ORMErrors.translate( e, errorContext( context, arguments ) );
		}
	}

	/**
	 * The error context for this call: the BIF name, the application's entity and property names (for suggestions), and
	 * the HQL, parameters and entity name when the BIF has them.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The error context; never null.
	 */
	protected ORMErrors.Context errorContext( IBoxContext context, ArgumentsScope arguments ) {
		String	operation	= bifName( arguments );
		ORMApp	app			= null;
		try {
			app = this.ormService.getORMAppByContext( context );
		} catch ( RuntimeException ignored ) {
			// No application: the context carries no names, which is fine for an error message.
		}
		ORMErrors.Context ctx = app == null ? ORMErrors.Context.of( operation ) : app.errorContext( operation );
		if ( arguments.get( ORMKeys.hql ) instanceof String hql ) {
			ctx = ctx.withQuery( hql, arguments.get( Key.params ) );
		}
		if ( arguments.get( ORMKeys.entityName ) instanceof String entityName ) {
			ctx = ctx.withEntity( entityName );
		}
		return ctx;
	}

	/**
	 * The name the BIF was called by, e.g. {@code entitySave}.
	 *
	 * @param arguments Argument scope for the BIF (carries the invoked name).
	 *
	 * @return The invoked name, or one derived from the class name.
	 */
	protected String bifName( ArgumentsScope arguments ) {
		Object name = arguments.get( BIF.__functionName );
		if ( name != null ) {
			return name instanceof Key k ? k.getName() : name.toString();
		}
		String simple = getClass().getSimpleName();
		return simple.startsWith( "ORM" ) ? "orm" + simple.substring( 3 ) : Character.toLowerCase( simple.charAt( 0 ) ) + simple.substring( 1 );
	}

	/**
	 * The argument as an ORM entity instance, or a clear {@code orm.argument} error naming what was passed instead.
	 *
	 * @param value    The argument value.
	 * @param argument The argument name, for the message.
	 * @param bif      The BIF name, for the message.
	 *
	 * @return The value as an entity instance.
	 *
	 * @throws ORMException When the value is not an entity instance.
	 */
	protected static IClassRunnable requireEntity( Object value, String argument, String bif ) {
		if ( value instanceof IClassRunnable runnable ) {
			return runnable;
		}
		String got = value == null ? "null" : ( value instanceof String s ? "the string [" + s + "]" : describe( value ) );
		throw new ORMException( ORMErrorType.ARGUMENT,
		    String.format( "%s() expects an ORM entity instance for [%s], but received %s.", bif, argument, got ),
		    "Pass an entity created with entityNew() or loaded with entityLoad()." );
	}

	/**
	 * A short description of a non-entity value's type for error messages, e.g. "a struct".
	 *
	 * @param value The value (not null).
	 *
	 * @return The description.
	 */
	private static String describe( Object value ) {
		if ( value instanceof ortus.boxlang.runtime.types.IStruct ) {
			return "a struct";
		}
		if ( value instanceof ortus.boxlang.runtime.types.Array ) {
			return "an array";
		}
		return "a " + value.getClass().getSimpleName();
	}

	/**
	 * Pull the entity name from a BoxLang class.
	 *
	 * @param entity The entity instance.
	 *
	 * @return The BoxLang entity name.
	 */
	protected String getEntityName( IClassRunnable entity ) {
		return ORMService.getEntityName( entity );
	}

	/**
	 * Retrieve the simple class name from a fully-qualified name.
	 *
	 * @param fqn The fully-qualified class name.
	 *
	 * @return The simple class name.
	 */
	protected String getClassNameFromFQN( String fqn ) {
		return ORMService.getClassNameFromFQN( fqn );
	}
}
