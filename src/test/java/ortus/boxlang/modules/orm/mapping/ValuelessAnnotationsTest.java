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

import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * Entities written with BoxLang annotations and no value on the switches: {@code @persistent}, {@code @unique},
 * {@code @notNull}, {@code @softDelete}, {@code @dynamicUpdate}, {@code @selectBeforeUpdate}, {@code @lazy}, {@code @inverse}.
 * The mapping of every switch is covered without a database in MappingGeneratorTest.
 * Fixtures: models/native/BareFlagNote.bx and models/native/BareFlagFolder.bx.
 */
public class ValuelessAnnotationsTest extends BaseORMTest {

	@DisplayName( "a valueless @persistent maps the entity, and the runtime treats it as persistent" )
	@Test
	public void testPersistentEntity() {
		// @formatter:off
		instance.executeSource(
		    """
		    transaction {
		        folder = entityNew( "BareFlagFolder", { name : "inbox" } );
		        entitySave( folder );
		        note = entityNew( "BareFlagNote", { code : createUUID().left( 30 ), title : "hello", folder : folder } );
		        entitySave( note );
		    }
		    ormClearSession();
		    loaded = entityLoadByPK( "BareFlagNote", note.getId() );
		    title  = loaded.getTitle();
		    folderName = loaded.getFolder().getName();
		    names  = entityNameArray().toList();
		    """,
		    context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "title" ) ) ).isEqualTo( "hello" );
		assertThat( variables.getAsString( Key.of( "folderName" ) ) ).isEqualTo( "inbox" );
		assertThat( variables.getAsString( Key.of( "names" ) ) ).contains( "BareFlagNote" );
		assertThat( variables.getAsString( Key.of( "names" ) ) ).contains( "BareFlagFolder" );
	}

	@DisplayName( "a valueless @unique and @notNull reach the schema; @persistent( false ) is not a column" )
	@Test
	public void testColumnSwitches() {
		// @formatter:off
		instance.executeSource(
		    """
		    cols = queryExecute( "
		        select column_name, is_nullable from information_schema.columns
		        where table_schema = database() and table_name = 'bare_flag_notes'" );
		    codeNullable = cols.filter( ( r ) => r.column_name == "code" ).is_nullable;
		    hasScratch   = cols.filter( ( r ) => r.column_name == "scratch" ).recordCount;
		    hasTitle     = cols.filter( ( r ) => r.column_name == "title" ).recordCount;
		    uniques = queryExecute( "
		        select index_name from information_schema.statistics
		        where table_schema = database() and table_name = 'bare_flag_notes' and column_name = 'code' and non_unique = 0" );
		    codeUnique = uniques.recordCount;
		    """,
		    context );
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "codeNullable" ) ) ).isEqualTo( "NO" );
		assertThat( variables.get( Key.of( "codeUnique" ) ) ).isEqualTo( 1 );
		assertThat( variables.get( Key.of( "hasScratch" ) ) ).isEqualTo( 0 );
		assertThat( variables.get( Key.of( "hasTitle" ) ) ).isEqualTo( 1 );
	}

	@DisplayName( "a valueless @softDelete marks rows deleted and hides them from loads" )
	@Test
	public void testSoftDelete() {
		// @formatter:off
		instance.executeSource(
		    """
		    transaction {
		        note = entityNew( "BareFlagNote", { code : createUUID().left( 30 ), title : "soft" } );
		        entitySave( note );
		    }
		    noteId = note.getId();
		    transaction {
		        entityDelete( entityLoadByPK( "BareFlagNote", noteId ) );
		    }
		    ormClearSession();
		    raw    = queryExecute( "SELECT * FROM bare_flag_notes WHERE id = :id", { id : noteId } );
		    rows   = raw.recordCount;
		    flag   = raw.deleted[ 1 ];
		    loaded = isNull( entityLoadByPK( "BareFlagNote", noteId ) );
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( Key.of( "rows" ) ) ).isEqualTo( 1 );
		assertThat( BooleanCaster.cast( variables.get( Key.of( "flag" ) ) ) ).isTrue();
		assertThat( variables.get( Key.of( "loaded" ) ) ).isEqualTo( true );
	}

	@DisplayName( "valueless @dynamicUpdate, @dynamicInsert and @readOnly reach Hibernate" )
	@Test
	public void testEntitySwitches() {
		// @formatter:off
		instance.executeSource(
		    """
		    descriptor   = ormGetSessionFactory().getMappingMetamodel().getEntityDescriptor( "BareFlagNote" );
		    dynamic      = descriptor.isDynamicUpdate();
		    folder       = ormGetSessionFactory().getMappingMetamodel().getEntityDescriptor( "BareFlagFolder" );
		    plain        = folder.isDynamicUpdate();
		    readOnly     = ormGetSessionFactory().getMappingMetamodel().getEntityDescriptor( "BareFlagReadOnly" );
		    mutable      = readOnly.isMutable();
		    dynamicIns   = readOnly.isDynamicInsert();
		    """,
		    context );
		// @formatter:on
		assertThat( variables.get( Key.of( "dynamic" ) ) ).isEqualTo( true );
		assertThat( variables.get( Key.of( "plain" ) ) ).isEqualTo( false );
		assertThat( variables.get( Key.of( "mutable" ) ) ).isEqualTo( false );
		assertThat( variables.get( Key.of( "dynamicIns" ) ) ).isEqualTo( true );
	}

	@DisplayName( "the class metadata reports the switches as true" )
	@Test
	public void testPublishedMetadata() {
		// @formatter:off
		instance.executeSource(
		    """
		    // The class the ORM loaded, through the test app's /root mapping
		    meta       = getClassMetadata( "root.models.native.BareFlagNote" );
		    persistent = meta.annotations.persistent;
		    softDelete = meta.annotations.softDelete;
		    code       = meta.properties.filter( ( p ) => p.name == "code" ).first().annotations;
		    unique     = code.unique;
		    notNull    = code.notNull;
		    """,
		    context );
		// @formatter:on
		assertThat( BooleanCaster.cast( variables.get( Key.of( "persistent" ) ) ) ).isTrue();
		assertThat( BooleanCaster.cast( variables.get( Key.of( "softDelete" ) ) ) ).isTrue();
		assertThat( BooleanCaster.cast( variables.get( Key.of( "unique" ) ) ) ).isTrue();
		assertThat( BooleanCaster.cast( variables.get( Key.of( "notNull" ) ) ) ).isTrue();
	}
}
