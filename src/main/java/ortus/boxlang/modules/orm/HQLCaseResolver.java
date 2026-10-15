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
package ortus.boxlang.modules.orm;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.model.domain.JpaMetamodel;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.query.sqm.UnknownEntityException;

import ortus.boxlang.modules.orm.errors.ORMErrors;

/**
 * Lets HQL name entities and properties in any case, as BoxLang does.
 * <p>
 * Hibernate 6+ resolves entity and property names case-sensitively. This class compiles the HQL as written, so correct
 * HQL costs nothing extra. When Hibernate reports an unknown entity or property whose name matches exactly one declared
 * name ignoring case, the name is rewritten to its declared case and the HQL is compiled again, once per wrong name.
 * String literals and parameter names are never rewritten. The corrected HQL is remembered per application, so the retry
 * cost is paid once per distinct query. When nothing matches, or the match is ambiguous, Hibernate's error is thrown and
 * the usual "Did you mean" translation applies.
 */
final class HQLCaseResolver {

	/** The most names one query may have rewritten before giving up. */
	private static final int		MAX_FIXES			= 32;

	private static final Pattern	RESOLVE_ATTRIBUTE	= Pattern.compile( "Could not resolve attribute '([^']+)' of '([^']+)'" );
	private static final Pattern	PATH_EXPRESSION		= Pattern.compile( "Could not interpret path expression '([^']+)'" );

	/**
	 * Static utility class; not instantiable.
	 */
	private HQLCaseResolver() {
	}

	/**
	 * Compile HQL, rewriting entity and property names to their declared case when Hibernate cannot resolve them as
	 * written.
	 *
	 * @param <Q>     The compiled query type.
	 * @param app     The ORM application (holds the cache of corrected HQL).
	 * @param session The session the query is compiled in (for the metamodel).
	 * @param hql     The HQL as the developer wrote it.
	 * @param compile Compiles HQL into a query; throws when Hibernate rejects it.
	 *
	 * @return The compiled query.
	 */
	static <Q> Q compile( ORMApp app, Session session, String hql, Function<String, Q> compile ) {
		String		current	= app.correctedHQL( hql );
		Set<String>	seen	= new HashSet<>();
		seen.add( current );
		for ( int fixes = 0;; fixes++ ) {
			try {
				Q query = compile.apply( current );
				if ( !current.equals( hql ) ) {
					app.rememberCorrectedHQL( hql, current );
				}
				return query;
			} catch ( RuntimeException e ) {
				String fixed = fixes < MAX_FIXES ? fix( app, session, current, e ) : null;
				// No fix, or a fix we already tried (two names that only differ in case): report Hibernate's error.
				if ( fixed == null || !seen.add( fixed ) ) {
					throw e;
				}
				current = fixed;
			}
		}
	}

	/**
	 * Rewrite the one name Hibernate could not resolve, when it matches exactly one declared name ignoring case.
	 *
	 * @param app     The ORM application.
	 * @param session The session (for the metamodel).
	 * @param hql     The HQL that failed.
	 * @param error   Hibernate's error.
	 *
	 * @return The rewritten HQL, or null when the error is not a case mismatch this class can fix.
	 */
	private static String fix( ORMApp app, Session session, String hql, Throwable error ) {
		JpaMetamodel metamodel = session.getSessionFactory().unwrap( SessionFactoryImplementor.class ).getJpaMetamodel();
		for ( Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause() ) {
			if ( cause instanceof UnknownEntityException uee && uee.getEntityName() != null ) {
				return rewrite( hql, uee.getEntityName(), app.getEntityNames() );
			}
			String	message		= String.valueOf( cause.getMessage() );
			Matcher	attribute	= RESOLVE_ATTRIBUTE.matcher( message );
			if ( attribute.find() ) {
				return rewrite( hql, attribute.group( 1 ), attributeNames( app, metamodel, attribute.group( 2 ) ) );
			}
			Matcher path = PATH_EXPRESSION.matcher( message );
			if ( path.find() ) {
				// A bare property (no alias): it belongs to one of the entities the query names.
				String		name		= path.group( 1 );
				String		last		= name.substring( name.lastIndexOf( '.' ) + 1 );
				Set<String>	candidates	= new LinkedHashSet<>();
				for ( String entity : ORMErrors.entitiesIn( hql, app.getEntityNames() ) ) {
					candidates.addAll( attributeNames( app, metamodel, entity ) );
				}
				return rewrite( hql, last, candidates );
			}
		}
		return null;
	}

	/**
	 * Every attribute name of an entity or component, inherited ones included, in declared case.
	 *
	 * @param app       The ORM application (fallback property names).
	 * @param metamodel The Hibernate metamodel.
	 * @param typeName  The entity name, facade class name or component type name from Hibernate's message.
	 *
	 * @return The attribute names; empty when the type is unknown.
	 */
	private static Collection<String> attributeNames( ORMApp app, JpaMetamodel metamodel, String typeName ) {
		String					entity	= ORMErrors.entityName( typeName );
		ManagedDomainType<?>	type	= metamodel.findEntityType( entity );
		if ( type == null ) {
			type = metamodel.findManagedType( typeName );
		}
		if ( type == null ) {
			return app.getPropertyNames( entity );
		}
		Set<String> names = new LinkedHashSet<>();
		type.getAttributes().forEach( a -> names.add( a.getName() ) );
		return names;
	}

	/**
	 * Rewrite a name to the one candidate that matches it ignoring case.
	 *
	 * @param hql        The HQL.
	 * @param wrong      The name as written.
	 * @param candidates The declared names.
	 *
	 * @return The rewritten HQL, or null when no candidate or more than one matches, or the name does not occur.
	 */
	private static String rewrite( String hql, String wrong, Collection<String> candidates ) {
		List<String> matches = candidates.stream()
		    .filter( c -> c.equalsIgnoreCase( wrong ) && !c.equals( wrong ) )
		    .distinct()
		    .toList();
		return matches.size() == 1 ? replaceIdentifier( hql, wrong, matches.get( 0 ) ) : null;
	}

	/**
	 * Replace every occurrence of an identifier, outside string literals and parameter names.
	 *
	 * @param hql  The HQL.
	 * @param from The identifier to replace (exact case).
	 * @param to   Its replacement.
	 *
	 * @return The rewritten HQL, or null when the identifier does not occur.
	 */
	static String replaceIdentifier( String hql, String from, String to ) {
		StringBuilder	out			= new StringBuilder( hql.length() );
		boolean			replaced	= false;
		int				i			= 0;
		while ( i < hql.length() ) {
			char c = hql.charAt( i );
			if ( c == '\'' || c == '"' || c == '`' ) {
				// Copy a quoted literal (or quoted identifier) untouched; a doubled quote is an escaped quote.
				int end = i + 1;
				while ( end < hql.length() ) {
					if ( hql.charAt( end ) == c ) {
						if ( end + 1 < hql.length() && hql.charAt( end + 1 ) == c ) {
							end += 2;
							continue;
						}
						break;
					}
					end++;
				}
				end = Math.min( end + 1, hql.length() );
				out.append( hql, i, end );
				i = end;
			} else if ( Character.isJavaIdentifierStart( c ) ) {
				int end = i + 1;
				while ( end < hql.length() && Character.isJavaIdentifierPart( hql.charAt( end ) ) ) {
					end++;
				}
				String	token		= hql.substring( i, end );
				boolean	parameter	= i > 0 && hql.charAt( i - 1 ) == ':';
				if ( !parameter && token.equals( from ) ) {
					out.append( to );
					replaced = true;
				} else {
					out.append( token );
				}
				i = end;
			} else {
				out.append( c );
				i++;
			}
		}
		return replaced ? out.toString() : null;
	}
}
