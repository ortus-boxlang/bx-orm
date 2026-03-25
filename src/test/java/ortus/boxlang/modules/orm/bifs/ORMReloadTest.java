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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.DatasourceService;
import tools.BaseORMTest;

public class ORMReloadTest extends BaseORMTest {

	@DisplayName( "It closes all session factories" )
	@Test
	public void testORMReload() {
		instance.executeSource(
		    """
		    sessionFactoryPreReload = ormGetSessionFactory();
		    result = ormReload();
		    sessionFactoryPostReload = ormGetSessionFactory();

		    oldSessionFactoryClosed = sessionFactoryPreReload.isClosed();
		    """,
		    context
		);
		assertThat( variables.get( result ).getClass().getName() ).isEqualTo( "org.hibernate.internal.SessionFactoryImpl" );
		assertThat( variables.getAsBoolean( Key.of( "oldSessionFactoryClosed" ) ) ).isTrue();
		assertThat( variables.get( Key.of( "sessionFactoryPreReload" ) ) ).isNotEqualTo( variables.get( Key.of( "sessionFactoryPostReload" ) ) );
	}

	@Disabled( "Need to resolve datasource.getUniqueName() not found issue first" )
	@DisplayName( "It shuts down datasources and connection pools" )
	@Test
	public void testORMReloadDatasourceShutdown() {
		Key					testDB							= Key.of( "TestDB" );
		Key					dsn2							= Key.of( "dsn2" );

		DatasourceService	datasourceService				= instance.getDataSourceService();
		ConnectionManager	connectionManager				= context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		DataSource			preReloadDatasource				= connectionManager.getDatasource( testDB );
		DataSource			preReloadDatasource2			= connectionManager.getDatasource( dsn2 );
		Key					uniqueName						= preReloadDatasource.getUniqueName();
		DataSource			preReloadDatasourceFromService	= datasourceService.get( uniqueName );
		var					allDS							= datasourceService.getAll();

		instance.executeSource(
		    """
		    result = ormReload();
		    """,
		    context
		);

		DataSource	postReloadDatasource			= connectionManager.getDatasource( testDB );
		DataSource	postReloadDatasource2			= connectionManager.getDatasource( dsn2 );
		DataSource	postReloadDatasourceFromService	= datasourceService.get( preReloadDatasource.getUniqueName() );
		assertThat( preReloadDatasourceFromService.getHikariDataSource().isClosed() ).isTrue();
		assertThat( preReloadDatasource.getHikariDataSource().isClosed() ).isTrue();
		assertThat( preReloadDatasource2.getHikariDataSource().isClosed() ).isTrue();
		assertThat( preReloadDatasource ).isNotSameInstanceAs( postReloadDatasource );
		assertThat( preReloadDatasource2 ).isNotSameInstanceAs( postReloadDatasource2 );
		assertThat( preReloadDatasourceFromService ).isNotSameInstanceAs( postReloadDatasourceFromService );
	}
}
