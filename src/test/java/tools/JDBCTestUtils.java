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
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
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
	 * Remove the manufacturers table from the database.
	 *
	 * @param datasource
	 */
	public static void cleanupTables( DataSource datasource, IBoxContext context ) {
		datasource.execute( "DROP TABLE manufacturers", context );
	}

	public static void resetAlternateTables( DataSource datasource, IBoxContext context ) {
		try {
			datasource.execute( "CREATE TABLE alternate_ds ( id VARCHAR(40), name VARCHAR(155) )", context );
		} catch ( DatabaseException e ) {
			// Ignore the exception if the table already exists
		}
		datasource.execute( "DELETE FROM alternate_ds", context );
		datasource.execute(
		    """
		    INSERT INTO alternate_ds (id,name) VALUES
		    ('123e4567-e89b-12d3-a456-426614174000', 'Marty McTester' )
		    """, context );
	}

	/**
	 * Reset the `manufacturers` table to a known, consistent state for testing.
	 *
	 * @param datasource
	 */
	public static void resetTables( DataSource datasource, IBoxContext context ) {
		try {
			datasource.execute( "CREATE TABLE manufacturers ( id INTEGER, name VARCHAR(155), address VARCHAR(155) )", context );
			datasource.execute( "CREATE TABLE vehicles ( vin VARCHAR(17), make VARCHAR(155), model VARCHAR(155), FK_manufacturer INTEGER )", context );
		} catch ( DatabaseException e ) {
			// Ignore the exception if the table already exists
			System.out.println( e.getMessage() );
		}
		datasource.execute( "DELETE FROM manufacturers", context );
		datasource.execute( "DELETE FROM vehicles", context );
		datasource.execute(
		    """
		    INSERT INTO manufacturers ( id, name, address ) VALUES
		    ( 1, 'Ford Motor Company', '202 Ford Way, Dearborn MI' ),
		    ( 42, 'Honda Motor Co.', 'CHI-5, 1919 Torrance Blvd., Torrance, CA 90501 - 2746 ' ),
		    ( 77, 'General Moters Corporation', 'P.O. BOX 33170, Detroit, MI 48232-5170' )
		    """, context );

		datasource.execute(
		    """
		    INSERT INTO vehicles (vin,make,model,FK_manufacturer) VALUES
		    ('1HGCM82633A123456','Honda', 'Accord', 42 ),
		    ('2HGCM82633A654321','Honda', 'Civic', 42 ),
		    ('1HGCM82633A789012','Honda', 'Ridgeline', 42 )
		    """, context );
	}
}
