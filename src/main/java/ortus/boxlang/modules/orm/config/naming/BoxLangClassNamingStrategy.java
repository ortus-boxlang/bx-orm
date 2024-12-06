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
package ortus.boxlang.modules.orm.config.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * A physical naming strategy that fires the appropriate methods on a named
 * Boxlang class to convert all identifiers to whatever case the Boxlang class
 * specifies.
 * <p>
 * Historically the CFML component would implement these methods:
 * <ul>
 * <li><code>getTableName()</code></li>
 * <li><code>getColumnName()</code></li>
 * </ul>
 *
 * For BoxLang, we also support the following methods:
 * <ul>
 * <li><code>getCatalogName()</code></li>
 * <li><code>getSchemaName()</code></li>
 * <li><code>getSequenceName()</code></li>
 * </ul>
 */
public class BoxLangClassNamingStrategy implements PhysicalNamingStrategy {

	private final IClassRunnable customNamingStrategy;

	public BoxLangClassNamingStrategy( IClassRunnable customNamingStrategy ) {
		this.customNamingStrategy = customNamingStrategy;
	}

	@Override
	public Identifier toPhysicalCatalogName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		// @TODO: Implement this. This was unsupported in the Lucee Hibernate extension.
		// Call `getCatalogName()` on the customNamingStrategy class, passing
		// `logicalName` as the sole parameter.
		return null;
	}

	@Override
	public Identifier toPhysicalSchemaName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		// @TODO: Implement this. This was unsupported in the Lucee Hibernate extension.
		// Call `getSchemaName()` on the customNamingStrategy class, passing
		// `logicalName` as the sole parameter.
		return null;
	}

	@Override
	public Identifier toPhysicalTableName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		// @TODO: Implement this.
		// Call `getTableName()` on the customNamingStrategy class, passing
		// `logicalName` as the sole parameter.
		return null;
	}

	@Override
	public Identifier toPhysicalSequenceName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		// @TODO: Implement this. This was unsupported in the Lucee Hibernate extension.
		// Call `getSequenceName()` on the customNamingStrategy class, passing
		// `logicalName` as the sole parameter.
		return null;
	}

	@Override
	public Identifier toPhysicalColumnName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		// @TODO: Implement this.
		// Call `getColumnName()` on the customNamingStrategy class, passing
		// `logicalName` as the sole parameter.
		return null;
	}

}
