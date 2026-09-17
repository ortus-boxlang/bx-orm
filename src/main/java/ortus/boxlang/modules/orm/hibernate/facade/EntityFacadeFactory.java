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
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

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
	 * A mapped property to expose on the facade.
	 *
	 * @param name     The BoxLang property name (also the getter/setter suffix, e.g. {@code name} -> {@code getName}).
	 * @param javaType The Java type of the accessor. Use a concrete type for the id (e.g. {@code String} for uuid) so
	 *                 Hibernate's generator resolution has a real typed member; property accessors may use {@code Object}.
	 */
	public record PropertySpec( String name, Class<?> javaType ) {
	}

	/** Generated facade classes, cached by fully-qualified class name. */
	private static final Map<String, Class<?>> CACHE = new ConcurrentHashMap<>();

	/**
	 * Generate (or return a cached) facade class.
	 *
	 * @param className  Fully-qualified name for the generated facade class.
	 * @param id         The id property spec (its {@code javaType} should be concrete, e.g. String/Integer/UUID).
	 * @param properties The remaining mapped property specs.
	 * @param loader     The classloader to inject the generated class into (must be the one Hibernate resolves against).
	 *
	 * @return The generated facade {@link Class}, implementing {@link BoxEntityFacade}.
	 */
	public static Class<?> generate( String className, PropertySpec id, List<PropertySpec> properties, ClassLoader loader ) {
		return CACHE.computeIfAbsent( className, name -> build( name, id, properties, loader ) );
	}

	private static Class<?> build( String className, PropertySpec id, List<PropertySpec> properties, ClassLoader loader ) {
		DynamicType.Builder<?> builder = new ByteBuddy()
		    .subclass( Object.class )
		    .name( className )
		    .implement( BoxEntityFacade.class )
		    .defineField( "boxState", BoxEntityState.class, Visibility.PUBLIC )
		    // Expose the backing state (BoxEntityFacade.boxState()) straight from the field.
		    .method( named( "boxState" ) ).intercept( FieldAccessor.ofField( "boxState" ) )
		    // Constructor that wires the backing state: super(); this.boxState = arg0;
		    .defineConstructor( Visibility.PUBLIC ).withParameters( BoxEntityState.class )
		    .intercept( MethodCall.invoke( OBJECT_CTOR ).andThen( FieldAccessor.ofField( "boxState" ).setsArgumentAt( 0 ) ) );

		// The id gets a concretely-typed accessor pair so Hibernate's id-generator resolution sees a real member.
		builder = defineAccessor( builder, id );
		// Remaining mapped properties.
		for ( PropertySpec prop : properties ) {
			builder = defineAccessor( builder, prop );
		}

		return builder.make()
		    .load( loader, ClassLoadingStrategy.Default.INJECTION )
		    .getLoaded();
	}

	private static DynamicType.Builder<?> defineAccessor( DynamicType.Builder<?> builder, PropertySpec prop ) {
		String cap = Character.toUpperCase( prop.name().charAt( 0 ) ) + prop.name().substring( 1 );
		return builder
		    .defineMethod( "get" + cap, prop.javaType(), Visibility.PUBLIC )
		    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), true ) ) )
		    .defineMethod( "set" + cap, void.class, Visibility.PUBLIC ).withParameters( prop.javaType() )
		    .intercept( MethodDelegation.to( new PropertyInterceptor( prop.name(), false ) ) );
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

		public PropertyInterceptor( String property, boolean getter ) {
			this.property	= property;
			this.getter		= getter;
		}

		@RuntimeType
		public Object intercept( @This Object self, @AllArguments Object[] args ) {
			BoxEntityState state = ( ( BoxEntityFacade ) self ).boxState();
			if ( getter ) {
				return state == null ? null : state.get( property );
			}
			if ( state != null ) {
				state.set( property, args.length > 0 ? args[ 0 ] : null );
			}
			return null;
		}
	}
}
