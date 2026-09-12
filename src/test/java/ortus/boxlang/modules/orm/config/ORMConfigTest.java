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
	public void testMetadataDefaultsEnabledWhenDialectMissing() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB"
		), context ).toHibernateConfig();

		assertEquals( "true", config.getProperty( "hibernate.temp.use_jdbc_metadata_defaults" ) );
	}

	@Test
	public void testMetadataDefaultsDisabledWhenDialectConfigured() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.dialect, "DerbyTenSeven"
		), context ).toHibernateConfig();

		assertEquals( "false", config.getProperty( "hibernate.temp.use_jdbc_metadata_defaults" ) );
	}

	@Test
	public void testMetadataDefaultsEnabledWhenDialectBlank() {
		ORMConfig ormConfig = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB"
		), context );
		ormConfig.dialect = "   ";

		Configuration config = ormConfig.toHibernateConfig();

		assertEquals( "true", config.getProperty( "hibernate.temp.use_jdbc_metadata_defaults" ) );
		assertThat( config.getProperty( AvailableSettings.DIALECT ) ).isNull();
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
		assertThat( config.secondaryCacheEnabled ).isFalse();
		assertThat( config.logSQL ).isFalse();
		assertThat( config.eventHandling ).isFalse();
		assertThat( config.autoGenMap ).isTrue();
		assertThat( config.generateMappings ).isTrue();
		assertThat( config.saveMapping ).isFalse();

		// BREAKING CHANGE: These settings are both FALSE by default in BoxLang, but TRUE by default in Lucee.
		assertThat( config.flushAtRequestEnd ).isFalse();
		assertThat( config.autoManageSession ).isFalse();

		// BREAKING CHANGE: In Lucee, this is TRUE by default in the `skipCFCWithError` setting.
		assertThat( config.ignoreParseErrors ).isFalse();
	}

	@Test
	public void testDefaultOpposites() {
		ORMConfig config = new ORMConfig( Struct.of(
		    // common settings
		    ORMKeys.entityPaths, Array.of( "/foo/models" ),
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
		assertThat( config.autoGenMap ).isFalse();
		assertThat( config.generateMappings ).isFalse();
		assertThat( config.ignoreParseErrors ).isTrue();
	}

	@Test
	public void testHibernatePropertiesAbsentByDefault() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB"
		), context ).toHibernateConfig();

		// bx-orm's own hardcoded default is untouched when no `hibernateProperties` struct is supplied.
		assertEquals( "true", config.getProperty( AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION ) );
	}

	@Test
	public void testHibernatePropertiesEmptyStructIsNoOp() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.hibernateProperties, Struct.of()
		), context ).toHibernateConfig();

		assertEquals( "true", config.getProperty( AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION ) );
	}

	@Test
	public void testHibernatePropertiesAppliedToConfiguration() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.hibernateProperties, Struct.of(
		        "hibernate.connection.release_mode", "on_close",
		        "hibernate.custom.some_setting", "someValue"
		    )
		), context ).toHibernateConfig();

		assertEquals( "on_close", config.getProperty( "hibernate.connection.release_mode" ) );
		assertEquals( "someValue", config.getProperty( "hibernate.custom.some_setting" ) );
	}

	@Test
	public void testHibernatePropertiesCanOverrideBxOrmDefaults() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.hibernateProperties, Struct.of(
		        AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION, "false"
		    )
		), context ).toHibernateConfig();

		assertEquals( "false", config.getProperty( AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION ) );
	}

	@Test
	public void testOrmConfigFilePropertiesAreApplied() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.ormConfig, "src/test/resources/app/hibernate-test.properties"
		), context ).toHibernateConfig();

		assertEquals( "on_close", config.getProperty( "hibernate.connection.release_mode" ) );
		assertEquals( "fromFile", config.getProperty( "hibernate.custom.orm-config-test-key" ) );
	}

	@Test
	public void testHibernatePropertiesTakePrecedenceOverOrmConfigFile() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.ormConfig, "src/test/resources/app/hibernate-test.properties",
		    ORMKeys.hibernateProperties, Struct.of(
		        "hibernate.connection.release_mode", "after_transaction"
		    )
		), context ).toHibernateConfig();

		// hibernateProperties wins over the ormConfig file on a conflicting key...
		assertEquals( "after_transaction", config.getProperty( "hibernate.connection.release_mode" ) );
		// ...but non-conflicting keys from the file are still applied.
		assertEquals( "fromFile", config.getProperty( "hibernate.custom.orm-config-test-key" ) );
	}

	@Test
	public void testMissingOrmConfigFileDoesNotThrow() {
		Configuration config = new ORMConfig( Struct.of(
		    ORMKeys.datasource, "TestDB",
		    ORMKeys.ormConfig, "src/test/resources/app/does-not-exist.properties"
		), context ).toHibernateConfig();

		// No exception thrown; the rest of the configuration is unaffected.
		assertEquals( "true", config.getProperty( AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION ) );
	}

	@Test
	public void testAutoGenMap() {
		ORMConfig config = new ORMConfig( Struct.of(
		    ORMKeys.autoGenMap, false
		), context );
		assertThat( config.autoGenMap ).isFalse();
		assertThat( config.generateMappings ).isFalse();

		config = new ORMConfig( Struct.of(
		    ORMKeys.autoGenMap, true
		), context );
		assertThat( config.autoGenMap ).isTrue();
		assertThat( config.generateMappings ).isTrue();

		config = new ORMConfig( Struct.of(
		    ORMKeys.generateMappings, false
		), context );
		assertThat( config.autoGenMap ).isFalse();
		assertThat( config.generateMappings ).isFalse();

		config = new ORMConfig( Struct.of(
		    ORMKeys.generateMappings, true
		), context );
		assertThat( config.autoGenMap ).isTrue();
		assertThat( config.generateMappings ).isTrue();
	}
}
