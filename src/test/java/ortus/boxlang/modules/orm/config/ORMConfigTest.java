package ortus.boxlang.modules.orm.config;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;

public class ORMConfigTest {

	@Test
	public void testDialectTranslation() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.dialect, "DerbyTenSeven"
		) ).toHibernateConfig();

		assertEquals( "org.hibernate.dialect.DerbyTenSevenDialect", config.getProperty( AvailableSettings.DIALECT ) );
	}

	@Test
	public void testDialectNormalization() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.dialect, "DerbyTenSevenDialect"
		) ).toHibernateConfig();

		assertEquals( "org.hibernate.dialect.DerbyTenSevenDialect", config.getProperty( AvailableSettings.DIALECT ) );
	}

	@Test
	public void testGenericSettings() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.catalog, "foobar",
		    ORMKeys.schema, "dbo"
		) ).toHibernateConfig();

		assertEquals( "foobar", config.getProperty( AvailableSettings.DEFAULT_CATALOG ) );
		assertEquals( "dbo", config.getProperty( AvailableSettings.DEFAULT_SCHEMA ) );
	}

	@Test
	public void testDefaultConfig() {
		ORMConfig config = new ORMConfig( Struct.of() );
		assertFalse( config.secondaryCacheEnabled );
		assertFalse( config.logSQL );
		assertFalse( config.eventHandling );
		assertTrue( config.autoGenMap );
		assertFalse( config.saveMapping );

		// BREAKING CHANGE: These settings are both FALSE by default in BoxLang, but TRUE by default in Lucee.
		assertFalse( config.flushAtRequestEnd );
		assertFalse( config.autoManageSession );

		// BREAKING CHANGE: In Lucee, this is TRUE by default in the `skipCFCWithError` setting.
		assertFalse( config.ignoreParseErrors );
	}

	@Test
	public void testDefaultOpposites() {
		ORMConfig config = new ORMConfig( Struct.of(
		    // common settings
		    ORMKeys.cfclocation, Array.fromString( "/foo/models" ),
		    ORMKeys.dialect, "org.hibernate.dialect.SQLServer2008Dialect",
		    ORMKeys.dbcreate, "update",

		    // cache config
		    ORMKeys.secondaryCacheEnabled, true,
		    ORMKeys.cacheProvider, "ehCache",
		    ORMKeys.cacheConfig, "/cbapp/config/ehcache.xml",

		    // logging
		    ORMKeys.logSQL, true,

		    // session management
		    ORMKeys.flushAtRequestEnd, true,
		    ORMKeys.autoManageSession, true,

		    // event handling
		    ORMKeys.eventHandling, true,
		    ORMKeys.eventHandler, "cborm.models.EventHandler",

		    // mapping generation
		    ORMKeys.saveMapping, true,
		    ORMKeys.autoGenMap, false,
		    ORMKeys.skipCFCWithError, true
		) );

		// common settings
		assertEquals( "/foo/models", String.join( "", config.entityPaths ) );
		assertEquals( "org.hibernate.dialect.SQLServer2008Dialect", config.dialect );
		assertEquals( "update", config.dbcreate );

		// cache config
		assertTrue( config.secondaryCacheEnabled );
		assertEquals( "ehCache", config.cacheProvider );
		assertEquals( "/cbapp/config/ehcache.xml", config.cacheConfig );

		// logging
		assertTrue( config.logSQL );

		// session management
		assertTrue( config.flushAtRequestEnd );
		assertTrue( config.autoManageSession );

		// event handling
		assertTrue( config.eventHandling );
		assertEquals( "cborm.models.EventHandler", config.eventHandler );

		// mapping generation
		assertTrue( config.saveMapping );
		assertFalse( config.autoGenMap );
		assertTrue( config.ignoreParseErrors );
	}
}
