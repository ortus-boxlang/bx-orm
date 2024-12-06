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
package tools;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.config.segments.DatasourceConfig;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.DatabaseException;

/**
 * A collection of test utilities for assistance with JDBC tests, which are
 * highly environment-specific and depend on certain loaded JDBC drivers.
 */
public class JDBCTestUtils {

	/**
	 * Boolean test that a MySQL database is reachable at localhost:3306.
	 * <p>
	 * Useful in `@EnabledIf` annotations for conditionally executing MySQL-specific tests:
	 * <p>
	 * <code>
	 * &#64;EnabledIf( "tools.JDBCTestUtils#isMySQLReachable" )
	 * </code>
	 *
	 * @return
	 */
	public static boolean isMySQLReachable() {
		try {
			DataSource.fromStruct(
			    "MySQLReachable",
			    Struct.of(
			        "database", "MySQLReachable",
			        "driver", "mysql",
			        "connectionString", "jdbc:mysql//localhost:3306/mysqlStoredProc",
			        "maxConnections", 1,
			        "minConnections", 1,
			        "username", "root",
			        "password", "db_pass"
			    )
			);
		} catch ( Exception e ) {
			return false;
		}
		return true;
	}

	/**
	 * Boolean test for the presence of the BoxLang MySQL module.
	 * <p>
	 * Useful in `@EnabledIf` annotations for conditional test execution based on the loaded JDBC drivers:
	 * <p>
	 * <code>
	 * &#64;EnabledIf( "tools.JDBCTestUtils#hasMySQLModule" )
	 * </code>
	 *
	 * @return
	 */
	public static boolean hasMySQLModule() {
		return BoxRuntime.getInstance().getModuleService().hasModule( Key.of( "mysql" ) );
	}

	/**
	 * Boolean test for the presence of the BoxLang MSSQL module
	 * <p>
	 * Useful in `@EnabledIf` annotations for conditional test execution based on the loaded JDBC drivers:
	 * <p>
	 * <code>
	 * &#64;EnabledIf( "tools.JDBCTestUtils#hasMSSQLModule" )
	 * </code>
	 *
	 * @return
	 */
	public static boolean hasMSSQLModule() {
		return BoxRuntime.getInstance().getModuleService().hasModule( Key.of( "mssql" ) );
	}

	/**
	 * Build out a structure of datasource configuration for testing. This is to inflate the state of a DatasourceConfig object
	 *
	 * @param databaseName String database name; must be unique for each test. In the future, we can change this to use either reflection or a stack trace
	 * @param properties   The properties to merge in
	 */
	public static IStruct getDatasourceConfig( String databaseName, IStruct properties ) {
		properties.computeIfAbsent( Key.of( "connectionString" ), key -> "jdbc:derby:memory:" + databaseName + ";create=true" );

		return Struct.of(
		    "name", databaseName,
		    "properties", properties
		);
	}

	/**
	 * Build out a structure of datasource configuration for testing. This is to inflate the state of a DatasourceConfig object
	 *
	 * @param databaseName String database name; must be unique for each test. In the future, we can change this to use either reflection or a stack trace
	 */
	public static IStruct getDatasourceConfig( String databaseName ) {
		return getDatasourceConfig( databaseName, new Struct() );
	}

	/**
	 * Build out a DatasourceConfig object for testing.
	 *
	 * @param databaseName String database name; must be unique for each test. In the future, we can change this to use either reflection or a stack trace
	 */
	public static DatasourceConfig buildDatasourceConfig( String databaseName ) {
		return new DatasourceConfig(
		    Key.of( databaseName ),
		    Struct.of(
		        "database", databaseName,
		        "driver", "derby",
		        "connectionString", "jdbc:derby:memory:" + databaseName + ";create=true"
		    )
		);
	}

	/**
	 * Build out a DataSource for testing. This doesn't register it, just creates a mock datasource for testing.
	 * The driver will be derby
	 *
	 * @param databaseName String database name; must be unique for each test. In the future, we can change this to use either reflection or a stack trace
	 *                     to grab the caller class name and thus ensure uniqueness.
	 * @param properties   The properties to merge in
	 */
	public static DataSource buildDatasource( String databaseName, IStruct properties ) {
		return DataSource.fromStruct(
		    databaseName,
		    Struct.of(
		        "database", databaseName,
		        "driver", "derby",
		        "connectionString", "jdbc:derby:memory:" + databaseName + ";create=true"
		    ) );
	}

	/**
	 * Build out a DataSource for testing. This doesn't register it, just creates a mock datasource for testing.
	 * The driver will be derby
	 *
	 * @param databaseName String database name; must be unique for each test. In the future, we can change this to use either reflection or a stack trace
	 *                     to grab the caller class name and thus ensure uniqueness.
	 */
	public static DataSource buildDatasource( String databaseName ) {
		return buildDatasource( databaseName, new Struct() );
	}

	/**
	 * Construct a test DataSource for use in testing.
	 * <p>
	 * This method is useful for creating a DataSource for use in testing, and is especially useful for in-memory databases like Apache Derby.
	 *
	 * @param datasourceName String database name; must be unique for each test. In the future, we can change this to use either reflection or a stack
	 *                       trace to grab the caller class name and thus ensure uniqueness.
	 *
	 * @return A DataSource instance with a consistent `manufacturers` table created.
	 */
	public static DataSource constructTestDataSource( String datasourceName ) {
		DataSource datasource = DataSource.fromStruct(
		    datasourceName,
		    Struct.of(
		        "database", datasourceName,
		        "driver", "derby",
		        "connectionString", "jdbc:derby:memory:" + datasourceName + ";create=true",
		        "maxConnections", 50
		    ) );
		try {
			datasource.execute( "CREATE TABLE manufacturers ( id INTEGER, name VARCHAR(155), address VARCHAR(155) )" );
			datasource.execute( "CREATE TABLE vehicles ( vin VARCHAR(17), make VARCHAR(155), model VARCHAR(155), FK_manufacturer INTEGER )" );
		} catch ( DatabaseException e ) {
			// Ignore the exception if the table already exists
		}
		return datasource;
	}

	/**
	 * Remove the manufacturers table from the database.
	 *
	 * @param datasource
	 */
	public static void cleanupTables( DataSource datasource ) {
		datasource.execute( "DROP TABLE manufacturers" );
	}

	/**
	 * Reset the `manufacturers` table to a known, consistent state for testing.
	 *
	 * @param datasource
	 */
	public static void resetTables( DataSource datasource ) {
		datasource.execute( "TRUNCATE TABLE manufacturers" );
		datasource.execute(
		    "INSERT INTO manufacturers ( id, name, address ) VALUES ( 1, 'Ford Motor Company', '202 Ford Way, Dearborn MI' )" );
		datasource
		    .execute( "INSERT INTO manufacturers ( id, name, address ) VALUES ( 77, 'General Moters Corporation', 'P.O. BOX 33170, Detroit, MI 48232-5170' )" );
		datasource.execute(
		    "INSERT INTO manufacturers ( id, name, address ) VALUES ( 42, 'Honda Motor Co.', 'CHI-5, 1919 Torrance Blvd., Torrance, CA 90501 - 2746 ' )" );

		datasource
		    .execute( "TRUNCATE TABLE vehicles;INSERT INTO vehicles (vin,make,model,FK_manufacturer) VALUES('1HGCM82633A123456','Honda', 'Accord', 42 );" );
	}
}
