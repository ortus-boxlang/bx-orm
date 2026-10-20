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
package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * Usage of simple-value collections (`fieldtype="collection"`) through the public ORM BIFs and the generated entity helpers, on the
 * MySQL test application.
 * <p>
 * The generated mapping is covered in {@code HibernateXMLWriterTest}, the SQLite round trip in {@code SQLiteDialectBootTest}. The
 * fixtures live on {@code Manufacturer} (`aliases`, a string bag with a singular name, and `plantCodes`, an integer bag) and on the
 * discriminator parent {@code BaseContent} (`tags`, inherited by {@code cbEntry}). Seed rows are in {@code JDBCTestUtils}.
 * <p>
 * Tests that modify a collection work on a manufacturer they create themselves, so the seeded rows stay stable across the class.
 */
public class ElementCollectionTest extends BaseORMTest {

	@DisplayName( "It loads seeded collections ordered by the mapping, with elements of the mapped type" )
	@Test
	public void testLoadSeededCollections() {
		// @formatter:off
		instance.executeSource( """
			honda          = entityLoadByPK( "Manufacturer", 42 );

			aliasList      = honda.getAliases().toList();

			plantCodes     = honda.getPlantCodes();
			plantCodeList  = plantCodes.toList();
			firstPlantCode = arrayFirst( plantCodes );

			// a manufacturer without link rows gets an empty collection, not null
			emptyCount     = arrayLen( entityLoadByPK( "Manufacturer", 1 ).getPlantCodes() );
		""", context );
		// @formatter:on

		assertThat( variables.getAsString( Key.of( "aliasList" ) ) ).isEqualTo( "Honda,Honda Motor" );

		assertThat( variables.getAsString( Key.of( "plantCodeList" ) ) ).isEqualTo( "100,200" );
		assertThat( variables.get( Key.of( "firstPlantCode" ) ) ).isInstanceOf( Number.class );
		assertThat( ( ( Number ) variables.get( Key.of( "firstPlantCode" ) ) ).intValue() ).isEqualTo( 100 );

		assertThat( variables.getAsInteger( Key.of( "emptyCount" ) ) ).isEqualTo( 0 );
	}

	@DisplayName( "It persists collections passed to entityNew() as properties" )
	@Test
	public void testEntityNewWithCollections() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				rivian = entityNew(
					"Manufacturer",
					{
						name       : "Rivian Automotive",
						address    : "14600 Myford Road, Irvine, CA",
						aliases    : [ "Rivian", "RIVN" ],
						plantCodes : [ 7 ]
					}
				);
				entitySave( rivian );
			}
			id        = rivian.getId();
			aliasRows = queryExecute( "SELECT alias FROM manufacturer_aliases WHERE FK_manufacturer = :id ORDER BY alias", { id : id } );
			plantRows = queryExecute( "SELECT plant_code FROM manufacturer_plants WHERE FK_manufacturer = :id", { id : id } );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "aliasRows" ) ).size() ).isEqualTo( 2 );
		// MySQL's default collation orders case-insensitively, so "Rivian" sorts before "RIVN"
		assertThat( variables.getAsQuery( Key.of( "aliasRows" ) ).getRowAsStruct( 0 ).get( "alias" ) ).isEqualTo( "Rivian" );
		assertThat( variables.getAsQuery( Key.of( "aliasRows" ) ).getRowAsStruct( 1 ).get( "alias" ) ).isEqualTo( "RIVN" );
		assertThat( variables.getAsQuery( Key.of( "plantRows" ) ).size() ).isEqualTo( 1 );
		assertThat( ( ( Number ) variables.getAsQuery( Key.of( "plantRows" ) ).getRowAsStruct( 0 ).get( "plant_code" ) ).intValue() ).isEqualTo( 7 );
	}

	@DisplayName( "It generates hasX( item ) / addX( item ) / removeX( item ) helpers that take the element value, and the getter is a live view" )
	@Test
	public void testGeneratedHelpers() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				entitySave( entityNew( "Manufacturer", { name : "Lucid Group", address : "7373 Gateway Blvd, Newark, CA", aliases : [ "Lucid", "LCID" ] } ) );
			}
			ormClearSession();

			lucid    = entityLoad( "Manufacturer", { name : "Lucid Group" }, true );
			hasLucid = lucid.hasAlias( "Lucid" );
			hasTesla = lucid.hasAlias( "Tesla" );
			hasAny   = lucid.hasAlias();

			transaction {
				// singular name from the mapping, chainable, scalar argument
				lucid.addAlias( "Lucid Motors" ).removeAlias( "LCID" );
				entitySave( lucid );
			}
			ormClearSession();

			rows       = queryExecute( "SELECT alias FROM manufacturer_aliases WHERE FK_manufacturer = :id ORDER BY alias", { id : lucid.getId() } );
			reloaded   = entityLoadByPK( "Manufacturer", lucid.getId() );
			hasAdded   = reloaded.hasAlias( "Lucid Motors" );
			hasRemoved = reloaded.hasAlias( "LCID" );

			// the getter returns a live view of the collection: appending through it is seen by hasX() and persisted on save
			transaction {
				reloaded.getAliases().append( "Lucid USA" );
				hasAppended = reloaded.hasAlias( "Lucid USA" );
				entitySave( reloaded );
			}
			ormClearSession();
			rowsAfterAppend = queryExecute( "SELECT alias FROM manufacturer_aliases WHERE FK_manufacturer = :id ORDER BY alias", { id : lucid.getId() } );

			// no singular name: the helpers carry the property name; integer elements are matched by value
			honda        = entityLoadByPK( "Manufacturer", 42 );
			hasPlant100  = honda.hasPlantCodes( 100 );
			hasPlant999  = honda.hasPlantCodes( 999 );
		""", context );
		// @formatter:on

		assertThat( variables.getAsBoolean( Key.of( "hasLucid" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "hasTesla" ) ) ).isFalse();
		assertThat( variables.getAsBoolean( Key.of( "hasAny" ) ) ).isTrue();

		assertThat( variables.getAsQuery( Key.of( "rows" ) ).size() ).isEqualTo( 2 );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 0 ).get( "alias" ) ).isEqualTo( "Lucid" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 1 ).get( "alias" ) ).isEqualTo( "Lucid Motors" );
		assertThat( variables.getAsBoolean( Key.of( "hasAdded" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "hasRemoved" ) ) ).isFalse();

		assertThat( variables.getAsBoolean( Key.of( "hasAppended" ) ) ).isTrue();
		assertThat( variables.getAsQuery( Key.of( "rowsAfterAppend" ) ).size() ).isEqualTo( 3 );
		assertThat( variables.getAsQuery( Key.of( "rowsAfterAppend" ) ).getRowAsStruct( 2 ).get( "alias" ) ).isEqualTo( "Lucid USA" );

		assertThat( variables.getAsBoolean( Key.of( "hasPlant100" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "hasPlant999" ) ) ).isFalse();
	}

	@DisplayName( "It queries collection elements in HQL with elements(), size() and a collection join" )
	@Test
	public void testHQLOnCollectionElements() {
		// @formatter:off
		instance.executeSource( """
			byAlias     = ormExecuteQuery( "FROM Manufacturer m WHERE :alias IN elements( m.aliases )", { alias : "Honda Motor" } );
			byAliasName = byAlias[ 1 ].getName();
			byAliasSize = arrayLen( byAlias );

			aliasCount  = ormExecuteQuery( "SELECT size( m.aliases ) FROM Manufacturer m WHERE m.id = 42", true );

			joinedName  = ormExecuteQuery( "SELECT m.name FROM Manufacturer m JOIN m.aliases a WHERE a = :alias", { alias : "FoMoCo" }, true );
		""", context );
		// @formatter:on

		assertThat( variables.getAsInteger( Key.of( "byAliasSize" ) ) ).isEqualTo( 1 );
		assertThat( variables.getAsString( Key.of( "byAliasName" ) ) ).isEqualTo( "Honda Motor Co." );
		assertThat( ( ( Number ) variables.get( Key.of( "aliasCount" ) ) ).intValue() ).isEqualTo( 2 );
		assertThat( variables.getAsString( Key.of( "joinedName" ) ) ).isEqualTo( "Ford Motor Company" );
	}

	@DisplayName( "It removes the link-table rows when the owning entity is deleted" )
	@Test
	public void testEntityDeleteRemovesCollectionRows() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				fisker = entityNew( "Manufacturer", { name : "Fisker Inc.", address : "1888 Rosecrans Ave, Manhattan Beach, CA", aliases : [ "Fisker" ], plantCodes : [ 9, 10 ] } );
				entitySave( fisker );
			}
			id         = fisker.getId();
			rowsBefore = queryExecute( "SELECT alias FROM manufacturer_aliases WHERE FK_manufacturer = :id", { id : id } );

			transaction {
				entityDelete( fisker );
			}
			aliasRowsAfter = queryExecute( "SELECT alias FROM manufacturer_aliases WHERE FK_manufacturer = :id", { id : id } );
			plantRowsAfter = queryExecute( "SELECT plant_code FROM manufacturer_plants WHERE FK_manufacturer = :id", { id : id } );
			entityRows     = queryExecute( "SELECT id FROM manufacturers WHERE id = :id", { id : id } );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "rowsBefore" ) ).size() ).isEqualTo( 1 );
		assertThat( variables.getAsQuery( Key.of( "aliasRowsAfter" ) ).size() ).isEqualTo( 0 );
		assertThat( variables.getAsQuery( Key.of( "plantRowsAfter" ) ).size() ).isEqualTo( 0 );
		assertThat( variables.getAsQuery( Key.of( "entityRows" ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "It discards pending collection changes on entityReload()" )
	@Test
	public void testEntityReloadDiscardsPendingChanges() {
		// @formatter:off
		instance.executeSource( """
			ford         = entityLoadByPK( "Manufacturer", 1 );
			ford.addAlias( "Pending" );
			pendingCount = arrayLen( ford.getAliases() );

			entityReload( ford );
			reloadedList = ford.getAliases().toList();
		""", context );
		// @formatter:on

		assertThat( variables.getAsInteger( Key.of( "pendingCount" ) ) ).isEqualTo( 3 );
		assertThat( variables.getAsString( Key.of( "reloadedList" ) ) ).isEqualTo( "FoMoCo,Ford" );
	}

	@DisplayName( "It merges the collection of a detached entity with entityMerge()" )
	@Test
	public void testEntityMergeDetachedCollection() {
		// @formatter:off
		instance.executeSource( """
			transaction {
				polestar = entityNew( "Manufacturer", { name : "Polestar", address : "Assar Gabrielssons väg 9, Göteborg", aliases : [ "Polestar" ] } );
				entitySave( polestar );
			}
			id = polestar.getId();

			ormGetSession().detach( polestar );
			// a plain array assigned while detached
			polestar.setAliases( [ "Polestar", "PSNY" ] );
			merged = entityMerge( polestar );
			ormFlush();

			rows          = queryExecute( "SELECT alias FROM manufacturer_aliases WHERE FK_manufacturer = :id ORDER BY alias", { id : id } );
			mergedHasPSNY = merged.hasAlias( "PSNY" );
		""", context );
		// @formatter:on

		assertThat( variables.getAsQuery( Key.of( "rows" ) ).size() ).isEqualTo( 2 );
		// case-insensitive collation: "Polestar" sorts before "PSNY"
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 0 ).get( "alias" ) ).isEqualTo( "Polestar" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 1 ).get( "alias" ) ).isEqualTo( "PSNY" );
		assertThat( variables.getAsBoolean( Key.of( "mergedHasPSNY" ) ) ).isTrue();
	}

	@DisplayName( "It gives a persistent subclass the collection and the collection-aware helpers of its parent" )
	@Test
	public void testInheritedCollectionOnSubclass() {
		// @formatter:off
		instance.executeSource( """
			entryId = "779cc2bc-a444-11eb-ab6f-0290cc502ae3";

			transaction {
				entry = entityLoadByPK( "cbEntry", entryId );
				entry.setTags( [ "boxlang", "orm" ] );
				entitySave( entry );
			}
			ormClearSession();

			entry   = entityLoadByPK( "cbEntry", entryId );
			hasOrm  = entry.hasTag( "orm" );
			hasJava = entry.hasTag( "java" );

			transaction {
				entry.addTag( "java" ).removeTag( "boxlang" );
				entitySave( entry );
			}
			ormClearSession();

			rows = queryExecute( "SELECT tag FROM cb_content_tags WHERE FK_contentID = :id ORDER BY tag", { id : entryId } );
		""", context );
		// @formatter:on

		assertThat( variables.getAsBoolean( Key.of( "hasOrm" ) ) ).isTrue();
		assertThat( variables.getAsBoolean( Key.of( "hasJava" ) ) ).isFalse();
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).size() ).isEqualTo( 2 );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 0 ).get( "tag" ) ).isEqualTo( "java" );
		assertThat( variables.getAsQuery( Key.of( "rows" ) ).getRowAsStruct( 1 ).get( "tag" ) ).isEqualTo( "orm" );
	}
}
