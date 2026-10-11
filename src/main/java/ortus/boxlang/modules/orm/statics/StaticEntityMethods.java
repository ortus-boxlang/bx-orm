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
package ortus.boxlang.modules.orm.statics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.bifs.BIFDescriptor;
import ortus.boxlang.runtime.context.FunctionBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.BoxClassSupport;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.StaticScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DynamicFunction;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Static helper methods on entity classes: {@code User::get( 1 )}, {@code User::findWhere( { email : x } )},
 * {@code User::criteria()} and the rest of {@link #METHOD_NAMES}.
 * <p>
 * BoxLang static methods are not inherited, so a base class cannot provide them. Instead, at ORM startup bx-orm puts one
 * function per method straight into each entity class's static scope (a public BoxLang API). Each function calls the matching
 * bx-orm BIF with the entity name filled in, so it behaves exactly like the BIF: same session, events, errors and options.
 * <p>
 * Rules:
 * <ul>
 * <li>A static method the entity declares itself is never replaced.</li>
 * <li>Instance methods win over these on instance calls; an instance with no method of that name reaches the static
 * (BoxLang resolves instance calls to static functions), so {@code user.count()} works too.</li>
 * <li>When BoxLang recompiles an entity class its static scope starts empty. {@link #ensureInstalled} restores the methods when
 * an instance of the new class is created, and {@link #refresh} restores them at request start when the trusted cache is off.</li>
 * <li>{@code ormSettings.staticMethods = false} turns the whole feature off.</li>
 * </ul>
 *
 * @since 2.0.0
 */
public final class StaticEntityMethods {

	/**
	 * The static methods added to each entity class, in documentation order.
	 */
	public static final List<String>			METHOD_NAMES	= List.of(
	    "get", "getOrFail", "getAll", "read", "getReference",
	    "findWhere", "findWhereOrFail", "findAllWhere", "first", "exists", "count", "list",
	    "executeQuery", "criteria", "newCriteria",
	    "new", "firstOrNew", "firstOrCreate", "deleteWhere", "updateWhere" );

	/**
	 * Entity classes that already carry the methods. Keyed by the compiled Java class, which BoxLang replaces on recompile, so a
	 * recompiled class is seen as new. Weak keys let unloaded classes go.
	 */
	private static final Map<Class<?>, Boolean>	INSTALLED		= Collections.synchronizedMap( new WeakHashMap<>() );

	/**
	 * Classes seen at instance creation that are not entities of the creating application, so the next instance skips the
	 * entity lookup. Cleared at every ORM startup, since a reload can turn a class into an entity.
	 */
	private static final Map<Class<?>, Boolean>	NOT_ENTITY		= Collections.synchronizedMap( new WeakHashMap<>() );

	private static final Key					ENTITY_LOAD		= Key.of( "entityLoad" );
	private static final Key					LOAD_BY_PK		= Key.of( "entityLoadByPK" );
	private static final Key					LOAD_BY_PK_FAIL	= Key.of( "entityLoadByPKOrFail" );
	private static final Key					GET_REFERENCE	= Key.of( "entityGetReference" );
	private static final Key					ENTITY_NEW		= Key.of( "entityNew" );
	private static final Key					LOAD_OR_NEW		= Key.of( "entityLoadOrNew" );
	private static final Key					LOAD_OR_SAVE	= Key.of( "entityLoadOrSave" );
	private static final Key					CRITERIA		= Key.of( "entityCriteria" );
	private static final Key					EXECUTE_QUERY	= Key.of( "ormExecuteQuery" );

	private static final Key					FILTER			= Key.of( "filter" );
	private static final Key					SORT			= Key.of( "sort" );
	private static final Key					IDS				= Key.of( "ids" );
	private static final Key					VALUES			= Key.of( "values" );
	private static final Key					ID_OR_FILTER	= ORMKeys.idOrFilter;
	private static final Key					MAX				= Key.of( "max" );

	private static final BoxRuntime				runtime			= BoxRuntime.getInstance();

	private StaticEntityMethods() {
	}

	/**
	 * Add the methods to every entity class of an ORM application. Called at the end of ORM startup (first boot and every reload)
	 * when {@code staticMethods} is on. An entity class that cannot be loaded is logged and skipped; it never fails the boot.
	 *
	 * @param context The context the ORM application starts in.
	 * @param app     The ORM application.
	 */
	public static void install( IBoxContext context, ORMApp app ) {
		NOT_ENTITY.clear();
		for ( EntityRecord record : app.getEntityRecords() ) {
			try {
				installOn( context, record, true );
			} catch ( RuntimeException e ) {
				logger().warn( "Could not add static methods to entity [{}] ({}): {}", record.getEntityName(), record.getClassFQN(), e.getMessage() );
			}
		}
	}

	/**
	 * Remove the methods bx-orm added from every entity class of an ORM application, keeping the entities' own statics. Called at
	 * ORM startup when {@code staticMethods} is off, so turning the setting off and reloading takes the methods away.
	 *
	 * @param context The context the ORM application starts in.
	 * @param app     The ORM application.
	 */
	public static void uninstall( IBoxContext context, ORMApp app ) {
		NOT_ENTITY.clear();
		for ( EntityRecord record : app.getEntityRecords() ) {
			try {
				DynamicObject	boxClass	= ClassLocator.getInstance().load( context, record.getClassFQN(), record.getResolverPrefix() );
				StaticScope		scope		= BoxClassSupport.getStaticScope( context, boxClass );
				scope.entrySet().removeIf( entry -> entry.getValue() instanceof EntityStaticFunction );
				INSTALLED.remove( boxClass.getTargetClass() );
			} catch ( RuntimeException e ) {
				logger().debug( "Could not remove static methods from entity [{}]: {}", record.getEntityName(), e.getMessage() );
			}
		}
	}

	/**
	 * Whether a class was already handled at instance creation: it carries the methods, or it is not an entity.
	 *
	 * @param javaClass The compiled BoxLang class of a new instance.
	 *
	 * @return True when nothing needs doing for this class.
	 */
	public static boolean isKnown( Class<?> javaClass ) {
		return INSTALLED.containsKey( javaClass ) || NOT_ENTITY.containsKey( javaClass );
	}

	/**
	 * Re-add the methods to any entity class BoxLang recompiled since they were added. Called at request start, only when the
	 * runtime's trusted cache is off (classes never recompile with it on). Loading each entity class lets BoxLang notice an edited
	 * file and recompile it first, so a static call that is the first use of an edited entity still finds its methods.
	 *
	 * @param context The request context.
	 * @param app     The ORM application.
	 */
	public static void refresh( IBoxContext context, ORMApp app ) {
		for ( EntityRecord record : app.getEntityRecords() ) {
			try {
				installOn( context, record, false );
			} catch ( RuntimeException e ) {
				logger().debug( "Could not refresh static methods on entity [{}]: {}", record.getEntityName(), e.getMessage() );
			}
		}
	}

	/**
	 * Add the methods to the class of a just-created instance if it is an entity of the given ORM application and its class does
	 * not carry them yet (a class recompiled since startup). Called for every class instance BoxLang creates, so the common case
	 * (class already handled) is a single map lookup.
	 *
	 * @param instance The new instance.
	 * @param app      The ORM application of the creating request.
	 */
	public static void ensureInstalled( IClassRunnable instance, ORMApp app ) {
		Class<?> javaClass = instance.getClass();
		if ( isKnown( javaClass ) ) {
			return;
		}
		String			fqn		= instance.bxGetName().getName();
		EntityRecord	record	= app.getEntityRecords().stream()
		    .filter( r -> r.getClassFQN().equalsIgnoreCase( fqn ) )
		    .findFirst()
		    .orElse( null );
		if ( record == null ) {
			NOT_ENTITY.put( javaClass, Boolean.TRUE );
			return;
		}
		installInto( instance.getStaticScope(), javaClass, record.getEntityName() );
	}

	/**
	 * Load an entity's class and add the methods if that class does not carry them yet.
	 *
	 * @param context The current context.
	 * @param record  The entity.
	 * @param force   Add the methods even if this class was handled already (ORM startup, where the entity name may have changed).
	 */
	private static void installOn( IBoxContext context, EntityRecord record, boolean force ) {
		DynamicObject	boxClass	= ClassLocator.getInstance().load( context, record.getClassFQN(), record.getResolverPrefix() );
		Class<?>		javaClass	= boxClass.getTargetClass();
		if ( !force && INSTALLED.containsKey( javaClass ) ) {
			return;
		}
		installInto( BoxClassSupport.getStaticScope( context, boxClass ), javaClass, record.getEntityName() );
	}

	/**
	 * Put the methods into a static scope, keeping any static the class declares itself.
	 *
	 * @param scope      The entity class's static scope.
	 * @param javaClass  The compiled entity class, remembered as handled.
	 * @param entityName The entity name the methods act on.
	 */
	private static void installInto( StaticScope scope, Class<?> javaClass, String entityName ) {
		methodsFor( entityName ).forEach( ( name, function ) -> {
			Object existing = scope.get( name );
			if ( existing == null || existing instanceof EntityStaticFunction ) {
				scope.put( name, function );
			}
		} );
		INSTALLED.put( javaClass, Boolean.TRUE );
	}

	/**
	 * The static functions for one entity.
	 *
	 * @param entity The entity name.
	 *
	 * @return The functions, keyed by method name.
	 */
	static Map<Key, EntityStaticFunction> methodsFor( String entity ) {
		Map<Key, EntityStaticFunction> m = new LinkedHashMap<>();

		// Loading
		add( m, entity, "get", ( c, a ) -> bif( c, LOAD_BY_PK, ORMKeys.entity, entity, Key.id, a.get( Key.id ) ), required( Key.id ) );
		add( m, entity, "getOrFail", ( c, a ) -> bif( c, LOAD_BY_PK_FAIL, ORMKeys.entity, entity, Key.id, a.get( Key.id ) ), required( Key.id ) );
		add( m, entity, "getAll", ( c, a ) -> getAll( c, entity, a.get( IDS ) ), optional( IDS, null ) );
		add( m, entity, "read",
		    ( c, a ) -> bif( c, LOAD_BY_PK, ORMKeys.entity, entity, Key.id, a.get( Key.id ), ORMKeys.options, Struct.of( ORMKeys.readOnly, true ) ),
		    required( Key.id ) );
		add( m, entity, "getReference", ( c, a ) -> bif( c, GET_REFERENCE, ORMKeys.entityName, entity, Key.id, a.get( Key.id ) ), required( Key.id ) );

		// Finding
		add( m, entity, "findWhere", ( c, a ) -> findWhere( c, entity, a ), optional( FILTER, new Struct() ), optional( ORMKeys.options, new Struct() ) );
		add( m, entity, "findWhereOrFail", ( c, a ) -> {
			Object found = findWhere( c, entity, a );
			if ( found == null ) {
				throw new ORMException( ORMErrorType.NOT_FOUND,
				    String.format( "No [%s] entity matches the filter %s.", entity, a.get( FILTER ) ),
				    "Check the filter, or use findWhere() to get null instead of an error." );
			}
			return found;
		}, optional( FILTER, new Struct() ), optional( ORMKeys.options, new Struct() ) );
		add( m, entity, "findAllWhere",
		    ( c, a ) -> load( c, entity, a.get( FILTER ), StringCaster.cast( a.get( SORT ) ), struct( a.get( ORMKeys.options ) ) ),
		    optional( FILTER, new Struct() ), optional( SORT, "" ), optional( ORMKeys.options, new Struct() ) );
		add( m, entity, "first", ( c, a ) -> {
			Object rows = load( c, entity, a.get( FILTER ), StringCaster.cast( a.get( SORT ) ), Struct.of( ORMKeys.maxResults, 1 ) );
			return rows instanceof Array array && !array.isEmpty() ? array.getFirst() : null;
		}, optional( FILTER, new Struct() ), optional( SORT, "" ) );
		add( m, entity, "exists", ( c, a ) -> {
			Object	idOrFilter	= a.get( ID_OR_FILTER );
			Object	criteria	= idOrFilter instanceof IStruct filter
			    ? where( c, criteria( c, entity ), filter )
			    : call( c, criteria( c, entity ), "idEq", idOrFilter );
			return call( c, criteria, "exists" );
		}, required( ID_OR_FILTER ) );
		add( m, entity, "count", ( c, a ) -> call( c, where( c, criteria( c, entity ), struct( a.get( FILTER ) ) ), "count" ),
		    optional( FILTER, new Struct() ) );
		add( m, entity, "list", ( c, a ) -> list( c, entity, struct( a.get( ORMKeys.options ) ) ), optional( ORMKeys.options, new Struct() ) );

		// Queries
		add( m, entity, "executeQuery",
		    ( c, a ) -> bif( c, EXECUTE_QUERY, ORMKeys.hql, a.get( ORMKeys.hql ), Key.params, a.get( Key.params ), Key.options,
		        struct( a.get( Key.options ) ) ),
		    required( ORMKeys.hql ), optional( Key.params, new Struct() ), optional( Key.options, new Struct() ) );
		add( m, entity, "criteria", ( c, a ) -> criteria( c, entity ) );
		add( m, entity, "newCriteria", ( c, a ) -> criteria( c, entity ) );

		// Creating and writing
		add( m, entity, "new", ( c, a ) -> bif( c, ENTITY_NEW, ORMKeys.entityName, entity, Key.properties, struct( a.get( Key.properties ) ) ),
		    optional( Key.properties, new Struct() ) );
		add( m, entity, "firstOrNew",
		    ( c, a ) -> bif( c, LOAD_OR_NEW, ORMKeys.entityName, entity, ORMKeys.idOrFilter, a.get( FILTER ), Key.properties,
		        struct( a.get( Key.properties ) ) ),
		    required( FILTER ), optional( Key.properties, new Struct() ) );
		add( m, entity, "firstOrCreate",
		    ( c, a ) -> bif( c, LOAD_OR_SAVE, ORMKeys.entityName, entity, ORMKeys.idOrFilter, a.get( FILTER ), Key.properties,
		        struct( a.get( Key.properties ) ) ),
		    required( FILTER ), optional( Key.properties, new Struct() ) );
		add( m, entity, "deleteWhere", ( c, a ) -> call( c, where( c, criteria( c, entity ), requireFilter( entity, "deleteWhere", a ) ), "deleteAll" ),
		    required( FILTER ) );
		add( m, entity, "updateWhere",
		    ( c, a ) -> call( c, where( c, criteria( c, entity ), requireFilter( entity, "updateWhere", a ) ), "updateAll", struct( a.get( VALUES ) ) ),
		    required( FILTER ), required( VALUES ) );
		return m;
	}

	/**
	 * {@code getAll( ids )}: every entity when no ids are given, else the entities for the ids (an array, or a comma-separated list), in
	 * the asked order with null for missing ids.
	 */
	private static Object getAll( IBoxContext context, String entity, Object ids ) {
		if ( ids == null || ( ids instanceof String s && s.isBlank() ) || ( ids instanceof Array a && a.isEmpty() ) ) {
			return bif( context, ENTITY_LOAD, ORMKeys.entityName, entity );
		}
		Array idArray;
		if ( ids instanceof Array a ) {
			idArray = a;
		} else if ( ids instanceof String s ) {
			idArray = new Array();
			for ( String id : s.split( "," ) ) {
				idArray.add( id.trim() );
			}
		} else {
			idArray = Array.of( ids );
		}
		return bif( context, LOAD_BY_PK, ORMKeys.entity, entity, Key.id, idArray );
	}

	/**
	 * {@code findWhere( filter, options )}: the one entity matching the filter, or null. More than one match is an
	 * {@code orm.query.nonUnique} error unless the options include {@code uniqueFirst : true}, like cborm's findWhere.
	 */
	private static Object findWhere( IBoxContext context, String entity, ArgumentsScope args ) {
		return bif( context, ENTITY_LOAD, ORMKeys.entityName, entity, ORMKeys.idOrFilter, struct( args.get( FILTER ) ), ORMKeys.uniqueOrOrder, true,
		    ORMKeys.options, struct( args.get( ORMKeys.options ) ) );
	}

	/**
	 * {@code list( options )}: the entities, paged and sorted. Takes {@code max}, {@code offset} and {@code sort}, plus every
	 * {@code entityLoad()} option ({@code readOnly}, {@code asStream}, {@code cacheable}, ...).
	 */
	private static Object list( IBoxContext context, String entity, IStruct options ) {
		IStruct	loadOptions	= new Struct( options );
		String	sort		= loadOptions.containsKey( SORT ) ? StringCaster.cast( loadOptions.remove( SORT ) ) : "";
		if ( loadOptions.containsKey( MAX ) ) {
			loadOptions.put( ORMKeys.maxResults, loadOptions.remove( MAX ) );
		}
		return load( context, entity, new Struct(), sort, loadOptions );
	}

	/**
	 * A filtered, sorted {@code entityLoad()}; a blank sort keeps the entity's {@code defaultSort}.
	 */
	private static Object load( IBoxContext context, String entity, Object filter, String sort, IStruct options ) {
		Map<Key, Object> args = new LinkedHashMap<>();
		args.put( ORMKeys.entityName, entity );
		args.put( ORMKeys.idOrFilter, struct( filter ) );
		if ( sort != null && !sort.isBlank() ) {
			args.put( ORMKeys.uniqueOrOrder, sort );
		}
		args.put( ORMKeys.options, options );
		return invoke( context, ENTITY_LOAD, args );
	}

	/**
	 * A new criteria builder for the entity.
	 */
	private static Object criteria( IBoxContext context, String entity ) {
		return bif( context, CRITERIA, ORMKeys.entityName, entity );
	}

	/**
	 * Add a filter struct to a criteria builder ({@code where( filter )}); an empty filter adds nothing.
	 */
	private static Object where( IBoxContext context, Object criteria, IStruct filter ) {
		return filter == null || filter.isEmpty() ? criteria : call( context, criteria, "where", filter );
	}

	/**
	 * Call a criteria builder method, the same way BoxLang code does.
	 */
	private static Object call( IBoxContext context, Object criteria, String method, Object... args ) {
		return ( ( ortus.boxlang.runtime.dynamic.IReferenceable ) criteria ).dereferenceAndInvoke( context, Key.of( method ), args, false );
	}

	/**
	 * The non-empty filter of a bulk statement. An empty filter would touch every row, so it is refused; use
	 * {@code criteria().deleteAll()} or {@code criteria().updateAll()} for that on purpose.
	 */
	private static IStruct requireFilter( String entity, String method, ArgumentsScope args ) {
		IStruct filter = struct( args.get( FILTER ) );
		if ( filter.isEmpty() ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    String.format( "%s::%s() needs a non-empty filter; an empty one would affect every row.", entity, method ),
			    String.format( "Pass a filter, or use %s::criteria().%s() to affect every row on purpose.", entity,
			        "deleteWhere".equals( method ) ? "deleteAll" : "updateAll( values )" ) );
		}
		return filter;
	}

	/**
	 * Call a BIF with named arguments given as key/value pairs.
	 */
	private static Object bif( IBoxContext context, Key name, Object... keysAndValues ) {
		Map<Key, Object> args = new LinkedHashMap<>();
		for ( int i = 0; i < keysAndValues.length; i += 2 ) {
			args.put( ( Key ) keysAndValues[ i ], keysAndValues[ i + 1 ] );
		}
		return invoke( context, name, args );
	}

	/**
	 * Call a BIF by name with named arguments, bypassing any same-named function the calling class might declare.
	 */
	private static Object invoke( IBoxContext context, Key name, Map<Key, Object> args ) {
		BIFDescriptor bif = runtime.getFunctionService().getGlobalFunction( name );
		return bif.invoke( context, args, false, name );
	}

	/**
	 * A struct argument, treating null as empty.
	 */
	private static IStruct struct( Object value ) {
		return value instanceof IStruct s ? s : new Struct();
	}

	private static Argument required( Key name ) {
		return new Argument( true, "any", name );
	}

	private static Argument optional( Key name, Object defaultValue ) {
		return new Argument( false, "any", name, defaultValue );
	}

	private static void add( Map<Key, EntityStaticFunction> methods, String entity, String name, BiFunction<IBoxContext, ArgumentsScope, Object> body,
	    Argument... arguments ) {
		Key key = Key.of( name );
		methods.put( key, new EntityStaticFunction( key, entity, ( fctx, fn ) -> body.apply( fctx, fctx.getArgumentsScope() ), arguments ) );
	}

	private static BoxLangLogger logger() {
		return runtime.getLoggingService().getLogger( "orm" );
	}

	/**
	 * A static method bx-orm added to an entity class. The type marks it as ours, so a later install can replace it while
	 * leaving the entity's own statics alone.
	 */
	public static final class EntityStaticFunction extends DynamicFunction {

		private final String entityName;

		EntityStaticFunction( Key name, String entityName, BiFunction<FunctionBoxContext, DynamicFunction, Object> body, Argument[] arguments ) {
			super( name, body, arguments );
			this.entityName = entityName;
		}

		/**
		 * @return The entity this method acts on.
		 */
		public String getEntityName() {
			return entityName;
		}
	}
}
