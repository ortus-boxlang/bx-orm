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
import java.util.List;

/**
 * The pieces a criteria's {@code where} clause is made of. Property paths are resolved to HQL aliases when a condition
 * is added, so rendering only has to join text and number the parameters ({@code ?1..?n}) in the order they appear.
 */
final class HqlParts {

	/**
	 * Not instantiable.
	 */
	private HqlParts() {
	}

	/**
	 * Collects the bound values while HQL is rendered and hands out their placeholder numbers.
	 */
	static final class RenderContext {

		/** The bound values, in placeholder order. */
		final List<Object> values = new ArrayList<>();

		/**
		 * Register a value and return its placeholder.
		 *
		 * @param value The value to bind.
		 *
		 * @return The placeholder, e.g. {@code ?3}.
		 */
		String bind( Object value ) {
			values.add( value );
			return "?" + values.size();
		}
	}

	/**
	 * A piece of a {@code where} clause.
	 */
	interface Node {

		/**
		 * Append this node's HQL.
		 *
		 * @param hql The HQL being built.
		 * @param ctx Collects bound values.
		 */
		void render( StringBuilder hql, RenderContext ctx );

		/**
		 * Whether this node renders nothing (an empty group).
		 *
		 * @return True when there is nothing to render.
		 */
		default boolean isEmpty() {
			return false;
		}

		/**
		 * A copy that can be changed without changing this one.
		 *
		 * @return The copy (immutable nodes return themselves).
		 */
		default Node copy() {
			return this;
		}
	}

	/**
	 * A bound value inside a {@link Frag}.
	 *
	 * @param value The value.
	 */
	record Param( Object value ) {
	}

	/**
	 * A condition made of HQL text, bound values ({@link Param}) and subqueries ({@link CriteriaBuilder}), rendered in
	 * order.
	 *
	 * @param parts The parts.
	 */
	record Frag( List<Object> parts ) implements Node {

		/**
		 * Build a fragment from its parts.
		 *
		 * @param parts HQL strings, {@link Param} values and subquery builders.
		 *
		 * @return The fragment.
		 */
		static Frag of( Object... parts ) {
			return new Frag( List.of( parts ) );
		}

		/**
		 * Append this node's HQL.
		 *
		 * @param hql The HQL being built.
		 * @param ctx Collects bound values.
		 */
		@Override
		public void render( StringBuilder hql, RenderContext ctx ) {
			for ( Object part : parts ) {
				if ( part instanceof Param p ) {
					hql.append( ctx.bind( p.value() ) );
				} else if ( part instanceof CriteriaBuilder sub ) {
					hql.append( '(' ).append( sub.renderSubquery( ctx ) ).append( ')' );
				} else {
					hql.append( part );
				}
			}
		}
	}

	/**
	 * Conditions joined by {@code and} or {@code or}.
	 */
	static final class Group implements Node {

		/** True for {@code or}, false for {@code and}. */
		final boolean		or;
		/** The conditions. */
		final List<Node>	children;

		/**
		 * Create an empty group.
		 *
		 * @param or True for an {@code or} group.
		 */
		Group( boolean or ) {
			this( or, new ArrayList<>() );
		}

		/**
		 * Create a group with conditions.
		 *
		 * @param or       True for an {@code or} group.
		 * @param children The conditions.
		 */
		private Group( boolean or, List<Node> children ) {
			this.or			= or;
			this.children	= children;
		}

		/**
		 * Add a condition.
		 *
		 * @param node The condition.
		 */
		void add( Node node ) {
			children.add( node );
		}

		/**
		 * Whether this node renders nothing.
		 *
		 * @return True when there is nothing to render.
		 */
		@Override
		public boolean isEmpty() {
			return children.stream().allMatch( Node::isEmpty );
		}

		/**
		 * Append this node's HQL.
		 *
		 * @param hql The HQL being built.
		 * @param ctx Collects bound values.
		 */
		@Override
		public void render( StringBuilder hql, RenderContext ctx ) {
			List<Node>	live	= children.stream().filter( c -> !c.isEmpty() ).toList();
			boolean		wrap	= live.size() > 1;
			if ( wrap ) {
				hql.append( '(' );
			}
			for ( int i = 0; i < live.size(); i++ ) {
				if ( i > 0 ) {
					hql.append( or ? " or " : " and " );
				}
				live.get( i ).render( hql, ctx );
			}
			if ( wrap ) {
				hql.append( ')' );
			}
		}

		/**
		 * A copy that can be changed without changing this one.
		 *
		 * @return The copy.
		 */
		@Override
		public Group copy() {
			List<Node> copied = new ArrayList<>();
			children.forEach( c -> copied.add( c.copy() ) );
			return new Group( or, copied );
		}
	}

	/**
	 * A negated condition.
	 *
	 * @param child The condition to negate.
	 */
	record Not( Node child ) implements Node {

		/**
		 * Whether this node renders nothing.
		 *
		 * @return True when there is nothing to render.
		 */
		@Override
		public boolean isEmpty() {
			return child.isEmpty();
		}

		/**
		 * Append this node's HQL.
		 *
		 * @param hql The HQL being built.
		 * @param ctx Collects bound values.
		 */
		@Override
		public void render( StringBuilder hql, RenderContext ctx ) {
			hql.append( "not (" );
			child.render( hql, ctx );
			hql.append( ')' );
		}

		/**
		 * A copy that can be changed without changing this one.
		 *
		 * @return The copy.
		 */
		@Override
		public Node copy() {
			return new Not( child.copy() );
		}
	}
}
