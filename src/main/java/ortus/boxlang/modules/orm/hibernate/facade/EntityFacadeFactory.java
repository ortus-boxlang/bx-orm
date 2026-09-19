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
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

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
		TO_MANY_MAP
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

	/** Generated facade classes, cached by fully-qualified class name. */
	private static final Map<String, Class<?>> CACHE = new ConcurrentHashMap<>();

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

		return builder.make()
		    .load( loader, ClassLoadingStrategy.Default.INJECTION )
		    .getLoaded();
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
			    .defineMethod( "get" + cap, LIST_OF_OBJECT, Visibility.PUBLIC )
			    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), true, prop.assoc() ) ) )
			    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( LIST_OF_OBJECT )
			    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), false, prop.assoc() ) ) );
		}
		if ( prop.assoc() == AssocKind.TO_MANY_MAP ) {
			return builder
			    .defineMethod( "get" + cap, MAP_OF_OBJECT, Visibility.PUBLIC )
			    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), true, prop.assoc() ) ) )
			    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( MAP_OF_OBJECT )
			    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), false, prop.assoc() ) ) );
		}
		return builder
		    .defineMethod( "get" + cap, prop.javaType(), Visibility.PUBLIC )
		    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), true, prop.assoc() ) ) )
		    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( prop.javaType() )
		    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), false, prop.assoc() ) ) );
	}

	private static final java.lang.reflect.Constructor<Object> OBJECT_CTOR;
	static {
		try {
			OBJECT_CTOR = Object.class.getConstructor();
		} catch ( NoSuchMethodException e ) {
			throw new ExceptionInInitializerError( e );
		}
	}

	/**
	 * Routes a single generated getter or setter to the facade's {@link BoxEntityState}. One instance is bound per
	 * method, carrying the property name and whether it is a getter, so no method-name parsing is needed at call time.
	 */
	public static class PropertyInterceptor {

		private final String	property;
		private final boolean	getter;
		private final AssocKind	assoc;

		public PropertyInterceptor( String property, boolean getter, AssocKind assoc ) {
			this.property	= property;
			this.getter		= getter;
			this.assoc		= assoc;
		}

		@RuntimeType
		public Object intercept( @This Object self, @AllArguments Object[] args ) {
			BoxEntityState state = ( ( BoxEntityFacade ) self ).boxState();
			if ( getter ) {
				return state == null ? null : readForHibernate( state.get( property ) );
			}
			if ( state != null ) {
				state.set( property, writeToScope( args.length > 0 ? args[ 0 ] : null ) );
			}
			return null;
		}

		/**
		 * Translate the value stored in the BoxLang scope into the representation Hibernate expects when it reads this
		 * property. Plain properties pass straight through; associations translate at the boundary.
		 *
		 * @param scopeValue The current value held in the BoxLang instance's scope.
		 *
		 * @return The value to hand back to Hibernate.
		 */
		private Object readForHibernate( Object scopeValue ) {
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
		private Object writeToScope( Object hibernateValue ) {
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
				default :
					return hibernateValue;
			}
		}
	}
}
