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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.AnnotationFlags;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Switch annotations written without a value ({@code @persistent}, {@code @unique}) mean true.
 */
public class AnnotationFlagsTest {

	private static Set<Key> allFlags() {
		Set<Key> flags = new HashSet<>( AnnotationFlags.ENTITY_FLAGS );
		flags.addAll( AnnotationFlags.PROPERTY_FLAGS );
		return flags;
	}

	@DisplayName( "every switch annotation without a value is true" )
	@Test
	public void testValuelessIsTrue() {
		for ( Key flag : allFlags() ) {
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, "" ), flag, false ) ).isTrue();
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, "  " ), flag, false ) ).isTrue();
		}
	}

	@DisplayName( "an explicit value still decides" )
	@Test
	public void testExplicitValues() {
		for ( Key flag : allFlags() ) {
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, "false" ), flag, true ) ).isFalse();
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, false ), flag, true ) ).isFalse();
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, "no" ), flag, true ) ).isFalse();
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, "true" ), flag, false ) ).isTrue();
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, true ), flag, false ) ).isTrue();
			assertThat( AnnotationFlags.isTrue( Struct.of( flag, "yes" ), flag, false ) ).isTrue();
		}
	}

	@DisplayName( "a missing annotation gives the default" )
	@Test
	public void testAbsent() {
		assertThat( AnnotationFlags.isTrue( Struct.of(), ORMKeys.persistent, true ) ).isTrue();
		assertThat( AnnotationFlags.isTrue( Struct.of(), ORMKeys.persistent, false ) ).isFalse();
		assertThat( AnnotationFlags.isTrue( null, ORMKeys.unique, false ) ).isFalse();
	}

	@DisplayName( "normalize stores true for valueless switches and leaves everything else alone" )
	@Test
	public void testNormalize() {
		IStruct entity = Struct.of(
		    ORMKeys.persistent, "",
		    ORMKeys.dynamicUpdate, "",
		    ORMKeys.readOnly, "false",
		    ORMKeys.softDelete, "timestamp",
		    ORMKeys.table, ""
		);
		AnnotationFlags.normalize( entity, AnnotationFlags.ENTITY_FLAGS );
		assertThat( entity.get( ORMKeys.persistent ) ).isEqualTo( true );
		assertThat( entity.get( ORMKeys.dynamicUpdate ) ).isEqualTo( true );
		assertThat( entity.get( ORMKeys.readOnly ) ).isEqualTo( "false" );
		assertThat( entity.get( ORMKeys.softDelete ) ).isEqualTo( "timestamp" );
		// not a switch: left as written
		assertThat( entity.get( ORMKeys.table ) ).isEqualTo( "" );

		IStruct property = Struct.of(
		    ORMKeys.unique, "",
		    ORMKeys.notNull, "",
		    ORMKeys.inverse, "",
		    ORMKeys.lazy, "extra",
		    ORMKeys.insert, false
		);
		AnnotationFlags.normalize( property, AnnotationFlags.PROPERTY_FLAGS );
		assertThat( property.get( ORMKeys.unique ) ).isEqualTo( true );
		assertThat( property.get( ORMKeys.notNull ) ).isEqualTo( true );
		assertThat( property.get( ORMKeys.inverse ) ).isEqualTo( true );
		assertThat( property.get( ORMKeys.lazy ) ).isEqualTo( "extra" );
		assertThat( property.get( ORMKeys.insert ) ).isEqualTo( false );
	}

	@DisplayName( "the switch lists cover the documented boolean annotations" )
	@Test
	public void testCoverage() {
		assertThat( AnnotationFlags.ENTITY_FLAGS ).containsAtLeast(
		    ORMKeys.persistent, ORMKeys.readOnly, ORMKeys.dynamicInsert, ORMKeys.dynamicUpdate, ORMKeys.selectBeforeUpdate,
		    ORMKeys.mappedSuperClass, ORMKeys.softDelete );
		assertThat( AnnotationFlags.PROPERTY_FLAGS ).containsAtLeast(
		    ORMKeys.persistent, ORMKeys.unique, ORMKeys.notNull, ORMKeys.insert, ORMKeys.update, ORMKeys.readOnly,
		    ORMKeys.optimisticLock, ORMKeys.inverse, ORMKeys.constrained, ORMKeys.missingRowIgnored, ORMKeys.lazy );
	}
}
