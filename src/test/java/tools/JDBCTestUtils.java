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

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.jdbc.DataSource;

/**
 * A collection of test utilities for assistance with JDBC tests, which are
 * highly environment-specific and depend on certain loaded JDBC drivers.
 */
public class JDBCTestUtils {

	public static void resetAlternateTables( DataSource datasource, IBoxContext context ) {
		datasource.execute( "DELETE FROM alternate_ds", context );
		datasource.execute(
		    """
		    INSERT INTO alternate_ds (id,name) VALUES
		    ('123e4567-e89b-12d3-a456-426614174000', 'Bilbo Baggins' ),
		    ('123e4567-e89b-12d3-a456-426614174001', 'Frodo Baggins' )
		    """, context );
	}

	/**
	 * Reset the `manufacturers` table to a known, consistent state for testing.
	 *
	 * @param datasource
	 */
	public static void resetTables( DataSource datasource, IBoxContext context ) {
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
		    ('1HGCM82633A789012','Honda', 'Ridgeline', 42 ),
		    ('9ABAZ85656A776723','Ford', 'Fusion', 1 )
		    """, context );
	}
}
