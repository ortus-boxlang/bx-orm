package ortus.boxlang.modules.orm.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class EntityEventsTest extends BaseORMTest {

	@DisplayName( "It fires preLoad,postLoad" )
	@Test
	public void testEntityLoadEvents() {

		// @formatter:off
		instance.executeSource(
			"""
				entity = entityLoadByPK( "Vehicle", '1HGCM82633A123456' );
				result = entity.getEventLog();
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preLoad", "postLoad" );
	}

	@DisplayName( "It fires preInsert,postInsert" )
	@Test
	public void testEntityInsertEvents() {

		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entity = entityNew( "Vehicle", {
					vin : "1HGCM82633A654321",
					make : "Toyota",
					model : "Tacoma"
				} );
				entitySave( entity );
				ormFlush();
				result = entity.getEventLog();
			}
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preInsert", "postInsert" );
	}

	@DisplayName( "It fires preUpdate,postUpdate" )
	@Test
	public void testEntityUpdateEvents() {

		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entity = entityLoadByPK( "Vehicle", '1HGCM82633A123456' );
				entity.setModel( "Civic" );
				entitySave( entity );
				ormFlush();
				result = entity.getEventLog();
			}
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preLoad", "postLoad", "preUpdate", "postUpdate" );
	}

	@DisplayName( "It fires preDelete,postDelete" )
	@Test
	public void testEntityDeleteEvents() {

		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entity = entityLoadByPK( "Vehicle", '1HGCM82633A123456' );
				entityDelete( entity );
				ormFlush();
				result = entity.getEventLog();
			}
			""",
			context
		);
		// @formatter:on
		assertInstanceOf( Array.class, variables.get( "result" ) );
		Array eventLog = variables.getAsArray( result );
		assertThat( eventLog.toList() ).containsExactly( "preLoad", "postLoad", "preDelete", "postDelete" );
	}
}
