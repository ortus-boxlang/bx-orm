package ortus.boxlang.orm.config.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import ortus.boxlang.runtime.runnables.IBoxRunnable;

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

	private final Class<IBoxRunnable> customNamingStrategy;

	public BoxLangClassNamingStrategy( Class<IBoxRunnable> customNamingStrategy ) {
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
