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
package ortus.boxlang.modules.orm.criteria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.modules.orm.memento.EntityMemento;
import ortus.boxlang.modules.orm.memento.IsoDates;
import ortus.boxlang.modules.orm.memento.MementoSpec;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Function;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * {@code asStruct( includes )} and {@code entityLoadAsStruct()}: the structs {@code entityToStruct()} would build, read
 * with projection queries instead of loading entities.
 * <p>
 * The includes (with the entity's {@code this.memento}, read from a prototype instance) become a tree of nodes. The
 * criteria's own query selects the plain values of the root and of every to-one association below it (left joins). Each
 * to-many association is one more query, rooted at the same entity and limited to the root ids just read, that selects
 * the parent's id and the collection's values; its rows are grouped back into their parent structs (in id order). Dates
 * are ISO 8601 strings, nulls become the {@code defaults} entry or an empty string, and mappers run last, as in
 * {@code entityToStruct()}. Getters need an entity: one listed in {@code this.memento} is left out, one the caller asks for is
 * an error.
 */
final class MementoProjection {

	/** How many root ids one collection query filters on. */
	private static final int	ID_CHUNK	= 500;

	/** The operation name for errors. */
	private static final String	OPERATION	= "entityCriteria.asStruct";

	/**
	 * Not instantiable.
	 */
	private MementoProjection() {
	}

	/**
	 * What a node's field is.
	 */
	private enum Kind {
		/** A plain value (or an association id on a type cycle). */
		PLAIN,
		/** A to-one association (a nested struct from the same row). */
		TO_ONE,
		/** A to-many association (filled from its own query). */
		TO_MANY,
		/** A key a mapper computes. */
		COMPUTED
	}

	/**
	 * One key of a struct.
	 *
	 * @param key    The output key.
	 * @param kind   What it is.
	 * @param column The select column (PLAIN), else -1.
	 * @param child  The nested node (TO_ONE, TO_MANY), else null.
	 * @param group  The collection query (TO_MANY), else null.
	 */
	private record Field( Key key, Kind kind, int column, Node child, Group group ) {
	}

	/**
	 * One struct shape: an entity reached by a path.
	 */
	private static final class Node {

		/** The effective memento settings. */
		final MementoSpec.Effective	effective;
		/** The select column of this entity's id. */
		int							idColumn;
		/** The keys, in order. */
		final List<Field>			fields	= new ArrayList<>();

		/**
		 * Create a node.
		 *
		 * @param effective The effective settings.
		 */
		Node( MementoSpec.Effective effective ) {
			this.effective = effective;
		}
	}

	/**
	 * One query: the root query or a collection query, with its select paths (relative to the root entity).
	 */
	private static final class Group {

		/** The select paths. */
		final List<String>	paths	= new ArrayList<>();
		/** Which select paths need inner joins (the collection path of a collection query). */
		final List<Boolean>	inner	= new ArrayList<>();
		/** For a collection query: the path of the parent's id (selected first), else null. */
		final String		parentIdPath;
		/** For a collection query: the collection path, else null. */
		final String		collectionPath;
		/** The node whose rows this query reads. */
		Node				node;

		/**
		 * Create a group.
		 *
		 * @param parentIdPath   The parent's id path, or null for the root query.
		 * @param collectionPath The collection path, or null for the root query.
		 */
		Group( String parentIdPath, String collectionPath ) {
			this.parentIdPath	= parentIdPath;
			this.collectionPath	= collectionPath;
		}

		/**
		 * Add a select path.
		 *
		 * @param path      The path relative to the root entity.
		 * @param innerJoin Whether it needs inner joins.
		 *
		 * @return Its column index.
		 */
		int add( String path, boolean innerJoin ) {
			paths.add( path );
			inner.add( innerJoin );
			return paths.size() - 1;
		}
	}

	/**
	 * Run the criteria and build its structs.
	 *
	 * @param base    The criteria (not changed).
	 * @param context The context.
	 * @param first   The first row, or null.
	 * @param max     The maximum rows, or null.
	 *
	 * @return The structs.
	 */
	static Array list( CriteriaBuilder base, IBoxContext context, Integer first, Integer max ) {
		ORMApp			app			= base.app();
		EntityRecord	rootRecord	= base.record();
		String			rootId		= singleId( rootRecord );
		Group			root		= new Group( null, null );
		root.node = build( context, app, rootRecord, base.rootModel(), base.memento(), "", root, new ArrayList<>( List.of( rootRecord.getEntityName() ) ) );

		// The root query: the criteria's conditions and ordering, with the root group's columns.
		CriteriaBuilder	query		= base.copy();
		List<String>	expressions	= new ArrayList<>();
		for ( int i = 0; i < root.paths.size(); i++ ) {
			expressions.add( query.hqlFor( root.paths.get( i ), false ) );
		}
		List<?>									rows	= query.runCompiled( context, query.compileColumns( expressions, null ), first, max );

		Array									result	= new Array();
		List<Object>							rootIds	= new ArrayList<>();
		Map<Group, Map<Object, List<IStruct>>>	waiting	= new LinkedHashMap<>();
		List<Runnable>							mappers	= new ArrayList<>();
		for ( Object row : rows ) {
			Object[] values = row instanceof Object[] tuple ? tuple : new Object[] { row };
			rootIds.add( values[ root.node.idColumn ] );
			result.add( struct( context, root.node, values, waiting, mappers ) );
		}
		fillCollections( base, context, rootId, rootIds, waiting, mappers );
		// Mappers run last, innermost first, so each sees its complete struct.
		for ( int i = mappers.size() - 1; i >= 0; i-- ) {
			mappers.get( i ).run();
		}
		return result;
	}

	/**
	 * Run the collection queries whose parents were read, adding their rows to the parents (and repeating for collections
	 * inside collections).
	 *
	 * @param base    The criteria (for its entity).
	 * @param context The context.
	 * @param rootId  The root entity's id property.
	 * @param rootIds The root ids read.
	 * @param waiting Parent structs waiting for each collection, by parent id.
	 * @param mappers Mapper steps, in build order.
	 */
	private static void fillCollections( CriteriaBuilder base, IBoxContext context, String rootId, List<Object> rootIds,
	    Map<Group, Map<Object, List<IStruct>>> waiting, List<Runnable> mappers ) {
		while ( !waiting.isEmpty() ) {
			Group									group	= waiting.keySet().iterator().next();
			Map<Object, List<IStruct>>				parents	= waiting.remove( group );
			Map<Group, Map<Object, List<IStruct>>>	nested	= new LinkedHashMap<>();
			if ( rootId == null ) {
				throw new ORMException( ORMErrorType.ARGUMENT,
				    OPERATION + "() cannot read a collection of [" + base.record().getEntityName() + "], which has a composite id.",
				    "Leave the collection out, or use entityToStruct() on the loaded entities." );
			}
			for ( int from = 0; from < rootIds.size(); from += ID_CHUNK ) {
				List<Object>	chunk	= rootIds.subList( from, Math.min( rootIds.size(), from + ID_CHUNK ) );
				CriteriaBuilder	sub		= base.fresh();
				sub.in( rootId, Array.fromList( chunk ), false );
				List<String> expressions = new ArrayList<>();
				expressions.add( sub.hqlFor( group.parentIdPath, true ) );
				String childId = null;
				for ( int i = 0; i < group.paths.size(); i++ ) {
					String hql = sub.hqlFor( group.paths.get( i ), group.inner.get( i ) );
					expressions.add( hql );
					if ( i == group.node.idColumn ) {
						childId = hql;
					}
				}
				List<?> rows = sub.runCompiled( context, sub.compileColumns( expressions, childId ), null, null );
				for ( Object row : rows ) {
					Object[]		values	= ( Object[] ) row;
					Object[]		shifted	= java.util.Arrays.copyOfRange( values, 1, values.length );
					List<IStruct>	targets	= parents.get( values[ 0 ] );
					if ( targets == null || shifted[ group.node.idColumn ] == null ) {
						continue;
					}
					for ( IStruct target : targets ) {
						IStruct child = struct( context, group.node, shifted, nested, mappers );
						( ( Array ) target.get( fieldKey( target, group ) ) ).add( child );
					}
				}
			}
			nested.forEach( ( g, p ) -> waiting.merge( g, p, ( a, b ) -> {
				b.forEach( ( k, v ) -> a.computeIfAbsent( k, x -> new ArrayList<>() ).addAll( v ) );
				return a;
			} ) );
		}
	}

	/**
	 * The key of the collection a group fills, stored on the parent struct while it waits.
	 *
	 * @param target The parent struct.
	 * @param group  The collection query.
	 *
	 * @return The key.
	 */
	private static Key fieldKey( IStruct target, Group group ) {
		return ( Key ) target.get( Key.of( "__bxorm_collection_" + System.identityHashCode( group ) ) );
	}

	/**
	 * Build one struct from a row.
	 *
	 * @param context The context (for mappers).
	 * @param node    The shape.
	 * @param values  The row.
	 * @param waiting Parent structs waiting for collections.
	 * @param mappers Mapper steps to run at the end.
	 *
	 * @return The struct.
	 */
	private static IStruct struct( IBoxContext context, Node node, Object[] values, Map<Group, Map<Object, List<IStruct>>> waiting,
	    List<Runnable> mappers ) {
		IStruct result = new Struct( IStruct.TYPES.LINKED );
		for ( Field field : node.fields ) {
			Object value = switch ( field.kind() ) {
				case PLAIN -> IsoDates.convert( values[ field.column() ] );
				case TO_ONE -> values[ field.child().idColumn ] == null ? null : struct( context, field.child(), values, waiting, mappers );
				case TO_MANY -> {
					Object parentId = values[ node.idColumn ];
					if ( parentId != null ) {
						waiting.computeIfAbsent( field.group(), g -> new LinkedHashMap<>() ).computeIfAbsent( parentId, k -> new ArrayList<>() ).add( result );
						result.put( Key.of( "__bxorm_collection_" + System.identityHashCode( field.group() ) ), field.key() );
					}
					yield new Array();
				}
				case COMPUTED -> null;
			};
			if ( value == null ) {
				Object fallback = node.effective.defaults().get( field.key() );
				value = fallback != null ? fallback : "";
			}
			result.put( field.key(), value );
		}
		mappers.add( () -> {
			result.keySet().removeIf( k -> k.getName().startsWith( "__bxorm_collection_" ) );
			for ( Key key : new ArrayList<>( result.keySet() ) ) {
				if ( node.effective.mappers().get( key ) instanceof Function mapper ) {
					result.put( key, context.invokeFunction( mapper, new Object[] { result.get( key ), result } ) );
				}
			}
		} );
		return result;
	}

	/**
	 * Build the node for an entity reached by {@code prefix}.
	 *
	 * @param context  The context.
	 * @param app      The ORM application.
	 * @param record   The entity.
	 * @param model    The entity's Hibernate model.
	 * @param spec     What to include.
	 * @param prefix   The path from the root entity ({@code ""}, {@code "manufacturer."}, ...).
	 * @param group    The query its values are read by.
	 * @param typePath The entity names above it (to stop default expansion cycles).
	 *
	 * @return The node.
	 */
	private static Node build( IBoxContext context, ORMApp app, EntityRecord record, EntityModel model, MementoSpec spec, String prefix, Group group,
	    List<String> typePath ) {
		IClassRunnable	prototype	= app.prototype( context, record.getEntityName() );
		Node			node		= new Node( spec.resolve( prototype ) );
		String			id			= singleId( record );
		if ( id == null ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    OPERATION + "() needs entities with a single id, but [" + record.getEntityName() + "] has a composite id.",
			    "Use entityToStruct() on the loaded entities instead." );
		}
		node.idColumn = group.add( prefix + id, group.collectionPath != null && prefix.equals( group.collectionPath + "." ) );
		Map<String, MementoSpec.Include> includes = node.effective.includes();
		if ( includes.isEmpty() || includes.containsKey( "*" ) ) {
			includes = withPlain( record, includes );
		}
		for ( MementoSpec.Include include : includes.values() ) {
			if ( include.name().equals( "*" ) || node.effective.excludes( include.name() ) ) {
				continue;
			}
			IPropertyMeta property = EntityMemento.property( record, include.name() );
			if ( property == null ) {
				Key key = Key.of( include.outputName( null ) );
				if ( node.effective.mappers().containsKey( Key.of( include.name() ) ) ) {
					node.fields.add( new Field( key, Kind.COMPUTED, -1, null, null ) );
					continue;
				}
				if ( prototype.getThisScope().containsKey( Key.of( "get" + include.name() ) ) ) {
					// A getter needs a loaded entity: skip one that comes from this.memento, refuse one the caller asked for.
					boolean asked = spec.includes().stream().anyMatch( i -> i.split( "[.:]" )[ 0 ].trim().equalsIgnoreCase( include.name() ) );
					if ( !asked ) {
						continue;
					}
					throw new ORMException( ORMErrorType.ARGUMENT,
					    OPERATION + "() cannot include [" + include.name() + "] of [" + record.getEntityName()
					        + "]: it is a getter, and structs read without entities cannot call it.",
					    "Use entityToStruct() on loaded entities, or compute the key with a mapper." );
				}
				throw ORMErrors.propertyNotFound( record.getEntityName(), include.name(), app.getPropertyNames( record.getEntityName() ), OPERATION );
			}
			Key key = Key.of( include.outputName( property.getName() ) );
			if ( EntityMemento.isPlain( property ) ) {
				node.fields.add( new Field( key, Kind.PLAIN, group.add( prefix + property.getName(), false ), null, null ) );
				continue;
			}
			if ( !property.isAssociationType() ) {
				throw new ORMException( ORMErrorType.ARGUMENT,
				    OPERATION + "() cannot include [" + property.getName() + "] of [" + record.getEntityName() + "], a value collection.",
				    "Use entityToStruct() on the loaded entities for value collections." );
			}
			EntityModel		targetModel	= model.find( property.getName() ).target();
			EntityRecord	target		= app.lookupEntity( targetModel.name(), true );
			MementoSpec		childSpec	= spec.child( include, node.effective.nestedExcludes( include.name() ) );
			boolean			cycle		= typePath.contains( target.getEntityName() ) && include.children().isEmpty();
			if ( EntityMemento.isToMany( property ) ) {
				if ( cycle ) {
					continue; // a collection of an entity type already above, pulled in only by defaults
				}
				Group			collection	= new Group( prefix + id, prefix + property.getName() );
				List<String>	path		= new ArrayList<>( typePath );
				path.add( target.getEntityName() );
				collection.node = build( context, app, target, targetModel, childSpec, prefix + property.getName() + ".", collection, path );
				node.fields.add( new Field( key, Kind.TO_MANY, -1, collection.node, collection ) );
			} else if ( cycle ) {
				// Pulled in only by defaults, and the type is already above: its id ends the cycle.
				node.fields.add( new Field( key, Kind.PLAIN, group.add( prefix + property.getName() + "." + singleId( target ), false ), null, null ) );
			} else {
				List<String> path = new ArrayList<>( typePath );
				path.add( target.getEntityName() );
				node.fields.add( new Field( key, Kind.TO_ONE, -1,
				    build( context, app, target, targetModel, childSpec, prefix + property.getName() + ".", group, path ), null ) );
			}
		}
		return node;
	}

	/**
	 * The plain properties in place of {@code "*"} or an empty include list.
	 *
	 * @param record   The entity.
	 * @param includes The includes.
	 *
	 * @return The includes with the plain properties first.
	 */
	private static Map<String, MementoSpec.Include> withPlain( EntityRecord record, Map<String, MementoSpec.Include> includes ) {
		Map<String, MementoSpec.Include> result = new LinkedHashMap<>();
		if ( record.getEntityMeta() != null ) {
			record.getEntityMeta().getIdProperties()
			    .forEach( p -> result.put( p.getName().toLowerCase(), new MementoSpec.Include( p.getName(), null, new ArrayList<>() ) ) );
			for ( IPropertyMeta p : record.getEntityMeta().getAllPersistentProperties() ) {
				if ( EntityMemento.isPlain( p ) ) {
					result.putIfAbsent( p.getName().toLowerCase(), new MementoSpec.Include( p.getName(), null, new ArrayList<>() ) );
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
	 * The name of an entity's single id property.
	 *
	 * @param record The entity.
	 *
	 * @return The id property name, or null for a composite id.
	 */
	private static String singleId( EntityRecord record ) {
		if ( record.getEntityMeta() == null || record.getEntityMeta().getIdProperties().size() != 1 ) {
			return null;
		}
		return record.getEntityMeta().getIdProperties().iterator().next().getName();
	}
}
