package ortus.boxlang.modules.orm.errors;

import static com.google.common.truth.Truth.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.hibernate.LazyInitializationException;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.NonUniqueResultException;
import org.hibernate.PropertyValueException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.TransientPropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.id.IdentifierGenerationException;
import org.hibernate.query.QueryArgumentException;
import org.hibernate.query.SemanticException;
import org.hibernate.query.SyntaxException;
import org.hibernate.query.sqm.UnknownEntityException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.PersistenceException;
import ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Unit tests for the error translator: every Hibernate exception it maps, the facade-name rewrite, and "did you mean".
 * No database needed; the live counterparts are in ORMErrorMessagesTest.
 */
public class ORMErrorsTest {

	static final String				USER_FACADE		= EntityFacadeNaming.facadeClassName( "shop", "User" );
	static final String				ADDRESS_FACADE	= EntityFacadeNaming.facadeClassName( "shop", "Address" );

	static final ORMErrors.Context	CTX				= ORMErrors.Context.of( "entitySave" )
	    .withNames( List.of( "User", "Address", "Order" ), entity -> Map.of(
	        "User", List.of( "id", "name", "email", "address" ),
	        "Address", List.of( "id", "street", "city" ),
	        "Order", List.of( "id", "total" ) ).getOrDefault( entity, List.of() ) );

	/**
	 * Register a facade under a lower-case entity name, so the tests can prove the registry (not the class-name fallback)
	 * decides the entity name shown in messages.
	 */
	@BeforeAll
	public static void registerFacades() {
		// Registered under a lower-case entity name: the registry must win over the class-name fallback ("Account").
		FacadeSupport.register( "regtest", "account", ortus.boxlang.modules.orm.hibernate.facade.generated.regtest.AccountFacade.class );
	}

	/* ------------------------------------------------------------------------------------------------------------ */

	/**
	 * Test: Facade class names in a message become BoxLang entity names.
	 */
	@DisplayName( "Facade class names in a message become BoxLang entity names" )
	@Test
	public void testRewriteNames() {
		// Registered facades resolve through the registry; unregistered ones fall back to the class-name stem.
		String message = "Persistent instance of '"
		    + ortus.boxlang.modules.orm.hibernate.facade.generated.regtest.AccountFacade.class.getName() + "' references '"
		    + EntityFacadeNaming.facadeClassName( "other", "Invoice" ) + "'";
		assertThat( ORMErrors.rewriteNames( message ) ).isEqualTo( "Persistent instance of 'account' references 'Invoice'" );
		assertThat( ORMErrors.entityName( USER_FACADE ) ).isEqualTo( "User" );
		assertThat( ORMErrors.rewriteNames( "no facade here" ) ).isEqualTo( "no facade here" );
		assertThat( ORMErrors.rewriteNames( null ) ).isNull();
	}

	/**
	 * Test: Edit distance and did-you-mean suggestions.
	 */
	@DisplayName( "Edit distance and did-you-mean suggestions" )
	@Test
	public void testSuggestions() {
		assertThat( ORMErrors.distance( "nmae", "name" ) ).isEqualTo( 1 );
		assertThat( ORMErrors.distance( "kitten", "sitting" ) ).isEqualTo( 3 );
		assertThat( ORMErrors.distance( "", "abc" ) ).isEqualTo( 3 );
		assertThat( ORMErrors.closest( "nmae", List.of( "name", "email", "id" ) ) ).containsExactly( "name" );
		assertThat( ORMErrors.closest( "EMAIL", List.of( "name", "email" ) ) ).containsExactly( "email" );
		assertThat( ORMErrors.closest( "zzzzzz", List.of( "name", "email" ) ) ).isEmpty();
		assertThat( ORMErrors.closest( "x", List.of() ) ).isEmpty();
		assertThat( ORMErrors.suggestion( "Usr", List.of( "User", "Order" ) ) ).isEqualTo( " Did you mean [User]?" );
		assertThat( ORMErrors.suggestion( "qqq", List.of( "User" ) ) ).isEmpty();
	}

	/**
	 * Test: An ORMException passes through untouched.
	 */
	@DisplayName( "An ORMException passes through untouched" )
	@Test
	public void testOrmExceptionPassesThrough() {
		ORMException original = new ORMException( ORMErrorType.ARGUMENT, "x", "y" );
		assertThat( ORMErrors.translate( original, CTX ) ).isSameInstanceAs( original );
	}

	/**
	 * Test: A developer's own BoxLang error (e.g. from an event handler) is not rewritten.
	 */
	@DisplayName( "A developer's own BoxLang error (e.g. from an event handler) is not rewritten" )
	@Test
	public void testUserErrorPassesThrough() {
		BoxRuntimeException userError = new BoxRuntimeException( "my validation failed" );
		assertThat( ORMErrors.translate( userError, CTX ) ).isSameInstanceAs( userError );
		NullPointerException npe = new NullPointerException( "boom" );
		assertThat( ORMErrors.translate( npe, CTX ) ).isSameInstanceAs( npe );
	}

	/**
	 * Test: Orm.transient: unsaved association target.
	 */
	@DisplayName( "orm.transient: unsaved association target" )
	@Test
	public void testTransient() {
		ORMException e = translate( new TransientPropertyValueException( "raw", ADDRESS_FACADE, USER_FACADE, "address" ) );
		assertThat( e.getType() ).isEqualTo( "orm.transient" );
		assertThat( e.getMessage() ).isEqualTo( "User.address points to a Address that was never saved." );
		assertThat( e.getDetail() ).contains( "cascade=\"save-update\"" );
		assertThat( e.info( "property" ) ).isEqualTo( "address" );
		assertThat( e.info( "targetEntity" ) ).isEqualTo( "Address" );
	}

	/**
	 * Test: Orm.constraint.notNull: a notnull property with no value.
	 */
	@DisplayName( "orm.constraint.notNull: a notnull property with no value" )
	@Test
	public void testNotNullProperty() {
		ORMException e = translate(
		    new PropertyValueException( "not-null property references a null or transient value", USER_FACADE, "email" ) );
		assertThat( e.getType() ).isEqualTo( "orm.constraint.notNull" );
		assertThat( e.getMessage() ).contains( "User.email is required" );
	}

	/**
	 * Test: Orm.id.missing: assigned id not set.
	 */
	@DisplayName( "orm.id.missing: assigned id not set" )
	@Test
	public void testAssignedIdMissing() {
		ORMException e = translate( new IdentifierGenerationException(
		    "Identifier of entity '" + USER_FACADE + "' must be manually assigned before calling 'persist()'" ) );
		assertThat( e.getType() ).isEqualTo( "orm.id.missing" );
		assertThat( e.getMessage() ).startsWith( "User has an assigned id" );
	}

	/**
	 * Test: Orm.lazy.noSession: lazy collection and lazy proxy.
	 */
	@DisplayName( "orm.lazy.noSession: lazy collection and lazy proxy" )
	@Test
	public void testLazy() {
		ORMException collection = translate( new LazyInitializationException(
		    "Cannot lazily initialize collection of role '" + USER_FACADE + ".orders' with key '7' (no session)" ) );
		assertThat( collection.getType() ).isEqualTo( "orm.lazy.noSession" );
		assertThat( collection.getMessage() ).contains( "Cannot load User.orders (for User #7)" );
		assertThat( collection.getDetail() ).contains( "entityReload" );

		ORMException proxy = translate( new LazyInitializationException( "Could not initialize proxy [" + ADDRESS_FACADE + "#3] - no session" ) );
		assertThat( proxy.getMessage() ).startsWith( "Cannot load Address #3" );
	}

	/**
	 * Test: Orm.stale and orm.session.duplicate.
	 */
	@DisplayName( "orm.stale and orm.session.duplicate" )
	@Test
	public void testStaleAndDuplicate() {
		ORMException stale = translate( new StaleObjectStateException( USER_FACADE, 5 ) );
		assertThat( stale.getType() ).isEqualTo( "orm.stale" );
		assertThat( stale.getMessage() ).contains( "User #5 was changed or deleted" );

		ORMException dup = translate( new NonUniqueObjectException( 9, USER_FACADE ) );
		assertThat( dup.getType() ).isEqualTo( "orm.session.duplicate" );
		assertThat( dup.getMessage() ).contains( "A different User object with id [9]" );
		assertThat( dup.getDetail() ).contains( "entityMerge" );
	}

	/**
	 * Test: Orm.query.nonUnique.
	 */
	@DisplayName( "orm.query.nonUnique" )
	@Test
	public void testNonUnique() {
		ORMException e = translate( new NonUniqueResultException( 3 ) );
		assertThat( e.getType() ).isEqualTo( "orm.query.nonUnique" );
		assertThat( e.getMessage() ).contains( "returned 3" );
		assertThat( ORMErrors.nonUniqueResult( 2, "ormExecuteQuery", "from User" ).info( "hql" ) ).isEqualTo( "from User" );
	}

	/**
	 * Test: Orm.entity.notFound with a suggestion.
	 */
	@DisplayName( "orm.entity.notFound with a suggestion" )
	@Test
	public void testUnknownEntity() {
		ORMException e = translate( new UnknownEntityException( "Could not resolve root entity 'Usr'", "Usr" ) );
		assertThat( e.getType() ).isEqualTo( "orm.entity.notFound" );
		assertThat( e.getMessage() ).isEqualTo( "There is no entity named [Usr]. Did you mean [User]?" );
		assertThat( e.getDetail() ).contains( "Known entities: User, Address, Order" );
		ORMException direct = ORMErrors.entityNotFound( "Adress", List.of( "User", "Address" ), "entityNew" );
		assertThat( direct.getMessage() ).contains( "Did you mean [Address]?" );
	}

	/**
	 * Test: Orm.query.syntax with line, column and expected tokens.
	 */
	@DisplayName( "orm.query.syntax with line, column and expected tokens" )
	@Test
	public void testSyntax() {
		ORMException e = translate( new SyntaxException(
		    "At 1:0 and token 'form', mismatched input 'form', expecting one of the following tokens: DELETE, FROM, SELECT [form User]",
		    "form User" ) );
		assertThat( e.getType() ).isEqualTo( "orm.query.syntax" );
		assertThat( e.getMessage() ).isEqualTo( "HQL syntax error at line 1, column 1 near 'form'. HQL: form User" );
		assertThat( e.getDetail() ).isEqualTo( "Expected one of: DELETE, FROM, SELECT" );
	}

	/**
	 * Test: Orm.property.unknown from an HQL path, with a suggestion from the entities in the query.
	 */
	@DisplayName( "orm.property.unknown from an HQL path, with a suggestion from the entities in the query" )
	@Test
	public void testUnknownPropertyInHql() {
		ORMErrors.Context	ctx	= CTX.withQuery( "from User where nmae = 'x'", null );
		ORMException		e	= ( ORMException ) ORMErrors.translate( new SemanticException( "Could not interpret path expression 'nmae'" ), ctx );
		assertThat( e.getType() ).isEqualTo( "orm.property.unknown" );
		assertThat( e.getMessage() ).startsWith( "User has no property [nmae]. Did you mean [name]?" );
		assertThat( e.getMessage() ).contains( "HQL: from User where nmae = 'x'" );
	}

	/**
	 * Test: Orm.property.unknown from a resolve-attribute message names the entity.
	 */
	@DisplayName( "orm.property.unknown from a resolve-attribute message names the entity" )
	@Test
	public void testUnknownAttribute() {
		ORMException e = translate( new SemanticException( "Could not resolve attribute 'stret' of '" + ADDRESS_FACADE + "'" ) );
		assertThat( e.getMessage() ).startsWith( "Address has no property [stret]. Did you mean [street]?" );
	}

	/**
	 * Test: Orm.query.parameter: wrong type and empty string.
	 */
	@DisplayName( "orm.query.parameter: wrong type and empty string" )
	@Test
	public void testQueryArgument() {
		ORMException wrong = translate( new QueryArgumentException( "incompatible", Integer.class, "abc" ) );
		assertThat( wrong.getType() ).isEqualTo( "orm.query.parameter" );
		assertThat( wrong.getMessage() ).isEqualTo( "The value [abc] cannot be used as a whole number (Integer)." );
		ORMException empty = translate( new QueryArgumentException( "incompatible", Integer.class, "" ) );
		assertThat( empty.getMessage() ).contains( "An empty string" );
		assertThat( empty.getDetail() ).contains( "Pass null" );
	}

	/**
	 * Test: Orm.constraint.*: each constraint kind.
	 */
	@DisplayName( "orm.constraint.*: each constraint kind" )
	@Test
	public void testConstraints() {
		SQLException sql = new SQLException( "Duplicate entry 'Ada' for key 'uk_name'" );
		assertThat( translate( new ConstraintViolationException( "x", sql, "insert ...", ConstraintViolationException.ConstraintKind.UNIQUE, "uk_name" ) )
		    .getType() ).isEqualTo( "orm.constraint.unique" );
		assertThat( translate( new ConstraintViolationException( "x", sql, "insert ...", ConstraintViolationException.ConstraintKind.FOREIGN_KEY, "fk" ) )
		    .getType() ).isEqualTo( "orm.constraint.foreignKey" );
		assertThat( translate( new ConstraintViolationException( "x", sql, "insert ...", ConstraintViolationException.ConstraintKind.NOT_NULL, "nn" ) )
		    .getType() ).isEqualTo( "orm.constraint.notNull" );
		assertThat( translate( new ConstraintViolationException( "x", sql, "insert ...", ConstraintViolationException.ConstraintKind.CHECK, "ck" ) )
		    .getType() ).isEqualTo( "orm.constraint.check" );
		ORMException unique = translate(
		    new ConstraintViolationException( "x", sql, "insert into users", ConstraintViolationException.ConstraintKind.UNIQUE, "uk_name" ) );
		assertThat( unique.getMessage() ).contains( "[uk_name]" );
		assertThat( unique.info( "sql" ) ).isEqualTo( "insert into users" );
		assertThat( unique.getDetail() ).contains( "Duplicate entry" );
	}

	/**
	 * Test: A specific cause wrapped in a generic PersistenceException is found.
	 */
	@DisplayName( "A specific cause wrapped in a generic PersistenceException is found" )
	@Test
	public void testNestedCause() {
		PersistenceException wrapper = new PersistenceException( "wrapper",
		    new TransientPropertyValueException( "raw", ADDRESS_FACADE, USER_FACADE, "address" ) );
		assertThat( translate( wrapper ).getType() ).isEqualTo( "orm.transient" );
	}

	/**
	 * Test: A ClassCastException from passing a struct to an entity BIF becomes orm.argument.
	 */
	@DisplayName( "A ClassCastException from passing a struct to an entity BIF becomes orm.argument" )
	@Test
	public void testArgumentCast() {
		ClassCastException	cce	= new ClassCastException(
		    "class ortus.boxlang.runtime.types.Struct cannot be cast to class ortus.boxlang.runtime.runnables.IClassRunnable" );
		ORMException		e	= translate( cce );
		assertThat( e.getType() ).isEqualTo( "orm.argument" );
		assertThat( e.getMessage() ).isEqualTo( "entitySave() expects an ORM entity instance, but received a struct." );
	}

	/**
	 * Test: Unknown Hibernate errors still become orm with facade names rewritten.
	 */
	@DisplayName( "Unknown Hibernate errors still become orm with facade names rewritten" )
	@Test
	public void testGenericFallback() {
		ORMException e = translate( new org.hibernate.HibernateException( "something about " + USER_FACADE ) );
		assertThat( e.getType() ).isEqualTo( "orm" );
		assertThat( e.getMessage() ).isEqualTo( "something about User" );
	}

	/**
	 * Test: Every error type is under the orm prefix.
	 */
	@DisplayName( "Every error type is under the orm prefix" )
	@Test
	public void testTypesArePrefixed() {
		for ( ORMErrorType type : ORMErrorType.values() ) {
			assertThat( type.type() ).matches( "orm(\\.[a-zA-Z]+)*" );
		}
	}

	/**
	 * Translate with the shared test context and assert the result is an ORMException.
	 *
	 * @param t The exception to translate.
	 *
	 * @return The translated error.
	 */
	private static ORMException translate( Throwable t ) {
		RuntimeException result = ORMErrors.translate( t, CTX );
		assertThat( result ).isInstanceOf( ORMException.class );
		return ( ORMException ) result;
	}
}
