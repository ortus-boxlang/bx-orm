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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import ortus.boxlang.modules.orm.EntityInspector;
import ortus.boxlang.modules.orm.HQLQuery;
import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.criteria.HqlParts.Frag;
import ortus.boxlang.modules.orm.criteria.HqlParts.Group;
import ortus.boxlang.modules.orm.criteria.HqlParts.Node;
import ortus.boxlang.modules.orm.criteria.HqlParts.Not;
import ortus.boxlang.modules.orm.criteria.HqlParts.Param;
import ortus.boxlang.modules.orm.criteria.HqlParts.RenderContext;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.dynamic.IReferenceable;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Function;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Query;
import ortus.boxlang.runtime.types.QueryColumnType;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.util.ListUtil;

/**
 * The fluent query builder returned by {@code entityCriteria( entityName )}.
 * <p>
 * The builder records the conditions, joins, projections, ordering and options it is given, then compiles them to one
 * HQL query when a terminal method runs ({@code list()}, {@code count()}, {@code get()}, {@code paginate()}, ...).
 * Building methods change this builder and return it, so they chain; terminal methods never change it, so the same
 * builder can run several queries; {@code copy()} branches a builder.
 * <p>
 * Property paths are checked against the entity while the criteria is built, so a typo fails at once with "Did you
 * mean". Dotted paths ({@code "manufacturer.name"}) join their associations automatically: conditions use an inner
 * join (a left join inside {@code anyOf()} and {@code not()}, where an inner join would drop rows), ordering and
 * projections a left join, and each path is joined once and reused. The root entity's alias is {@code this}.
 * <p>
 * BoxLang calls reach the builder through {@link IReferenceable}: {@link CriteriaMethods} maps every method name and
 * alias (case-insensitive, positional or named arguments) to the Java method that implements it.
 */
public final class CriteriaBuilder implements IReferenceable {

	/**
	 * What {@code list()} returns.
	 */
	enum Shape {
		/** Entity instances (or projection values). */
		ENTITY,
		/** One struct per row. */
		STRUCT,
		/** A BoxLang query. */
		QUERY,
		/** A Java stream of entity instances (or projection values). */
		STREAM
	}

	/**
	 * A join type and its HQL keyword.
	 */
	enum JoinKind {

		/** Inner join. */
		INNER( "inner join" ),
		/** Left outer join. */
		LEFT( "left join" ),
		/** Right outer join. */
		RIGHT( "right join" ),
		/** Full outer join. */
		FULL( "full join" );

		/** The HQL keyword. */
		final String hql;

		/**
		 * Bind a join type to its keyword.
		 *
		 * @param hql The HQL keyword.
		 */
		JoinKind( String hql ) {
			this.hql = hql;
		}

		/**
		 * Parse a join type: {@code inner}, {@code left}, {@code right}, {@code full} (also {@code left outer}, ...) or
		 * the cborm constants 0 (inner), 1 (left), 2 (right) and 4 (full).
		 *
		 * @param value The join type; null or empty means the default.
		 * @param def   The default.
		 *
		 * @return The join type.
		 */
		static JoinKind parse( Object value, JoinKind def ) {
			if ( value == null || value.toString().isBlank() ) {
				return def;
			}
			String text = value.toString().trim().toLowerCase().replace( "_", " " ).replace( " outer", "" ).replace( " join", "" );
			return switch ( text ) {
				case "inner", "0" -> INNER;
				case "left", "1" -> LEFT;
				case "right", "2" -> RIGHT;
				case "full", "4" -> FULL;
				default -> throw new ORMException( ORMErrorType.ARGUMENT, "Unknown join type [" + value + "].",
				    "Use inner, left, right or full." );
			};
		}
	}

	/**
	 * An alias the criteria can navigate from: the root entity, a joined association or a subquery root.
	 *
	 * @param hql   The alias used in the generated HQL.
	 * @param model The entity it stands for.
	 */
	record Alias( String hql, EntityModel model ) {
	}

	/**
	 * One join of the generated HQL.
	 */
	static final class Join {

		/** The alias used in the generated HQL. */
		final String		hql;
		/** The alias it joins from. */
		final String		parentHql;
		/** The association attribute. */
		final String		attribute;
		/** The joined entity. */
		final EntityModel	model;
		/** Whether the association is a collection. */
		final boolean		plural;
		/** The join type. */
		JoinKind			kind;
		/** Whether the association is fetched with the root ({@code fetch()}). */
		boolean				fetch;
		/** Whether a condition, order or projection uses this join (a fetch-only join is left out of counts). */
		boolean				referenced;

		/**
		 * Create a join.
		 *
		 * @param hql       The HQL alias.
		 * @param parentHql The alias it joins from.
		 * @param attribute The association attribute.
		 * @param model     The joined entity.
		 * @param plural    Whether the association is a collection.
		 * @param kind      The join type.
		 */
		Join( String hql, String parentHql, String attribute, EntityModel model, boolean plural, JoinKind kind ) {
			this.hql		= hql;
			this.parentHql	= parentHql;
			this.attribute	= attribute;
			this.model		= model;
			this.plural		= plural;
			this.kind		= kind;
		}

		/**
		 * A copy that can be changed independently.
		 *
		 * @return The copy.
		 */
		Join copy() {
			Join j = new Join( hql, parentHql, attribute, model, plural, kind );
			j.fetch			= fetch;
			j.referenced	= referenced;
			return j;
		}
	}

	/**
	 * One ordering.
	 *
	 * @param expr The HQL expression (already wrapped in {@code lower()} for a case-insensitive text sort).
	 * @param asc  True for ascending.
	 */
	record Order( String expr, boolean asc ) {
	}

	/**
	 * One projected column.
	 *
	 * @param function The projection: {@code property}, {@code group}, {@code sum}, {@code avg}, {@code min},
	 *                 {@code max}, {@code count}, {@code countDistinct}, {@code rowCount} or {@code id}.
	 * @param expr     The HQL expression.
	 * @param alias    The column name in structs and queries.
	 */
	record Projection( String function, String expr, String alias ) {

		/**
		 * The HQL of this column in the select list.
		 *
		 * @return The select expression.
		 */
		String select() {
			return switch ( function ) {
				case "sum", "avg", "min", "max", "count" -> function + "(" + expr + ")";
				case "countDistinct" -> "count(distinct " + expr + ")";
				case "rowCount" -> "count(*)";
				default -> expr;
			};
		}
	}

	/**
	 * A compiled query.
	 *
	 * @param hql     The HQL.
	 * @param values  The bound values, one per {@code ?n}.
	 * @param columns The column names of struct/query results (empty for entity results).
	 */
	record Compiled( String hql, List<Object> values, List<String> columns ) {
	}

	/**
	 * What a compiled query selects.
	 */
	enum Mode {
		/** Rows (entities, projections or struct columns). */
		LIST,
		/** The number of root entities. */
		COUNT,
		/** Whether any row matches. */
		EXISTS,
		/** One expression per row (pluck, sum, avg, min, max). */
		EXPRESSION
	}

	/** The operation name used in error messages. */
	static final String						OPERATION	= "entityCriteria";

	/** The ORM application. */
	private final ORMApp					app;
	/** The root entity. */
	private final EntityRecord				record;
	/** The root entity's datasource name. */
	private final String					datasource;
	/** The session factory the entity is mapped in. */
	private final SessionFactoryImplementor	factory;
	/** The enclosing criteria, for a subquery (null otherwise). */
	private final CriteriaBuilder			outer;
	/** Alias counter shared by a criteria and its subqueries, so generated aliases never collide. */
	private final int[]						counter;
	/** The root alias. */
	private final Alias						root;

	/** User aliases (lower-cased) to their HQL aliases. */
	private Map<String, Alias>				aliases;
	/** Joins by {@code parentAlias.attribute}. */
	private LinkedHashMap<String, Join>		joins;
	/** The top-level {@code and} group. */
	private Group							where;
	/** The group new conditions go into (top of the stack). */
	private Deque<Group>					groups;
	/** How many {@code anyOf()}/{@code not()} groups enclose the current call (paths inside use left joins). */
	private int								optionalDepth;
	/** The alias unqualified paths start from ({@code with{Association}()} changes it). */
	private Alias							current;
	/** Projected columns. */
	private List<Projection>				projections;
	/** Whether rows are distinct. */
	private boolean							distinct;
	/** What {@code list()} returns. */
	private Shape							shape;
	/** Orderings. */
	private List<Order>						orders;
	/** First row (0-based), or null. */
	private Integer							firstResult;
	/** Maximum rows, or null. */
	private Integer							maxResults;
	/** Query options: cacheable, cacheName, timeout, readOnly, fetchSize, comment, hints. */
	private IStruct							options;
	/** The recorded calls, for {@code toString()}. */
	private List<String>					steps;
	/** The closure nesting of the call being recorded (for indentation). */
	private int								stepDepth;
	/** The context of the last call, so {@code toString()} (and so {@code writeDump()}) can show the SQL. */
	private transient IBoxContext			lastContext;

	/**
	 * Create a criteria for an entity.
	 *
	 * @param app     The ORM application.
	 * @param record  The entity.
	 * @param factory The session factory the entity is mapped in.
	 */
	public CriteriaBuilder( ORMApp app, EntityRecord record, SessionFactoryImplementor factory ) {
		this( app, record, factory, null, new int[] { 0 }, "this" );
	}

	/**
	 * Create a criteria or a subquery.
	 *
	 * @param app       The ORM application.
	 * @param record    The root entity.
	 * @param factory   The session factory the entity is mapped in.
	 * @param outer     The enclosing criteria (null for a top-level criteria).
	 * @param counter   The shared alias counter.
	 * @param userAlias The alias the developer uses for the root.
	 */
	private CriteriaBuilder( ORMApp app, EntityRecord record, SessionFactoryImplementor factory, CriteriaBuilder outer, int[] counter,
	    String userAlias ) {
		this.app		= app;
		this.record		= record;
		this.datasource	= record.getDatasource() == null ? null : record.getDatasource().getName();
		this.factory	= factory;
		this.outer		= outer;
		this.counter	= counter;
		this.root		= new Alias( outer == null ? "bx_this" : "bx_s" + ( ++counter[ 0 ] ), EntityModel.of( factory, record.getEntityName() ) );
		this.aliases	= new LinkedHashMap<>();
		this.aliases.put( userAlias.toLowerCase(), root );
		this.joins	= new LinkedHashMap<>();
		this.where	= new Group( false );
		this.groups	= new ArrayDeque<>();
		this.groups.push( where );
		this.current		= root;
		this.projections	= new ArrayList<>();
		this.shape			= Shape.ENTITY;
		this.orders			= new ArrayList<>();
		this.options		= new Struct();
		this.steps			= new ArrayList<>();
		this.steps.add( ( outer == null ? "entityCriteria( " : "subquery( " ) + CriteriaMethods.format( record.getEntityName() )
		    + ( outer == null ? "" : ", " + CriteriaMethods.format( userAlias ) ) + " )" );
	}

	/* ============================================================================================================= */
	/* IReferenceable */
	/* ============================================================================================================= */

	/**
	 * Read a property: the cborm join-type constants {@code INNER_JOIN} (0), {@code LEFT_JOIN} (1), {@code RIGHT_JOIN}
	 * (2) and {@code FULL_JOIN} (4).
	 *
	 * @param context The context.
	 * @param name    The property name.
	 * @param safe    Whether a missing property returns null instead of failing.
	 *
	 * @return The constant, or null.
	 */
	@Override
	public Object dereference( IBoxContext context, Key name, Boolean safe ) {
		return switch ( name.getName().toUpperCase() ) {
			case "INNER_JOIN" -> 0;
			case "LEFT_JOIN", "LEFT_OUTER_JOIN" -> 1;
			case "RIGHT_JOIN", "RIGHT_OUTER_JOIN" -> 2;
			case "FULL_JOIN", "FULL_OUTER_JOIN" -> 4;
			default -> {
				if ( Boolean.TRUE.equals( safe ) ) {
					yield null;
				}
				throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria has no property [" + name.getName() + "].",
				    "Call its methods instead, e.g. c.isEq( \"name\", value ).list()." );
			}
		};
	}

	/**
	 * Call a method with positional arguments.
	 *
	 * @param context   The calling context.
	 * @param name      The method name.
	 * @param arguments The arguments.
	 * @param safe      Unused.
	 *
	 * @return The method's result.
	 */
	@Override
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Object[] arguments, Boolean safe ) {
		return CriteriaMethods.invoke( this, context, name.getName(), arguments, null );
	}

	/**
	 * Call a method with named arguments.
	 *
	 * @param context   The calling context.
	 * @param name      The method name.
	 * @param arguments The arguments by name.
	 * @param safe      Unused.
	 *
	 * @return The method's result.
	 */
	@Override
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Map<Key, Object> arguments, Boolean safe ) {
		return CriteriaMethods.invoke( this, context, name.getName(), null, arguments );
	}

	/**
	 * Properties cannot be assigned.
	 *
	 * @param context The context.
	 * @param name    The property name.
	 * @param value   The value.
	 *
	 * @return Never returns.
	 */
	@Override
	public Object assign( IBoxContext context, Key name, Object value ) {
		throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria properties cannot be set ([" + name.getName() + "]).",
		    "Use its methods, e.g. c.maxResults( 10 )." );
	}

	/* ============================================================================================================= */
	/* Bookkeeping */
	/* ============================================================================================================= */

	/**
	 * The root entity's name.
	 *
	 * @return The entity name.
	 */
	public String getEntityName() {
		return record.getEntityName();
	}

	/**
	 * Whether this builder is a subquery.
	 *
	 * @return True for a subquery.
	 */
	boolean isSubquery() {
		return outer != null;
	}

	/**
	 * Record a call for {@code toString()}.
	 *
	 * @param step The call, e.g. {@code .isEq( "make", "Ford" )}.
	 */
	void recordStep( String step ) {
		steps.add( "  ".repeat( stepDepth ) + step );
	}

	/**
	 * Remember the context of the latest call (for the SQL in {@code toString()}).
	 *
	 * @param context The calling context.
	 */
	void rememberContext( IBoxContext context ) {
		this.lastContext = context;
	}

	/**
	 * How many calls are recorded.
	 *
	 * @return The count.
	 */
	int stepCount() {
		return steps.size();
	}

	/**
	 * Record a call before the calls its closures made.
	 *
	 * @param index Where to insert it.
	 * @param step  The call.
	 */
	void insertStep( int index, String step ) {
		steps.add( Math.min( index, steps.size() ), "  ".repeat( stepDepth ) + step );
	}

	/**
	 * Indent the calls made inside a closure.
	 */
	void enterStep() {
		stepDepth++;
	}

	/**
	 * Stop indenting after a closure returns.
	 */
	void leaveStep() {
		stepDepth--;
	}

	/**
	 * Add a condition to the current group.
	 *
	 * @param node The condition.
	 *
	 * @return This builder.
	 */
	private CriteriaBuilder add( Node node ) {
		groups.peek().add( node );
		return this;
	}

	/**
	 * Run a callback with a group as the target of new conditions.
	 *
	 * @param context  The context.
	 * @param group    The group.
	 * @param optional Whether rows may lack the joined associations (use left joins).
	 * @param callback The callback, called with this builder.
	 */
	private void inGroup( IBoxContext context, Group group, boolean optional, Object callback ) {
		groups.push( group );
		if ( optional ) {
			optionalDepth++;
		}
		try {
			call( context, callback, this );
		} finally {
			groups.pop();
			if ( optional ) {
				optionalDepth--;
			}
		}
	}

	/**
	 * Negate the conditions a callback adds.
	 *
	 * @param context  The context.
	 * @param callback Adds conditions to this builder.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder negate( IBoxContext context, Runnable callback ) {
		Group group = new Group( false );
		groups.push( group );
		optionalDepth++;
		try {
			callback.run();
		} finally {
			groups.pop();
			optionalDepth--;
		}
		return add( new Not( group ) );
	}

	/**
	 * Call a BoxLang closure.
	 *
	 * @param context  The context.
	 * @param callback The closure or function.
	 * @param args     Its arguments.
	 *
	 * @return What it returned.
	 */
	static Object call( IBoxContext context, Object callback, Object... args ) {
		if ( ! ( callback instanceof Function ) ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    "entityCriteria expected a closure but received " + ( callback == null ? "null" : callback.getClass().getSimpleName() ) + ".",
			    "Pass a closure, e.g. c.anyOf( ( c ) => c.isEq( \"a\", 1 ).isEq( \"b\", 2 ) )." );
		}
		return context.invokeFunction( callback, args );
	}

	/**
	 * Announce a criteria interception point when anyone listens.
	 *
	 * @param point The interception point.
	 * @param data  Builds the event data.
	 */
	private static void announce( Key point, Supplier<IStruct> data ) {
		var interceptors = BoxRuntime.getInstance().getInterceptorService();
		if ( interceptors.hasState( point ) ) {
			interceptors.announce( point, data );
		}
	}

	/**
	 * Announce {@code onCriteriaBuilderAddition} for an added condition.
	 *
	 * @param type The method that added it.
	 */
	void announceAddition( String type ) {
		announce( ORMKeys.EVENT_CRITERIA_ADDITION, () -> Struct.of( Key.type, type, Key.of( "criteriaBuilder" ), this ) );
	}

	/* ============================================================================================================= */
	/* Paths and joins */
	/* ============================================================================================================= */

	/**
	 * A resolved property path.
	 *
	 * @param hql   The HQL expression.
	 * @param attr  The last attribute (null when the path is an alias or an id() reference).
	 * @param owner The model that owns the last attribute.
	 */
	record Path( String hql, EntityModel.Attr attr, EntityModel owner ) {
	}

	/**
	 * Find a user alias here or in an enclosing criteria.
	 *
	 * @param name The alias, any casing.
	 *
	 * @return The alias, or null.
	 */
	private Alias findAlias( String name ) {
		for ( CriteriaBuilder c = this; c != null; c = c.outer ) {
			Alias a = c.aliases.get( name.toLowerCase() );
			if ( a != null ) {
				return a;
			}
		}
		return null;
	}

	/**
	 * Resolve a property path to HQL, joining its associations.
	 *
	 * @param path        The path, e.g. {@code name}, {@code manufacturer.name}, {@code this.id} or {@code alias.prop}.
	 * @param defaultKind The join type for joins this path creates.
	 *
	 * @return The resolved path.
	 */
	Path resolve( String path, JoinKind defaultKind ) {
		if ( path == null || path.isBlank() ) {
			throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria needs a property name but received an empty one.",
			    "Pass the property, e.g. c.isEq( \"name\", value )." );
		}
		JoinKind	kind		= optionalDepth > 0 && defaultKind == JoinKind.INNER ? JoinKind.LEFT : defaultKind;
		String[]	segments	= path.trim().split( "\\." );
		Alias		start		= current;
		int			index		= 0;
		Alias		named		= findAlias( segments[ 0 ] );
		if ( named != null ) {
			start	= named;
			index	= 1;
			if ( segments.length == 1 ) {
				return new Path( named.hql(), null, named.model() );
			}
		}
		String		expr	= start.hql();
		EntityModel	model	= start.model();
		String		joinAt	= start.hql();
		for ( int i = index; i < segments.length; i++ ) {
			String				segment	= segments[ i ];
			boolean				last	= i == segments.length - 1;
			EntityModel.Attr	attr	= model.find( segment );
			if ( attr == null ) {
				if ( last && segment.equalsIgnoreCase( "id" ) && model.isEntity() ) {
					String idName = model.singleIdName();
					return new Path( idName != null ? expr + "." + idName : "id(" + expr + ")", null, model );
				}
				throw ORMErrors.propertyNotFound( model.name(), segment, model.attributeNames(), OPERATION );
			}
			if ( last ) {
				return new Path( expr + "." + attr.name(), attr, model );
			}
			if ( attr.kind() == EntityModel.Kind.TO_ONE && i == segments.length - 2 ) {
				// "manufacturer.id": the foreign key already holds the id, so no join is needed.
				String	idName	= attr.target().singleIdName();
				String	lastSeg	= segments[ i + 1 ];
				if ( idName != null && ( lastSeg.equalsIgnoreCase( idName ) || ( lastSeg.equalsIgnoreCase( "id" ) && attr.target().find( "id" ) == null ) ) ) {
					return new Path( expr + "." + attr.name() + "." + idName, attr.target().find( idName ), attr.target() );
				}
			}
			switch ( attr.kind() ) {
				case TO_ONE, TO_MANY -> {
					Join join = join( joinAt, attr, kind );
					join.referenced	= true;
					expr			= join.hql;
					joinAt			= join.hql;
					model			= join.model;
				}
				case COMPONENT -> {
					expr	= expr + "." + attr.name();
					model	= attr.target();
				}
				default -> throw new ORMException( ORMErrorType.PROPERTY_UNKNOWN,
				    "[" + attr.name() + "] on [" + model.name() + "] is a value, not an association, so the path [" + path + "] cannot continue past it.",
				    "Use [" + attr.name() + "] on its own, or navigate an association (" + String.join( ", ", associationNames( model ) ) + ")." );
			}
		}
		throw new IllegalStateException( "unreachable" );
	}

	/**
	 * The association names of a model, for error messages.
	 *
	 * @param model The model.
	 *
	 * @return The association names.
	 */
	private static List<String> associationNames( EntityModel model ) {
		List<String> names = new ArrayList<>();
		for ( String n : model.attributeNames() ) {
			if ( model.find( n ).isAssociation() ) {
				names.add( n );
			}
		}
		return names;
	}

	/**
	 * Find or create the join of an association.
	 *
	 * @param parentHql The alias it joins from.
	 * @param attr      The association.
	 * @param kind      The join type for a new join.
	 *
	 * @return The join.
	 */
	private Join join( String parentHql, EntityModel.Attr attr, JoinKind kind ) {
		String	key		= parentHql + "." + attr.name();
		Join	join	= joins.get( key );
		if ( join == null ) {
			join = new Join( "bx_j" + ( ++counter[ 0 ] ), parentHql, attr.name(), attr.target(), attr.kind() == EntityModel.Kind.TO_MANY, kind );
			joins.put( key, join );
		}
		return join;
	}

	/**
	 * Join every association of a path (all segments must be associations).
	 *
	 * @param path The association path, e.g. {@code manufacturer} or {@code vehicles.features}.
	 * @param kind The join type.
	 *
	 * @return The last join.
	 */
	private Join joinPath( String path, JoinKind kind ) {
		String[]	segments	= path.trim().split( "\\." );
		Alias		start		= current;
		int			index		= 0;
		Alias		named		= findAlias( segments[ 0 ] );
		if ( named != null && segments.length > 1 ) {
			start	= named;
			index	= 1;
		}
		String		at		= start.hql();
		EntityModel	model	= start.model();
		Join		join	= null;
		for ( int i = index; i < segments.length; i++ ) {
			EntityModel.Attr attr = model.find( segments[ i ] );
			if ( attr == null ) {
				throw ORMErrors.propertyNotFound( model.name(), segments[ i ], model.attributeNames(), OPERATION );
			}
			if ( !attr.isAssociation() ) {
				throw new ORMException( ORMErrorType.PROPERTY_UNKNOWN,
				    "[" + attr.name() + "] on [" + model.name() + "] is not an association, so it cannot be joined.",
				    "Associations of [" + model.name() + "]: " + String.join( ", ", associationNames( model ) ) + "." );
			}
			join	= join( at, attr, kind );
			at		= join.hql;
			model	= join.model;
		}
		return join;
	}

	/**
	 * Resolve a path for ordering or projecting (left joins).
	 *
	 * @param path The property path.
	 *
	 * @return The HQL expression.
	 */
	private String selectPath( String path ) {
		return resolve( path, JoinKind.LEFT ).hql();
	}

	/**
	 * Resolve a path for a condition (inner joins, left joins inside anyOf/not).
	 *
	 * @param path The property path.
	 *
	 * @return The HQL expression.
	 */
	private String wherePath( String path ) {
		return resolve( path, JoinKind.INNER ).hql();
	}

	/* ============================================================================================================= */
	/* Conditions */
	/* ============================================================================================================= */

	/**
	 * Compare a property with a value.
	 *
	 * @param property The property path.
	 * @param operator The HQL operator, e.g. {@code =}.
	 * @param value    The value (null turns {@code =} into {@code is null} and {@code <>} into {@code is not null}).
	 *
	 * @return This builder.
	 */
	CriteriaBuilder compare( String property, String operator, Object value ) {
		String expr = wherePath( property );
		if ( value == null ) {
			if ( operator.equals( "=" ) ) {
				return add( Frag.of( expr, " is null" ) );
			}
			if ( operator.equals( "<>" ) ) {
				return add( Frag.of( expr, " is not null" ) );
			}
			throw new ORMException( ORMErrorType.ARGUMENT, "entityCriteria cannot compare [" + property + "] " + operator + " null.",
			    "Use isNull() or isNotNull() for null checks." );
		}
		if ( value instanceof CriteriaBuilder sub ) {
			return add( Frag.of( expr, " " + operator + " ", sub ) );
		}
		return add( Frag.of( expr, " " + operator + " ", new Param( value ) ) );
	}

	/**
	 * {@code property like value}; the value carries its own wildcards.
	 *
	 * @param property   The property path.
	 * @param value      The pattern, e.g. {@code "Ford%"}.
	 * @param ignoreCase True to compare lower-cased.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder like( String property, Object value, boolean ignoreCase ) {
		String expr = wherePath( property );
		if ( ignoreCase ) {
			return add( Frag.of( "lower(", expr, ") like lower(", new Param( StringCaster.cast( value ) ), ")" ) );
		}
		return add( Frag.of( expr, " like ", new Param( StringCaster.cast( value ) ) ) );
	}

	/**
	 * {@code property between min and max}.
	 *
	 * @param property The property path.
	 * @param min      The lower bound.
	 * @param max      The upper bound.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder between( String property, Object min, Object max ) {
		return add( Frag.of( wherePath( property ), " between ", new Param( min ), " and ", new Param( max ) ) );
	}

	/**
	 * {@code property in (values)}.
	 *
	 * @param property The property path.
	 * @param values   An array, a comma-separated list, or a subquery.
	 * @param negated  True for {@code not in}.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder in( String property, Object values, boolean negated ) {
		String	expr	= wherePath( property );
		String	keyword	= negated ? " not in " : " in ";
		if ( values instanceof CriteriaBuilder sub ) {
			return add( Frag.of( expr, keyword, sub ) );
		}
		List<Object> items = toList( values );
		if ( items.isEmpty() ) {
			// "in ()" is invalid SQL; an empty list matches nothing (and "not in" everything).
			return add( Frag.of( negated ? "1 = 1" : "1 = 0" ) );
		}
		List<Object> parts = new ArrayList<>();
		parts.add( expr + keyword + "(" );
		for ( int i = 0; i < items.size(); i++ ) {
			if ( i > 0 ) {
				parts.add( ", " );
			}
			parts.add( new Param( items.get( i ) ) );
		}
		parts.add( ")" );
		return add( new Frag( parts ) );
	}

	/**
	 * Turn an in-list argument into values.
	 *
	 * @param values An array, a Java list, or a comma-separated string.
	 *
	 * @return The values.
	 */
	private static List<Object> toList( Object values ) {
		if ( values instanceof List<?> list ) {
			return new ArrayList<>( list );
		}
		if ( values instanceof Object[] array ) {
			return new ArrayList<>( List.of( array ) );
		}
		if ( values instanceof String text ) {
			return new ArrayList<>( ListUtil.asList( text, "," ).stream().map( v -> ( Object ) StringCaster.cast( v ).trim() ).toList() );
		}
		List<Object> single = new ArrayList<>();
		single.add( values );
		return single;
	}

	/**
	 * A condition with no value, e.g. {@code is null}.
	 *
	 * @param property The property path.
	 * @param suffix   The HQL after the path, e.g. {@code " is null"}.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder unary( String property, String suffix ) {
		return add( Frag.of( wherePath( property ), suffix ) );
	}

	/**
	 * A condition on a collection property that must not be joined ({@code is empty}, {@code size()}).
	 *
	 * @param property The collection property path.
	 *
	 * @return The HQL expression of the collection.
	 */
	private String collectionPath( String property ) {
		Path path = resolve( property, JoinKind.INNER );
		if ( path.attr() == null || ( path.attr().kind() != EntityModel.Kind.TO_MANY && path.attr().kind() != EntityModel.Kind.VALUES ) ) {
			throw new ORMException( ORMErrorType.ARGUMENT, "[" + property + "] is not a collection, so it has no size and cannot be empty.",
			    "Use isEmpty(), isNotEmpty() and size*() on one-to-many, many-to-many or collection properties." );
		}
		return path.hql();
	}

	/**
	 * {@code property is [not] empty}.
	 *
	 * @param property The collection property.
	 * @param empty    True for {@code is empty}.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder empty( String property, boolean empty ) {
		return add( Frag.of( collectionPath( property ), empty ? " is empty" : " is not empty" ) );
	}

	/**
	 * Compare a collection's size.
	 *
	 * @param property The collection property.
	 * @param operator The HQL operator.
	 * @param size     The size.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder size( String property, String operator, Object size ) {
		return add( Frag.of( "size(", collectionPath( property ), ") " + operator + " ", new Param( IntegerCaster.cast( size ) ) ) );
	}

	/**
	 * Compare two properties.
	 *
	 * @param property The first property path.
	 * @param operator The HQL operator.
	 * @param other    The second property path.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder compareProperties( String property, String operator, String other ) {
		return add( Frag.of( wherePath( property ), " " + operator + " ", wherePath( other ) ) );
	}

	/**
	 * Match the root entity's id: a value, or a struct of values for a composite id.
	 *
	 * @param id The id.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder idEq( Object id ) {
		String idName = root.model().singleIdName();
		if ( idName != null ) {
			return add( Frag.of( root.hql() + "." + idName, " = ", new Param( id ) ) );
		}
		if ( ! ( id instanceof IStruct values ) ) {
			throw new ORMException( ORMErrorType.ARGUMENT, "[" + getEntityName() + "] has a composite id, so idEq() needs a struct of id values.",
			    "Example: idEq( { " + String.join( " : 1, ", idProperties() ) + " : 1 } )." );
		}
		Group group = new Group( false );
		for ( String name : idProperties() ) {
			group.add( Frag.of( root.hql() + "." + name, " = ", new Param( values.get( Key.of( name ) ) ) ) );
		}
		return add( group );
	}

	/**
	 * The root entity's id property names.
	 *
	 * @return The names.
	 */
	private List<String> idProperties() {
		List<String> names = new ArrayList<>();
		for ( Object name : app.getEntityMetadata( getEntityName() ).getAsArray( Key.of( "idProperties" ) ) ) {
			names.add( name.toString() );
		}
		return names;
	}

	/**
	 * A native SQL condition. {@code ?} placeholders take the params in order; {@code {alias}.column} and
	 * {@code {property}} reference the root entity. Values are always bound, never concatenated.
	 *
	 * @param fragment The SQL condition, e.g. {@code "upper({alias}.first_name) = ?"}.
	 * @param params   The values for the {@code ?} placeholders.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder sql( String fragment, Object params ) {
		List<Object>	values	= params == null ? List.of() : toList( params );
		List<Object>	args	= new ArrayList<>();
		StringBuilder	sql		= new StringBuilder();
		int				next	= 0;
		for ( int i = 0; i < fragment.length(); i++ ) {
			char c = fragment.charAt( i );
			if ( c == '\'' ) {
				int end = fragment.indexOf( '\'', i + 1 );
				end = end < 0 ? fragment.length() - 1 : end;
				sql.append( fragment, i, end + 1 );
				i = end;
			} else if ( c == '?' ) {
				if ( next >= values.size() ) {
					throw new ORMException( ORMErrorType.QUERY_PARAMETER, "The sql() condition has more ? placeholders than params.",
					    "Pass one value per ?: " + fragment );
				}
				sql.append( '?' );
				args.add( new Param( values.get( next++ ) ) );
			} else if ( c == '{' && fragment.indexOf( '}', i ) > i ) {
				int		end		= fragment.indexOf( '}', i );
				String	token	= fragment.substring( i + 1, end );
				String	column	= null;
				if ( token.equalsIgnoreCase( "alias" ) && end + 1 < fragment.length() && fragment.charAt( end + 1 ) == '.' ) {
					int stop = end + 2;
					while ( stop < fragment.length() && ( Character.isLetterOrDigit( fragment.charAt( stop ) ) || fragment.charAt( stop ) == '_' ) ) {
						stop++;
					}
					column	= fragment.substring( end + 2, stop );
					end		= stop - 1;
				}
				sql.append( '?' );
				args.add( column != null ? root.hql() + "." + propertyForColumn( column ) : resolve( token, JoinKind.INNER ).hql() );
				i = end;
			} else {
				sql.append( c );
			}
		}
		if ( next < values.size() ) {
			throw new ORMException( ORMErrorType.QUERY_PARAMETER,
			    "The sql() condition received " + values.size() + " params but has only " + next + " ? placeholders.",
			    "Pass one value per ?: " + fragment );
		}
		List<Object> parts = new ArrayList<>();
		// sql() returns an untyped value, so cast it to compare with 1.
		parts.add( "cast(sql('(case when " + sql.toString().replace( "'", "''" ) + " then 1 else 0 end)'" );
		for ( Object arg : args ) {
			parts.add( ", " );
			parts.add( arg );
		}
		parts.add( ") as Integer) = 1" );
		return add( new Frag( parts ) );
	}

	/**
	 * The root property mapped to a column, for {@code {alias}.column} in {@link #sql(String, Object)}.
	 *
	 * @param column The column name.
	 *
	 * @return The property name.
	 */
	private String propertyForColumn( String column ) {
		IStruct meta = app.getEntityMetadata( getEntityName() );
		for ( Object item : meta.getAsArray( Key.of( "properties" ) ) ) {
			IStruct prop = ( IStruct ) item;
			if ( column.equalsIgnoreCase( prop.getAsString( Key.of( "column" ) ) ) || column.equalsIgnoreCase( prop.getAsString( Key._NAME ) ) ) {
				return prop.getAsString( Key._NAME );
			}
		}
		for ( Object id : meta.getAsArray( Key.of( "idProperties" ) ) ) {
			if ( column.equalsIgnoreCase( id.toString() ) ) {
				return id.toString();
			}
		}
		for ( Object item : meta.getAsArray( Key.of( "associations" ) ) ) {
			IStruct assoc = ( IStruct ) item;
			if ( column.equalsIgnoreCase( assoc.getAsString( Key.of( "fkcolumn" ) ) ) ) {
				return assoc.getAsString( Key._NAME ) + "." + EntityModel.of( factory, assoc.getAsString( Key.of( "target" ) ) ).singleIdName();
			}
		}
		throw new ORMException( ORMErrorType.PROPERTY_UNKNOWN, "No property of [" + getEntityName() + "] is mapped to the column [" + column + "].",
		    "Use {alias}.<column> with a mapped column, or {property} with a property name." );
	}

	/**
	 * Add conditions from a struct: each key is a property, a null value means {@code is null}.
	 *
	 * @param filter The property/value pairs.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder whereStruct( IStruct filter ) {
		filter.forEach( ( key, value ) -> compare( key.getName(), "=", value ) );
		return this;
	}

	/**
	 * {@code where( property, operator, value )}.
	 *
	 * @param property The property path.
	 * @param operator {@code =}, {@code ==}, {@code eq}, {@code !=}, {@code <>}, {@code ne}, {@code >}, {@code gt},
	 *                 {@code >=}, {@code gte}, {@code ge}, {@code <}, {@code lt}, {@code <=}, {@code lte},
	 *                 {@code le}, {@code like}, {@code ilike}, {@code in}, {@code not in}.
	 * @param value    The value.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder whereOperator( String property, String operator, Object value ) {
		return switch ( operator.trim().toLowerCase() ) {
			case "=", "==", "eq" -> compare( property, "=", value );
			case "!=", "<>", "ne", "neq" -> compare( property, "<>", value );
			case ">", "gt" -> compare( property, ">", value );
			case ">=", "gte", "ge" -> compare( property, ">=", value );
			case "<", "lt" -> compare( property, "<", value );
			case "<=", "lte", "le" -> compare( property, "<=", value );
			case "like" -> like( property, value, false );
			case "ilike" -> like( property, value, true );
			case "in" -> in( property, value, false );
			case "not in", "notin" -> in( property, value, true );
			default -> throw new ORMException( ORMErrorType.ARGUMENT, "Unknown where() operator [" + operator + "].",
			    "Use =, !=, >, >=, <, <=, like, ilike, in or not in." );
		};
	}

	/**
	 * Add a group of conditions joined by {@code or} ({@code anyOf}) or {@code and} ({@code allOf}).
	 *
	 * @param context   The context.
	 * @param or        True for {@code or}.
	 * @param callbacks Closures that add the group's conditions.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder group( IBoxContext context, boolean or, Object... callbacks ) {
		Group group = new Group( or );
		for ( Object callback : callbacks ) {
			if ( or && callbacks.length > 1 ) {
				// Several closures: each one is an alternative, and its own conditions must all match.
				Group branch = new Group( false );
				inGroup( context, branch, true, callback );
				group.add( branch );
			} else {
				inGroup( context, group, or, callback );
			}
		}
		return add( group );
	}

	/**
	 * Add the negation of the conditions a closure adds.
	 *
	 * @param context  The context.
	 * @param callback The closure.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder not( IBoxContext context, Object callback ) {
		return negate( context, () -> call( context, callback, this ) );
	}

	/**
	 * {@code [not] exists (subquery)}.
	 *
	 * @param sub     The subquery.
	 * @param negated True for {@code not exists}.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder exists( CriteriaBuilder sub, boolean negated ) {
		return add( Frag.of( negated ? "not exists " : "exists ", requireSub( sub ) ) );
	}

	/**
	 * Compare a value with a subquery's result, e.g. {@code subEq( 5, sub )} is {@code 5 = (select ...)}.
	 *
	 * @param value    The value.
	 * @param operator The HQL operator.
	 * @param sub      The subquery.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder valueVsSubquery( Object value, String operator, CriteriaBuilder sub ) {
		return add( Frag.of( new Param( value ), " " + operator + " ", requireSub( sub ) ) );
	}

	/**
	 * Check that a value is a subquery of this criteria.
	 *
	 * @param sub The value.
	 *
	 * @return The subquery.
	 */
	static CriteriaBuilder requireSub( Object sub ) {
		if ( sub instanceof CriteriaBuilder c && c.isSubquery() ) {
			return c;
		}
		throw new ORMException( ORMErrorType.ARGUMENT, "Expected a subquery but received " + ( sub == null ? "null" : sub.getClass().getSimpleName() ) + ".",
		    "Create one with c.subquery( \"Entity\", \"alias\" )." );
	}

	/* ============================================================================================================= */
	/* Joins */
	/* ============================================================================================================= */

	/**
	 * Join an association, optionally giving it an alias for later paths ({@code alias.property}).
	 *
	 * @param association The association path.
	 * @param alias       The alias (empty for none).
	 * @param kind        The join type.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder joinTo( String association, String alias, JoinKind kind ) {
		Join join = joinPath( association, kind );
		join.kind		= kind;
		join.referenced	= true;
		if ( alias != null && !alias.isBlank() ) {
			if ( !alias.matches( "[A-Za-z_][A-Za-z0-9_]*" ) ) {
				throw new ORMException( ORMErrorType.ARGUMENT, "The alias [" + alias + "] is not a valid name.",
				    "Use letters, digits and underscores, starting with a letter." );
			}
			aliases.put( alias.toLowerCase(), new Alias( join.hql, join.model ) );
		}
		return this;
	}

	/**
	 * Fetch an association with the root rows (a {@code left join fetch}), so reading it later needs no extra query.
	 *
	 * @param association The association path.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder fetch( String association ) {
		String[]		segments	= association.trim().split( "\\." );
		StringBuilder	prefix		= new StringBuilder();
		for ( String segment : segments ) {
			if ( prefix.length() > 0 ) {
				prefix.append( '.' );
			}
			prefix.append( segment );
			joinPath( prefix.toString(), JoinKind.LEFT ).fetch = true;
		}
		return this;
	}

	/**
	 * {@code with{Association}()}: join an association and make it the start of unqualified paths, either for the
	 * conditions a closure adds or, without a closure, until {@code end()}.
	 *
	 * @param context     The context.
	 * @param association The association path.
	 * @param kind        The join type.
	 * @param callback    The closure, or null.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder withAssociation( IBoxContext context, String association, JoinKind kind, Object callback ) {
		Join join = joinPath( association, kind );
		join.referenced = true;
		Alias target = new Alias( join.hql, join.model );
		if ( callback == null ) {
			current = target;
			return this;
		}
		Alias saved = current;
		current = target;
		try {
			call( context, callback, this );
		} finally {
			current = saved;
		}
		return this;
	}

	/**
	 * Return to the root entity after {@code with{Association}()}.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder end() {
		current = root;
		return this;
	}

	/* ============================================================================================================= */
	/* Flow */
	/* ============================================================================================================= */

	/**
	 * Apply a closure when a test passes (or another when it fails).
	 *
	 * @param context   The context.
	 * @param test      A boolean, or a closure that receives this builder and returns one.
	 * @param then      Applied when the test is true.
	 * @param otherwise Applied when the test is false (may be null).
	 *
	 * @return This builder.
	 */
	CriteriaBuilder when( IBoxContext context, Object test, Object then, Object otherwise ) {
		boolean passed = test instanceof Function ? BooleanCaster.cast( call( context, test, this ) ) : BooleanCaster.cast( test );
		if ( passed ) {
			call( context, then, this );
		} else if ( otherwise != null ) {
			call( context, otherwise, this );
		}
		return this;
	}

	/**
	 * A copy of this criteria that can be changed without changing this one.
	 *
	 * @return The copy.
	 */
	public CriteriaBuilder copy() {
		CriteriaBuilder c = new CriteriaBuilder( app, record, factory, outer, counter, "this" );
		copyStateInto( c );
		return c;
	}

	/**
	 * Copy this builder's state into another builder of the same entity.
	 *
	 * @param c The target.
	 */
	private void copyStateInto( CriteriaBuilder c ) {
		c.aliases = new LinkedHashMap<>( aliases );
		// the copy keeps this builder's root alias so every copied path stays valid
		c.aliases.replaceAll( ( k, v ) -> v == root ? c.root : v );
		c.joins = new LinkedHashMap<>();
		joins.forEach( ( k, j ) -> c.joins.put( k.startsWith( root.hql() + "." ) ? c.root.hql() + k.substring( root.hql().length() ) : k,
		    retarget( j, c ) ) );
		c.where		= ( Group ) retargetNode( where.copy(), c );
		c.groups	= new ArrayDeque<>();
		c.groups.push( c.where );
		c.current		= current == root ? c.root : current;
		c.projections	= new ArrayList<>();
		projections.forEach( p -> c.projections.add( new Projection( p.function(), c.retargetText( p.expr(), root ), p.alias() ) ) );
		c.distinct	= distinct;
		c.shape		= shape;
		c.orders	= new ArrayList<>();
		orders.forEach( o -> c.orders.add( new Order( c.retargetText( o.expr(), root ), o.asc() ) ) );
		c.firstResult	= firstResult;
		c.maxResults	= maxResults;
		c.options		= new Struct();
		c.options.putAll( options );
		c.steps = new ArrayList<>( steps );
	}

	/**
	 * Rename this builder's root alias to another builder's in an HQL string.
	 *
	 * @param text    The HQL.
	 * @param oldRoot The old root alias.
	 *
	 * @return The HQL using this builder's root alias.
	 */
	private String retargetText( String text, Alias oldRoot ) {
		return oldRoot.hql().equals( root.hql() ) ? text : text.replaceAll( "\\b" + oldRoot.hql() + "\\b", root.hql() );
	}

	/**
	 * Copy a join for another builder, renaming the root alias.
	 *
	 * @param j The join.
	 * @param c The target builder.
	 *
	 * @return The copy.
	 */
	private Join retarget( Join j, CriteriaBuilder c ) {
		Join copy = new Join( j.hql, j.parentHql.equals( root.hql() ) ? c.root.hql() : j.parentHql, j.attribute, j.model, j.plural, j.kind );
		copy.fetch		= j.fetch;
		copy.referenced	= j.referenced;
		return copy;
	}

	/**
	 * Copy a condition for another builder, renaming the root alias.
	 *
	 * @param node The condition.
	 * @param c    The target builder.
	 *
	 * @return The copy.
	 */
	private Node retargetNode( Node node, CriteriaBuilder c ) {
		if ( root.hql().equals( c.root.hql() ) ) {
			return node;
		}
		if ( node instanceof Group g ) {
			Group copy = new Group( g.or );
			g.children.forEach( child -> copy.add( retargetNode( child, c ) ) );
			return copy;
		}
		if ( node instanceof Not n ) {
			return new Not( retargetNode( n.child(), c ) );
		}
		if ( node instanceof Frag f ) {
			List<Object> parts = new ArrayList<>();
			f.parts().forEach( p -> parts.add( p instanceof String s ? c.retargetText( s, root ) : p ) );
			return new Frag( parts );
		}
		return node;
	}

	/**
	 * Create a subquery on another entity, for {@code exists()}, {@code isIn()} and the {@code property*}/{@code sub*}
	 * conditions. Inside it, unqualified paths start at the subquery's entity and {@code this.} reaches the outer
	 * criteria.
	 *
	 * @param entityName The subquery's entity.
	 * @param alias      The subquery root's alias (default {@code sub}).
	 *
	 * @return The subquery builder.
	 */
	CriteriaBuilder subquery( String entityName, String alias ) {
		EntityRecord target = app.lookupEntity( entityName, true );
		return new CriteriaBuilder( app, target, factory, this, counter, alias == null || alias.isBlank() ? "sub" : alias );
	}

	/* ============================================================================================================= */
	/* Shape */
	/* ============================================================================================================= */

	/**
	 * Add a projected column.
	 *
	 * @param function The projection function (see {@link Projection}).
	 * @param property The property path (ignored for rowCount).
	 * @param alias    The column name (empty for the default).
	 *
	 * @return This builder.
	 */
	CriteriaBuilder project( String function, String property, String alias ) {
		String	expr;
		String	defaultAlias;
		if ( function.equals( "rowCount" ) ) {
			expr			= "*";
			defaultAlias	= "count";
		} else if ( function.equals( "id" ) ) {
			expr			= resolve( "id", JoinKind.LEFT ).hql();
			defaultAlias	= root.model().singleIdName() == null ? "id" : root.model().singleIdName();
		} else {
			expr = selectPath( property );
			String[] parts = property.split( "\\." );
			defaultAlias = parts[ parts.length - 1 ];
		}
		String name = alias == null || alias.isBlank() ? defaultAlias : alias;
		for ( Projection p : projections ) {
			if ( p.alias().equalsIgnoreCase( name ) ) {
				name = function + Character.toUpperCase( name.charAt( 0 ) ) + name.substring( 1 );
			}
		}
		projections.add( new Projection( function, expr, name ) );
		return this;
	}

	/**
	 * cborm {@code withProjections()}: projections given as a struct, each value a list of {@code property[:alias]}.
	 *
	 * @param spec Keys: property, groupProperty, sum, avg, min, max, count, countDistinct, distinct, rowCount, id.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder withProjections( IStruct spec ) {
		spec.forEach( ( key, value ) -> {
			String kind = key.getName().toLowerCase();
			switch ( kind ) {
				case "rowcount", "id" -> {
					if ( value != null && !"false".equalsIgnoreCase( value.toString() ) ) {
						String alias = value instanceof Boolean || "true".equalsIgnoreCase( value.toString() ) ? "" : value.toString();
						project( kind.equals( "id" ) ? "id" : "rowCount", null, alias );
					}
				}
				case "property", "groupproperty", "group", "sum", "avg", "min", "max", "count", "countdistinct", "distinct" -> {
					String function = switch ( kind ) {
						case "groupproperty", "group" -> "group";
						case "countdistinct" -> "countDistinct";
						case "distinct" -> "property";
						default -> kind;
					};
					if ( kind.equals( "distinct" ) ) {
						distinct = true;
					}
					for ( Object item : toList( value ) ) {
						String[] pair = item.toString().trim().split( ":" );
						project( function, pair[ 0 ].trim(), pair.length > 1 ? pair[ 1 ].trim() : "" );
					}
				}
				default -> throw new ORMException( ORMErrorType.ARGUMENT, "Unknown projection [" + key.getName() + "].",
				    "Use property, groupProperty, sum, avg, min, max, count, countDistinct, distinct, rowCount or id." );
			}
		} );
		return this;
	}

	/**
	 * Set the result shape.
	 *
	 * @param newShape The shape.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder shape( Shape newShape ) {
		this.shape = newShape;
		return this;
	}

	/**
	 * Make rows distinct.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder asDistinct() {
		this.distinct = true;
		return this;
	}

	/**
	 * Add orderings: {@code order( "name" )}, {@code order( "name", "desc" )}, {@code order( "name desc, id" )}.
	 *
	 * @param property   One property, or a comma-separated list with optional {@code asc}/{@code desc}.
	 * @param direction  {@code asc} or {@code desc} (applies to entries without their own).
	 * @param ignoreCase True to sort text properties case-insensitively.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder order( String property, String direction, boolean ignoreCase ) {
		for ( String entry : property.split( "," ) ) {
			String[]	words	= entry.trim().split( "\\s+" );
			String		dir		= words.length > 1 ? words[ 1 ] : ( direction == null || direction.isBlank() ? "asc" : direction );
			boolean		asc		= switch ( dir.trim().toLowerCase() ) {
									case "asc", "ascending" -> true;
									case "desc", "descending" -> false;
									default -> throw new ORMException( ORMErrorType.ARGUMENT, "Unknown sort direction [" + dir + "].",
									    "Use asc or desc." );
								};
			Path		path	= resolve( words[ 0 ], JoinKind.LEFT );
			String		expr	= ignoreCase && isText( path ) ? "lower(" + path.hql() + ")" : path.hql();
			orders.add( new Order( expr, asc ) );
		}
		return this;
	}

	/**
	 * Whether a resolved path is a text value (for case-insensitive sorting): a String-typed attribute, or a property
	 * whose ormtype is a text type (an untyped BoxLang property defaults to string).
	 *
	 * @param path The resolved path.
	 *
	 * @return True for text.
	 */
	private boolean isText( Path path ) {
		if ( path.attr() == null || path.attr().kind() != EntityModel.Kind.BASIC ) {
			return false;
		}
		if ( path.attr().isText() ) {
			return true;
		}
		EntityRecord owner = path.owner().isEntity() ? app.lookupEntity( path.owner().name(), false ) : null;
		return owner != null && ORMApp.isTextProperty( owner, path.attr().name() );
	}

	/**
	 * Skip rows.
	 *
	 * @param offset The number of rows to skip (0-based first row).
	 *
	 * @return This builder.
	 */
	CriteriaBuilder firstResult( Object offset ) {
		this.firstResult = nonNegative( offset, "firstResult" );
		return this;
	}

	/**
	 * Limit rows.
	 *
	 * @param max The maximum number of rows.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder maxResults( Object max ) {
		this.maxResults = nonNegative( max, "maxResults" );
		return this;
	}

	/**
	 * Check a paging number.
	 *
	 * @param value The value.
	 * @param name  The option name for the error.
	 *
	 * @return The number.
	 */
	private static Integer nonNegative( Object value, String name ) {
		Integer number = IntegerCaster.cast( value );
		if ( number == null || number < 0 ) {
			throw new ORMException( ORMErrorType.ARGUMENT, name + "() needs a number of 0 or more but received [" + value + "].", "" );
		}
		return number;
	}

	/**
	 * Set a query option.
	 *
	 * @param key   The option.
	 * @param value The value.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder option( Key key, Object value ) {
		options.put( key, value );
		return this;
	}

	/**
	 * Add a query hint.
	 *
	 * @param name  The hint name, e.g. {@code org.hibernate.readOnly}.
	 * @param value The value.
	 *
	 * @return This builder.
	 */
	CriteriaBuilder hint( String name, Object value ) {
		IStruct hints = options.get( ORMKeys.hints ) instanceof IStruct h ? h : new Struct();
		hints.put( Key.of( name ), value );
		options.put( ORMKeys.hints, hints );
		return this;
	}

	/* ============================================================================================================= */
	/* Compile */
	/* ============================================================================================================= */

	/**
	 * Whether a collection join is used by a condition (entity rows then need {@code distinct}).
	 *
	 * @return True when a referenced join is to-many.
	 */
	private boolean hasPluralJoin() {
		return joins.values().stream().anyMatch( j -> j.plural && j.referenced );
	}

	/**
	 * Compile this criteria.
	 *
	 * @param mode       What to select.
	 * @param expression The expression for {@link Mode#EXPRESSION} (e.g. {@code sum(bx_this.price)}), else null.
	 *
	 * @return The HQL, its values and its struct/query columns.
	 */
	Compiled compile( Mode mode, String expression ) {
		RenderContext	ctx		= new RenderContext();
		List<String>	columns	= new ArrayList<>();
		StringBuilder	hql		= new StringBuilder( "select " );
		switch ( mode ) {
			case COUNT -> hql.append( distinct || hasPluralJoin() ? "count(distinct " + root.hql() + ")" : "count(" + root.hql() + ")" );
			case EXISTS -> hql.append( "1" );
			case EXPRESSION -> hql.append( expression );
			case LIST -> {
				if ( distinct || ( projections.isEmpty() && hasPluralJoin() ) ) {
					hql.append( "distinct " );
				}
				if ( !projections.isEmpty() ) {
					List<String> selects = new ArrayList<>();
					projections.forEach( p -> {
						selects.add( p.select() );
						columns.add( p.alias() );
					} );
					hql.append( String.join( ", ", selects ) );
				} else if ( shape == Shape.STRUCT || shape == Shape.QUERY ) {
					List<String> selects = new ArrayList<>();
					root.model().basicAttributeNames().forEach( n -> {
						selects.add( root.hql() + "." + n );
						columns.add( n );
					} );
					hql.append( String.join( ", ", selects ) );
				} else {
					hql.append( root.hql() );
				}
			}
		}
		renderFrom( hql, ctx, mode == Mode.LIST && projections.isEmpty() && shape != Shape.STRUCT && shape != Shape.QUERY );
		List<String> groupBy = projections.stream().filter( p -> p.function().equals( "group" ) ).map( Projection::expr ).toList();
		if ( mode == Mode.LIST && !groupBy.isEmpty() ) {
			hql.append( " group by " ).append( String.join( ", ", groupBy ) );
		}
		if ( ( mode == Mode.LIST || ( mode == Mode.EXPRESSION && expression.indexOf( '(' ) < 0 ) ) && !orders.isEmpty() ) {
			List<String> clauses = new ArrayList<>();
			orders.forEach( o -> clauses.add( o.expr() + ( o.asc() ? " asc" : " desc" ) ) );
			hql.append( " order by " ).append( String.join( ", ", clauses ) );
		}
		return new Compiled( hql.toString(), ctx.values, columns );
	}

	/**
	 * Append {@code from ... joins ... where ...}.
	 *
	 * @param hql        The HQL being built.
	 * @param ctx        Collects bound values.
	 * @param allowFetch Whether fetch joins may be rendered (only when selecting root entities).
	 */
	private void renderFrom( StringBuilder hql, RenderContext ctx, boolean allowFetch ) {
		hql.append( " from " ).append( getEntityName() ).append( ' ' ).append( root.hql() );
		for ( Join join : joins.values() ) {
			if ( join.fetch && !join.referenced && !allowFetch ) {
				continue;
			}
			hql.append( ' ' ).append( join.kind.hql );
			if ( join.fetch && allowFetch ) {
				hql.append( " fetch" );
			}
			hql.append( ' ' ).append( join.parentHql ).append( '.' ).append( join.attribute ).append( ' ' ).append( join.hql );
		}
		if ( !where.isEmpty() ) {
			hql.append( " where " );
			where.render( hql, ctx );
		}
	}

	/**
	 * Render this criteria as a subquery inside its outer query (sharing the outer query's parameter numbering).
	 *
	 * @param ctx The outer query's render context.
	 *
	 * @return The subquery HQL, without parentheses.
	 */
	String renderSubquery( RenderContext ctx ) {
		StringBuilder hql = new StringBuilder( "select " );
		if ( distinct ) {
			hql.append( "distinct " );
		}
		hql.append( projections.isEmpty() ? root.hql() : projections.get( 0 ).select() );
		renderFrom( hql, ctx, false );
		List<String> groupBy = projections.stream().filter( p -> p.function().equals( "group" ) ).map( Projection::expr ).toList();
		if ( !groupBy.isEmpty() ) {
			hql.append( " group by " ).append( String.join( ", ", groupBy ) );
		}
		return hql.toString();
	}

	/* ============================================================================================================= */
	/* Run */
	/* ============================================================================================================= */

	/**
	 * Refuse to run a subquery on its own.
	 */
	private void requireTopLevel() {
		if ( isSubquery() ) {
			throw new ORMException( ORMErrorType.ARGUMENT, "A subquery cannot be run on its own.",
			    "Pass it to exists(), notExists(), isIn() or a property*/sub* condition of the outer criteria." );
		}
	}

	/**
	 * The query options for one run.
	 *
	 * @param first The first row, or null.
	 * @param max   The maximum rows, or null.
	 *
	 * @return The options struct for {@link HQLQuery}.
	 */
	private IStruct runOptions( Integer first, Integer max ) {
		IStruct opts = new Struct();
		opts.putAll( options );
		if ( datasource != null ) {
			opts.put( Key.datasource, datasource );
		}
		if ( first != null && first > 0 ) {
			opts.put( Key.offset, first );
		}
		if ( max != null ) {
			opts.put( ORMKeys.maxResults, max );
		}
		return opts;
	}

	/**
	 * Run a compiled query and return its raw rows.
	 *
	 * @param context The context.
	 * @param c       The compiled query.
	 * @param first   The first row, or null.
	 * @param max     The maximum rows, or null.
	 *
	 * @return The rows as Hibernate returned them.
	 */
	private List<?> run( IBoxContext context, Compiled c, Integer first, Integer max ) {
		try {
			return HQLQuery.ofNumbered( context, c.hql(), c.values(), runOptions( first, max ) ).prepare( true ).list();
		} catch ( ORMException e ) {
			throw e;
		} catch ( RuntimeException e ) {
			throw ORMErrors.translate( e, app.errorContext( OPERATION ).withEntity( getEntityName() ).withQuery( c.hql(), Array.fromList( c.values() ) ) );
		}
	}

	/**
	 * Turn a Hibernate value into a BoxLang value: facades become their BoxLang instances, tuples become arrays.
	 *
	 * @param value The value.
	 *
	 * @return The BoxLang value.
	 */
	private static Object toBox( Object value ) {
		if ( value instanceof Object[] tuple ) {
			Array row = new Array();
			for ( Object v : tuple ) {
				row.add( FacadeSupport.unwrapIfFacade( v ) );
			}
			return row;
		}
		return FacadeSupport.unwrapIfFacade( value );
	}

	/**
	 * Shape raw rows as this criteria's result type.
	 *
	 * @param rows The rows.
	 * @param c    The compiled query (for its columns).
	 *
	 * @return An array of entities/values/structs, or a query.
	 */
	private Object shapeRows( List<?> rows, Compiled c ) {
		if ( ( shape == Shape.STRUCT || shape == Shape.QUERY ) && !c.columns().isEmpty() ) {
			List<String> columns = c.columns();
			if ( shape == Shape.QUERY ) {
				Query query = new Query();
				columns.forEach( col -> query.addColumn( Key.of( col ), QueryColumnType.OBJECT ) );
				for ( Object row : rows ) {
					Object[]	values	= row instanceof Object[] tuple ? tuple : new Object[] { row };
					Object[]	boxed	= new Object[ values.length ];
					for ( int i = 0; i < values.length; i++ ) {
						boxed[ i ] = FacadeSupport.unwrapIfFacade( values[ i ] );
					}
					query.addRow( boxed );
				}
				return query;
			}
			Array result = new Array();
			for ( Object row : rows ) {
				Object[]	values	= row instanceof Object[] tuple ? tuple : new Object[] { row };
				IStruct		struct	= new Struct( IStruct.TYPES.LINKED );
				for ( int i = 0; i < columns.size(); i++ ) {
					struct.put( Key.of( columns.get( i ) ), FacadeSupport.unwrapIfFacade( values[ i ] ) );
				}
				result.add( struct );
			}
			return result;
		}
		Array result = new Array();
		rows.forEach( r -> result.add( toBox( r ) ) );
		return result;
	}

	/**
	 * Run the criteria and return its rows (terminal).
	 *
	 * @param context The context.
	 *
	 * @return An array of entities (or projection values, structs), a query ({@code asQuery()}) or a stream
	 *         ({@code asStream()}).
	 */
	public Object list( IBoxContext context ) {
		requireTopLevel();
		announce( ORMKeys.EVENT_BEFORE_CRITERIA_LIST, () -> Struct.of( Key.of( "criteriaBuilder" ), this ) );
		Compiled	c	= compile( Mode.LIST, null );
		Object		result;
		if ( shape == Shape.STREAM ) {
			try {
				result = HQLQuery.ofNumbered( context, c.hql(), c.values(), runOptions( firstResult, maxResults ) ).prepare( true ).getResultStream()
				    .map( CriteriaBuilder::toBox );
			} catch ( RuntimeException e ) {
				throw ORMErrors.translate( e, app.errorContext( OPERATION ).withEntity( getEntityName() ).withQuery( c.hql(), Array.fromList( c.values() ) ) );
			}
		} else {
			result = shapeRows( run( context, c, firstResult, maxResults ), c );
		}
		final Object results = result;
		announce( ORMKeys.EVENT_AFTER_CRITERIA_LIST, () -> Struct.of( Key.of( "criteriaBuilder" ), this, Key.of( "results" ), results ) );
		return result;
	}

	/**
	 * cborm {@code list( max, offset, timeout, sortOrder, ignoreCase, asQuery )}: run with these settings applied to a
	 * copy.
	 *
	 * @param context    The context.
	 * @param max        Maximum rows (0 or null for all).
	 * @param offset     Rows to skip.
	 * @param timeout    Timeout in seconds.
	 * @param sortOrder  Ordering, e.g. {@code "name desc"}.
	 * @param ignoreCase Sort text case-insensitively.
	 * @param asQuery    Return a query.
	 *
	 * @return The rows.
	 */
	public Object list( IBoxContext context, Object max, Object offset, Object timeout, Object sortOrder, Object ignoreCase, Object asQuery ) {
		if ( max == null && offset == null && timeout == null && sortOrder == null && ignoreCase == null && asQuery == null ) {
			return list( context );
		}
		CriteriaBuilder c = copy();
		if ( max != null && IntegerCaster.cast( max ) > 0 ) {
			c.maxResults( max );
		}
		if ( offset != null && IntegerCaster.cast( offset ) > 0 ) {
			c.firstResult( offset );
		}
		if ( timeout != null && IntegerCaster.cast( timeout ) > 0 ) {
			c.option( Key.timeout, IntegerCaster.cast( timeout ) );
		}
		if ( sortOrder != null && !sortOrder.toString().isBlank() ) {
			c.order( sortOrder.toString(), "asc", ignoreCase != null && BooleanCaster.cast( ignoreCase ) );
		}
		if ( asQuery != null && BooleanCaster.cast( asQuery ) ) {
			c.shape( Shape.QUERY );
		}
		return c.list( context );
	}

	/**
	 * Count the matching root entities (terminal).
	 *
	 * @param context  The context.
	 * @param property Count the distinct values of this property instead (empty for rows).
	 *
	 * @return The count.
	 */
	public long count( IBoxContext context, String property ) {
		requireTopLevel();
		announce( ORMKeys.EVENT_BEFORE_CRITERIA_COUNT, () -> Struct.of( Key.of( "criteriaBuilder" ), this ) );
		Compiled	c		= property == null || property.isBlank()
		    ? compile( Mode.COUNT, null )
		    : compile( Mode.EXPRESSION, "count(distinct " + selectPath( property ) + ")" );
		List<?>		rows	= run( context, c, null, null );
		long		count	= rows.isEmpty() || rows.get( 0 ) == null ? 0 : ( ( Number ) rows.get( 0 ) ).longValue();
		announce( ORMKeys.EVENT_AFTER_CRITERIA_COUNT, () -> Struct.of( Key.of( "criteriaBuilder" ), this, Key.of( "count" ), count ) );
		return count;
	}

	/**
	 * Whether any row matches (terminal).
	 *
	 * @param context The context.
	 *
	 * @return True when at least one row matches.
	 */
	public boolean exists( IBoxContext context ) {
		requireTopLevel();
		return !run( context, compile( Mode.EXISTS, null ), null, 1 ).isEmpty();
	}

	/**
	 * The single matching row (terminal).
	 *
	 * @param context     The context.
	 * @param uniqueFirst True to return the first of several matches instead of failing.
	 *
	 * @return The entity (or projection value/struct), or null when nothing matches.
	 */
	public Object get( IBoxContext context, boolean uniqueFirst ) {
		requireTopLevel();
		announce( ORMKeys.EVENT_BEFORE_CRITERIA_GET, () -> Struct.of( Key.of( "criteriaBuilder" ), this ) );
		Compiled	c		= compile( Mode.LIST, null );
		List<?>		rows	= run( context, c, firstResult, uniqueFirst ? 1 : 2 );
		if ( rows.size() > 1 ) {
			throw ORMErrors.nonUniqueResult( 0, OPERATION + ".get", c.hql() );
		}
		Object result = rows.isEmpty() ? null : first( shapeRows( rows, c ) );
		announce( ORMKeys.EVENT_AFTER_CRITERIA_GET, () -> Struct.of( Key.of( "criteriaBuilder" ), this, Key.of( "result" ), result ) );
		return result;
	}

	/**
	 * The first element of shaped rows (a query stays a query).
	 *
	 * @param shaped An array or a query.
	 *
	 * @return The first element, or the query.
	 */
	private static Object first( Object shaped ) {
		if ( shaped instanceof Array array ) {
			return array.isEmpty() ? null : array.get( 0 );
		}
		return shaped;
	}

	/**
	 * The first row in the criteria's order (terminal).
	 *
	 * @param context The context.
	 *
	 * @return The first entity (or value/struct), or null.
	 */
	public Object first( IBoxContext context ) {
		requireTopLevel();
		Compiled c = compile( Mode.LIST, null );
		return first( shapeRows( run( context, c, firstResult, 1 ), c ) );
	}

	/**
	 * Fail when a required row is missing.
	 *
	 * @param result    The row or null.
	 * @param operation The terminal name.
	 *
	 * @return The row.
	 */
	private Object orFail( Object result, String operation ) {
		if ( result == null ) {
			IStruct info = Struct.of( Key.of( "entityName" ), getEntityName(), Key.of( "hql" ), compile( Mode.LIST, null ).hql(), Key.of( "operation" ),
			    operation );
			throw new ORMException( ORMErrorType.NOT_FOUND, "No [" + getEntityName() + "] matched the criteria.",
			    "Use " + ( operation.startsWith( "first" ) ? "first()" : "get()" ) + " to receive null instead of an error.", info, null );
		}
		return result;
	}

	/**
	 * {@code get()} that fails with {@code orm.notFound} when nothing matches (terminal).
	 *
	 * @param context     The context.
	 * @param uniqueFirst True to return the first of several matches.
	 *
	 * @return The row.
	 */
	public Object getOrFail( IBoxContext context, boolean uniqueFirst ) {
		return orFail( get( context, uniqueFirst ), "getOrFail" );
	}

	/**
	 * {@code first()} that fails with {@code orm.notFound} when nothing matches (terminal).
	 *
	 * @param context The context.
	 *
	 * @return The row.
	 */
	public Object firstOrFail( IBoxContext context ) {
		return orFail( first( context ), "firstOrFail" );
	}

	/**
	 * One page of rows plus the paging numbers (terminal).
	 *
	 * @param context The context.
	 * @param page    The page number (1-based).
	 * @param maxRows Rows per page.
	 *
	 * @return {@code { results, pagination : { page, maxRows, totalRecords, totalPages } }}.
	 */
	public IStruct paginate( IBoxContext context, Object page, Object maxRows ) {
		int			p		= positive( page, "page" );
		int			size	= positive( maxRows, "maxRows" );
		long		total	= count( context, null );
		Compiled	c		= compile( Mode.LIST, null );
		Object		results	= shapeRows( run( context, c, ( p - 1 ) * size, size ), c );
		IStruct		paging	= Struct.linkedOf( Key.of( "page" ), p, Key.of( "maxRows" ), size, Key.of( "totalRecords" ), total, Key.of( "totalPages" ),
		    ( long ) Math.ceil( total / ( double ) size ) );
		return Struct.linkedOf( Key.of( "results" ), results, Key.of( "pagination" ), paging );
	}

	/**
	 * One page of rows without counting the total (terminal): fetches one extra row to know whether there are more.
	 *
	 * @param context The context.
	 * @param page    The page number (1-based).
	 * @param maxRows Rows per page.
	 *
	 * @return {@code { results, pagination : { page, maxRows, hasMore } }}.
	 */
	public IStruct simplePaginate( IBoxContext context, Object page, Object maxRows ) {
		int			p		= positive( page, "page" );
		int			size	= positive( maxRows, "maxRows" );
		Compiled	c		= compile( Mode.LIST, null );
		List<?>		rows	= run( context, c, ( p - 1 ) * size, size + 1 );
		boolean		hasMore	= rows.size() > size;
		Object		results	= shapeRows( hasMore ? rows.subList( 0, size ) : rows, c );
		return Struct.linkedOf( Key.of( "results" ), results, Key.of( "pagination" ),
		    Struct.linkedOf( Key.of( "page" ), p, Key.of( "maxRows" ), size, Key.of( "hasMore" ), hasMore ) );
	}

	/**
	 * Check a page number or size.
	 *
	 * @param value The value.
	 * @param name  The argument name.
	 *
	 * @return The number.
	 */
	private static int positive( Object value, String name ) {
		Integer number = value == null ? null : IntegerCaster.cast( value );
		if ( number == null || number < 1 ) {
			throw new ORMException( ORMErrorType.ARGUMENT, "[" + name + "] must be 1 or more but was [" + value + "].", "" );
		}
		return number;
	}

	/**
	 * The values of one property for every matching row, in order (terminal).
	 *
	 * @param context  The context.
	 * @param property The property path.
	 *
	 * @return The values.
	 */
	public Array pluck( IBoxContext context, String property ) {
		requireTopLevel();
		Compiled	c		= compile( Mode.EXPRESSION, selectPath( property ) );
		Array		result	= new Array();
		run( context, c, firstResult, maxResults ).forEach( r -> result.add( toBox( r ) ) );
		return result;
	}

	/**
	 * An aggregate of one property over the matching rows (terminal).
	 *
	 * @param context  The context.
	 * @param function {@code sum}, {@code avg}, {@code min} or {@code max}.
	 * @param property The property path.
	 *
	 * @return The aggregate (null when no rows match).
	 */
	public Object aggregate( IBoxContext context, String function, String property ) {
		requireTopLevel();
		List<?> rows = run( context, compile( Mode.EXPRESSION, function + "(" + selectPath( property ) + ")" ), null, null );
		return rows.isEmpty() ? null : toBox( rows.get( 0 ) );
	}

	/**
	 * Process the matching rows in batches (terminal). Rows are read {@code size} at a time; after each batch the
	 * session is flushed (so changes made in the callback are saved) and cleared (so memory stays flat). With a single
	 * id and no ordering, batches follow the id (keyset), so changing or deleting rows in the callback cannot skip any.
	 *
	 * @param context  The context.
	 * @param size     Rows per batch.
	 * @param callback A closure called with an array of rows per batch.
	 *
	 * @return The number of rows processed.
	 */
	public long chunk( IBoxContext context, Object size, Object callback ) {
		requireCallback( callback );
		return batches( context, size, rows -> call( context, callback, rows ) );
	}

	/**
	 * Call a closure for every matching row (terminal), reading rows in batches like {@link #chunk}.
	 *
	 * @param context  The context.
	 * @param callback A closure called with each row.
	 * @param size     Rows per batch (default 100).
	 *
	 * @return The number of rows processed.
	 */
	public long each( IBoxContext context, Object callback, Object size ) {
		requireCallback( callback );
		return batches( context, size == null ? 100 : size, rows -> {
			if ( rows instanceof Array array ) {
				for ( Object row : array ) {
					call( context, callback, row );
				}
			}
		} );
	}

	/**
	 * Fail early when a callback is not a closure.
	 *
	 * @param callback The value.
	 */
	private static void requireCallback( Object callback ) {
		if ( ! ( callback instanceof Function ) ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    "entityCriteria expected a closure but received " + ( callback == null ? "null" : callback.getClass().getSimpleName() ) + ".",
			    "Pass a closure, e.g. c.each( ( row ) => ... )." );
		}
	}

	/**
	 * Read the matching rows in batches (see {@link #chunk}).
	 *
	 * @param context The context.
	 * @param size    Rows per batch.
	 * @param onBatch Receives each batch (an array, or a query for {@code asQuery()}).
	 *
	 * @return The number of rows processed.
	 */
	private long batches( IBoxContext context, Object size, java.util.function.Consumer<Object> onBatch ) {
		requireTopLevel();
		int		batch		= positive( size, "size" );
		String	idName		= root.model().singleIdName();
		boolean	keyset		= idName != null && orders.isEmpty() && projections.isEmpty() && ( shape == Shape.ENTITY || shape == Shape.STREAM );
		long	processed	= 0;
		Object	lastId		= null;
		int		offset		= firstResult == null ? 0 : firstResult;
		Session	session		= ORMContext.getForContext( context.getParentOfType( IJDBCCapableContext.class ) )
		    .getSession( datasource == null ? null : Key.of( datasource ) );
		while ( true ) {
			int take = batch;
			if ( maxResults != null ) {
				long left = maxResults - processed;
				if ( left <= 0 ) {
					break;
				}
				take = ( int ) Math.min( batch, left );
			}
			CriteriaBuilder c = copy();
			if ( c.shape == Shape.STREAM ) {
				c.shape = Shape.ENTITY;
			}
			Integer first;
			if ( keyset ) {
				c.orders.add( new Order( c.root.hql() + "." + idName, true ) );
				if ( lastId != null ) {
					c.where.add( Frag.of( c.root.hql() + "." + idName, " > ", new Param( lastId ) ) );
				}
				first = processed == 0 ? offset : null;
			} else {
				if ( idName != null ) {
					c.orders.add( new Order( c.root.hql() + "." + idName, true ) );
				}
				first = offset + ( int ) processed;
			}
			Compiled	compiled	= c.compile( Mode.LIST, null );
			List<?>		rows		= run( context, compiled, first, take );
			if ( rows.isEmpty() ) {
				break;
			}
			if ( keyset ) {
				lastId = EntityInspector.getId( record, FacadeSupport.unwrapIfFacade( rows.get( rows.size() - 1 ) ) );
			}
			onBatch.accept( c.shapeRows( rows, compiled ) );
			processed += rows.size();
			if ( session.isDirty() ) {
				ORMContext.flush( session, OPERATION + ".chunk" );
			}
			session.clear();
			if ( rows.size() < take ) {
				break;
			}
		}
		return processed;
	}

	/* ============================================================================================================= */
	/* SQL */
	/* ============================================================================================================= */

	/**
	 * The HQL this criteria runs for {@code list()}.
	 *
	 * @return The HQL.
	 */
	public String getHQL() {
		return compile( Mode.LIST, null ).hql();
	}

	/**
	 * The SQL this criteria runs for {@code list()}, without running it. Paging is not included.
	 *
	 * @param context    The context.
	 * @param executable True to put the bound values in the SQL (for pasting into a SQL tool); false to keep {@code ?}.
	 * @param format     True to break the SQL into lines.
	 *
	 * @return The SQL.
	 */
	public String getSQL( IBoxContext context, boolean executable, boolean format ) {
		requireTopLevel();
		if ( factory.getSessionFactoryOptions().getStatementInspector() != SqlCapture.INSTANCE ) {
			throw new ORMException( ORMErrorType.CONFIG, "getSQL() is not available: the application configured its own Hibernate statement inspector.",
			    "Remove hibernate.session_factory.statement_inspector from the ORM settings to use getSQL(), peekSQL() and logSQL()." );
		}
		Compiled	c	= compile( Mode.LIST, null );
		String		sql;
		try {
			IStruct opts = runOptions( null, null );
			opts.remove( ORMKeys.cacheable );
			opts.remove( ORMKeys.cacheName );
			org.hibernate.query.Query<?> query = HQLQuery.ofNumbered( context, c.hql(), c.values(), opts ).prepare( false );
			query.setHibernateFlushMode( FlushMode.MANUAL );
			query.setCacheable( false );
			sql = SqlCapture.capture( query::list );
		} catch ( ORMException e ) {
			throw e;
		} catch ( RuntimeException e ) {
			throw ORMErrors.translate( e, app.errorContext( OPERATION ).withEntity( getEntityName() ).withQuery( c.hql(), Array.fromList( c.values() ) ) );
		}
		if ( sql == null ) {
			throw new ORMException( ORMErrorType.GENERIC, "Hibernate produced no SQL for this criteria.", "", null, null );
		}
		if ( executable ) {
			sql = inline( sql, c.values() );
		}
		return format ? SqlFormat.format( sql ) : sql;
	}

	/**
	 * Put bound values into SQL in place of its {@code ?} placeholders (outside string literals).
	 *
	 * @param sql    The SQL.
	 * @param values The values, in placeholder order.
	 *
	 * @return The SQL with literals.
	 */
	private String inline( String sql, List<Object> values ) {
		StringBuilder	out		= new StringBuilder();
		int				next	= 0;
		boolean			quoted	= false;
		for ( int i = 0; i < sql.length(); i++ ) {
			char c = sql.charAt( i );
			if ( c == '\'' ) {
				quoted = !quoted;
			}
			if ( c == '?' && !quoted && next < values.size() ) {
				out.append( literal( values.get( next++ ) ) );
			} else {
				out.append( c );
			}
		}
		return out.toString();
	}

	/**
	 * A SQL literal for a bound value.
	 *
	 * @param value The value.
	 *
	 * @return The literal.
	 */
	private String literal( Object value ) {
		Object v = FacadeSupport.unwrapIfFacade( value );
		if ( v instanceof IClassRunnable entity ) {
			v = EntityInspector.getId( EntityInspector.resolve( app, entity, "getSQL" ), entity );
		}
		if ( v == null ) {
			return "null";
		}
		if ( v instanceof Number || v instanceof Boolean ) {
			return v.toString();
		}
		if ( v instanceof ortus.boxlang.runtime.types.DateTime dt ) {
			return "'" + dt.format( "yyyy-MM-dd HH:mm:ss" ) + "'";
		}
		return "'" + StringCaster.cast( v ).replace( "'", "''" ) + "'";
	}

	/* ============================================================================================================= */
	/* toString */
	/* ============================================================================================================= */

	/**
	 * A readable description: the recorded calls, the HQL and its parameters. This is what {@code writeDump()} shows.
	 *
	 * @return The description.
	 */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		for ( int i = 0; i < steps.size(); i++ ) {
			out.append( i == 0 ? "" : "\n    " ).append( steps.get( i ) );
		}
		try {
			Compiled c = compile( Mode.LIST, null );
			out.append( "\nHQL: " ).append( c.hql() );
			out.append( "\nParams: " ).append( c.values().stream().map( CriteriaMethods::format ).toList() );
		} catch ( RuntimeException e ) {
			out.append( "\n(HQL not available: " ).append( e.getMessage() ).append( ')' );
			return out.toString();
		}
		if ( lastContext != null && !isSubquery() ) {
			try {
				out.append( "\nSQL: " ).append( getSQL( lastContext, false, true ).replace( "\n", "\n     " ) );
			} catch ( RuntimeException e ) {
				// The request that built the criteria may be over; the HQL above still describes the query.
			}
		}
		return out.toString();
	}
}
