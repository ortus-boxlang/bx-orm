package ortus.boxlang.modules.orm.errors;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import tools.BaseORMTest;

/**
 * Live (MySQL) checks of what a developer sees for common mistakes: every case asserts the orm.* type and the message.
 * Writes run inside a rolled-back transaction so the seed data is untouched.
 */
public class ORMErrorMessagesTest extends BaseORMTest {

	static final Key ERR = Key.of( "err" );

	/**
	 * Clear the ORM session after each test so no entity leaks into the next one.
	 */
	@AfterEach
	public void clearSession() {
		instance.executeSource( "try { ormClearSession(); } catch ( any e ) {}", context );
	}

	/**
	 * Run BoxLang code that must throw, inside a rolled-back transaction.
	 *
	 * @param code BoxLang statements to run.
	 *
	 * @return { type, message, detail, extendedInfo } of the error, or { type : "NO ERROR" } when nothing was thrown.
	 */
	private IStruct error( String code ) {
		// @formatter:off
		instance.executeSource(
		    """
		    err = {};
		    try {
		        transaction {
		            try {
		                %s
		            } finally {
		                transactionRollback();
		            }
		        }
		        err = { type : "NO ERROR" };
		    } catch ( any e ) {
		        err = { type : e.type, message : e.message, detail : e.detail, extendedInfo : e.extendedInfo ?: {} };
		    }
		    """.formatted( code ),
		    context );
		// @formatter:on
		return variables.getAsStruct( ERR );
	}

	/**
	 * Assert an error's type and message, and that the message never shows a generated facade class name.
	 *
	 * @param err             The error struct from {@link #error(String)}.
	 * @param type            The expected orm.* type.
	 * @param messageFragment Text the message must contain.
	 */
	private static void assertError( IStruct err, String type, String messageFragment ) {
		assertThat( err.getAsString( Key.type ) ).isEqualTo( type );
		assertThat( err.getAsString( Key.message ) ).contains( messageFragment );
		assertThat( err.getAsString( Key.message ) ).doesNotContain( "ortus.boxlang.modules.orm.hibernate.facade.generated" );
	}

	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: EntityNew with a misspelled entity name suggests the right one.
	 */
	@DisplayName( "entityNew with a misspelled entity name suggests the right one" )
	@Test
	public void testEntityNewTypo() {
		IStruct err = error( "entityNew( \"Manufactuer\" );" );
		assertError( err, "orm.entity.notFound", "There is no entity named [Manufactuer]. Did you mean [Manufacturer]?" );
		assertThat( err.getAsString( Key.detail ) ).contains( "Known entities:" );
	}

	/**
	 * Test: EntityLoad with a misspelled entity name suggests the right one.
	 */
	@DisplayName( "entityLoad with a misspelled entity name suggests the right one" )
	@Test
	public void testEntityLoadTypo() {
		assertError( error( "entityLoad( \"Manufactuer\" );" ), "orm.entity.notFound", "Did you mean [Manufacturer]?" );
	}

	/**
	 * Test: EntityLoad filter with an unknown property suggests the right one.
	 */
	@DisplayName( "entityLoad filter with an unknown property suggests the right one" )
	@Test
	public void testFilterPropertyTypo() {
		IStruct err = error( "entityLoad( \"Manufacturer\", { nmae : \"x\" } );" );
		assertError( err, "orm.property.unknown", "Manufacturer has no property [nmae]. Did you mean [name]?" );
		assertThat( err.getAsString( Key.detail ) ).contains( "Properties:" );
	}

	/**
	 * Test: EntityLoad sort order on an unknown property suggests the right one.
	 */
	@DisplayName( "entityLoad sort order on an unknown property suggests the right one" )
	@Test
	public void testSortPropertyTypo() {
		assertError( error( "entityLoad( \"Manufacturer\", {}, \"nmae asc\" );" ), "orm.property.unknown",
		    "Manufacturer has no property [nmae]. Did you mean [name]?" );
	}

	/**
	 * Test: An empty string filter on an integer property is explained.
	 */
	@DisplayName( "An empty string filter on an integer property is explained" )
	@Test
	public void testEmptyStringFilter() {
		IStruct err = error( "entityLoad( \"Manufacturer\", { id : \"\" } );" );
		assertError( err, "orm.query.parameter", "The filter for Manufacturer.id is an empty string" );
		assertThat( err.getAsString( Key.detail ) ).contains( "Pass null" );
	}

	/**
	 * Test: EntityLoadByPK with an id of the wrong type names the entity and id type.
	 */
	@DisplayName( "entityLoadByPK with an id of the wrong type names the entity and id type" )
	@Test
	public void testBadId() {
		assertError( error( "entityLoadByPK( \"Manufacturer\", \"abc\" );" ), "orm.argument",
		    "[abc] is not a valid id for Manufacturer, whose id is of type Integer." );
	}

	/**
	 * Test: HQL with an unknown entity suggests the right one.
	 */
	@DisplayName( "HQL with an unknown entity suggests the right one" )
	@Test
	public void testHqlUnknownEntity() {
		assertError( error( "ormExecuteQuery( \"from Manufactuer\" );" ), "orm.entity.notFound",
		    "There is no entity named [Manufactuer]. Did you mean [Manufacturer]?" );
	}

	/**
	 * Test: HQL with an unknown property suggests the right one and shows the HQL.
	 */
	@DisplayName( "HQL with an unknown property suggests the right one and shows the HQL" )
	@Test
	public void testHqlUnknownProperty() {
		IStruct err = error( "ormExecuteQuery( \"from Manufacturer where nmae = 'x'\" );" );
		assertError( err, "orm.property.unknown", "Manufacturer has no property [nmae]. Did you mean [name]?" );
		assertThat( err.getAsString( Key.message ) ).contains( "HQL: from Manufacturer where nmae = 'x'" );
	}

	/**
	 * Test: HQL syntax errors give the position and the expected tokens.
	 */
	@DisplayName( "HQL syntax errors give the position and the expected tokens" )
	@Test
	public void testHqlSyntax() {
		IStruct err = error( "ormExecuteQuery( \"form Manufacturer\" );" );
		assertError( err, "orm.query.syntax", "HQL syntax error at line 1, column 1 near 'form'." );
		assertThat( err.getAsString( Key.detail ) ).contains( "Expected one of:" );
	}

	/**
	 * Test: A missing named HQL parameter is named.
	 */
	@DisplayName( "A missing named HQL parameter is named" )
	@Test
	public void testHqlMissingParameter() {
		IStruct err = error( "ormExecuteQuery( \"from Manufacturer where id = :id\" );" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.query.parameter" );
		assertThat( err.getAsString( Key.message ) ).contains( "id" );
	}

	/**
	 * Test: Named HQL parameters with an array of values explain the mismatch.
	 */
	@DisplayName( "Named HQL parameters with an array of values explain the mismatch" )
	@Test
	public void testHqlNamedWithArray() {
		assertError( error( "ormExecuteQuery( \"from Manufacturer where id = :id\", [ 1 ] );" ), "orm.query.parameter",
		    "named parameter [:id] but the params are positional" );
	}

	/**
	 * Test: An extra HQL parameter is ignored, not an error.
	 */
	@DisplayName( "An extra HQL parameter is ignored, not an error" )
	@Test
	public void testHqlExtraParameter() {
		assertThat( error( "ormExecuteQuery( \"from Manufacturer where id = :id\", { id : 1, extra : 2 } );" ).getAsString( Key.type ) )
		    .isEqualTo( "NO ERROR" );
	}

	/**
	 * Test: An HQL parameter of the wrong type is explained.
	 */
	@DisplayName( "An HQL parameter of the wrong type is explained" )
	@Test
	public void testHqlParameterType() {
		assertError( error( "ormExecuteQuery( \"from Manufacturer where id = :id\", { id : \"abc\" } );" ), "orm.query.parameter",
		    "The value [abc] cannot be used as a whole number (Integer)." );
	}

	/**
	 * Test: A unique HQL query that matches many rows is an error.
	 */
	@DisplayName( "A unique HQL query that matches many rows is an error" )
	@Test
	public void testHqlUniqueManyRows() {
		assertError( error( "ormExecuteQuery( \"from cbContent\", {}, true );" ), "orm.query.nonUnique",
		    "The query was expected to return one result but returned more than one." );
	}

	/**
	 * Test: UniqueFirst keeps the old take-the-first-row behavior for HQL.
	 */
	@DisplayName( "uniqueFirst keeps the old take-the-first-row behavior for HQL" )
	@Test
	public void testHqlUniqueFirst() {
		// @formatter:off
		instance.executeSource(
		    """
		    one = ormExecuteQuery( "from cbContent", {}, false, { uniqueFirst : true } );
		    viaOptions = ormExecuteQuery( "from cbContent", {}, { uniqueFirst : true } );
		    result = !isNull( one ) && !isArray( one ) && !isNull( viaOptions ) && !isArray( viaOptions );
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( true );
	}

	/**
	 * Test: Unique=true inside the options struct is honored (and strict).
	 */
	@DisplayName( "unique=true inside the options struct is honored (and strict)" )
	@Test
	public void testHqlUniqueInOptions() {
		assertError( error( "ormExecuteQuery( \"from cbContent\", {}, { unique : true } );" ), "orm.query.nonUnique", "returned more than one" );
	}

	/**
	 * Test: A unique HQL query with one row still returns the entity.
	 */
	@DisplayName( "A unique HQL query with one row still returns the entity" )
	@Test
	public void testHqlUniqueOneRow() {
		instance.executeSource( "result = ormExecuteQuery( \"from Manufacturer where id = 1\", {}, true ).getName();", context );
		assertThat( variables.get( result ) ).isNotNull();
	}

	/**
	 * Test: A unique entityLoad that matches many rows is an error; uniqueFirst takes the first.
	 */
	@DisplayName( "A unique entityLoad that matches many rows is an error; uniqueFirst takes the first" )
	@Test
	public void testEntityLoadUnique() {
		assertError( error( "entityLoad( \"cbContent\", {}, true );" ), "orm.query.nonUnique", "returned more than one" );
		instance.executeSource( "one = entityLoad( \"cbContent\", {}, true, { uniqueFirst : true } ); result = isObject( one );", context );
		assertThat( variables.get( result ) ).isEqualTo( true );
	}

	/**
	 * Test: A lazy many-to-one read after the session is closed is explained.
	 */
	@DisplayName( "A lazy many-to-one read after the session is closed is explained" )
	@Test
	public void testLazyProxyNoSession() {
		IStruct err = error( """
		                     v = entityLoadByPK( "Vehicle", "9ABAZ85656A776723" );
		                     ormCloseSession();
		                     v.getManufacturer().getName();
		                     """ );
		assertError( err, "orm.lazy.noSession", "Cannot load Manufacturer #1" );
		assertThat( err.getAsString( Key.detail ) ).contains( "entityReload" );
	}

	/**
	 * Test: A lazy collection read after the session is cleared is explained.
	 */
	@DisplayName( "A lazy collection read after the session is cleared is explained" )
	@Test
	public void testLazyCollectionNoSession() {
		assertError( error( """
		                    m = entityLoadByPK( "Manufacturer", 1 );
		                    ormClearSession();
		                    m.getVehicles().len();
		                    """ ), "orm.lazy.noSession", "Cannot load Manufacturer.vehicles (for Manufacturer #1)" );
	}

	/**
	 * Test: Saving an entity that points to an unsaved entity explains the cascade fix.
	 */
	@DisplayName( "Saving an entity that points to an unsaved entity explains the cascade fix" )
	@Test
	public void testTransientReference() {
		IStruct err = error( """
		                     v = entityNew( "Vehicle", { vin : "PROBE-VIN-1", make : "x", model : "y" } );
		                     v.setManufacturer( entityNew( "Manufacturer", { name : "unsaved" } ) );
		                     entitySave( v );
		                     ormFlush();
		                     """ );
		assertError( err, "orm.transient", "Vehicle.manufacturer points to a Manufacturer that was never saved." );
		assertThat( err.getAsString( Key.detail ) ).contains( "cascade" );
	}

	/**
	 * Test: An assigned id that was not set is explained.
	 */
	@DisplayName( "An assigned id that was not set is explained" )
	@Test
	public void testAssignedIdMissing() {
		assertError( error( "entitySave( entityNew( \"Vehicle\", { make : \"x\", model : \"y\" } ) ); ormFlush();" ), "orm.id.missing",
		    "Vehicle has an assigned id (generator=\"assigned\") that was not set." );
	}

	/**
	 * Test: A notnull property without a value is named.
	 */
	@DisplayName( "A notnull property without a value is named" )
	@Test
	public void testNotNullProperty() {
		assertError( error( "entitySave( entityNew( \"ConstrainedThing\", { firstName : \"A\", lastName : \"B\" } ) ); ormFlush();" ),
		    "orm.constraint.notNull", "ConstrainedThing.code is required" );
	}

	/**
	 * Test: A unique key violation is an orm.constraint error naming the constraint.
	 */
	@DisplayName( "A unique key violation is an orm.constraint error naming the constraint" )
	@Test
	public void testUniqueViolation() {
		IStruct err = error( """
		                     entitySave( entityNew( "ConstrainedThing", { firstName : "Ada", lastName : "L", code : "c" } ) );
		                     entitySave( entityNew( "ConstrainedThing", { firstName : "Ada", lastName : "L", code : "c" } ) );
		                     ormFlush();
		                     """ );
		assertThat( err.getAsString( Key.type ) ).startsWith( "orm.constraint" );
		assertThat( err.getAsString( Key.detail ) ).contains( "Duplicate entry" );
	}

	/**
	 * Test: EntitySave of a struct says it needs an entity.
	 */
	@DisplayName( "entitySave of a struct says it needs an entity" )
	@Test
	public void testSaveStruct() {
		assertError( error( "entitySave( { name : \"x\" } );" ), "orm.argument",
		    "entitySave() expects an ORM entity instance for [entity], but received a struct." );
	}

	/**
	 * Test: EntityMerge of a string says it needs an entity.
	 */
	@DisplayName( "entityMerge of a string says it needs an entity" )
	@Test
	public void testMergeString() {
		assertError( error( "entityMerge( \"abc\" );" ), "orm.argument", "received the string [abc]" );
	}

	/**
	 * Test: EntityLoadByExample of a non-entity says it needs an entity.
	 */
	@DisplayName( "entityLoadByExample of a non-entity says it needs an entity" )
	@Test
	public void testOtherNonEntityArguments() {
		// (entityDelete( [1,2] ) is stopped earlier by BoxLang's own argument type check, which names the argument and type.)
		assertError( error( "entityLoadByExample( { name : \"x\" } );" ), "orm.argument", "entityLoadByExample() expects an ORM entity" );
	}

	/**
	 * Test: A value that does not fit its column is an orm.property.type error.
	 */
	@DisplayName( "A value that does not fit its column is an orm.property.type error" )
	@Test
	public void testWrongValueType() {
		assertError( error( "c = entityLoad( \"cbContent\" )[ 1 ]; c.setCacheTimeout( \"abc\" ); ormFlush();" ), "orm.property.type",
		    "cannot be stored in its database column" );
	}

	/**
	 * Test: A string set on a many-to-one is an orm.property.type error.
	 */
	@DisplayName( "A string set on a many-to-one is an orm.property.type error" )
	@Test
	public void testStringOnAssociation() {
		IStruct err = error( "v = entityLoadByPK( \"Vehicle\", \"9ABAZ85656A776723\" ); v.setManufacturer( \"abc\" ); ormFlush();" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.property.type" );
		assertThat( err.getAsString( Key.detail ) ).contains( "entity instance" );
	}

	/**
	 * Test: An unknown database function is an orm.sql error with the SQL in extendedInfo.
	 */
	@DisplayName( "An unknown database function is an orm.sql error with the SQL in extendedInfo" )
	@Test
	public void testUnknownSqlFunction() {
		IStruct err = error( "ormExecuteQuery( \"select nope(m.name) from Manufacturer m\" );" );
		assertThat( err.getAsString( Key.type ) ).startsWith( "orm" );
		assertThat( err.getAsString( Key.message ) ).contains( "nope" );
	}

	/**
	 * Test: EntityReload of a variable name that does not exist is an orm.argument error.
	 */
	@DisplayName( "entityReload of a variable name that does not exist is an orm.argument error" )
	@Test
	public void testReloadUnknownVariable() {
		assertError( error( "entityReload( \"noSuchVariable\" );" ), "orm.argument", "no such variable exists" );
	}

	/**
	 * Test: A detached entity bound as an HQL or filter parameter resolves (no facade class name leak).
	 */
	@DisplayName( "A detached entity bound as an HQL or filter parameter resolves (no facade class name leak)" )
	@Test
	public void testDetachedEntityParameter() {
		// @formatter:off
		instance.executeSource(
		    """
		    m = entityLoadByPK( "Manufacturer", 1 );
		    ormClearSession();
		    viaHql = ormExecuteQuery( "from Vehicle where manufacturer = :m", { m : m } ).len();
		    ormClearSession();
		    viaFilter = entityLoad( "Vehicle", { manufacturer : m } ).len();
		    result = viaHql & "," & viaFilter;
		    """,
		    context );
		// @formatter:on
		assertThat( variables.getAsString( result ) ).isEqualTo( "1,1" );
	}

	/**
	 * Test: Every translated error can be caught with catch( "orm" ).
	 */
	@DisplayName( "Every translated error can be caught with catch( \"orm\" )" )
	@Test
	public void testCatchByPrefix() {
		// @formatter:off
		instance.executeSource(
		    """
		    caught = [];
		    try { entityNew( "Nope" ); } catch ( "orm" e ) { caught.append( "orm" ); }
		    try { ormExecuteQuery( "form X" ); } catch ( "orm.query" e ) { caught.append( "orm.query" ); }
		    try { entityLoad( "Manufacturer", { zzz : 1 } ); } catch ( "orm.property.unknown" e ) { caught.append( "exact" ); }
		    result = caught.toList();
		    """,
		    context );
		// @formatter:on
		assertThat( variables.getAsString( result ) ).isEqualTo( "orm,orm.query,exact" );
	}
}
