package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

public class EntityIsAttachedTest extends BaseORMTest {

	@DisplayName( "It returns false for a transient entity and true after saving it" )
	@Test
	public void testEntityIsAttached() {
		instance.executeSource(
		    """
		    theEntity = entityNew( "Manufacturer", { name : "Attached Test", address : "101 Attached Way" } );
		    beforeSave = entityIsAttached( theEntity );
		    entitySave( theEntity );
		    afterSave = entityIsAttached( theEntity );
		    """,
		    context
		);

		assertThat( variables.getAsBoolean( Key.of( "beforeSave" ) ) ).isFalse();
		assertThat( variables.getAsBoolean( Key.of( "afterSave" ) ) ).isTrue();
	}

	@DisplayName( "It checks the session for the entity datasource" )
	@Test
	public void testEntityIsAttachedUsesEntityDatasource() {
		instance.executeSource(
		    """
		    theEntity = entityNew( "AlternateDS", { id: createUUID(), name : "Attached Alternate Test" } );
		    entitySave( theEntity );
		    beforeClear = entityIsAttached( theEntity );
		    ormClearSession( "dsn2" );
		    afterClear = entityIsAttached( theEntity );
		    """,
		    context
		);

		assertThat( variables.getAsBoolean( Key.of( "beforeClear" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "afterClear" ) ) ).isFalse();
	}
}