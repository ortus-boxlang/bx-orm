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
package ortus.boxlang.modules.orm.criteria;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Live tests for the cborm 4/5 criteria features ContentBox relies on: {@code joinTo( ..., withClause )},
 * {@code resultTransformer( DISTINCT_ROOT_ENTITY )}, sorting by a projection alias, subclass properties on a base
 * entity, and the {@code class} pseudo-property.
 * <p>
 * Each test runs on a small CMS data set (an author, two entries, a page, their versions and comments, and two menu
 * items) inserted before and removed after it.
 */
public class CriteriaCbormCompatTest extends CriteriaTestSupport {

	/**
	 * Insert the CMS rows: entries e1 (only its inactive version mentions boxlang) and e2, page p1, comments (two on e1,
	 * one on p1) and a content and a URL menu item.
	 */
	@BeforeEach
	public void seedContent() {
		// @formatter:off
		instance.executeSource( """
			removeCompatRows = () => {
				for ( var table in [ "cb_comment", "cb_contentVersion", "cb_entry", "cb_page", "cb_content", "cb_menuItem", "users" ] ) {
					queryExecute( "delete from #table# where #table == 'users' ? 'authorID' : ( table == 'cb_menuItem' ? 'menuItemID' : ( table == 'cb_comment' ? 'commentID' : ( table == 'cb_contentVersion' ? 'contentVersionID' : 'contentID' ) ) )# like 'compat-%'" );
				}
			};
			removeCompatRows();
			queryExecute( "insert into users ( authorID, firstName, lastName, email, username, password, isActive, isDeleted, isPasswordReset, is2FactorAuth, createdDate, modifiedDate )
				values ( 'compat-a1', 'Ada', 'Lovelace', 'ada@x.com', 'compat-ada', 'x', 1, 0, 0, 0, now(), now() )" );
			for ( c in [ [ "compat-e1", "Entry", "e-one" ], [ "compat-e2", "Entry", "e-two" ], [ "compat-p1", "Page", "p-one" ] ] ) {
				queryExecute( "insert into cb_content ( contentID, contentType, title, slug, createdDate, modifiedDate, isDeleted, isPublished, allowComments, cache, markup, showInSearch, FK_authorID )
					values ( ?, ?, ?, ?, now(), now(), 0, 1, 1, 0, 'HTML', 1, 'compat-a1' )", [ c[ 1 ], c[ 2 ], "Title " & c[ 3 ], c[ 3 ] ] );
				queryExecute( c[ 2 ] == "Page" ? "insert into cb_page ( contentID, showInMenu ) values ( ?, 1 )" : "insert into cb_entry ( contentID ) values ( ? )", [ c[ 1 ] ] );
			}
			for ( v in [ [ "compat-v1", "compat-e1", 0, "old boxlang text" ], [ "compat-v2", "compat-e1", 1, "hello world" ],
				[ "compat-v3", "compat-e2", 1, "boxlang rocks" ], [ "compat-v4", "compat-p1", 1, "a boxlang page" ] ] ) {
				queryExecute( "insert into cb_contentVersion ( contentVersionID, FK_contentID, isActive, content, version, isDeleted, createdDate, modifiedDate, FK_authorID )
					values ( ?, ?, #v[ 3 ]#, ?, 1, 0, now(), now(), 'compat-a1' )", [ v[ 1 ], v[ 2 ], v[ 4 ] ] );
			}
			for ( m in [ [ "compat-c1", "compat-e1" ], [ "compat-c2", "compat-e1" ], [ "compat-c3", "compat-p1" ] ] ) {
				queryExecute( "insert into cb_comment ( commentID, FK_contentID, content, author, authorIP, authorEmail, isApproved, isDeleted, createdDate, modifiedDate )
					values ( ?, ?, 'nice', 'Bob', '127.0.0.1', 'bob@x.com', 1, 0, now(), now() )", [ m[ 1 ], m[ 2 ] ] );
			}
			queryExecute( "insert into cb_menuItem ( menuItemID, menuType, title, contentSlug, isDeleted, createdDate, modifiedDate ) values ( 'compat-m1', 'Content', 'To e-one', 'e-one', 0, now(), now() )" );
			queryExecute( "insert into cb_menuItem ( menuItemID, menuType, title, url, isDeleted, createdDate, modifiedDate ) values ( 'compat-m2', 'URL', 'Elsewhere', 'https://x.com', 0, now(), now() )" );
		""", context );
		// @formatter:on
	}

	/**
	 * Remove the CMS rows.
	 */
	@AfterEach
	public void removeContent() {
		instance.executeSource( "ormClearSession(); removeCompatRows();", context );
	}

	/**
	 * Test: a withClause restriction goes into the join's on clause, so only active versions are searched.
	 */
	@DisplayName( "joinTo( ..., withClause ) restricts the join, not the rows" )
	@Test
	public void testWithClauseRestriction() {
		assertThat( list( """
		                  c = entityCriteria( 'cbContent' );
		                  result = c.joinTo( 'contentVersions', 'ac', c.INNER_JOIN, c.restrictions.isTrue( 'ac.isActive' ) )
		                      .like( 'ac.content', '%boxlang%' ).isLike( 'contentID', 'compat-%' ).order( 'slug' ).list().map( ( e ) => e.getSlug() );
		                  """ ) ).containsExactly( "e-two", "p-one" ).inOrder();
		// without it the inactive version of e-one matches too
		assertThat( number( """
		                    result = entityCriteria( 'cbContent' ).joinTo( 'contentVersions', 'ac' )
		                        .like( 'ac.content', '%boxlang%' ).isLike( 'contentID', 'compat-%' ).count();
		                    """ ) ).isEqualTo( 3 );
		assertThat( ( String ) run( """
		                            c = entityCriteria( 'cbContent' );
		                            result = c.joinTo( 'contentVersions', 'ac', 'inner', c.restrictions.isTrue( 'ac.isActive' ) ).getHQL();
		                            """ ) ).contains( " on bx_j" );
	}

	/**
	 * Test: the cborm named-argument form and a closure both work, and the count matches the rows.
	 */
	@DisplayName( "createAlias( associationName, alias, withClause ) takes named arguments and closures" )
	@Test
	public void testWithClauseNamedAndClosure() {
		assertThat( number(
		    """
		    result = entityCriteria( 'cbContent' )
		        .createAlias( associationName = 'contentVersions', alias = 'ac', withClause = entityCriteria( 'Vehicle' ).restrictions.isTrue( 'ac.isActive' ) )
		        .like( 'ac.content', '%boxlang%' ).isLike( 'contentID', 'compat-%' ).count( 'contentID' );
		    """ ) ).isEqualTo( 2 );
		assertThat( number( """
		                    result = entityCriteria( 'cbContent' )
		                        .leftJoin( 'contentVersions', 'ac', ( c ) => c.isTrue( 'ac.isActive' ).like( 'ac.content', '%world%' ) )
		                        .isNotNull( 'ac.contentVersionID' ).isLike( 'contentID', 'compat-%' ).count();
		                    """ ) ).isEqualTo( 1 );
		// a left join with a withClause keeps the rows without a matching association
		assertThat( number( """
		                    c = entityCriteria( 'cbContent' );
		                    result = c.joinTo( 'contentVersions', 'ac', c.LEFT_JOIN, c.restrictions.like( 'ac.content', '%world%' ) )
		                        .isLike( 'contentID', 'compat-%' ).count();
		                    """ ) ).isEqualTo( 3 );
	}

	/**
	 * Test: a fetched association with a withClause keeps its condition (joined, not fetched: Hibernate refuses an on
	 * clause on a fetch join).
	 */
	@DisplayName( "A withClause on a fetched association keeps the condition" )
	@Test
	public void testWithClauseOnFetchedJoin() {
		String hql = ( String ) run( """
		                             c = entityCriteria( 'cbContent' ).fetch( 'contentVersions' );
		                             result = c.joinTo( 'contentVersions', 'ac', c.INNER_JOIN, c.restrictions.isTrue( 'ac.isActive' ) ).getHQL();
		                             """ );
		assertThat( hql ).contains( " on " );
		assertThat( hql ).doesNotContain( "fetch" );
		assertThat( number( """
		                    c = entityCriteria( 'cbContent' ).fetch( 'contentVersions' );
		                    result = c.joinTo( 'contentVersions', 'ac', c.INNER_JOIN, c.restrictions.isTrue( 'ac.isActive' ) )
		                        .like( 'ac.content', '%boxlang%' ).isLike( 'contentID', 'compat-%' ).list().len();
		                    """ ) ).isEqualTo( 2 );
	}

	/**
	 * Test: a withClause cannot add a join of its own (an on clause cannot see later joins).
	 */
	@DisplayName( "A withClause that needs another join is an orm.argument error" )
	@Test
	public void testWithClauseNeedsJoin() {
		IStruct err = error( """
		                     c = entityCriteria( 'cbContent' );
		                     c.joinTo( 'contentVersions', 'ac', c.INNER_JOIN, c.restrictions.isEq( 'ac.author.firstName', 'Ada' ) ).list();
		                     """ );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "withClause" );
	}

	/**
	 * Test: the cborm result transformer constants.
	 */
	@DisplayName( "resultTransformer() takes the cborm constants" )
	@Test
	public void testResultTransformer() {
		assertThat( list( """
		                  c = entityCriteria( 'cbContent' );
		                  result = c.joinTo( 'comments', 'cm' ).isLike( 'contentID', 'compat-%' ).resultTransformer( c.DISTINCT_ROOT_ENTITY ).order( 'slug' )
		                      .list().map( ( e ) => e.getSlug() );
		                  """ ) ).containsExactly( "e-one", "p-one" ).inOrder();
		Array rows = ( Array ) run( """
		                            c = entityCriteria( 'cbContent' );
		                            result = c.isEq( 'slug', 'e-one' ).resultTransformer( c.ALIAS_TO_ENTITY_MAP ).list();
		                            """ );
		assertThat( ( ( IStruct ) rows.get( 0 ) ).getAsString( Key.of( "slug" ) ) ).isEqualTo( "e-one" );
		assertThat( run( "c = entityCriteria( 'cbContent' ); result = c.isEq( 'slug', 'e-one' ).resultTransformer( 'root_entity' ).get();" ) )
		    .isInstanceOf( IClassRunnable.class );
		assertThat( run( "result = entityCriteria( 'cbContent' ).DISTINCT_ROOT_ENTITY;" ) ).isEqualTo( "DISTINCT_ROOT_ENTITY" );
		IStruct err = error( "entityCriteria( 'cbContent' ).resultTransformer( 'TO_LIST' );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: DISTINCT_ROOT_ENTITY drops repeated root entities from the rows.
	 */
	@DisplayName( "DISTINCT_ROOT_ENTITY returns each root entity once" )
	@Test
	public void testDistinctRootEntityDedupes() {
		// comments joined from the comment side: one comment row per comment, the related content repeats
		assertThat( list( """
		                  c = entityCriteria( 'cbComment' );
		                  result = c.isLike( 'commentID', 'compat-%' ).order( 'commentID' ).list().map( ( m ) => m.getRelatedContent().getSlug() );
		                  """ ) ).containsExactly( "e-one", "e-one", "p-one" ).inOrder();
		assertThat( number( """
		                    c = entityCriteria( 'cbContent' );
		                    result = c.joinTo( 'comments', 'cm' ).isLike( 'contentID', 'compat-%' ).resultTransformer( c.DISTINCT_ROOT_ENTITY ).list().len();
		                    """ ) ).isEqualTo( 2 );
	}

	/**
	 * Test: a sort name that is a projection alias sorts by the projection.
	 */
	@DisplayName( "list( sortOrder ) and order() accept a projection alias" )
	@Test
	public void testOrderByProjectionAlias() {
		Array rows = ( Array ) run( """
		                            result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' )
		                                .withProjections( groupProperty = 'relatedContent.slug:slug', count = 'commentID:value' )
		                                .asStruct().list( sortOrder = 'value DESC' );
		                            """ );
		assertThat( ( ( IStruct ) rows.get( 0 ) ).getAsString( Key.of( "slug" ) ) ).isEqualTo( "e-one" );
		assertThat( ( ( IStruct ) rows.get( 0 ) ).get( Key.of( "value" ) ).toString() ).isEqualTo( "2" );
		Array ascending = ( Array ) run( """
		                                 result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' )
		                                     .withProjections( groupProperty = 'relatedContent.slug:slug', count = 'commentID:value' )
		                                     .order( 'value' ).asStruct().list();
		                                 """ );
		assertThat( ( ( IStruct ) ascending.get( 0 ) ).getAsString( Key.of( "slug" ) ) ).isEqualTo( "p-one" );
	}

	/**
	 * Test: a property only a subclass entity has can be used on the base entity.
	 */
	@DisplayName( "A subclass property works on a base entity criteria" )
	@Test
	public void testSubclassProperty() {
		assertThat(
		    list( "result = entityCriteria( 'cbMenuItem' ).isEq( 'contentSlug', 'e-one' ).list().map( ( m ) => m.getTitle() & '|' & m.getContentSlug() );" ) )
		    .containsExactly( "To e-one|e-one" );
		assertThat( number( "result = entityCriteria( 'cbMenuItem' ).isLike( 'menuItemID', 'compat-%' ).order( 'contentSlug' ).count();" ) ).isEqualTo( 2 );
		// a typo still gets "did you mean"
		assertThat( type( error( "entityCriteria( 'cbMenuItem' ).isEq( 'contentSlugg', 'x' ).list();" ) ) ).isEqualTo( "orm.property.unknown" );
	}

	/**
	 * Test: the class pseudo-property compares an entity's type by discriminator value or entity name.
	 */
	@DisplayName( "isEq( 'class', ... ) compares the entity type" )
	@Test
	public void testClassPseudoProperty() {
		assertThat( number(
		    "result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' ).createCriteria( 'relatedContent' ).isEq( 'class', 'Entry' ).count();" ) )
		    .isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' ).isEq( 'relatedContent.class', 'cbPage' ).count();" ) )
		    .isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'cbContent' ).isLike( 'contentID', 'compat-%' ).isIn( 'class', 'Entry,Page' ).count();" ) )
		    .isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'cbContent' ).isLike( 'contentID', 'compat-%' ).ne( 'class', 'Entry' ).count();" ) ).isEqualTo( 1 );
		// as in Hibernate 5, an unknown value matches no row
		assertThat( number( "result = entityCriteria( 'cbContent' ).isLike( 'contentID', 'compat-%' ).isEq( 'class', 'invalid' ).count();" ) ).isEqualTo( 0 );
		assertThat( number( "result = entityCriteria( 'cbContent' ).isLike( 'contentID', 'compat-%' ).ne( 'class', 'invalid' ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'cbContent' ).isLike( 'contentID', 'compat-%' ).isIn( 'class', 'Page,invalid' ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: select new map( ... ) and tuple rows return BoxLang entities, not facades.
	 */
	@DisplayName( "HQL map and tuple rows hold BoxLang entities" )
	@Test
	public void testMapAndTupleRowsUnwrapFacades() {
		assertThat( run( """
		                 rows = ormExecuteQuery( "select new map( c as content, count( cm.commentID ) as total ) from cbContent c join c.comments cm
		                     where c.contentID like 'compat-%' group by c order by count( cm.commentID ) desc" );
		                 result = rows[ 1 ].content.getSlug() & ":" & rows[ 1 ].total;
		                 """ ) ).isEqualTo( "e-one:2" );
		assertThat( run( """
		                 rows = ormExecuteQuery( "select c, count( cm.commentID ) from cbContent c join c.comments cm
		                     where c.contentID like 'compat-%' group by c order by count( cm.commentID ) desc" );
		                 result = rows[ 1 ][ 1 ].getSlug();
		                 """ ) ).isEqualTo( "e-one" );
	}

	/**
	 * Test: cborm's sqlProjection adds a native SQL column, with {alias}/this_ references, as a struct or an array.
	 */
	@DisplayName( "withProjections( sqlProjection ) adds a native SQL column" )
	@Test
	public void testSqlProjection() {
		IStruct row = ( IStruct ) run(
		    """
		    result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' )
		        .withProjections( sqlProjection = { sql : "count(distinct this_.FK_contentID)", alias : "contents", property : "content" } )
		        .asStruct().get();
		    """ );
		assertThat( Long.parseLong( row.get( Key.of( "contents" ) ).toString() ) ).isEqualTo( 2 );
		IStruct both = ( IStruct ) run(
		    """
		    result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' )
		        .withProjections( sqlProjection = [ { sql : "count(*)", alias : "total" }, { sql : "max({alias}.author)", property : "author" } ] )
		        .asStruct().get();
		    """ );
		assertThat( Long.parseLong( both.get( Key.of( "total" ) ).toString() ) ).isEqualTo( 3 );
		assertThat( both.getAsString( Key.of( "author" ) ) ).isEqualTo( "Bob" );
		IStruct err = error( "entityCriteria( 'cbComment' ).withProjections( sqlProjection = { sql : 'count(*) + ?' } ).asStruct().get();" );
		assertThat( type( err ) ).isEqualTo( "orm.query.parameter" );
	}

	/**
	 * Test: the Hibernate 5 table aliases cborm code wrote in sqlRestriction (this_, alias1_) resolve.
	 */
	@DisplayName( "sqlRestriction() resolves the Hibernate 5 aliases this_ and <alias><n>_" )
	@Test
	public void testLegacySqlAliases() {
		assertThat( list( """
		                  result = entityCriteria( 'cbContent' ).createAlias( 'creator', 'creator' ).isLike( 'contentID', 'compat-%' )
		                      .sqlRestriction( "this_.slug <> ? and creator1_.firstName = ?", [ 'e-two', 'Ada' ] )
		                      .order( 'slug' ).list().map( ( e ) => e.getSlug() );
		                  """ ) ).containsExactly( "e-one", "p-one" ).inOrder();
		// the second alias is numbered 2, as Hibernate 5 did
		assertThat( number( """
		                    c = entityCriteria( 'cbComment' ).createAlias( 'relatedContent', 'content' ).createAlias( 'content.creator', 'creator' );
		                    result = c.isLike( 'commentID', 'compat-%' ).sql( "creator2_.lastName = 'Lovelace' and content1_.slug = 'e-one'" ).count();
		                    """ ) ).isEqualTo( 2 );
		// a name that is not one of the criteria's aliases is left as written
		assertThat( ( String ) run( "result = entityCriteria( 'cbContent' ).sql( 'other3_.x = 1' ).getHQL();" ) ).contains( "other3_.x = 1" );
	}

	/**
	 * Test: count() clears projections and struct results, so the same criteria lists entities afterwards (cborm).
	 */
	@DisplayName( "count() leaves the criteria ready to list entities" )
	@Test
	public void testCountResetsProjections() {
		Array result = ( Array ) run( """
		                              c = entityCriteria( 'cbContent' ).isLike( 'contentID', 'compat-%' );
		                              total = c.withProjections( count = "contentID:n" ).asStruct().get();
		                              n = c.count();
		                              rows = c.order( 'slug' ).list();
		                              result = [ total.n, n, rows.len(), rows[ 1 ].getSlug() ];
		                              """ );
		assertThat( Long.parseLong( result.get( 0 ).toString() ) ).isEqualTo( 3 );
		assertThat( Long.parseLong( result.get( 1 ).toString() ) ).isEqualTo( 3 );
		assertThat( Long.parseLong( result.get( 2 ).toString() ) ).isEqualTo( 3 );
		assertThat( result.get( 3 ) ).isEqualTo( "e-one" );
	}

	/**
	 * Test: an array value in a where() struct is an in-list, for ids and for entities (cborm deleteWhere/findWhere).
	 */
	@DisplayName( "where( struct ) treats an array value as in (...)" )
	@Test
	public void testWhereStructArray() {
		assertThat( number( "result = entityCriteria( 'cbComment' ).where( { commentID : [ 'compat-c1', 'compat-c3' ] } ).count();" ) ).isEqualTo( 2 );
		assertThat(
		    number( """
		            result = entityCriteria( 'cbComment' )
		                .where( { relatedContent : [ entityLoadByPK( 'cbContent', 'compat-e1' ), entityLoadByPK( 'cbContent', 'compat-p1' ) ] } ).count();
		            """ ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'cbComment' ).where( { commentID : [] } ).count();" ) ).isEqualTo( 0 );
		assertThat( number( """
		                    result = entityCriteria( 'cbComment' ).where( { relatedContent : [ entityLoadByPK( 'cbEntry', 'compat-e1' ) ] } ).deleteAll();
		                    """ ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'cbComment' ).isLike( 'commentID', 'compat-%' ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: a subclass entity's id is the inherited one, for entityGetId, entityGetMetadata and idEq/notIdEq.
	 */
	@DisplayName( "A subclass entity uses its inherited id" )
	@Test
	public void testSubclassInheritedId() {
		assertThat( run( "result = entityGetId( entityLoadByPK( 'cbEntry', 'compat-e1' ) );" ) ).isEqualTo( "compat-e1" );
		assertThat( list( "result = entityGetMetadata( 'cbEntry' ).idProperties;" ) ).containsExactly( "contentID" );
		assertThat( run( "result = entityCriteria( 'cbEntry' ).isEq( 'slug', 'e-one' ).notIdEq( 'compat-e1' ).exists();" ) ).isEqualTo( false );
		assertThat( run( "result = entityCriteria( 'cbEntry' ).isEq( 'slug', 'e-one' ).idEq( 'compat-e1' ).exists();" ) ).isEqualTo( true );
	}
}
