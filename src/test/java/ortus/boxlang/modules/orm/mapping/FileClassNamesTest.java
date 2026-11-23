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
package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * A class that no mapping reaches (an entity in a relative entityPaths folder of a CLI script or of module:orm generate)
 * gets a simple name built from its whole folder path. Its metadata is given back the file name, so the entity is
 * called {@code User}, not {@code tmpappmodelsUser}.
 */
public class FileClassNamesTest {

	private static IStruct meta( String path, String simpleName ) {
		return Struct.of( Key.path, path, Key.simpleName, simpleName );
	}

	@DisplayName( "A simple name built from the folder path becomes the file name" )
	@Test
	public void testUnmappedName() {
		IStruct result = MappingGenerator.withFileClassNames( meta( "/tmp/app/models/User.bx", "tmpappmodelsUser" ) );
		assertThat( result.getAsString( Key.simpleName ) ).isEqualTo( "User" );
	}

	@DisplayName( "A simple name that already matches the file is kept as written" )
	@Test
	public void testMappedName() {
		assertThat( MappingGenerator.withFileClassNames( meta( "/app/models/User.bx", "User" ) ).getAsString( Key.simpleName ) ).isEqualTo( "User" );
		assertThat( MappingGenerator.withFileClassNames( meta( "/app/models/user.cfc", "User" ) ).getAsString( Key.simpleName ) ).isEqualTo( "User" );
	}

	@DisplayName( "Parents in the extends chain are fixed too, without changing the shared parent struct" )
	@Test
	public void testParents() {
		IStruct	sharedParent	= meta( "/tmp/app/models/Media.bx", "tmpappmodelsMedia" );
		IStruct	child			= meta( "/tmp/app/models/Image.bx", "tmpappmodelsImage" );
		child.put( Key._EXTENDS, sharedParent );

		IStruct result = MappingGenerator.withFileClassNames( child );
		assertThat( result.getAsString( Key.simpleName ) ).isEqualTo( "Image" );
		assertThat( result.getAsStruct( Key._EXTENDS ).getAsString( Key.simpleName ) ).isEqualTo( "Media" );
		// The struct the class shares with every other reader is left alone
		assertThat( sharedParent.getAsString( Key.simpleName ) ).isEqualTo( "tmpappmodelsMedia" );
	}
}
