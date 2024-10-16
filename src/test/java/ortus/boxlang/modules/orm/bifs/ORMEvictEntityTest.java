package ortus.boxlang.modules.orm.bifs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.BaseORMTest;

// TODO implement test
@Disabled
public class ORMEvictEntityTest extends BaseORMTest {

	@DisplayName( "It can test the ExampleBIF" )
	@Test
	public void testTestBIF() {
		instance.executeSource( "result = ORMTestBIF()", context );
		assertEquals( "Hello from an ORMTestBIF!", variables.get( result ) );
	}

}
