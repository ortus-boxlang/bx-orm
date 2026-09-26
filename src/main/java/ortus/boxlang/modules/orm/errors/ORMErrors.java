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
package ortus.boxlang.modules.orm.errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.hibernate.LazyInitializationException;
import org.hibernate.MappingException;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.NonUniqueResultException;
import org.hibernate.PropertyValueException;
import org.hibernate.QueryException;
import org.hibernate.QueryParameterException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.StaleStateException;
import org.hibernate.TransientObjectException;
import org.hibernate.TransientPropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.id.IdentifierGenerationException;
import org.hibernate.query.QueryArgumentException;
import org.hibernate.query.SemanticException;
import org.hibernate.query.SyntaxException;
import org.hibernate.query.sqm.PathElementException;
import org.hibernate.query.sqm.UnknownEntityException;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxLangException;

/**
 * Turns Hibernate, JPA and JDBC exceptions into {@link ORMException}s a BoxLang developer can act on.
 * <p>
 * Every translated error says what went wrong using BoxLang entity and property names (generated facade class names are
 * rewritten), how to fix it, and carries the context (entity, property, HQL, parameters, SQL, original message) in
 * {@code extendedInfo}. Exceptions that are not ORM-related (for example an error thrown by the developer's own event
 * handler) pass through unchanged.
 */
public final class ORMErrors {

	/** Matches a generated facade class name: {@code ortus...facade.generated.<namespace>.<Name>Facade}. */
	private static final Pattern	FACADE_NAME			= Pattern.compile(
	    Pattern.quote( EntityFacadeNaming.PACKAGE ) + "\\.[A-Za-z0-9_$]+\\.([A-Za-z0-9_$]+?)Facade\\b" );

	private static final Pattern	SYNTAX_POSITION		= Pattern.compile( "At (\\d+):(\\d+) and token '([^']*)'" );
	private static final Pattern	SYNTAX_EXPECTING	= Pattern.compile( "expecting one of the following tokens: ([^\\[]+)" );
	private static final Pattern	PATH_EXPRESSION		= Pattern.compile( "Could not interpret path expression '([^']+)'" );
	private static final Pattern	RESOLVE_ATTRIBUTE	= Pattern.compile( "Could not resolve attribute '([^']+)' of '([^']+)'" );
	private static final Pattern	LAZY_COLLECTION		= Pattern.compile( "collection of role '([^']+)\\.([^'.]+)' with key '([^']*)'" );
	private static final Pattern	LAZY_PROXY			= Pattern.compile( "initialize proxy \\[([^#\\]]+)#([^\\]]*)\\]" );
	private static final Pattern	NAMED_PARAMETER		= Pattern.compile( "named parameter '?:?([A-Za-z0-9_]+)'?" );
	private static final Pattern	HQL_ENTITIES		= Pattern.compile( "(?i)\\b(?:from|join|update|into)\\s+([A-Za-z_][A-Za-z0-9_.]*)" );
	private static final Pattern	ID_ASSIGNED			= Pattern.compile( "Identifier of entity '([^']+)' must be manually assigned" );

	/**
	 * Static utility class; not instantiable.
	 */
	private ORMErrors() {
	}

	/**
	 * Where an error happened and what it can use to explain itself. All fields are optional.
	 *
	 * @param operation      The BIF or operation name, e.g. {@code entitySave}.
	 * @param entityName     The BoxLang entity involved, if known.
	 * @param hql            The HQL being run, if any.
	 * @param params         The query parameters, if any.
	 * @param entityNames    Every known entity name (for "did you mean").
	 * @param propertyLookup Property names for an entity name (for "did you mean"); may return an empty list.
	 */
	public record Context(
	    String operation,
	    String entityName,
	    String hql,
	    Object params,
	    Collection<String> entityNames,
	    Function<String, Collection<String>> propertyLookup ) {

		/**
		 * A context that only knows the operation name.
		 *
		 * @param operation The BIF or operation name, e.g. {@code entitySave}; may be null.
		 *
		 * @return A context with no entity, query or names.
		 */
		public static Context of( String operation ) {
			return new Context( operation, null, null, null, List.of(), e -> List.of() );
		}

		/**
		 * A copy of this context for a specific entity.
		 *
		 * @param entity The BoxLang entity name.
		 *
		 * @return A new context with the entity set.
		 */
		public Context withEntity( String entity ) {
			return new Context( operation, entity, hql, params, entityNames, propertyLookup );
		}

		/**
		 * A copy of this context for a specific query.
		 *
		 * @param theHql    The HQL being run.
		 * @param theParams The query parameters (struct, array or null).
		 *
		 * @return A new context with the HQL and parameters set.
		 */
		public Context withQuery( String theHql, Object theParams ) {
			return new Context( operation, entityName, theHql, theParams, entityNames, propertyLookup );
		}

		/**
		 * A copy of this context that can make "Did you mean" suggestions.
		 *
		 * @param entities   Every known entity name (null for none).
		 * @param properties Property names for an entity name (null for none).
		 *
		 * @return A new context with the name sources set.
		 */
		public Context withNames( Collection<String> entities, Function<String, Collection<String>> properties ) {
			return new Context( operation, entityName, hql, params, entities == null ? List.of() : entities,
			    properties == null ? e -> List.of() : properties );
		}
	}

	/**
	 * Translate any throwable raised by ORM work into the exception to throw.
	 *
	 * @param error   The raised throwable.
	 * @param context Where it happened.
	 *
	 * @return An {@link ORMException} for ORM-related failures; otherwise the original exception (wrapped only if it is
	 *         a checked exception).
	 */
	public static RuntimeException translate( Throwable error, Context context ) {
		if ( error instanceof ORMException ormException ) {
			return ormException;
		}
		Context			ctx		= context == null ? Context.of( null ) : context;
		ORMException	mapped	= map( error, ctx );
		if ( mapped != null ) {
			return mapped;
		}
		if ( error instanceof RuntimeException runtime ) {
			return runtime;
		}
		return new ORMException( ORMErrorType.GENERIC, rewriteNames( String.valueOf( error.getMessage() ) ), "", baseInfo( ctx, error ), error );
	}

	/**
	 * Find the first ORM-related exception in the cause chain and map it to an {@link ORMException}.
	 *
	 * @param error The raised throwable.
	 * @param ctx   Where it happened.
	 *
	 * @return The translated exception, or null when nothing in the chain is ORM-related.
	 */
	private static ORMException map( Throwable error, Context ctx ) {
		// A developer's own BoxLang error (e.g. thrown from an event handler) is not ours to rewrite.
		if ( error instanceof BoxLangException && findOrmCause( error ) == null ) {
			return null;
		}
		Throwable ormCause = findOrmCause( error );
		if ( ormCause == null ) {
			return mapJavaArgumentError( error, ctx );
		}
		IStruct info = baseInfo( ctx, ormCause );

		if ( ormCause instanceof LazyInitializationException ) {
			return lazy( ormCause, info );
		}
		if ( ormCause instanceof TransientPropertyValueException tpv ) {
			String	owner	= entityName( tpv.getPropertyOwnerEntityName() );
			String	target	= entityName( tpv.getTransientEntityName() );
			String	prop	= tpv.getPropertyName();
			put( info, "entityName", owner );
			put( info, "property", prop );
			put( info, "targetEntity", target );
			return new ORMException( ORMErrorType.TRANSIENT,
			    String.format( "%s.%s points to a %s that was never saved.", owner, prop, target ),
			    String.format( "Save the %s first with entitySave(), or add cascade=\"save-update\" (or \"all\") to the %s property on %s.",
			        target, prop, owner ),
			    info, ormCause );
		}
		if ( ormCause instanceof TransientObjectException ) {
			return new ORMException( ORMErrorType.TRANSIENT,
			    "An entity references another entity that was never saved: " + rewriteNames( ormCause.getMessage() ),
			    "Save the referenced entity first with entitySave(), or add a cascade to the association.", info, ormCause );
		}
		if ( ormCause instanceof PropertyValueException pve ) {
			String	entity	= entityName( pve.getEntityName() );
			String	prop	= pve.getPropertyName();
			put( info, "entityName", entity );
			put( info, "property", prop );
			if ( String.valueOf( pve.getMessage() ).contains( "not-null" ) ) {
				return new ORMException( ORMErrorType.CONSTRAINT_NOT_NULL,
				    String.format( "%s.%s is required (notnull=\"true\") but has no value.", entity, prop ),
				    String.format( "Set %s on the %s before saving it.", prop, entity ), info, ormCause );
			}
			return new ORMException( ORMErrorType.PROPERTY_TYPE,
			    String.format( "%s.%s has an invalid value: %s", entity, prop, rewriteNames( pve.getMessage() ) ), "", info, ormCause );
		}
		if ( ormCause instanceof IdentifierGenerationException ) {
			Matcher m = ID_ASSIGNED.matcher( String.valueOf( ormCause.getMessage() ) );
			if ( m.find() ) {
				String entity = entityName( m.group( 1 ) );
				put( info, "entityName", entity );
				return new ORMException( ORMErrorType.ID_MISSING,
				    String.format( "%s has an assigned id (generator=\"assigned\") that was not set.", entity ),
				    String.format( "Set the id on the %s before calling entitySave(), or give its id property a generator.", entity ),
				    info, ormCause );
			}
			return new ORMException( ORMErrorType.ID_MISSING, "Could not create the id: " + rewriteNames( ormCause.getMessage() ), "", info,
			    ormCause );
		}
		if ( ormCause instanceof ConstraintViolationException cve ) {
			return constraint( cve, info );
		}
		if ( ormCause instanceof StaleObjectStateException stale ) {
			String entity = entityName( stale.getEntityName() );
			put( info, "entityName", entity );
			put( info, "id", stale.getIdentifier() );
			return new ORMException( ORMErrorType.STALE,
			    String.format( "%s #%s was changed or deleted by someone else after it was loaded.", entity, stale.getIdentifier() ),
			    "Reload it (entityReload or entityLoadByPK) and apply your change again.", info, ormCause );
		}
		if ( ormCause instanceof StaleStateException || ormCause instanceof OptimisticLockException ) {
			return new ORMException( ORMErrorType.STALE,
			    "A row was changed or deleted by someone else after it was loaded: " + rewriteNames( ormCause.getMessage() ),
			    "Reload the entity and apply your change again.", info, ormCause );
		}
		if ( ormCause instanceof NonUniqueObjectException nuo ) {
			String entity = entityName( nuo.getEntityName() );
			put( info, "entityName", entity );
			put( info, "id", nuo.getIdentifier() );
			return new ORMException( ORMErrorType.SESSION_DUPLICATE,
			    String.format( "A different %s object with id [%s] is already in this ORM session.", entity, nuo.getIdentifier() ),
			    "Use entityMerge() to copy your changes onto the managed instance, or keep working with the instance you loaded.",
			    info, ormCause );
		}
		if ( ormCause instanceof NonUniqueResultException nur ) {
			put( info, "resultCount", nur.getResultCount() );
			return nonUnique( nur.getResultCount(), info, ormCause );
		}
		if ( ormCause instanceof UnknownEntityException uee ) {
			return unknownEntity( uee.getEntityName(), ctx, info, ormCause );
		}
		if ( ormCause instanceof SyntaxException ) {
			return syntax( ormCause, ctx, info );
		}
		if ( ormCause instanceof PathElementException || ormCause instanceof SemanticException ) {
			return semantic( ormCause, ctx, info );
		}
		if ( ormCause instanceof QueryParameterException ) {
			return missingParameter( ormCause, info );
		}
		if ( ormCause instanceof QueryArgumentException qae ) {
			put( info, "argument", qae.getArgument() );
			String typeName = qae.getParameterType() == null ? "the column type" : friendlyType( qae.getParameterType() );
			if ( qae.getArgument() instanceof String s && s.isEmpty() ) {
				return new ORMException( ORMErrorType.QUERY_PARAMETER,
				    "An empty string was passed where a value of type " + typeName + " is expected.",
				    "Pass null (or leave the filter out) for 'no value'. Empty strings are only valid for text columns.", info, ormCause );
			}
			return new ORMException( ORMErrorType.QUERY_PARAMETER,
			    String.format( "The value [%s] cannot be used as %s.", qae.getArgument(), typeName ),
			    "Check the value you pass for this property or query parameter.", info, ormCause );
		}
		if ( ormCause instanceof QueryException qe ) {
			if ( String.valueOf( qe.getMessage() ).contains( "parameter" ) ) {
				return missingParameter( qe, info );
			}
			return new ORMException( ORMErrorType.QUERY_SEMANTIC, "The HQL is not valid: " + rewriteNames( qe.getMessage() ), "", info, qe );
		}
		if ( ormCause instanceof org.hibernate.PropertyAccessException pae ) {
			String owner = pae.getPersistentClass() == null ? null : entityName( pae.getPersistentClass().getName() );
			put( info, "entityName", owner );
			put( info, "property", pae.getPropertyName() );
			return new ORMException( ORMErrorType.PROPERTY_TYPE,
			    String.format( "Could not read or write %s.%s: a property holds a value of the wrong type.", owner, pae.getPropertyName() ),
			    "An association must be set to an entity instance (not an id or a string), and each property to a value that fits its ormtype.",
			    info, pae );
		}
		if ( ormCause instanceof JDBCException jdbc ) {
			put( info, "sql", jdbc.getSQL() );
			put( info, "sqlState", jdbc.getSQLState() );
			put( info, "databaseMessage", databaseMessage( jdbc ) );
			return new ORMException( ORMErrorType.SQL,
			    "The database rejected the query: " + firstNonBlank( databaseMessage( jdbc ), rewriteNames( jdbc.getMessage() ) ),
			    "The generated SQL is in extendedInfo.sql.", info, jdbc );
		}
		if ( ormCause instanceof MappingException ) {
			String msg = rewriteNames( ormCause.getMessage() );
			if ( msg.contains( "Unknown entity" ) || msg.contains( "entity type" ) ) {
				return new ORMException( ORMErrorType.ENTITY_NOT_FOUND, msg, "Check the entity name.", info, ormCause );
			}
			return new ORMException( ORMErrorType.BOOT, "Invalid ORM mapping: " + msg, "", info, ormCause );
		}
		String message = rewriteNames( String.valueOf( ormCause.getMessage() ) );
		if ( message.contains( "AttributeConverter" ) ) {
			return new ORMException( ORMErrorType.PROPERTY_TYPE,
			    "A property value cannot be stored in its database column: " + message.replaceFirst( "^.*?AttributeConverter:\\s*", "" ),
			    "Check the values you set on the entity against each property's ormtype.", info, ormCause );
		}
		return new ORMException( ORMErrorType.GENERIC, message, "", info, ormCause );
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Specific translations
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Translate a lazy-load failure (collection or to-one proxy read after its session was closed or cleared).
	 *
	 * @param cause The Hibernate {@code LazyInitializationException}.
	 * @param info  The context struct to enrich.
	 *
	 * @return An {@code orm.lazy.noSession} error naming the entity, property and id when Hibernate's message has them.
	 */
	private static ORMException lazy( Throwable cause, IStruct info ) {
		String	raw		= String.valueOf( cause.getMessage() );
		Matcher	col		= LAZY_COLLECTION.matcher( raw );
		String	hint	= "Load the entity again in the current session (entityLoadByPK or entityReload) before reading lazy "
		    + "associations, or map the association with lazy=\"false\".";
		if ( col.find() ) {
			String entity = entityName( col.group( 1 ) );
			put( info, "entityName", entity );
			put( info, "property", col.group( 2 ) );
			put( info, "id", col.group( 3 ) );
			return new ORMException( ORMErrorType.LAZY_NO_SESSION,
			    String.format( "Cannot load %s.%s (for %s #%s): the ORM session that loaded it was closed or cleared.", entity,
			        col.group( 2 ), entity, col.group( 3 ) ),
			    hint, info, cause );
		}
		Matcher proxy = LAZY_PROXY.matcher( raw );
		if ( proxy.find() ) {
			String entity = entityName( proxy.group( 1 ) );
			put( info, "entityName", entity );
			put( info, "id", proxy.group( 2 ) );
			return new ORMException( ORMErrorType.LAZY_NO_SESSION,
			    String.format( "Cannot load %s #%s: it is a lazy reference and the ORM session that loaded it was closed or cleared.",
			        entity, proxy.group( 2 ) ),
			    hint, info, cause );
		}
		return new ORMException( ORMErrorType.LAZY_NO_SESSION,
		    "Cannot load a lazy association: the ORM session that loaded it was closed or cleared.", hint, info, cause );
	}

	/**
	 * Translate a database constraint violation into the {@code orm.constraint} type for its kind.
	 *
	 * @param cve  The Hibernate constraint violation.
	 * @param info The context struct to enrich with the constraint name, SQL and database message.
	 *
	 * @return An {@code orm.constraint.*} error.
	 */
	private static ORMException constraint( ConstraintViolationException cve, IStruct info ) {
		String db = databaseMessage( cve );
		put( info, "constraint", cve.getConstraintName() );
		put( info, "sql", cve.getSQL() );
		put( info, "databaseMessage", db );
		String										name	= cve.getConstraintName() == null ? "" : " [" + cve.getConstraintName() + "]";
		ConstraintViolationException.ConstraintKind	kind	= cve.getKind();
		if ( kind == ConstraintViolationException.ConstraintKind.UNIQUE ) {
			return new ORMException( ORMErrorType.CONSTRAINT_UNIQUE,
			    "The change would duplicate a value protected by the unique constraint" + name + ".",
			    "Another row already has this value. Database said: " + db, info, cve );
		}
		if ( kind == ConstraintViolationException.ConstraintKind.FOREIGN_KEY ) {
			return new ORMException( ORMErrorType.CONSTRAINT_FOREIGN_KEY,
			    "The change breaks the foreign key" + name + ": it points to a row that does not exist, or deletes a row that is still referenced.",
			    "Save or keep the referenced row, or add a cascade to the association. Database said: " + db, info, cve );
		}
		if ( kind == ConstraintViolationException.ConstraintKind.NOT_NULL ) {
			return new ORMException( ORMErrorType.CONSTRAINT_NOT_NULL, "A required column has no value" + name + ".",
			    "Set the property before saving. Database said: " + db, info, cve );
		}
		if ( kind == ConstraintViolationException.ConstraintKind.CHECK ) {
			return new ORMException( ORMErrorType.CONSTRAINT_CHECK, "The change breaks the check constraint" + name + ".",
			    "Database said: " + db, info, cve );
		}
		return new ORMException( ORMErrorType.CONSTRAINT, "The database rejected the change" + name + ": " + db, "", info, cve );
	}

	/**
	 * Build the error for a unique query that matched more than one row.
	 *
	 * @param count How many rows matched, or 0 (or 1) when only "more than one" is known.
	 * @param info  The context struct.
	 * @param cause The original exception, or null.
	 *
	 * @return An {@code orm.query.nonUnique} error.
	 */
	private static ORMException nonUnique( int count, IStruct info, Throwable cause ) {
		return new ORMException( ORMErrorType.QUERY_NON_UNIQUE,
		    String.format( "The query was expected to return one result but returned %s.", count > 1 ? count : "more than one" ),
		    "Add conditions so only one row matches, or ask for the first row instead of a unique result.", info, cause );
	}

	/**
	 * A unique query returned more than one row. Used by bx-orm's own unique queries.
	 *
	 * @param count     How many rows came back, or 0 when only "more than one" is known (bx-orm fetches at most two rows
	 *                  to check uniqueness).
	 * @param operation The BIF name.
	 * @param hql       The query, if any.
	 *
	 * @return An {@code orm.query.nonUnique} error to throw.
	 */
	public static ORMException nonUniqueResult( int count, String operation, String hql ) {
		IStruct info = new Struct();
		put( info, "operation", operation );
		put( info, "hql", hql );
		put( info, "resultCount", count );
		return nonUnique( count, info, null );
	}

	/**
	 * Build the error for an entity name that does not exist, with a suggestion and the known entities.
	 *
	 * @param name  The entity name that was asked for.
	 * @param ctx   The context with the known entity names.
	 * @param info  The context struct.
	 * @param cause The original exception, or null.
	 *
	 * @return An {@code orm.entity.notFound} error.
	 */
	private static ORMException unknownEntity( String name, Context ctx, IStruct info, Throwable cause ) {
		put( info, "entityName", name );
		put( info, "knownEntities", String.join( ", ", ctx.entityNames() ) );
		return new ORMException( ORMErrorType.ENTITY_NOT_FOUND,
		    String.format( "There is no entity named [%s].%s", name, suggestion( name, ctx.entityNames() ) ),
		    ctx.entityNames().isEmpty() ? "" : "Known entities: " + String.join( ", ", ctx.entityNames() ), info, cause );
	}

	/**
	 * The error for an entity name that does not exist, with a "did you mean".
	 *
	 * @param name        The name that was asked for.
	 * @param entityNames Every known entity name.
	 * @param operation   The BIF name (may be null).
	 *
	 * @return An {@code orm.entity.notFound} error to throw.
	 */
	public static ORMException entityNotFound( String name, Collection<String> entityNames, String operation ) {
		Context ctx = Context.of( operation ).withNames( entityNames, null );
		return unknownEntity( name, ctx, baseInfo( ctx, null ), null );
	}

	/**
	 * Translate an HQL parse error, pulling the line, column, token and expected tokens out of Hibernate's message.
	 *
	 * @param cause The Hibernate {@code SyntaxException}.
	 * @param ctx   The context (for the HQL).
	 * @param info  The context struct to enrich with line and column.
	 *
	 * @return An {@code orm.query.syntax} error.
	 */
	private static ORMException syntax( Throwable cause, Context ctx, IStruct info ) {
		String	raw		= String.valueOf( cause.getMessage() );
		Matcher	pos		= SYNTAX_POSITION.matcher( raw );
		String	where	= "";
		if ( pos.find() ) {
			int	line	= Integer.parseInt( pos.group( 1 ) );
			int	column	= Integer.parseInt( pos.group( 2 ) ) + 1;
			put( info, "line", line );
			put( info, "column", column );
			where = String.format( " at line %d, column %d near '%s'", line, column, pos.group( 3 ) );
		}
		Matcher	expecting	= SYNTAX_EXPECTING.matcher( raw );
		String	detail		= expecting.find() ? "Expected one of: " + expecting.group( 1 ).trim() : "";
		String	hql			= hqlOf( cause, ctx );
		return new ORMException( ORMErrorType.QUERY_SYNTAX, "HQL syntax error" + where + "." + ( hql == null ? "" : " HQL: " + hql ),
		    detail, info, cause );
	}

	/**
	 * Translate an HQL that parses but is invalid. Unknown attributes and paths become {@code orm.property.unknown} with a
	 * suggestion taken from the entities the query uses; anything else is {@code orm.query.semantic}.
	 *
	 * @param cause The Hibernate semantic or path exception.
	 * @param ctx   The context (HQL and name sources).
	 * @param info  The context struct.
	 *
	 * @return The translated error.
	 */
	private static ORMException semantic( Throwable cause, Context ctx, IStruct info ) {
		String	raw		= rewriteNames( String.valueOf( cause.getMessage() ) );
		String	hql		= hqlOf( cause, ctx );
		Matcher	attr	= RESOLVE_ATTRIBUTE.matcher( String.valueOf( cause.getMessage() ) );
		if ( attr.find() ) {
			String	entity		= entityName( attr.group( 2 ) );
			String	property	= attr.group( 1 );
			return unknownProperty( entity, property, ctx.propertyLookup().apply( entity ), hql, info, cause );
		}
		Matcher path = PATH_EXPRESSION.matcher( raw );
		if ( path.find() ) {
			String		property	= path.group( 1 );
			String		last		= property.contains( "." ) ? property.substring( property.lastIndexOf( '.' ) + 1 ) : property;
			Set<String>	candidates	= new LinkedHashSet<>();
			String		entity		= null;
			for ( String e : entitiesIn( hql, ctx.entityNames() ) ) {
				entity = entity == null ? e : entity;
				candidates.addAll( ctx.propertyLookup().apply( e ) );
			}
			return unknownProperty( entity, last, candidates, hql, info, cause );
		}
		return new ORMException( ORMErrorType.QUERY_SEMANTIC, "The HQL is not valid: " + raw + ( hql == null ? "" : " HQL: " + hql ), "",
		    info, cause );
	}

	/**
	 * Build the error for a property that does not exist on an entity.
	 *
	 * @param entity     The entity name, or null when unknown.
	 * @param property   The property that was asked for.
	 * @param candidates The entity's property names, for the suggestion and the detail.
	 * @param hql        The HQL involved, or null.
	 * @param info       The context struct.
	 * @param cause      The original exception, or null.
	 *
	 * @return An {@code orm.property.unknown} error.
	 */
	private static ORMException unknownProperty( String entity, String property, Collection<String> candidates, String hql, IStruct info,
	    Throwable cause ) {
		put( info, "entityName", entity );
		put( info, "property", property );
		String owner = entity == null ? "The entity" : entity;
		return new ORMException( ORMErrorType.PROPERTY_UNKNOWN,
		    String.format( "%s has no property [%s].%s", owner, property, suggestion( property, candidates ) )
		        + ( hql == null ? "" : " HQL: " + hql ),
		    candidates.isEmpty() ? "" : "Properties: " + String.join( ", ", candidates ), info, cause );
	}

	/**
	 * The error for a property name that does not exist on an entity, with a "did you mean".
	 *
	 * @param entity     The entity name.
	 * @param property   The property that was asked for.
	 * @param candidates The entity's property names.
	 * @param operation  The BIF name.
	 *
	 * @return An {@code orm.property.unknown} error to throw.
	 */
	public static ORMException propertyNotFound( String entity, String property, Collection<String> candidates, String operation ) {
		IStruct info = new Struct();
		put( info, "operation", operation );
		return unknownProperty( entity, property, candidates, null, info, null );
	}

	/**
	 * Translate a missing or invalid query parameter, naming the parameter when Hibernate's message does.
	 *
	 * @param cause The Hibernate parameter exception.
	 * @param info  The context struct to enrich with the parameter name.
	 *
	 * @return An {@code orm.query.parameter} error.
	 */
	private static ORMException missingParameter( Throwable cause, IStruct info ) {
		String	raw		= String.valueOf( cause.getMessage() );
		Matcher	named	= NAMED_PARAMETER.matcher( raw );
		if ( raw.contains( "No argument" ) && named.find() ) {
			put( info, "parameter", named.group( 1 ) );
			return new ORMException( ORMErrorType.QUERY_PARAMETER,
			    String.format( "The HQL parameter [:%s] has no value.", named.group( 1 ) ),
			    String.format( "Pass it in the params struct, e.g. { %s : value }.", named.group( 1 ) ), info, cause );
		}
		return new ORMException( ORMErrorType.QUERY_PARAMETER, "Invalid query parameters: " + rewriteNames( raw ), "", info, cause );
	}

	/**
	 * Map common Java errors a BIF raises when it receives the wrong kind of value (e.g. a struct instead of an entity).
	 *
	 * @param error The raised throwable.
	 * @param ctx   The context (for the BIF name).
	 *
	 * @return An {@code orm.argument} error, or null when the error is not one of these.
	 */
	private static ORMException mapJavaArgumentError( Throwable error, Context ctx ) {
		if ( error instanceof ClassCastException cce && String.valueOf( cce.getMessage() ).contains( "IClassRunnable" ) ) {
			String	got			= describeCastSource( cce.getMessage() );
			String	operation	= ctx.operation() == null ? "This function" : ctx.operation() + "()";
			return new ORMException( ORMErrorType.ARGUMENT,
			    String.format( "%s expects an ORM entity instance, but received %s.", operation, got ),
			    "Pass an entity created with entityNew() or loaded with entityLoad().", baseInfo( ctx, error ), error );
		}
		return null;
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Helpers
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Replace every generated facade class name in a message with its BoxLang entity name.
	 *
	 * @param message The message.
	 *
	 * @return The message with BoxLang entity names.
	 */
	public static String rewriteNames( String message ) {
		if ( message == null || !message.contains( EntityFacadeNaming.PACKAGE ) ) {
			return message;
		}
		Matcher			m	= FACADE_NAME.matcher( message );
		StringBuilder	sb	= new StringBuilder();
		while ( m.find() ) {
			String entity = FacadeSupport.entityNameForFacade( m.group( 0 ) );
			m.appendReplacement( sb, Matcher.quoteReplacement( entity != null ? entity : m.group( 1 ) ) );
		}
		m.appendTail( sb );
		return sb.toString();
	}

	/**
	 * The BoxLang entity name for a Hibernate entity name (which is the generated facade class name).
	 *
	 * @param hibernateName The Hibernate entity name; may be null.
	 *
	 * @return The BoxLang entity name, the rewritten input when it is not a registered facade, or null for null input.
	 */
	public static String entityName( String hibernateName ) {
		if ( hibernateName == null ) {
			return null;
		}
		String entity = FacadeSupport.entityNameForFacade( hibernateName );
		return entity != null ? entity : rewriteNames( hibernateName );
	}

	/**
	 * A " Did you mean [x]?" sentence, or an empty string when nothing is close.
	 *
	 * @param input      What the developer typed.
	 * @param candidates The valid names.
	 *
	 * @return The sentence (with a leading space) or an empty string.
	 */
	public static String suggestion( String input, Collection<String> candidates ) {
		List<String> close = closest( input, candidates );
		if ( close.isEmpty() ) {
			return "";
		}
		return close.size() == 1 ? " Did you mean [" + close.get( 0 ) + "]?"
		    : " Did you mean one of [" + String.join( ", ", close ) + "]?";
	}

	/**
	 * The candidates closest to the input: a case-insensitive match first, then up to three names within a small edit
	 * distance (about a third of the input length, at least 1 and at most 3).
	 *
	 * @param input      What the developer typed.
	 * @param candidates The valid names.
	 *
	 * @return The closest names, best first; empty when none is close.
	 */
	public static List<String> closest( String input, Collection<String> candidates ) {
		if ( input == null || input.isBlank() || candidates == null || candidates.isEmpty() ) {
			return List.of();
		}
		String lower = input.toLowerCase( Locale.ROOT );
		for ( String c : candidates ) {
			if ( c != null && c.equalsIgnoreCase( input ) && !c.equals( input ) ) {
				return List.of( c );
			}
		}
		int				max		= Math.max( 1, Math.min( 3, input.length() / 3 ) );
		List<String>	scored	= new ArrayList<>();
		for ( String c : new LinkedHashSet<>( candidates ) ) {
			if ( c == null || c.equals( input ) ) {
				continue;
			}
			if ( distance( lower, c.toLowerCase( Locale.ROOT ) ) <= max ) {
				scored.add( c );
			}
		}
		scored.sort( Comparator.comparingInt( c -> distance( lower, c.toLowerCase( Locale.ROOT ) ) ) );
		return scored.size() > 3 ? scored.subList( 0, 3 ) : scored;
	}

	/**
	 * Edit distance where inserting, deleting, replacing or swapping two adjacent characters each cost 1 (optimal string
	 * alignment), so a transposition typo like {@code nmae} is one step from {@code name}.
	 *
	 * @param a The first string.
	 * @param b The second string.
	 *
	 * @return The number of edits between them.
	 */
	static int distance( String a, String b ) {
		int[][] d = new int[ a.length() + 1 ][ b.length() + 1 ];
		for ( int i = 0; i <= a.length(); i++ ) {
			d[ i ][ 0 ] = i;
		}
		for ( int j = 0; j <= b.length(); j++ ) {
			d[ 0 ][ j ] = j;
		}
		for ( int i = 1; i <= a.length(); i++ ) {
			for ( int j = 1; j <= b.length(); j++ ) {
				int cost = a.charAt( i - 1 ) == b.charAt( j - 1 ) ? 0 : 1;
				d[ i ][ j ] = Math.min( Math.min( d[ i - 1 ][ j ] + 1, d[ i ][ j - 1 ] + 1 ), d[ i - 1 ][ j - 1 ] + cost );
				if ( i > 1 && j > 1 && a.charAt( i - 1 ) == b.charAt( j - 2 ) && a.charAt( i - 2 ) == b.charAt( j - 1 ) ) {
					d[ i ][ j ] = Math.min( d[ i ][ j ], d[ i - 2 ][ j - 2 ] + 1 );
				}
			}
		}
		return d[ a.length() ][ b.length() ];
	}

	/**
	 * Walk the cause chain for the first Hibernate, JPA or query-argument exception, preferring a more specific cause
	 * nested inside it (e.g. a constraint violation wrapped in a generic persistence exception).
	 *
	 * @param error The raised throwable.
	 *
	 * @return The ORM-related cause, or null when there is none.
	 */
	private static Throwable findOrmCause( Throwable error ) {
		Throwable current = error;
		for ( int depth = 0; current != null && depth < 15; depth++ ) {
			if ( current instanceof HibernateException || current instanceof PersistenceException
			    || current instanceof QueryArgumentException || current instanceof PathElementException ) {
				// Prefer a more specific nested ORM cause (e.g. a ConstraintViolationException wrapped in a generic one).
				Throwable deeper = current.getCause();
				while ( deeper != null && deeper != current ) {
					if ( isSpecific( deeper ) ) {
						return deeper;
					}
					deeper = deeper.getCause();
				}
				return current;
			}
			if ( current.getCause() == current ) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}

	/**
	 * Whether an exception is one of the specific Hibernate types {@link #map} has a dedicated translation for.
	 *
	 * @param t The exception.
	 *
	 * @return True for a specific, translatable type.
	 */
	private static boolean isSpecific( Throwable t ) {
		return t instanceof ConstraintViolationException || t instanceof LazyInitializationException
		    || t instanceof TransientObjectException || t instanceof PropertyValueException || t instanceof StaleStateException
		    || t instanceof NonUniqueObjectException || t instanceof SemanticException || t instanceof SyntaxException
		    || t instanceof QueryArgumentException || t instanceof PathElementException;
	}

	/**
	 * The context struct every translated error starts with: operation, entity, HQL, params, and the original exception's
	 * class and (rewritten) message.
	 *
	 * @param ctx   The error context.
	 * @param cause The original exception, or null.
	 *
	 * @return A new struct.
	 */
	private static IStruct baseInfo( Context ctx, Throwable cause ) {
		IStruct info = new Struct();
		put( info, "operation", ctx.operation() );
		put( info, "entityName", ctx.entityName() );
		put( info, "hql", ctx.hql() );
		put( info, "params", ctx.params() );
		if ( cause != null ) {
			put( info, "hibernateException", cause.getClass().getName() );
			put( info, "originalMessage", rewriteNames( cause.getMessage() ) );
		}
		return info;
	}

	/**
	 * Put a value into the context struct, skipping nulls.
	 *
	 * @param info  The struct.
	 * @param key   The key name.
	 * @param value The value; ignored when null.
	 */
	private static void put( IStruct info, String key, Object value ) {
		if ( value != null ) {
			info.put( Key.of( key ), value );
		}
	}

	/**
	 * The HQL for an error: from the context, or from Hibernate's query exception.
	 *
	 * @param cause The exception.
	 * @param ctx   The context.
	 *
	 * @return The HQL, or null when unknown.
	 */
	private static String hqlOf( Throwable cause, Context ctx ) {
		if ( ctx.hql() != null ) {
			return ctx.hql();
		}
		return cause instanceof QueryException qe ? qe.getQueryString() : null;
	}

	/**
	 * Entity names referenced in an HQL string (FROM / JOIN / UPDATE / INTO targets that are known entities).
	 *
	 * @param hql   The HQL; may be null.
	 * @param known Every known entity name.
	 *
	 * @return The known entities the query names, in order of appearance.
	 */
	private static List<String> entitiesIn( String hql, Collection<String> known ) {
		List<String> found = new ArrayList<>();
		if ( hql == null ) {
			return found;
		}
		Matcher m = HQL_ENTITIES.matcher( hql );
		while ( m.find() ) {
			String name = m.group( 1 );
			for ( String k : known ) {
				if ( k.equalsIgnoreCase( name ) && !found.contains( k ) ) {
					found.add( k );
				}
			}
		}
		return found;
	}

	/**
	 * A developer-friendly name for the Java type a parameter expects, e.g. "a whole number (Integer)".
	 *
	 * @param type The Java type.
	 *
	 * @return The friendly name.
	 */
	private static String friendlyType( Class<?> type ) {
		String simple = type.getSimpleName();
		return switch ( simple ) {
			case "Integer", "Long", "Short", "BigInteger" -> "a whole number (" + simple + ")";
			case "Double", "Float", "BigDecimal" -> "a number (" + simple + ")";
			case "Boolean" -> "a boolean";
			case "Timestamp", "Date", "LocalDate", "LocalDateTime", "Instant", "OffsetDateTime", "ZonedDateTime" -> "a date/time (" + simple + ")";
			default -> simple;
		};
	}

	/**
	 * Describe what was passed, from a {@code ClassCastException} message, e.g. "a struct" or "a string".
	 *
	 * @param message The ClassCastException message.
	 *
	 * @return A short description of the value's type.
	 */
	private static String describeCastSource( String message ) {
		if ( message == null ) {
			return "a value that is not an entity";
		}
		Matcher m = Pattern.compile( "class ([\\w.$]+) cannot be cast" ).matcher( message );
		if ( !m.find() ) {
			return "a value that is not an entity";
		}
		String cls = m.group( 1 );
		if ( cls.endsWith( ".Struct" ) || cls.contains( "IStruct" ) ) {
			return "a struct";
		}
		if ( cls.equals( "java.lang.String" ) ) {
			return "a string";
		}
		if ( cls.endsWith( ".Array" ) ) {
			return "an array";
		}
		if ( cls.startsWith( "java.lang." ) ) {
			return "a " + cls.substring( "java.lang.".length() ).toLowerCase( Locale.ROOT );
		}
		return "a " + cls.substring( cls.lastIndexOf( '.' ) + 1 );
	}

	/**
	 * The database driver's own message (from the wrapped SQLException), falling back to Hibernate's.
	 *
	 * @param jdbc The JDBC exception.
	 *
	 * @return The message, or an empty string.
	 */
	private static String databaseMessage( JDBCException jdbc ) {
		String driver = jdbc.getSQLException() == null ? null : jdbc.getSQLException().getMessage();
		return firstNonBlank( driver, firstNonBlank( jdbc.getErrorMessage(), "" ) );
	}

	/**
	 * The first of two strings that is not null or blank.
	 *
	 * @param a The preferred value.
	 * @param b The fallback.
	 *
	 * @return {@code a} when it has text, otherwise {@code b}.
	 */
	private static String firstNonBlank( String a, String b ) {
		return a != null && !a.isBlank() ? a : b;
	}
}
