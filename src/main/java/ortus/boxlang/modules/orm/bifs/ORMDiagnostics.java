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

import org.hibernate.Session;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * {@code ormDiagnostics()}: a read-only snapshot of the ORM for the current application (status, last startup error,
 * entities per datasource, warnings, key settings, open sessions). See the Errors and Diagnostics docs page.
 */
@BoxBIF
public class ORMDiagnostics extends BaseORMBIF {

	/**
	 * A snapshot of the ORM for this application: the first thing to check when something is wrong. It never throws.
	 * <p>
	 * Returns a struct with:
	 * <ul>
	 * <li><code>status</code>: <code>running</code>, <code>failed</code> (startup failed), <code>notStarted</code> or
	 * <code>notEnabled</code>.</li>
	 * <li><code>applicationName</code>, <code>hibernateVersion</code>.</li>
	 * <li><code>startupError</code>: <code>{ at, type, message, detail }</code> when the last startup failed.</li>
	 * <li><code>defaultDatasource</code> and <code>datasources</code>: <code>{ name : [ entity names ] }</code>.</li>
	 * <li><code>entityCount</code> and <code>warnings</code> (e.g. unknown ormtype values).</li>
	 * <li><code>settings</code>: the effective ORM settings that most often explain surprises.</li>
	 * <li><code>sessions</code>: this request's open ORM sessions per datasource, with how many entities each holds and
	 * whether it has unsaved changes.</li>
	 * </ul>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return The diagnostics struct.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IStruct result = new Struct();
		result.put( Key.of( "hibernateVersion" ), org.hibernate.Version.getVersionString() );

		ApplicationBoxContext appContext = context.getApplicationContext();
		if ( appContext == null ) {
			result.put( Key.of( "status" ), "notEnabled" );
			result.put( Key.of( "message" ), "Not running inside an application." );
			return result;
		}
		Key appName = appContext.getApplication().getName();
		result.put( Key.of( "applicationName" ), appName.getName() );

		if ( ORMConfig.loadFromContext( context ) == null ) {
			result.put( Key.of( "status" ), "notEnabled" );
			result.put( Key.of( "message" ), "this.ormEnabled is not true in Application.bx." );
			return result;
		}

		ORMService.BootFailure failure = this.ormService.getBootFailure( appName );
		if ( failure != null ) {
			IStruct error = new Struct();
			error.put( Key.of( "at" ), failure.at().toString() );
			error.put( Key.of( "message" ), String.valueOf( failure.error().getMessage() ) );
			if ( failure.error() instanceof ortus.boxlang.runtime.types.exceptions.BoxLangException ble ) {
				error.put( Key.of( "type" ), ble.getType() );
				error.put( Key.of( "detail" ), ble.getDetail() );
			}
			result.put( Key.of( "startupError" ), error );
		}

		ORMApp app = null;
		try {
			app = this.ormService.getORMAppByContext( context );
		} catch ( RuntimeException ignored ) {
			// Reported through the status below.
		}
		if ( app == null ) {
			result.put( Key.of( "status" ), failure != null ? "failed" : "notStarted" );
			return result;
		}
		result.put( Key.of( "status" ), "running" );

		ORMConfig config = app.getConfig();
		result.put( Key.of( "defaultDatasource" ), config.datasource == null ? "" : config.datasource.getName() );
		IStruct datasources = new Struct( IStruct.TYPES.LINKED );
		for ( EntityRecord record : app.getEntityRecords() ) {
			String ds = record.getDatasource() == null ? "" : record.getDatasource().getName();
			datasources.computeIfAbsent( Key.of( ds ), k -> new Array() );
			( ( Array ) datasources.get( Key.of( ds ) ) ).append( record.getEntityName() );
		}
		datasources.values().forEach( names -> ( ( Array ) names ).sort( ( a, b ) -> a.toString().compareToIgnoreCase( b.toString() ) ) );
		result.put( Key.of( "datasources" ), datasources );
		result.put( Key.of( "entityCount" ), app.getEntityRecords().size() );
		result.put( Key.of( "warnings" ), Array.fromList( app.getOrmTypeWarnings() ) );

		IStruct settings = new Struct( IStruct.TYPES.LINKED );
		settings.put( Key.of( "dbcreate" ), config.dbcreate == null ? "" : config.dbcreate );
		settings.put( Key.of( "dialect" ), config.dialect == null ? "" : config.dialect );
		settings.put( Key.of( "entityPaths" ), config.entityPaths == null ? new Array() : Array.fromArray( config.entityPaths ) );
		settings.put( Key.of( "ormManifest" ), config.ormManifest );
		settings.put( Key.of( "flushAtRequestEnd" ), config.flushAtRequestEnd );
		settings.put( Key.of( "autoManageSession" ), config.autoManageSession );
		settings.put( Key.of( "eventHandling" ), config.eventHandling );
		settings.put( Key.of( "secondaryCacheEnabled" ), config.secondaryCacheEnabled );
		settings.put( Key.of( "logSQL" ), config.logSQL );
		settings.put( Key.of( "ignoreParseErrors" ), config.ignoreParseErrors );
		result.put( Key.of( "settings" ), settings );

		result.put( Key.of( "sessions" ), sessions( context ) );
		return result;
	}

	/**
	 * This request's open ORM sessions, keyed by datasource. Reads only: never opens a session.
	 *
	 * @param context The context in which the BIF is being invoked.
	 *
	 * @return A struct of datasource name to session description; empty when the request has no ORM sessions.
	 */
	private IStruct sessions( IBoxContext context ) {
		IStruct		sessions	= new Struct( IStruct.TYPES.LINKED );
		IBoxContext	jdbcContext	= context.getParentOfType( IJDBCCapableContext.class );
		if ( jdbcContext == null || !jdbcContext.hasAttachment( ORMKeys.ORMContext ) ) {
			return sessions;
		}
		ORMContext ormContext = ( ORMContext ) jdbcContext.getAttachment( ORMKeys.ORMContext );
		ormContext.getSessions().forEach( ( datasource, session ) -> sessions.put( datasource, describe( session ) ) );
		return sessions;
	}

	/**
	 * Describe one session: whether it is open, how many entities and collections it holds, and whether it has unsaved
	 * changes.
	 *
	 * @param session The Hibernate session.
	 *
	 * @return The description struct (with an {@code error} key if the session could not be inspected).
	 */
	private static IStruct describe( Session session ) {
		IStruct info = new Struct( IStruct.TYPES.LINKED );
		info.put( Key.of( "open" ), session.isOpen() );
		if ( session.isOpen() ) {
			try {
				info.put( Key.of( "entityCount" ), session.getStatistics().getEntityCount() );
				info.put( Key.of( "collectionCount" ), session.getStatistics().getCollectionCount() );
				info.put( Key.of( "dirty" ), session.isDirty() );
			} catch ( RuntimeException e ) {
				info.put( Key.of( "error" ), String.valueOf( e.getMessage() ) );
			}
		}
		return info;
	}
}
