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
package ortus.boxlang.modules.orm.config;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import tools.BaseORMTest;

/**
 * The postCommit event: fired on the entity and the global event handler once a write is committed, never for
 * rolled-back writes. CommitNote logs to request.commitLog, the global handler to request.globalCommitLog.
 */
public class PostCommitTest extends BaseORMTest {

	/**
	 * Remove the rows and logs these tests add.
	 */
	@AfterEach
	public void cleanUp() {
		instance.executeSource( """
		                        queryExecute( "DELETE FROM commit_notes" );
		                        structDelete( request, "commitLog" );
		                        structDelete( request, "globalCommitLog" );
		                        """, context );
	}

	/**
	 * Inside a transaction, events fire after the commit, in write order, on the entity and the global handler.
	 */
	@DisplayName( "postCommit fires after the transaction commits, in write order, on entity and global handler" )
	@Test
	public void testFiresAfterCommit() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				a = entityNew( "CommitNote", { title : "a" } );
				b = entityNew( "CommitNote", { title : "b" } );
				entitySave( a );
				entitySave( b );
				ormFlush();
				insideLog = ( request.commitLog ?: [] ).len();
			}
			afterInsert = request.commitLog.toList();
			transaction {
				a.setTitle( "a2" );
				entitySave( a );
				entityDelete( b );
			}
			entityLog = request.commitLog.toList();
			globalLog = request.globalCommitLog.toList();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "insideLog" ) ) ).isEqualTo( 0 );
		assertThat( variables.getAsString( Key.of( "afterInsert" ) ) ).isEqualTo( "insert:a,insert:b" );
		assertThat( variables.getAsString( Key.of( "entityLog" ) ) ).isEqualTo( "insert:a,insert:b,update:a2,delete:b" );
		assertThat( variables.getAsString( Key.of( "globalLog" ) ) ).isEqualTo( "insert:a,insert:b,update:a2,delete:b" );
	}

	/**
	 * A rolled-back write gets no event.
	 */
	@DisplayName( "postCommit does not fire for rolled-back writes" )
	@Test
	public void testNoEventOnRollback() {
		// @formatter:off
		instance.executeSource(
			"""
			transaction {
				entitySave( entityNew( "CommitNote", { title : "gone" } ) );
				ormFlush();
				transactionRollback();
			}
			transaction {
				entitySave( entityNew( "CommitNote", { title : "kept" } ) );
			}
			log  = request.commitLog.toList();
			rows = queryExecute( "SELECT title FROM commit_notes" ).recordCount;
			""",
			context
		);
		// @formatter:on
		assertThat( variables.getAsString( Key.of( "log" ) ) ).isEqualTo( "insert:kept" );
		assertThat( variables.get( Key.of( "rows" ) ) ).isEqualTo( 1 );
	}

	/**
	 * Outside a transaction, events fire when the flush that wrote them ends.
	 */
	@DisplayName( "postCommit fires after the flush outside a transaction" )
	@Test
	public void testOutsideTransaction() {
		// @formatter:off
		instance.executeSource(
			"""
			n = entityNew( "CommitNote", { title : "solo" } );
			entitySave( n );
			beforeFlush = ( request.commitLog ?: [] ).len();
			ormFlush();
			afterFlush = request.commitLog.toList();
			""",
			context
		);
		// @formatter:on
		assertThat( variables.get( Key.of( "beforeFlush" ) ) ).isEqualTo( 0 );
		assertThat( variables.getAsString( Key.of( "afterFlush" ) ) ).isEqualTo( "insert:solo" );
	}
}
