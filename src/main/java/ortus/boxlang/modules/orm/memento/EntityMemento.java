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
package ortus.boxlang.modules.orm.memento;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.orm.EntityInspector;
import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.hibernate.BoxProxy;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Function;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * {@code entityToStruct()}: turns entities into structs, mementifier-style (see {@link MementoSpec}).
 * <p>
 * Values are read through the entity's getters (so lazy associations load as needed, and computed getters can be
 * included). Associations become nested structs (to-one) or arrays of structs (to-many). An entity that is already
 * being written higher up the same branch is written as its id, so cycles end. Nulls become the matching
 * {@code defaults} entry, else an empty string (an empty array for a to-many), and dates become ISO 8601 strings.
 * Mappers run last, with {@code ( value, memento )}, on the keys the struct holds; an include that is neither a property
 * nor a getter is a computed key built by its mapper. Unlike mementifier, an include that is none of these is an
 * {@code orm.property.unknown} error rather than silently skipped.
 */
public final class EntityMemento {

	/** The ORM application. */
	private final ORMApp		app;
	/** The context closures and getters run in. */
	private final IBoxContext	context;
	/** The BIF, for errors. */
	private final String		bif;

	/**
	 * Create a writer.
	 *
	 * @param app     The ORM application.
	 * @param context The context.
	 * @param bif     The BIF, for errors.
	 */
	public EntityMemento( ORMApp app, IBoxContext context, String bif ) {
		this.app		= app;
		this.context	= context;
		this.bif		= bif;
	}

	/**
	 * Write one entity or an array of entities.
	 *
	 * @param value An entity, or an array of entities.
	 * @param spec  What to include.
	 *
	 * @return A struct, or an array of structs.
	 */
	public Object write( Object value, MementoSpec spec ) {
		if ( value instanceof Array array ) {
			Array result = new Array();
			for ( Object item : array ) {
				result.add( item == null ? null : write( item, spec, newPath() ) );
			}
			return result;
		}
		return write( value, spec, newPath() );
	}

	/**
	 * An empty identity set for the entities on the current branch.
	 *
	 * @return The set.
	 */
	private static Set<IClassRunnable> newPath() {
		return Collections.newSetFromMap( new IdentityHashMap<>() );
	}

	/**
	 * Write one entity.
	 *
	 * @param value The entity (or lazy reference).
	 * @param spec  What to include.
	 * @param path  The entities being written on this branch.
	 *
	 * @return The struct.
	 */
	private IStruct write( Object value, MementoSpec spec, Set<IClassRunnable> path ) {
		IClassRunnable			entity		= runnable( value );
		EntityRecord			record		= EntityInspector.resolve( app, entity, bif );
		MementoSpec.Effective	effective	= spec.resolve( entity );
		IStruct					result		= new Struct( IStruct.TYPES.LINKED );
		path.add( entity );
		try {
			Map<String, MementoSpec.Include> includes = effective.includes();
			if ( includes.isEmpty() || includes.containsKey( "*" ) ) {
				includes = withPlainProperties( record, includes );
			}
			for ( MementoSpec.Include include : includes.values() ) {
				if ( include.name().equals( "*" ) || effective.excludes( include.name() ) ) {
					continue;
				}
				IPropertyMeta	property	= property( record, include.name() );
				// Neither a property nor a getter: a computed key, when a mapper builds it (as in mementifier).
				boolean			computed	= property == null && !hasGetter( entity, include.name() );
				if ( computed && !effective.mappers().containsKey( Key.of( include.name() ) ) ) {
					throw ORMErrors.propertyNotFound( record.getEntityName(), include.name(), app.getPropertyNames( record.getEntityName() ), bif );
				}
				String	name	= property != null ? property.getName() : include.name();
				Key		key		= Key.of( include.outputName( property != null ? property.getName() : null ) );
				Object	raw		= computed ? null : read( entity, name );
				Object	out;
				if ( property != null && property.isAssociationType() ) {
					out = association( raw, spec.child( include, effective.nestedExcludes( include.name() ) ), path, isToMany( property ) );
				} else {
					out = IsoDates.convert( raw );
				}
				if ( out == null ) {
					Object fallback = effective.defaults().get( key );
					out = fallback != null ? fallback : ( property != null && isToMany( property ) ? new Array() : "" );
				}
				result.put( key, out );
			}
			// Mappers transform the keys the memento holds (a computed key is one included by name).
			for ( Key key : new java.util.ArrayList<>( result.keySet() ) ) {
				if ( effective.mappers().get( key ) instanceof Function mapper ) {
					result.put( key, context.invokeFunction( mapper, new Object[] { result.get( key ), result } ) );
				}
			}
		} finally {
			path.remove( entity );
		}
		return result;
	}

	/**
	 * Write an association value: a struct for a to-one, an array (or struct, for a map) of structs for a to-many; an
	 * entity already on this branch becomes its id.
	 *
	 * @param raw    The value read from the getter.
	 * @param spec   What to include in the associated entities.
	 * @param path   The entities being written on this branch.
	 * @param toMany Whether the association is a collection.
	 *
	 * @return The converted value, or null.
	 */
	private Object association( Object raw, MementoSpec spec, Set<IClassRunnable> path, boolean toMany ) {
		if ( raw == null ) {
			return null;
		}
		if ( raw instanceof Map<?, ?> map && ! ( raw instanceof IClassRunnable ) ) {
			IStruct result = new Struct( IStruct.TYPES.LINKED );
			map.forEach( ( k, v ) -> result.put( Key.of( String.valueOf( k ) ), related( v, spec, path ) ) );
			return result;
		}
		if ( raw instanceof Collection<?> collection ) {
			Array result = new Array();
			for ( Object item : collection ) {
				result.add( related( item, spec, path ) );
			}
			return result;
		}
		return related( raw, spec, path );
	}

	/**
	 * Write one associated entity, or its id when it is already on this branch.
	 *
	 * @param value The associated entity.
	 * @param spec  What to include.
	 * @param path  The entities being written on this branch.
	 *
	 * @return The struct, the id, or null.
	 */
	private Object related( Object value, MementoSpec spec, Set<IClassRunnable> path ) {
		if ( value == null ) {
			return null;
		}
		IClassRunnable entity = runnable( value );
		if ( path.contains( entity ) ) {
			return EntityInspector.getId( EntityInspector.resolve( app, entity, bif ), entity );
		}
		return write( entity, spec, path );
	}

	/**
	 * Add every plain property (id, columns, version) in place of a {@code "*"} or an empty include list.
	 *
	 * @param record   The entity.
	 * @param includes The includes so far.
	 *
	 * @return The includes with the plain properties first.
	 */
	private static Map<String, MementoSpec.Include> withPlainProperties( EntityRecord record, Map<String, MementoSpec.Include> includes ) {
		Map<String, MementoSpec.Include> result = new java.util.LinkedHashMap<>();
		if ( record.getEntityMeta() != null ) {
			record.getEntityMeta().getIdProperties()
			    .forEach( p -> result.put( p.getName().toLowerCase(), new MementoSpec.Include( p.getName(), null, new java.util.ArrayList<>() ) ) );
			for ( IPropertyMeta p : record.getEntityMeta().getAllPersistentProperties() ) {
				if ( isPlain( p ) ) {
					result.putIfAbsent( p.getName().toLowerCase(), new MementoSpec.Include( p.getName(), null, new java.util.ArrayList<>() ) );
				}
			}
		}
		includes.forEach( ( k, v ) -> {
			if ( !k.equals( "*" ) ) {
				result.put( k, v );
			}
		} );
		return result;
	}

	/**
	 * Whether a property is a plain value (id, column, version or timestamp), not an association or value collection.
	 *
	 * @param property The property.
	 *
	 * @return True for a plain value.
	 */
	public static boolean isPlain( IPropertyMeta property ) {
		return switch ( property.getFieldType() ) {
			case ID, COLUMN, VERSION, TIMESTAMP -> true;
			default -> false;
		};
	}

	/**
	 * Whether an association is a collection.
	 *
	 * @param property The association.
	 *
	 * @return True for one-to-many and many-to-many.
	 */
	public static boolean isToMany( IPropertyMeta property ) {
		return property.getFieldType() == IPropertyMeta.FIELDTYPE.ONE_TO_MANY || property.getFieldType() == IPropertyMeta.FIELDTYPE.MANY_TO_MANY;
	}

	/**
	 * A persistent property by name, ignoring case.
	 *
	 * @param record The entity.
	 * @param name   The name.
	 *
	 * @return The property, or null.
	 */
	public static IPropertyMeta property( EntityRecord record, String name ) {
		if ( record.getEntityMeta() == null ) {
			return null;
		}
		for ( IPropertyMeta p : record.getEntityMeta().getAllPersistentProperties() ) {
			if ( p.getName().equalsIgnoreCase( name ) ) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Whether the entity has a {@code get<Name>()} method.
	 *
	 * @param entity The entity.
	 * @param name   The name, without {@code get}.
	 *
	 * @return True when the getter exists.
	 */
	private static boolean hasGetter( IClassRunnable entity, String name ) {
		return entity.getThisScope().containsKey( Key.of( "get" + name ) );
	}

	/**
	 * Read a value through its getter, else from the variables scope.
	 *
	 * @param entity The entity.
	 * @param name   The property or getter name.
	 *
	 * @return The value.
	 */
	private Object read( IClassRunnable entity, String name ) {
		Key getter = Key.of( "get" + name );
		if ( entity.getThisScope().containsKey( getter ) ) {
			return entity.dereferenceAndInvoke( context, getter, new Object[] {}, false );
		}
		return entity.getVariablesScope().get( Key.of( name ) );
	}

	/**
	 * The BoxLang instance of an entity, facade or lazy reference (loading the reference).
	 *
	 * @param value The value.
	 *
	 * @return The instance.
	 */
	private IClassRunnable runnable( Object value ) {
		if ( value instanceof BoxProxy proxy ) {
			return proxy.getRunnable();
		}
		Object unwrapped = FacadeSupport.unwrapIfFacade( value );
		if ( unwrapped instanceof IClassRunnable runnable ) {
			return runnable;
		}
		// Let resolve() explain what was passed.
		EntityInspector.resolve( app, value, bif );
		throw new IllegalStateException( "unreachable" );
	}
}
