package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * Live (MySQL) checks that the uniquekey and index property annotations reach the database schema.
 * Fixtures: models/ConstrainedThing.bx and models/timebox/TimeOff.cfc (index="idx_timeOffStatus").
 */
public class TableConstraintsTest extends BaseORMTest {

	/**
	 * Test: Uniquekey creates one multi-column unique index.
	 */
	@DisplayName( "uniquekey creates one multi-column unique index" )
	@Test
	public void testUniqueKeyInSchema() {
		// @formatter:off
		instance.executeSource(
		    """
		    q = queryExecute( "
		        select column_name, non_unique from information_schema.statistics
		        where table_schema = database() and table_name = 'constrained_things' and index_name = 'uk_ct_name'
		        order by seq_in_index" );
		    result = q.recordCount;
		    cols = q.columnData( "column_name" ).toList();
		    nonUnique = q.non_unique[ 1 ];
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( 2 );
		assertThat( variables.getAsString( Key.of( "cols" ) ).toLowerCase() ).isEqualTo( "first_name,last_name" );
		assertThat( variables.get( Key.of( "nonUnique" ) ).toString() ).isEqualTo( "0" );
	}

	/**
	 * Test: Index creates a named non-unique index.
	 */
	@DisplayName( "index creates a named non-unique index" )
	@Test
	public void testIndexInSchema() {
		// @formatter:off
		instance.executeSource(
		    """
		    q = queryExecute( "
		        select column_name, non_unique from information_schema.statistics
		        where table_schema = database() and table_name = 'constrained_things' and index_name = 'idx_ct_status'" );
		    result = q.recordCount;
		    col = q.column_name[ 1 ];
		    nonUnique = q.non_unique[ 1 ];
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( 1 );
		assertThat( variables.getAsString( Key.of( "col" ) ) ).isEqualTo( "status" );
		assertThat( variables.get( Key.of( "nonUnique" ) ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: Index on an existing fixture entity (TimeOff.status) reaches the schema.
	 */
	@DisplayName( "index on an existing fixture entity (TimeOff.status) reaches the schema" )
	@Test
	public void testExistingFixtureIndex() {
		// @formatter:off
		instance.executeSource(
		    """
		    q = queryExecute( "
		        select column_name from information_schema.statistics
		        where table_schema = database() and index_name = 'idx_timeOffStatus'" );
		    result = q.recordCount;
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( 1 );
	}

	/**
	 * Test: The unique key is enforced: a duplicate (firstName, lastName) pair fails on flush.
	 */
	@DisplayName( "the unique key is enforced: a duplicate (firstName, lastName) pair fails on flush" )
	@Test
	public void testUniqueKeyEnforced() {
		// @formatter:off
		instance.executeSource(
		    """
		    failed = false;
		    try {
		        transaction {
		            try {
		                entitySave( entityNew( "ConstrainedThing", { firstName : "Ada", lastName : "Lovelace", status : "new", code : "c" } ) );
		                entitySave( entityNew( "ConstrainedThing", { firstName : "Ada", lastName : "Lovelace", status : "new", code : "c" } ) );
		                ormFlush();
		            } finally {
		                transactionRollback();
		            }
		        }
		    } catch ( any e ) {
		        failed = true;
		    }
		    ormClearSession();
		    result = failed;
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( true );
	}

	/**
	 * Test: The same first name with different last names is allowed (the key spans both columns).
	 */
	@DisplayName( "the same first name with different last names is allowed (the key spans both columns)" )
	@Test
	public void testUniqueKeyIsComposite() {
		// @formatter:off
		instance.executeSource(
		    """
		    transaction {
		        try {
		            entitySave( entityNew( "ConstrainedThing", { firstName : "Ada", lastName : "Lovelace", status : "new", code : "c" } ) );
		            entitySave( entityNew( "ConstrainedThing", { firstName : "Ada", lastName : "Byron", status : "new", code : "c" } ) );
		            ormFlush();
		            result = queryExecute( "select count(*) as c from constrained_things where first_name = 'Ada'" ).c;
		        } finally {
		            transactionRollback();
		        }
		    }
		    ormClearSession();
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( result ).toString() ).isEqualTo( "2" );
	}
}
