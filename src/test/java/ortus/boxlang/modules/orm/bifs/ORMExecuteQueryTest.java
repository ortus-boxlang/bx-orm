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

import ortus.boxlang.runtime.dynamic.casters.ArrayCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import tools.BaseORMTest;

public class ORMExecuteQueryTest extends BaseORMTest {

	@DisplayName( "It can run an HQL query with just HQL" )
	@Test
	public void testHQLOnly() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle" );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 4 );
	}

	@DisplayName( "It can run an HQL query on another datasource" )
	@Test
	public void testHQLOnAlternateDatasource() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM AlternateDS", [], { datasource: "dsn2" } );
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 2 );
	}

	@DisplayName( "It can run an HQL query with hql, unique, and options" )
	@Test
	public void testUniqueAndOptions() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle ORDER BY model ASC", true, { readOnly: true, offset: 1, maxResults: 5 } );
		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( IClassRunnable.class );
		IClassRunnable vehicle = ( IClassRunnable ) item;
		assertThat( vehicle.get( Key.of( "model" ) ) ).isEqualTo( "Civic" );
	}

	@DisplayName( "It can run an HQL query with HQL and named params" )
	@Test
	public void testHQLAndParams() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery(
			"FROM Vehicle WHERE make=:make OR model IN (:model) OR model=:specificModel",
			{ make : "Ford", model : { value: ['Civic','Accord'], list : true }, specificModel : "Ridgeline" }
		);
		""", context );
		// @formatter:on
		Object array = variables.get( result );
		assertThat( array ).isInstanceOf( Array.class );
		Array a = ( Array ) array;
		assertThat( a.size() ).isEqualTo( 4 );
	}

	@DisplayName( "It can run an HQL query with HQL, params, and unique boolean" )
	@Test
	public void testHQLParamsAndUnique() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle WHERE id=?1 OR make=?2", ['1HGCM82633A123456','Honda'], true );
		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( IClassRunnable.class );
		IClassRunnable vehicle = ( IClassRunnable ) item;
		assertThat( vehicle.get( Key.of( "model" ) ) ).isEqualTo( "Accord" );
	}

	@DisplayName( "It supports JDBC-style positional params" )
	@Test
	public void testHQLPositionalParams() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Vehicle WHERE id=? OR make=?", ['1HGCM82633A123456','Honda'], true );
		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( IClassRunnable.class );
		IClassRunnable vehicle = ( IClassRunnable ) item;
		assertThat( vehicle.get( Key.of( "model" ) ) ).isEqualTo( "Accord" );
	}

	@DisplayName( "It can retrieve subclassed entities correctly" )
	@Test
	public void testHQLWithSubClasses() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM cbEntry WHERE slug=?", [ 'another-test' ], true );
		println( "Created:" & result.getCreatedDate() );
		println( "Slug:" & result.getSlug() );
		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( IClassRunnable.class );
	}

	@DisplayName( "It can effectively loop a query of subclassed entities" )
	@Test
	public void testHQLCollection() {
		// @formatter:off
		instance.executeSource( """
		results = ormExecuteQuery( "FROM cbEntry");
		result = results.map( ( entity ) => getMetadata( entity ).name );

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( item ).size() ).isEqualTo( 15 );
		ArrayCaster.cast( item ).forEach( ( className ) -> {
			assertThat( className ).isInstanceOf( String.class );
		} );
	}

	@DisplayName( "It can query using a date restriction and an HQL map" )
	@Test
	public void testHQLAdvancedMap() {
		// @formatter:off
		instance.executeSource( """
		results = ormExecuteQuery( "
			SELECT new map(
				count(*) as count,
				YEAR( MAX( publishedDate ) ) as year,
				MONTH( MAX( publishedDate ) ) as month
			)
			FROM cbEntry
			WHERE isPublished = true
			AND passwordProtection = ''
			AND publishedDate <= :now",
			{ now : now() }
		);
		result = results.map( ( entity ) => getMetadata( entity ).name );

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( item ).size() ).isEqualTo( 1 );
		ArrayCaster.cast( item ).forEach( ( className ) -> {
			assertThat( className ).isInstanceOf( String.class );
		} );
	}

	@DisplayName( "It can retrieve the relationships of subclassed entities" )
	@Test
	public void testHQLRelationships() {
		// @formatter:off
		instance.executeSource( """
		results = ormExecuteQuery( "FROM cbEntry WHERE contentID = ?", [ "779cc4e2-a444-11eb-ab6f-0290cc502ae3" ] );
		result = results.map( ( entity ) => entity.getContentVersions().map( ( version ) -> version.getContentVersionId() ) );

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( item ).size() ).isEqualTo( 1 );
		Array versions = ArrayCaster.cast( ArrayCaster.cast( item ).get( 0 ) );
		assertThat( versions.size() ).isEqualTo( 2 );
		versions.forEach( ( version ) -> {
			assertThat( version ).isInstanceOf( String.class );
		} );
	}

	@DisplayName( "It can can query by a date range" )
	@Test
	public void testStringDateRangeQuery() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery(
			"FROM cbContent where createdDate BETWEEN ? AND ?",
			[ "2013-07-11", "2013-07-13" ],
			false
		);
		creators = result.map( ( entity ) => entity.getCreator() );

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( item ).size() ).isEqualTo( 2 );

		Object creators = variables.get( Key.of( "creators" ) );
		assertThat( creators ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( creators ).size() ).isEqualTo( 2 );
	}

	@DisplayName( "It can can delete a record" )
	@Test
	public void testDelete() {
		// @formatter:off
		instance.executeSource( """
		transaction {
			try{
				result = ormExecuteQuery(
					"DELETE FROM Category WHERE category = ?",
					[ "Training" ]
				);
			} catch( any e ){
				rethrow;
			} finally {
				transactionRollback();
			}
		}

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isEqualTo( 1 );
	}

	@DisplayName( "It can can update a record" )
	@Test
	public void canUpdateARecord() {
		// @formatter:off
		instance.executeSource( """
		transaction {
			try{
				result = ormExecuteQuery(
					"UPDATE Category SET description = ? WHERE category = ?",
					[ "unittest updates", "Training" ]
				);
			} catch( any e ){
				rethrow;
			} finally {
				transactionRollback();
			}
		}

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isEqualTo( 1 );
	}

	@DisplayName( "It can perform an IN query on the discriminator value" )
	@Test
	public void getDiscriminatorInQuery() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM cbContent WHERE contentType IN ( 'Entry', 'Page' )" );
		""", context );
		// @formatter:on
		Object entities = variables.get( result );
		assertThat( entities ).isInstanceOf( Array.class );
	}

	@DisplayName( "It can perform use a cache query" )
	@Test
	public void getWithCache() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM cbContent WHERE contentType = 'Page'", {}, { cacheable : true } );
		""", context );
		// @formatter:on
		Object entities = variables.get( result );
		assertThat( entities ).isInstanceOf( Array.class );
	}

	@DisplayName( "It can query by the relationship discrimator value" )
	@Test
	public void getByRelationshipDiscrimator() {
		// @formatter:off
		instance.executeSource( """
		// using both variations of the reationship discriminator
		result1 = ormExecuteQuery( "FROM cbComment WHERE relatedContent.class = 'Entry'" );
		result = ormExecuteQuery( "FROM cbComment WHERE relatedContent.contentType = 'Entry'" );
		resultsRelated = result.map( ( entity ) => entity.getRelatedContent().getSlug() );
		""", context );
		// @formatter:on
		Object entities = variables.get( result );
		assertThat( entities ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( entities ).size() ).isGreaterThan( 0 );
	}

	@DisplayName( "It can retrieve and re-retrieve cache cache-enabled entries" )
	@Test
	public void getRetrieveSubClassCacheEntity() {
		// @formatter:off
		instance.executeSource( """
		result = ormExecuteQuery( "FROM Category WHERE category = ?", [ "general" ] );
		categoriesReloaded = result.map( ( cat ) => EntityLoadByPK( "Category", cat.getcatid() ) )
		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( item ).size() ).isEqualTo( 2 );
		Object reloaded = variables.get( Key.of( "categoriesReloaded" ) );
		assertThat( reloaded ).isInstanceOf( Array.class );
		assertThat( ArrayCaster.cast( reloaded ).size() ).isEqualTo( 2 );
	}

	@DisplayName( "It can can execute a query with all options" )
	@Test
	public void canUseCacheOptionsWithMultipleQueries() {
		// @formatter:off
		instance.executeSource( """
			resultfirst = ormExecuteQuery(
				"from Category",
				[],
				false,
				{
					ignorecase: true,
					cacheName : "abstract_categories",
					cacheable : false
				}
			)
			result = ormExecuteQuery(
				"from Category where category = ?1",
				[ "general" ],
				false,
				{
					ignorecase: false,
					cacheName : "abstract_categories",
					cacheable : false
				}
			);

		""", context );
		// @formatter:on
		Object item = variables.get( result );
		assertThat( item ).isInstanceOf( Array.class );
	}

}
