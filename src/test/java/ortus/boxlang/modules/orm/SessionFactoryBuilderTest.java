// package ortus.boxlang.modules.orm;

// import static org.junit.Assert.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertEquals;

// import java.nio.file.Path;
// import java.util.List;
// import java.util.Map;

// import org.hibernate.SessionFactory;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Disabled;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;

// import ortus.boxlang.modules.orm.config.ORMConfig;
// import ortus.boxlang.modules.orm.config.ORMKeys;
// import ortus.boxlang.modules.orm.mapping.EntityRecord;
// import ortus.boxlang.modules.orm.mapping.MappingGenerator;
// import ortus.boxlang.runtime.BoxRuntime;
// import ortus.boxlang.runtime.context.RequestBoxContext;
// import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
// import ortus.boxlang.runtime.jdbc.DataSource;
// import ortus.boxlang.runtime.scopes.Key;
// import ortus.boxlang.runtime.types.Struct;
// import tools.BaseORMTest;
// import tools.JDBCTestUtils;

// public class SessionFactoryBuilderTest {

// static BoxRuntime instance;
// static Key appName = Key.of( "BXORMTest" );
// static Key result = Key.of( "result" );
// static DataSource datasource;
// static RequestBoxContext context;
// public static DataSource alternateDataSource;

// @BeforeAll
// public static void setUp() {
// instance = BoxRuntime.getInstance( true );
// datasource = JDBCTestUtils.constructTestDataSource( "TestDB" );

// context = new ScriptingRequestBoxContext( instance.getRuntimeContext() );
// BaseORMTest.setupApplicationContext( context );

// if ( alternateDataSource == null ) {
// // construct a second datasource
// alternateDataSource = DataSource.fromStruct( "dsn2", Struct.of(
// "database", "dsn2",
// "driver", "derby",
// "connectionString", "jdbc:derby:memory:" + "dsn2" + ";create=true"
// ) );
// }
// // make sure to register the alternate datasource BEFORE orm app startup.
// context.getConnectionManager().register( alternateDataSource );
// }

// @DisplayName( "It can setup a session factory" )
// @Test
// public void testSimpleCase() {
// ORMConfig config = new ORMConfig( Struct.of(
// ORMKeys.datasource, "TestDB"
// ) );
// Map<String, List<EntityRecord>> entities = MappingGenerator.discoverEntities( context, config );
// SessionFactory sessionFactory = new SessionFactoryBuilder( context, datasource, config,
// entities.get( datasource.getOriginalName() ) ).build();

// assertNotNull( sessionFactory );
// }

// @DisplayName( "It can set dialects with alias names" )
// @Test
// public void testDerbyShortNameDialect() {
// ORMConfig config = new ORMConfig( Struct.of(
// ORMKeys.datasource, "TestDB",
// ORMKeys.dialect, "DerbyTenSeven"
// ) );
// Map<String, List<EntityRecord>> entities = MappingGenerator.discoverEntities( context, config );
// SessionFactory sessionFactory = new SessionFactoryBuilder( context, datasource, config,
// entities.get( datasource.getOriginalName() ) ).build();

// assertNotNull( sessionFactory );
// }

// @DisplayName( "It can use the default application datasource" )
// @Test
// public void testApplicationDefaultDatasource() {
// context.getRuntime().executeSource(
// """
// application
// name="BXORMTest"
// datasource={
// driver = "derby",
// database = "test",
// connectionString = "jdbc:derby:memory:TestDB;create=true"
// };
// """, context );
// ORMConfig config = new ORMConfig( Struct.of() );
// Map<String, List<EntityRecord>> entities = MappingGenerator.discoverEntities( context, config );
// for ( String datasourceName : entities.keySet() ) {
// DataSource thisDataSource = context.getConnectionManager().getDatasource( Key.of( datasourceName ) );

// SessionFactory sessionFactory = new SessionFactoryBuilder( context, thisDataSource, config,
// entities.get( datasourceName ) ).build();

// assertNotNull( sessionFactory );
// }
// }

// @DisplayName( "It can use a named application datasource" )
// @Test
// public void testApplicationNamedDatasource() {
// context.getRuntime().executeSource(
// """
// application
// name="BXORMTest"
// datasources={
// "TestDB2" = {
// driver = "derby",
// database = "test",
// connectionString = "jdbc:derby:memory:TestDB2;create=true"
// }
// };
// """, context );
// ORMConfig config = new ORMConfig( Struct.of(
// ORMKeys.datasource, "TestDB2"
// ) );
// Map<String, List<EntityRecord>> entities = MappingGenerator.discoverEntities( context, config );
// SessionFactory sessionFactory = new SessionFactoryBuilder( context, datasource, config,
// entities.get( "TestDB2" ) ).build();

// assertNotNull( sessionFactory );
// }

// @DisplayName( "It can generate a unique key" )
// @Test
// public void testUniqueKey() {
// Key key = SessionFactoryBuilder.getUniqueName( context, datasource );

// assertNotNull( key );
// assertEquals( "BXORMTest_" + context.getConfig().hashCode() + "_" + datasource.getUniqueName().getName(), key.getName() );
// }

// @Disabled
// @DisplayName( "It can lookup entities by entity name" )
// @Test
// public void testEntityLookup() {
// List<EntityRecord> entities = List
// .of(
// new EntityRecord( "Auto", "models.Auto", Struct.EMPTY, "default" )
// .setXmlFilePath( Path.of( "src/test/resources/app/models/Auto.hbm.xml" ).toAbsolutePath() ),
// new EntityRecord( "Vehicle", "models.Vehicle", Struct.EMPTY, "default" )
// .setXmlFilePath( Path.of( "src/test/resources/app/models/Vehicle.hbm.xml" ).toAbsolutePath() )
// );
// SessionFactory sessionFactory = new SessionFactoryBuilder( context, datasource, new ORMConfig( Struct.EMPTY ),
// entities ).build();
// EntityRecord foundEntity = SessionFactoryBuilder.lookupEntity( sessionFactory, "Auto" );

// assertNotNull( foundEntity );
// assertEquals( "Auto", foundEntity.getEntityName() );

// EntityRecord foundEntityByLowercaseName = SessionFactoryBuilder.lookupEntity( sessionFactory, "auto " );

// assertNotNull( foundEntityByLowercaseName );
// assertEquals( "Auto", foundEntityByLowercaseName.getEntityName() );
// }
// }
