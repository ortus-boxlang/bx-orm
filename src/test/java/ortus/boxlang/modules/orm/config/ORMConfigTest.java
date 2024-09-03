package ortus.boxlang.modules.orm.config;

import static org.junit.Assert.assertEquals;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Struct;

public class ORMConfigTest {

	@Test
	public void testDialectTranslation() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB1",
		    ORMKeys.dialect, "DerbyTenSeven"
		) ).toHibernateConfig();

		assertEquals( "org.hibernate.dialect.DerbyTenSevenDialect", config.getProperty( AvailableSettings.DIALECT ) );
	}

	@Test
	public void testDialectNormalization() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB1",
		    ORMKeys.dialect, "DerbyTenSevenDialect"
		) ).toHibernateConfig();

		assertEquals( "org.hibernate.dialect.DerbyTenSevenDialect", config.getProperty( AvailableSettings.DIALECT ) );
	}

	@Test
	public void testGenericSettings() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB1",
		    ORMKeys.catalog, "foobar",
		    ORMKeys.schema, "dbo"
		) ).toHibernateConfig();

		assertEquals( "foobar", config.getProperty( AvailableSettings.DEFAULT_CATALOG ) );
		assertEquals( "dbo", config.getProperty( AvailableSettings.DEFAULT_SCHEMA ) );
	}
}
