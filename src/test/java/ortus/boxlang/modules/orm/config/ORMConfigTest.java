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
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;
import tools.BaseORMTest;

public class ORMConfigTest extends BaseORMTest {

	@Test
	public void testDialectTranslation() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.dialect, "DerbyTenSeven"
		), context ).toHibernateConfig();

		assertEquals( "org.hibernate.dialect.DerbyTenSevenDialect", config.getProperty( AvailableSettings.DIALECT ) );
	}

	@Test
	public void testDialectNormalization() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.dialect, "DerbyTenSevenDialect"
		), context ).toHibernateConfig();

		assertEquals( "org.hibernate.dialect.DerbyTenSevenDialect", config.getProperty( AvailableSettings.DIALECT ) );
	}

	@Test
	public void testGenericSettings() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.catalog, "foobar",
		    ORMKeys.schema, "dbo"
		), context ).toHibernateConfig();

		assertEquals( "foobar", config.getProperty( AvailableSettings.DEFAULT_CATALOG ) );
		assertEquals( "dbo", config.getProperty( AvailableSettings.DEFAULT_SCHEMA ) );
	}

	@Test
	public void testDefaultConfig() {
		ORMConfig config = new ORMConfig( Struct.of(), context );
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
		    ORMKeys.entityPaths, Array.fromString( "/foo/models" ),
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
		    ORMKeys.ignoreParseErrors, true
		), context );

		// common settings
		assertThat( String.join( "", config.entityPaths ) ).isEqualTo( "/foo/models" );
		assertThat( config.dialect ).isEqualTo( "org.hibernate.dialect.SQLServer2008Dialect" );
		assertThat( config.dbcreate ).isEqualTo( "update" );

		// cache config
		assertThat( config.secondaryCacheEnabled ).isTrue();
		assertThat( config.cacheProvider ).isEqualTo( "ehCache" );
		assertThat( config.cacheConfigFile ).isEqualTo( "/cbapp/config/ehcache.xml" );

		// logging
		assertThat( config.logSQL ).isTrue();

		// session management
		assertThat( config.flushAtRequestEnd ).isTrue();
		assertThat( config.autoManageSession ).isTrue();

		// event handling
		assertThat( config.eventHandling ).isTrue();
		assertThat( config.eventHandler ).isEqualTo( "cborm.models.EventHandler" );

		// mapping generation
		assertThat( config.saveMapping ).isTrue();
		assertFalse( config.autoGenMap );
		assertThat( config.ignoreParseErrors ).isTrue();
	}
}
