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
package ortus.boxlang.modules.orm.hibernate.facade;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import ortus.boxlang.runtime.runnables.IClassRunnable;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Generates a real Java "facade" class per ORM entity at runtime (via ByteBuddy). Hibernate maps the facade as a normal
 * POJO, so it has a real class name and a real id {@link java.lang.reflect.Member} - which is what lets Hibernate do
 * {@code uuid} (and every other) id generation, unavailable to class-less dynamic (MAP) entities.
 * <p>
 * The facade holds no state of its own: each generated getter/setter delegates to a {@link BoxEntityState} (backed in
 * production by the entity's BoxLang instance), so the BoxLang class and the facade share one state store.
 */
public final class EntityFacadeFactory {

	private EntityFacadeFactory() {
	}

	/**
	 * How a mapped property's facade accessors translate between Hibernate and the BoxLang instance.
	 * <ul>
	 * <li>{@link #NONE} - a plain column/version/id: read/write the scope value straight through.</li>
	 * <li>{@link #TO_ONE} - a one-to-one/many-to-one: the getter wraps the scope's {@link ortus.boxlang.runtime.runnables.IClassRunnable}
	 * to the target's facade for Hibernate; the setter unwraps Hibernate's facade back to the {@code IClassRunnable} the
	 * scope (and the BoxLang developer) holds. Hibernate proxies pass through unchanged so lazy loading is preserved.</li>
	 * <li>{@link #TO_MANY} - a one-to-many/many-to-many: the getter hands Hibernate its managed collection of facades; the
	 * setter stores a {@link FacadeCollectionView} in the scope so the developer only ever sees {@code IClassRunnable}s.</li>
	 * </ul>
	 */
	public enum AssocKind {
		NONE,
		TO_ONE,
		TO_MANY,
		/**
		 * A map-classified collection (an entity map or a value/element map collection). The accessor is a {@code Map}
		 * rather than a {@code List}; scalar keys/values pass through unwrapped.
		 */
		TO_MANY_MAP,
		/**
		 * A struct-typed (map-classified) entity collection: a {@code Map} accessor whose values are entities. Hibernate
		 * holds facade values; the developer sees {@code IClassRunnable} values through a {@link FacadeMapView}.
		 */
		TO_MANY_ENTITY_MAP
	}

	/**
	 * A mapped property to expose on the facade.
	 *
	 * @param name     The BoxLang property name (also the getter/setter suffix, e.g. {@code name} -> {@code getName}).
	 * @param javaType The Java type of the accessor. Use a concrete type for the id (e.g. {@code String} for uuid) so
	 *                 Hibernate's generator resolution has a real typed member; property accessors may use {@code Object}.
	 * @param assoc    How the accessor translates at the Hibernate/BoxLang boundary (see {@link AssocKind}).
	 */
	public record PropertySpec( String name, Class<?> javaType, AssocKind assoc ) {

		/**
		 * Convenience for a non-association (plain column/id) property.
		 *
		 * @param name     The property name.
		 * @param javaType The accessor Java type.
		 */
		public PropertySpec( String name, Class<?> javaType ) {
			this( name, javaType, AssocKind.NONE );
		}
	}

	/**
	 * Facades generated directly into a non-{@link FacadeClassLoader} loader (callers outside an ORM application build,
	 * such as unit tests), cached by fully-qualified class name. ORM application builds use a per-build
	 * {@link FacadeClassLoader} and its own cache instead, so a reload can redefine changed facades.
	 */
	private static final Map<String, Class<?>>	CACHE		= new ConcurrentHashMap<>();

	/**
	 * Bytecode of every facade generated this JVM (FQN -> class bytes), captured at generation so it can be persisted to a
	 * {@code .bxorm/facades.jar} for trust-mode boots that skip ByteBuddy codegen. A later build of the same facade
	 * overwrites its entry, so the jar always reflects the latest generation.
	 */
	private static final Map<String, byte[]>	BYTECODE	= new ConcurrentHashMap<>();

	/**
	 * Generate (or return a cached) facade class for a single-id, non-inheriting entity.
	 *
	 * @param className  Fully-qualified name for the generated facade class.
	 * @param id         The id property spec (its {@code javaType} should be concrete, e.g. String/Integer/UUID).
	 * @param properties The remaining mapped property specs.
	 * @param loader     The classloader to inject the generated class into (must be the one Hibernate resolves against).
	 *
	 * @return The generated facade {@link Class}, implementing {@link BoxEntityFacade}.
	 */
	public static Class<?> generate( String className, PropertySpec id, List<PropertySpec> properties, ClassLoader loader ) {
		return generate( className, List.of( id ), properties, Object.class, loader );
	}

	/**
	 * Generate (or return a cached) facade class, supporting composite ids and inheritance.
	 * <p>
	 * For an entity hierarchy the root facade holds the backing-state field, the {@link BoxEntityFacade#boxState()}
	 * accessor and the id accessors; a subclass facade instead {@code extends} its parent facade (so it inherits the
	 * field, state accessor and id accessors) and declares only its own local property accessors. Facades must therefore
	 * be generated parents-first.
	 *
	 * @param className  Fully-qualified name for the generated facade class.
	 * @param ids        The id property specs (one for a simple key, several for a composite key). Each {@code javaType}
	 *                   should be concrete so Hibernate's id resolution sees a real typed member. Pass an empty list for a
	 *                   subclass facade, whose id is inherited from the root facade.
	 * @param properties The (local) mapped property specs to declare on this facade.
	 * @param superClass The Java superclass to extend: {@link Object} for a hierarchy root, or the parent entity's
	 *                   already-generated facade class for a subclass.
	 * @param loader     The classloader to inject the generated class into (must be the one Hibernate resolves against).
	 *
	 * @return The generated facade {@link Class}, implementing {@link BoxEntityFacade}.
	 */
	public static Class<?> generate( String className, List<PropertySpec> ids, List<PropertySpec> properties, Class<?> superClass, ClassLoader loader ) {
		// An ORM application build generates into its own FacadeClassLoader (one per startup/reload), whose cache is scoped
		// to that build - so a reload with a changed entity defines a fresh class instead of reusing the previous one.
		if ( loader instanceof FacadeClassLoader facadeLoader ) {
			return facadeLoader.facades().computeIfAbsent( className, name -> build( name, ids, properties, superClass, loader ) );
		}
		return CACHE.computeIfAbsent( className, name -> build( name, ids, properties, superClass, loader ) );
	}

	private static Class<?> build( String className, List<PropertySpec> ids, List<PropertySpec> properties, Class<?> superClass, ClassLoader loader ) {
		boolean					root	= superClass == null || superClass == Object.class;
		DynamicType.Builder<?>	builder	= new ByteBuddy().subclass( root ? Object.class : superClass ).name( className );

		if ( root ) {
			// The hierarchy root owns the backing-state field, the BoxEntityFacade contract, and the concretely-typed id
			// accessors (so Hibernate's id-generator resolution sees a real member). Subclasses inherit all of this.
			builder = builder
			    .implement( BoxEntityFacade.class )
			    // Serializable with writeReplace() -> a constant marker: when BoxLang deep-copies an entity (duplicate()) or
			    // serializes it (session storage), the facade memoized in its variables scope is replaced by a harmless
			    // string instead of dragging the whole entity graph along (or failing outright). The copy then gets its own
			    // facade on its next save. The marker is a bytecode constant, so the class stays self-contained.
			    .implement( java.io.Serializable.class )
			    .defineMethod( "writeReplace", Object.class, Visibility.PROTECTED )
			    .intercept( net.bytebuddy.implementation.FixedValue.value( DETACHED_FACADE_MARKER ) )
			    .defineField( "boxState", BoxEntityState.class, Visibility.PUBLIC )
			    // Expose the backing state (BoxEntityFacade.boxState()) straight from the field.
			    .method( named( "boxState" ) ).intercept( FieldAccessor.ofField( "boxState" ) )
			    // Constructor that wires the backing state: super(); this.boxState = arg0;
			    .defineConstructor( Visibility.PUBLIC ).withParameters( BoxEntityState.class )
			    .intercept( MethodCall.invoke( OBJECT_CTOR ).andThen( FieldAccessor.ofField( "boxState" ).setsArgumentAt( 0 ) ) );
			// Each key property gets a concretely-typed accessor pair (composite ids emit one per key property).
			for ( PropertySpec id : ids ) {
				builder = defineAccessor( builder, id );
			}
		}
		// A subclass facade reuses the parent's field, state accessor and id accessors, and ByteBuddy's default
		// (IMITATE_SUPER_CLASS) constructor strategy already generates a public constructor forwarding BoxEntityState to the
		// parent facade's constructor - so no constructor is defined here for the subclass.

		// Local mapped properties (for a subclass, only its own declared properties).
		for ( PropertySpec prop : properties ) {
			builder = defineAccessor( builder, prop );
		}

		// Trust mode: if this build's loader carries pre-generated bytecode for this facade (read from .bxorm/facades.jar),
		// define it directly instead of running ByteBuddy. The caller generates parents-first, so a subclass's parent facade
		// is already defined. Any failure falls through to normal ByteBuddy generation, so the jar is only an optimization.
		if ( loader instanceof FacadeClassLoader facadeLoader ) {
			byte[] preGenerated = facadeLoader.pregenerated( className );
			if ( preGenerated != null ) {
				try {
					return facadeLoader.defineFromBytes( className, preGenerated );
				} catch ( RuntimeException | LinkageError e ) {
					// Fall back to ByteBuddy codegen below.
				}
			}
		}

		DynamicType.Unloaded<?>	unloaded		= builder.make();
		// Only self-contained bytecode may be written to facades.jar: a trust boot defines those raw bytes directly, so a
		// class that relies on ByteBuddy's load-time initializers (e.g. instance delegation stored in static fields) would
		// be defined half-initialized. The accessors below are plain static calls, so this always holds; the guard keeps
		// any future change from silently producing a broken jar.
		boolean					selfContained	= unloaded.getLoadedTypeInitializers().values().stream().noneMatch( LoadedTypeInitializer::isAlive );
		if ( selfContained ) {
			BYTECODE.put( className, unloaded.getBytes() );
		} else {
			BYTECODE.remove( className );
		}
		return unloaded.load( loader, ClassLoadingStrategy.Default.INJECTION ).getLoaded();
	}

	/**
	 * Read pre-generated facade bytecode from a {@code facades.jar}, to hand to a trust-mode build's
	 * {@link FacadeClassLoader} so it defines those classes instead of running ByteBuddy. Best-effort: a missing or
	 * unreadable jar yields an empty map (generation then proceeds normally).
	 *
	 * @param jarFile The {@code .bxorm/facades.jar} to read.
	 *
	 * @return The facade bytecode keyed by fully-qualified class name (empty if there is none).
	 */
	public static Map<String, byte[]> readFacadeJar( java.nio.file.Path jarFile ) {
		Map<String, byte[]> bytecode = new java.util.HashMap<>();
		if ( jarFile == null || !java.nio.file.Files.exists( jarFile ) ) {
			return bytecode;
		}
		try ( var jar = new java.util.jar.JarInputStream( java.nio.file.Files.newInputStream( jarFile ) ) ) {
			java.util.jar.JarEntry entry;
			while ( ( entry = jar.getNextJarEntry() ) != null ) {
				if ( entry.getName().endsWith( ".class" ) ) {
					String fqn = entry.getName().substring( 0, entry.getName().length() - ".class".length() ).replace( '/', '.' );
					bytecode.put( fqn, jar.readAllBytes() );
				}
			}
		} catch ( java.io.IOException e ) {
			// Best-effort: ignore a bad jar and let ByteBuddy generate.
			bytecode.clear();
		}
		return bytecode;
	}

	/**
	 * Write the facades generated for a namespace to a {@code facades.jar}. Only classes whose FQN contains the namespace
	 * segment are written, so one application's jar never captures another's facades.
	 *
	 * @param jarFile   The destination {@code .bxorm/facades.jar}.
	 * @param namespace The application's facade namespace (the {@code generated.<namespace>} package segment).
	 */
	public static void writeFacadeJar( java.nio.file.Path jarFile, String namespace ) {
		String infix = ".generated." + namespace + ".";
		try {
			java.nio.file.Files.createDirectories( jarFile.getParent() );
			try ( var jos = new java.util.jar.JarOutputStream( java.nio.file.Files.newOutputStream( jarFile ) ) ) {
				for ( Map.Entry<String, byte[]> entry : BYTECODE.entrySet() ) {
					if ( entry.getKey().contains( infix ) ) {
						jos.putNextEntry( new java.util.jar.JarEntry( entry.getKey().replace( '.', '/' ) + ".class" ) );
						jos.write( entry.getValue() );
						jos.closeEntry();
					}
				}
			}
		} catch ( java.io.IOException e ) {
			throw new ortus.boxlang.runtime.types.exceptions.BoxRuntimeException( "Failed to write facades.jar to [" + jarFile + "]", e );
		}
	}

	/**
	 * A generic {@code List<Object>} declared as the accessor type for to-many associations.
	 * <p>
	 * Hibernate 7's POJO mapping model validates a collection attribute's <em>element type</em> from the real member's
	 * generic signature (see {@code PluralAttributeMappingImpl.checkElementType}). A raw {@code Object} accessor has no
	 * resolvable element type (NPE), whereas an {@code Object} element type short-circuits the check as valid - so the
	 * generated collection accessors are declared {@code List<Object>}, which erases to a plain list at runtime while
	 * giving Hibernate the element type it needs.
	 */
	private static final TypeDescription.Generic	LIST_OF_OBJECT	= TypeDescription.Generic.Builder
	    .parameterizedType( List.class, Object.class ).build();

	/**
	 * A generic {@code Map<Object, Object>} declared as the accessor type for map-classified collections, for the same
	 * element-type-resolution reason as {@link #LIST_OF_OBJECT}.
	 */
	private static final TypeDescription.Generic	MAP_OF_OBJECT	= TypeDescription.Generic.Builder
	    .parameterizedType( Map.class, Object.class, Object.class ).build();

	private static DynamicType.Builder<?> defineAccessor( DynamicType.Builder<?> builder, PropertySpec prop ) {
		String cap = Character.toUpperCase( prop.name().charAt( 0 ) ) + prop.name().substring( 1 );
		if ( prop.assoc() == AssocKind.TO_MANY ) {
			return builder
			    .defineMethod( "get" + cap, LIST_OF_OBJECT, Visibility.PUBLIC ).intercept( getter( prop ) )
			    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( LIST_OF_OBJECT ).intercept( setter( prop ) );
		}
		if ( prop.assoc() == AssocKind.TO_MANY_MAP || prop.assoc() == AssocKind.TO_MANY_ENTITY_MAP ) {
			return builder
			    .defineMethod( "get" + cap, MAP_OF_OBJECT, Visibility.PUBLIC ).intercept( getter( prop ) )
			    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( MAP_OF_OBJECT ).intercept( setter( prop ) );
		}
		return builder
		    .defineMethod( "get" + cap, prop.javaType(), Visibility.PUBLIC ).intercept( getter( prop ) )
		    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( prop.javaType() ).intercept( setter( prop ) );
	}

	/**
	 * A generated getter: {@code return (T) Accessors.get( this, "<property>", "<assocKind>" );}. The property name and
	 * association kind are bytecode constants, so the class needs no load-time initialization and its raw bytes are
	 * complete (safe to write to, and define from, {@code facades.jar}).
	 */
	private static Implementation getter( PropertySpec prop ) {
		return MethodCall.invoke( ACCESSOR_GET )
		    .withThis()
		    .with( prop.name(), prop.assoc().name() )
		    .withAssigner( Assigner.DEFAULT, Assigner.Typing.DYNAMIC );
	}

	/**
	 * A generated setter: {@code Accessors.set( this, value, "<property>", "<assocKind>" );}. See {@link #getter}.
	 */
	private static Implementation setter( PropertySpec prop ) {
		return MethodCall.invoke( ACCESSOR_SET )
		    .withThis()
		    .withArgument( 0 )
		    .with( prop.name(), prop.assoc().name() )
		    .withAssigner( Assigner.DEFAULT, Assigner.Typing.DYNAMIC );
	}

	/**
	 * What a facade serializes as ({@code writeReplace}): a marker string, never the facade or its entity. Seeing it in an
	 * entity's scope just means "this copy has no facade yet".
	 */
	public static final String									DETACHED_FACADE_MARKER	= "bx-orm:detached-facade";

	private static final java.lang.reflect.Constructor<Object>	OBJECT_CTOR;
	private static final java.lang.reflect.Method				ACCESSOR_GET;
	private static final java.lang.reflect.Method				ACCESSOR_SET;
	static {
		try {
			OBJECT_CTOR		= Object.class.getConstructor();
			ACCESSOR_GET	= Accessors.class.getMethod( "get", Object.class, String.class, String.class );
			ACCESSOR_SET	= Accessors.class.getMethod( "set", Object.class, Object.class, String.class, String.class );
		} catch ( NoSuchMethodException e ) {
			throw new ExceptionInInitializerError( e );
		}
	}

	/**
	 * The runtime targets of every generated facade getter and setter. Each generated accessor calls {@link #get} or
	 * {@link #set} with the facade, the property name and the association kind as bytecode constants, and these route the
	 * call to the facade's {@link BoxEntityState} (the backing BoxLang instance). Public because generated classes, in a
	 * child classloader, call it.
	 */
	public static final class Accessors {

		private Accessors() {
		}

		/**
		 * Read a property for Hibernate.
		 *
		 * @param self     The facade instance.
		 * @param property The BoxLang property name.
		 * @param assoc    The {@link AssocKind} name.
		 *
		 * @return The value, translated for Hibernate.
		 */
		public static Object get( Object self, String property, String assoc ) {
			BoxEntityState state = ( ( BoxEntityFacade ) self ).boxState();
			return state == null ? null : readForHibernate( AssocKind.valueOf( assoc ), state.get( property ) );
		}

		/**
		 * Write a property from Hibernate onto the backing BoxLang instance.
		 *
		 * @param self     The facade instance.
		 * @param value    The value Hibernate is setting.
		 * @param property The BoxLang property name.
		 * @param assoc    The {@link AssocKind} name.
		 */
		public static void set( Object self, Object value, String property, String assoc ) {
			BoxEntityState state = ( ( BoxEntityFacade ) self ).boxState();
			if ( state != null ) {
				state.set( property, writeToScope( AssocKind.valueOf( assoc ), value ) );
			}
		}

		/**
		 * Translate the value stored in the BoxLang scope into the representation Hibernate expects when it reads this
		 * property. Plain properties pass straight through; associations translate at the boundary.
		 *
		 * @param scopeValue The current value held in the BoxLang instance's scope.
		 *
		 * @return The value to hand back to Hibernate.
		 */
		private static Object readForHibernate( AssocKind assoc, Object scopeValue ) {
			switch ( assoc ) {
				case TO_ONE :
					// The developer holds an IClassRunnable in the scope; Hibernate needs the target's (managed) facade so it
					// can resolve the FK. A Hibernate proxy (lazy) is already what Hibernate wants, so pass it through untouched.
					if ( scopeValue instanceof org.hibernate.proxy.HibernateProxy ) {
						return scopeValue;
					}
					if ( scopeValue instanceof IClassRunnable runnable ) {
						return FacadeSupport.wrapInstance( runnable );
					}
					return scopeValue;
				case TO_MANY :
					// Hand Hibernate the single collection instance it manages (facade elements). If the scope holds our view,
					// return its backing collection; if it holds a developer-supplied list (transient, pre-flush), wrap each
					// element to its facade.
					if ( scopeValue instanceof FacadeCollectionView view ) {
						return view.backing();
					}
					if ( scopeValue instanceof java.util.List<?> list ) {
						java.util.List<Object> facades = new java.util.ArrayList<>( list.size() );
						for ( Object element : list ) {
							facades.add( element instanceof IClassRunnable runnable ? FacadeSupport.wrapInstance( runnable ) : element );
						}
						return facades;
					}
					return scopeValue;
				case TO_MANY_MAP :
					// A value/element map collection. The developer sets a BoxLang Struct whose keys are Key instances, but the
					// map key type is a scalar (e.g. String), so convert Key keys to their scalar name before handing the map to
					// Hibernate. If the map already has plain scalar keys (Hibernate's own managed map), return it unchanged so
					// its identity - and Hibernate's dirty tracking - is preserved.
					if ( scopeValue instanceof java.util.Map<?, ?> map ) {
						boolean hasKeyKeys = map.keySet().stream().anyMatch( key -> key instanceof ortus.boxlang.runtime.scopes.Key );
						if ( !hasKeyKeys ) {
							return scopeValue;
						}
						java.util.LinkedHashMap<Object, Object> converted = new java.util.LinkedHashMap<>();
						for ( java.util.Map.Entry<?, ?> entry : map.entrySet() ) {
							Object key = entry.getKey() instanceof ortus.boxlang.runtime.scopes.Key k ? k.getName() : entry.getKey();
							converted.put( key, entry.getValue() );
						}
						return converted;
					}
					return scopeValue;
				case TO_MANY_ENTITY_MAP :
					// Hand Hibernate its own managed map (facade values). A developer-supplied struct (transient) is converted:
					// Key keys become their scalar name and IClassRunnable values become facades.
					if ( scopeValue instanceof FacadeMapView view ) {
						return view.backing();
					}
					if ( scopeValue instanceof java.util.Map<?, ?> map ) {
						java.util.LinkedHashMap<Object, Object> converted = new java.util.LinkedHashMap<>();
						for ( java.util.Map.Entry<?, ?> entry : map.entrySet() ) {
							Object	key		= entry.getKey() instanceof ortus.boxlang.runtime.scopes.Key k ? k.getName() : entry.getKey();
							Object	value	= entry.getValue() instanceof IClassRunnable runnable ? FacadeSupport.wrapInstance( runnable ) : entry.getValue();
							converted.put( key, value );
						}
						return converted;
					}
					return scopeValue;
				default :
					return scopeValue;
			}
		}

		/**
		 * Translate the value Hibernate is writing onto this property into the representation the BoxLang scope (and the
		 * developer) hold. Plain properties pass straight through; associations translate at the boundary.
		 *
		 * @param hibernateValue The value Hibernate is setting.
		 *
		 * @return The value to store in the BoxLang instance's scope.
		 */
		private static Object writeToScope( AssocKind assoc, Object hibernateValue ) {
			switch ( assoc ) {
				case TO_ONE :
					// Hibernate sets the target's facade (eager) or a Hibernate proxy (lazy). A proxy that is itself an
					// IClassRunnable (a BoxProxy) is stored as-is so the developer gets a lazy BoxLang instance; a facade is
					// unwrapped to the BoxLang instance the developer expects.
					if ( hibernateValue instanceof IClassRunnable ) {
						return hibernateValue;
					}
					return FacadeSupport.unwrapIfFacade( hibernateValue );
				case TO_MANY :
					// Store a live view so the developer only ever sees IClassRunnable elements while Hibernate keeps managing
					// its own collection instance underneath.
					if ( hibernateValue instanceof FacadeCollectionView ) {
						return hibernateValue;
					}
					if ( hibernateValue instanceof java.util.List<?> list ) {
						return new FacadeCollectionView( list );
					}
					return hibernateValue;
				case TO_MANY_ENTITY_MAP :
					// Store a live view so the developer sees IClassRunnable values over Hibernate's managed map.
					if ( hibernateValue instanceof FacadeMapView ) {
						return hibernateValue;
					}
					if ( hibernateValue instanceof java.util.Map<?, ?> map ) {
						return new FacadeMapView( map );
					}
					return hibernateValue;
				default :
					return hibernateValue;
			}
		}
	}
}
