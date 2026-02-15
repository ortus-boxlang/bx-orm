package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;

public class MappingGeneratorTest {

	static BoxRuntime	instance;
	RequestBoxContext	context;
	IScope				variables;
	static Key			result	= new Key( "result" );
	ORMConfig			ormConfig;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( false );
	}

	@BeforeEach
	public void setupEach() {

		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables	= context.getScopeNearby( VariablesScope.name );
		// We don't need an actual datasource for this test so we'll add one to prevent the error
		ormConfig	= new ORMConfig(
		    Struct.of(
		        "ignoreParseErrors", "true",
		        "generateMappings", "true",
		        "saveMapping", "true"
		    ),
		    context.getRequestContext()
		);
	}

	@AfterEach
	public void teardownEach() {
		variables.clear();
		context.getApplicationListener().onRequestEnd( context, null );
		RequestBoxContext.removeCurrent();
		context.shutdown();
	}

	@Test
	public void testConstructor() {
		MappingGenerator generator = new MappingGenerator( context.getRequestContext(), ormConfig );
		assertThat( generator ).isNotNull();
	}

	@Test
	public void testGenerateMapping() {
		// check XML modification time BEFORE generating mappings
		File				entityXMLFilePath				= Path.of( "src/test/resources/app/models/Manufacturer.hbm.xml" ).toFile();
		long				preGenerateXMLModificationTime	= entityXMLFilePath.lastModified();

		// Generate mappings
		MappingGenerator	generator						= new MappingGenerator( context.getRequestContext(), ormConfig );
		generator.generateMappings();

		// Check that the file has been modified since generateMappings is true
		long lastModifiedAfter = entityXMLFilePath.lastModified();
		assertThat( lastModifiedAfter ).isGreaterThan( preGenerateXMLModificationTime );

		Map<Key, List<EntityRecord>>	mappings		= generator.getEntityDatasourceMap();

		// Check that our map of mappings by datasource contains the expected entries
		Key								datasourceKey	= new Key( "TestDB" );
		assertThat( mappings ).isNotNull();
		assertThat( mappings ).containsKey( datasourceKey );
		List<EntityRecord> records = mappings.get( datasourceKey );
		assertThat( records ).isNotNull();
		assertThat( records.size() ).isGreaterThan( 0 );
		EntityRecord entity = records.stream().filter( r -> r.getClassName().equals( "Manufacturer" ) ).findFirst().orElse( null );
		assertThat( entity ).isNotNull();
		assertThat( entity.getClassName() ).isEqualTo( "Manufacturer" );
		assertThat( entity.getDatasource() ).isEqualTo( datasourceKey.getName() );
		assertThat( entity.getEntityMeta() ).isNotNull();

		Key alternateDS = new Key( "dsn2" );
		assertThat( mappings ).containsKey( alternateDS );
		List<EntityRecord> alternateDatasourceEntities = mappings.get( alternateDS );
		assertThat( alternateDatasourceEntities ).isNotNull();
		assertThat( alternateDatasourceEntities.size() ).isGreaterThan( 0 );
		EntityRecord alternateEntity = alternateDatasourceEntities.stream().filter( r -> r.getClassName().equals( "AlternateDS" ) ).findFirst().orElse( null );
		assertThat( alternateEntity ).isNotNull();
		assertThat( alternateEntity.getClassName() ).isEqualTo( "AlternateDS" );
		assertThat( alternateEntity.getDatasource() ).isEqualTo( alternateDS.getName() );
		assertThat( alternateEntity.getEntityMeta() ).isNotNull();
	}

	@Test
	public void testAutoGenMapFalse() {
		var					testORMConfig					= new ORMConfig(
		    Struct.of(
		        "ignoreParseErrors", "true",
		        "autoGenMap", "false",
		        "saveMapping", "true",
		        "entityPaths", Array.of( "/root/models" )
		    ),
		    context.getRequestContext()
		);
		File				entityXMLFilePath				= Path.of( "src/test/resources/app/models/Manufacturer.hbm.xml" ).toFile();
		long				preGenerateXMLModificationTime	= entityXMLFilePath.lastModified();

		MappingGenerator	generator						= new MappingGenerator( context.getRequestContext(), testORMConfig );
		generator.generateMappings();

		// Check that the file was not modified since autoGenMap is false
		long lastModifiedAfter = entityXMLFilePath.lastModified();
		assertThat( lastModifiedAfter ).isEqualTo( preGenerateXMLModificationTime );

		// Check that the mapping was still generated in memory
		Map<Key, List<EntityRecord>> mappings = generator.getEntityDatasourceMap();
		assertThat( mappings ).isNotNull();
		assertThat( mappings ).containsKey( new Key( "TestDB" ) );
		assertThat( mappings ).containsKey( new Key( "dsn2" ) );
	}

	@Test
	public void testGenerateMappings() {
		var					testORMConfig					= new ORMConfig(
		    Struct.of(
		        "ignoreParseErrors", "true",
		        "generateMappings", "false",
		        "saveMapping", "true",
		        "entityPaths", Array.of( "/root/models" )
		    ),
		    context.getRequestContext()
		);
		File				entityXMLFilePath				= Path.of( "src/test/resources/app/models/Manufacturer.hbm.xml" ).toFile();
		long				preGenerateXMLModificationTime	= entityXMLFilePath.lastModified();

		MappingGenerator	generator						= new MappingGenerator( context.getRequestContext(), testORMConfig );
		generator.generateMappings();

		// Check that the file was not modified since generateMappings is false
		long lastModifiedAfter = entityXMLFilePath.lastModified();
		assertThat( lastModifiedAfter ).isEqualTo( preGenerateXMLModificationTime );

		// Check that the mapping was still generated in memory
		Map<Key, List<EntityRecord>> mappings = generator.getEntityDatasourceMap();
		assertThat( mappings ).isNotNull();
		assertThat( mappings ).containsKey( new Key( "TestDB" ) );
		assertThat( mappings ).containsKey( new Key( "dsn2" ) );
	}
}
