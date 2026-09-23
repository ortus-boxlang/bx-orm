package ortus.boxlang.modules.orm.mapping;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.hibernate.converters.DateTimeConverter;
import ortus.boxlang.modules.orm.hibernate.converters.IntegerConverter;
import ortus.boxlang.modules.orm.hibernate.converters.StringConverter;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ParseException;

public class MappingGeneratorTest {

	static BoxRuntime	instance;
	RequestBoxContext	context;
	IScope				variables;
	static Key			result	= new Key( "result" );
	ORMConfig			ormConfig;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( false );
		// This test boots the MySQL-backed test app, so it must load the MySQL JDBC driver module itself rather than rely
		// on an earlier test class in the same JVM having done so (which made it pass or fail depending on test order).
		Key mysqlModule = Key.of( "bx-mysql" );
		if ( !instance.getModuleService().hasModule( mysqlModule ) ) {
			ModuleRecord mysqlRecord = new ModuleRecord( Path.of( "./src/test/resources/modules/bx-mysql" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( mysqlModule, mysqlRecord );
			mysqlRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}
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
		File				entityXMLFilePath				= Path.of( "src/test/resources/app/models/Manufacturer.orm.xml" ).toFile();
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
		File				entityXMLFilePath				= Path.of( "src/test/resources/app/models/Manufacturer.orm.xml" ).toFile();
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
		File				entityXMLFilePath				= Path.of( "src/test/resources/app/models/Manufacturer.orm.xml" ).toFile();
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

	/**
	 * ------------------------------------------------------------------------------------------------------------------
	 * MappingXMLWriter logical tests (ported from the retired HibernateXMLWriterTest, adapted to the modern Hibernate 7
	 * mapping.xml schema: <entity name/class>, <attributes>, <basic>/<id>/<version>, <many-to-one>/<one-to-many>/
	 * <many-to-many>, <element-collection>, <caching>, etc.). Each test parses a BoxLang entity to metadata, runs the
	 * writer, and asserts on the generated DOM.
	 * ------------------------------------------------------------------------------------------------------------------
	 */

	@DisplayName( "It sets the entity name and generated facade class" )
	@Test
	public void testWriterEntityNameAndClass() {
		Document	doc		= writeXML( "class persistent entityName=\"Car\" { property name=\"id\" fieldtype=\"id\"; }" );
		Element		entity	= first( doc, "entity" );
		assertThat( entity.getAttribute( "name" ) ).isEqualTo( "Car" );
		assertThat( entity.getAttribute( "class" ) ).endsWith( "CarFacade" );
	}

	@DisplayName( "It sets entity attributes (table, schema, catalog, optimistic-locking)" )
	@Test
	public void testWriterEntityAttributes() {
		Document	doc		= writeXML(
		    "class persistent entityName=\"Car\" table=\"vehicles\" schema=\"foo\" catalog=\"morefoo\" optimisticLock=\"all\" { property name=\"id\" fieldtype=\"id\"; }" );
		Element		table	= first( doc, "table" );
		assertThat( table.getAttribute( "name" ) ).isEqualTo( "vehicles" );
		assertThat( table.getAttribute( "schema" ) ).isEqualTo( "foo" );
		assertThat( table.getAttribute( "catalog" ) ).isEqualTo( "morefoo" );
		assertThat( first( doc, "optimistic-locking" ).getTextContent() ).isEqualTo( "ALL" );
	}

	@DisplayName( "It marks immutable (readonly) entities mutable=false" )
	@Test
	public void testWriterImmutableEntity() {
		Document doc = writeXML( "class persistent readonly=\"true\" { property name=\"id\" fieldtype=\"id\"; }" );
		assertThat( first( doc, "mutable" ).getTextContent() ).isEqualTo( "false" );
	}

	@DisplayName( "It maps a root discriminator column" )
	@Test
	public void testWriterDiscriminatorColumn() {
		Document doc = writeXML( "class persistent discriminatorColumn=\"autoType\" { property name=\"id\" fieldtype=\"id\"; }" );
		assertThat( first( doc, "discriminator-column" ).getAttribute( "name" ) ).isEqualTo( "autoType" );
	}

	@DisplayName( "It generates an id element from the id fieldtype" )
	@Test
	public void testWriterIdElement() {
		Document doc = writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\"; }" );
		assertThat( first( doc, "id" ).getAttribute( "name" ) ).isEqualTo( "the_id" );
	}

	@DisplayName( "It generates a composite id as multiple id elements" )
	@Test
	public void testWriterCompositeId() {
		Document	doc	= writeXML( """
		                            class persistent {
		                            	property name="id1" fieldtype="id" ormType="integer";
		                            	property name="id2" fieldtype="id" ormType="string";
		                            }
		                            """ );
		NodeList	ids	= doc.getElementsByTagName( "id" );
		assertThat( ids.getLength() ).isEqualTo( 2 );
		assertThat( byName( doc, "id", "id1" ) ).isNotNull();
		assertThat( byName( doc, "id", "id2" ) ).isNotNull();
		// non-converted id declares its Java type via <target>
		assertThat( childText( byName( doc, "id", "id1" ), "target" ) ).isEqualTo( "Integer" );
		assertThat( childText( byName( doc, "id", "id2" ), "target" ) ).isEqualTo( "String" );
	}

	@DisplayName( "It aliases id ormType (int -> Integer target)" )
	@Test
	public void testWriterIdOrmTypeAlias() {
		Document doc = writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\" ormType=\"int\"; }" );
		assertThat( childText( first( doc, "id" ), "target" ) ).isEqualTo( "Integer" );
	}

	@DisplayName( "It emits the increment id generator" )
	@Test
	public void testWriterIncrementGenerator() {
		Document	doc	= writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\" generator=\"increment\"; }" );
		Element		gv	= first( doc, "generated-value" );
		assertThat( gv.getAttribute( "generator" ) ).isEqualTo( "increment" );
	}

	@DisplayName( "It normalizes generator names regardless of case" )
	@ParameterizedTest
	@CsvSource( {
	    "UUID,uuid",
	    "GuId,uuid",
	    "uuid2,uuid"
	} )
	public void testWriterUuidGeneratorNormalization( String generator, String expectedClass ) {
		Document	doc	= writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\" generator=\"" + generator + "\"; }" );
		Element		gg	= first( doc, "generic-generator" );
		assertThat( gg ).isNotNull();
		assertThat( gg.getAttribute( "class" ) ).isEqualTo( expectedClass );
	}

	@DisplayName( "It emits IDENTITY for identity/native generators" )
	@Test
	public void testWriterIdentityGenerator() {
		Document doc = writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\" generator=\"identity\"; }" );
		assertThat( first( doc, "generated-value" ).getAttribute( "strategy" ) ).isEqualTo( "IDENTITY" );
	}

	@DisplayName( "It throws for an unrecognized generator name" )
	@Test
	public void testWriterInvalidGeneratorThrows() {
		IStruct meta = getClassMetaFromCode( "class persistent { property name=\"the_id\" fieldtype=\"id\" generator=\"bogusGenerator\"; }" );
		assertThrows( BoxRuntimeException.class, () -> AbstractEntityMeta.autoDiscoverMetaType( meta ) );
	}

	@DisplayName( "It emits a generic-generator for the less-common generator strategies" )
	@ParameterizedTest
	@CsvSource( {
	    "sequence",
	    "foreign",
	    "select",
	    "sequence-identity"
	} )
	public void testWriterGenericGenerator( String strategy ) {
		Document	doc	= writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\" generator=\"" + strategy + "\"; }" );
		Element		gg	= first( doc, "generic-generator" );
		assertThat( gg ).isNotNull();
		assertThat( gg.getAttribute( "class" ) ).isEqualTo( strategy );
	}

	@DisplayName( "It carries the property param for a foreign generator" )
	@Test
	public void testWriterForeignGeneratorParam() {
		Document	doc	= writeXML( "class persistent { property name=\"the_id\" fieldtype=\"id\" generator=\"foreign\" params={ property : \"owner\" }; }" );
		Element		gg	= first( doc, "generic-generator" );
		assertThat( gg.getAttribute( "class" ) ).isEqualTo( "foreign" );
		Element param = childElement( gg, "parameter" );
		assertThat( param ).isNotNull();
		assertThat( param.getAttribute( "name" ) ).isEqualTo( "property" );
		assertThat( param.getAttribute( "value" ) ).isEqualTo( "owner" );
	}

	@DisplayName( "It defaults a property to the String converter" )
	@Test
	public void testWriterPropertyDefaultsToStringConverter() {
		Document	doc		= writeXML( "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"the_name\"; }" );
		Element		basic	= byName( doc, "basic", "the_name" );
		assertThat( childElement( basic, "convert" ).getAttribute( "converter" ) ).isEqualTo( StringConverter.class.getName() );
	}

	@DisplayName( "It aliases ORM types to their converters" )
	@Test
	public void testWriterOrmTypeConverters() {
		Document doc = writeXML( """
		                         class persistent {
		                         	property name="id" fieldtype="id";
		                         	property name="the_name" ormtype="varchar(50)";
		                         	property name="foo" ormtype="java.sql.Timestamp";
		                         	property name="count" ormtype="int";
		                         }
		                         """ );
		assertThat( childElement( byName( doc, "basic", "the_name" ), "convert" ).getAttribute( "converter" ) )
		    .isEqualTo( StringConverter.class.getName() );
		assertThat( childElement( byName( doc, "basic", "foo" ), "convert" ).getAttribute( "converter" ) )
		    .isEqualTo( DateTimeConverter.class.getName() );
		assertThat( childElement( byName( doc, "basic", "count" ), "convert" ).getAttribute( "converter" ) )
		    .isEqualTo( IntegerConverter.class.getName() );
	}

	@DisplayName( "It generates a formula element" )
	@Test
	public void testWriterFormula() {
		Document	doc		= writeXML( "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"the_name\" formula=\"SELECT 1\"; }" );
		Element		basic	= byName( doc, "basic", "the_name" );
		assertThat( childText( basic, "formula" ) ).isEqualTo( "( SELECT 1 )" );
	}

	@DisplayName( "It maps column name, length, precision, scale, unique, not-null and sql-type" )
	@Test
	public void testWriterColumnAttributes() {
		Document	doc		= writeXML( """
		                                class persistent {
		                                	property name="id" fieldtype="id";
		                                	property name="amount" column="amountCol" length=12 precision=2 scale=10 unique=true notnull=true sqltype="numeric";
		                                }
		                                """ );
		Element		column	= childElement( byName( doc, "basic", "amount" ), "column" );
		assertThat( column.getAttribute( "name" ) ).isEqualTo( "amountCol" );
		assertThat( column.getAttribute( "length" ) ).isEqualTo( "12" );
		assertThat( column.getAttribute( "precision" ) ).isEqualTo( "2" );
		assertThat( column.getAttribute( "scale" ) ).isEqualTo( "10" );
		assertThat( column.getAttribute( "unique" ) ).isEqualTo( "true" );
		assertThat( column.getAttribute( "nullable" ) ).isEqualTo( "false" );
		assertThat( column.getAttribute( "column-definition" ) ).isEqualTo( "numeric" );
	}

	@DisplayName( "It escapes reserved words in table and column names" )
	@Test
	public void testWriterReservedWordEscaping() {
		Document doc = writeXML( "class persistent table=\"case\" { property name=\"id\" fieldtype=\"id\"; property name=\"order\" column=\"order\"; }" );
		assertThat( first( doc, "table" ).getAttribute( "name" ) ).isEqualTo( "`case`" );
		assertThat( childElement( byName( doc, "basic", "order" ), "column" ).getAttribute( "name" ) ).isEqualTo( "`order`" );
	}

	@DisplayName( "It does not map properties with persistent=false" )
	@Test
	public void testWriterPersistentFalse() {
		Document doc = writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"name\"; property name=\"notMapped\" persistent=\"false\"; }" );
		assertThat( byName( doc, "basic", "name" ) ).isNotNull();
		assertThat( byName( doc, "basic", "notMapped" ) ).isNull();
	}

	@DisplayName( "It maps a version property" )
	@Test
	public void testWriterVersion() {
		Document	doc		= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"version\" fieldtype=\"version\" column=\"itemVersion\" ormType=\"integer\"; }" );
		Element		version	= first( doc, "version" );
		assertThat( version.getAttribute( "name" ) ).isEqualTo( "version" );
		assertThat( childElement( version, "column" ).getAttribute( "name" ) ).isEqualTo( "itemVersion" );
	}

	@DisplayName( "It maps a many-to-one association with a join column" )
	@Test
	public void testWriterManyToOne() {
		Document	doc	= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"owner\" fieldtype=\"many-to-one\" class=\"Person\" fkcolumn=\"FK_owner\" fetch=\"select\"; }",
		    ( a, b ) -> new EntityRecord( "Person", "models.Person" ) );
		Element		m2o	= first( doc, "many-to-one" );
		assertThat( m2o.getAttribute( "name" ) ).isEqualTo( "owner" );
		assertThat( m2o.getAttribute( "target-entity" ) ).endsWith( "PersonFacade" );
		assertThat( childElement( m2o, "join-column" ).getAttribute( "name" ) ).isEqualTo( "FK_owner" );
	}

	@DisplayName( "It maps a one-to-many association with mapped-by" )
	@Test
	public void testWriterOneToMany() {
		Document	doc	= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"vehicles\" type=\"array\" fieldtype=\"one-to-many\" class=\"Vehicle\" mappedBy=\"manufacturer\"; }",
		    ( a, b ) -> new EntityRecord( "Vehicle", "models.Vehicle" ) );
		Element		o2m	= first( doc, "one-to-many" );
		assertThat( o2m.getAttribute( "name" ) ).isEqualTo( "vehicles" );
		assertThat( o2m.getAttribute( "mapped-by" ) ).isEqualTo( "manufacturer" );
		assertThat( o2m.getAttribute( "target-entity" ) ).endsWith( "VehicleFacade" );
	}

	@DisplayName( "It maps a many-to-many association" )
	@Test
	public void testWriterManyToMany() {
		Document	doc	= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"students\" type=\"array\" fieldtype=\"many-to-many\" class=\"Student\" linktable=\"link\" fkcolumn=\"FK_c\" inversejoincolumn=\"FK_s\"; }",
		    ( a, b ) -> new EntityRecord( "Student", "models.Student" ) );
		Element		m2m	= first( doc, "many-to-many" );
		assertThat( m2m.getAttribute( "name" ) ).isEqualTo( "students" );
		assertThat( m2m.getAttribute( "target-entity" ) ).endsWith( "StudentFacade" );
	}

	@DisplayName( "It maps an array element-collection (BAG)" )
	@Test
	public void testWriterElementCollectionArray() {
		Document	doc	= writeXML(
		    """
		    class persistent {
		    	property name="id" fieldtype="id";
		    	property name="tags" fieldtype="collection" type="array" table="t_tags" fkcolumn="FK_id" elementColumn="tag" elementType="string";
		    }
		    """ );
		Element		ec	= first( doc, "element-collection" );
		assertThat( ec.getAttribute( "name" ) ).isEqualTo( "tags" );
		assertThat( ec.getAttribute( "classification" ) ).isEqualTo( "BAG" );
		assertThat( childElement( ec, "column" ).getAttribute( "name" ) ).isEqualTo( "tag" );
		assertThat( childElement( ec, "collection-table" ).getAttribute( "name" ) ).isEqualTo( "t_tags" );
	}

	@DisplayName( "It maps a struct element-collection (MAP)" )
	@Test
	public void testWriterElementCollectionMap() {
		Document	doc	= writeXML(
		    """
		    class persistent {
		    	property name="id" fieldtype="id";
		    	property name="attributes" fieldtype="collection" type="struct" table="t_attrs" fkcolumn="FK_id" structKeyColumn="attr_key" elementColumn="attr_value" elementType="string";
		    }
		    """ );
		Element		ec	= first( doc, "element-collection" );
		assertThat( ec.getAttribute( "name" ) ).isEqualTo( "attributes" );
		assertThat( ec.getAttribute( "classification" ) ).isEqualTo( "MAP" );
		assertThat( childElement( ec, "map-key-column" ).getAttribute( "name" ) ).isEqualTo( "attr_key" );
	}

	@DisplayName( "It maps entity-level cache metadata" )
	@Test
	public void testWriterEntityLevelCache() {
		Document	doc		= writeXML( "class persistent cacheuse=\"read-write\" cacheName=\"foo\" { property name=\"id\" fieldtype=\"id\"; }" );
		Element		cache	= first( doc, "caching" );
		assertThat( cache.getAttribute( "access" ) ).isEqualTo( "READ_WRITE" );
		assertThat( cache.getAttribute( "region" ) ).isEqualTo( "foo" );
	}

	@DisplayName( "Regression: a to-one association defaults to LAZY (Hibernate 5 lazy-proxy default), EAGER only when asked" )
	@Test
	public void testWriterToOneDefaultFetchIsLazy() {
		BiFunction<String, Key, EntityRecord>	lookup	= ( a, b ) -> new EntityRecord( "Person", "models.Person" );
		Document								def		= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"owner\" fieldtype=\"many-to-one\" cfc=\"Person\" fkcolumn=\"ownerId\"; }",
		    lookup );
		assertThat( first( def, "many-to-one" ).getAttribute( "fetch" ) ).isEqualTo( "LAZY" );

		Document eager = writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"owner\" fieldtype=\"many-to-one\" cfc=\"Person\" fkcolumn=\"ownerId\" lazy=\"false\"; }",
		    lookup );
		assertThat( first( eager, "many-to-one" ).getAttribute( "fetch" ) ).isEqualTo( "EAGER" );

		Document join = writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"owner\" fieldtype=\"many-to-one\" cfc=\"Person\" fkcolumn=\"ownerId\" fetch=\"join\"; }",
		    lookup );
		assertThat( first( join, "many-to-one" ).getAttribute( "fetch" ) ).isEqualTo( "EAGER" );
	}

	@DisplayName( "Regression: a hierarchy root writes its own discriminator value" )
	@Test
	public void testWriterRootDiscriminatorValue() {
		Document doc = writeXML(
		    "class persistent discriminatorColumn=\"kind\" discriminatorValue=\"vehicle\" { property name=\"id\" fieldtype=\"id\"; }" );
		assertThat( first( doc, "discriminator-value" ).getTextContent() ).isEqualTo( "vehicle" );
		assertThat( first( doc, "discriminator-column" ).getAttribute( "name" ) ).isEqualTo( "kind" );
	}

	@DisplayName( "Regression: a one-to-one without fkcolumn is a shared primary-key association" )
	@Test
	public void testWriterOneToOnePrimaryKey() {
		BiFunction<String, Key, EntityRecord>	lookup	= ( a, b ) -> new EntityRecord( "Profile", "models.Profile" );
		Document								doc		= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"profile\" fieldtype=\"one-to-one\" cfc=\"Profile\"; }", lookup );
		Element									o2o		= first( doc, "one-to-one" );
		assertThat( childElement( o2o, "primary-key-join-column" ) ).isNotNull();
		assertThat( childElement( o2o, "join-column" ) ).isNull();
		// Unconstrained side: no FK (only the constrained side carries one, as in hbm).
		assertThat( childElement( o2o, "primary-key-foreign-key" ).getAttribute( "constraint-mode" ) ).isEqualTo( "NO_CONSTRAINT" );

		Document constrained = writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"profile\" fieldtype=\"one-to-one\" cfc=\"Profile\" constrained=\"true\"; }",
		    lookup );
		assertThat( first( constrained, "one-to-one" ).getAttribute( "optional" ) ).isEqualTo( "false" );
		assertThat( childElement( first( constrained, "one-to-one" ), "primary-key-foreign-key" ) ).isNull();
	}

	@DisplayName( "Regression: a collection where= becomes sql-restriction (entity and value collections)" )
	@Test
	public void testWriterCollectionWhere() {
		Document	doc	= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"activeBooks\" type=\"array\" fieldtype=\"one-to-many\" cfc=\"Book\" fkcolumn=\"authorId\" where=\"active = 1\"; }",
		    ( a, b ) -> new EntityRecord( "Book", "models.Book" ) );
		Element		o2m	= first( doc, "one-to-many" );
		assertThat( childText( o2m, "sql-restriction" ) ).isEqualTo( "active = 1" );

		Document values = writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"tags\" fieldtype=\"collection\" type=\"array\" table=\"t_tags\" fkcolumn=\"FK_id\" elementColumn=\"tag\" elementType=\"string\" where=\"tag <> 'x'\"; }" );
		assertThat( childText( first( values, "element-collection" ), "sql-restriction" ) ).isEqualTo( "tag <> 'x'" );
	}

	@DisplayName( "Regression: a struct-typed one-to-many writes its map key (class + column)" )
	@Test
	public void testWriterStructOneToManyMapKey() {
		Document	doc	= writeXML(
		    "class persistent { property name=\"id\" fieldtype=\"id\"; property name=\"itemsByCode\" type=\"struct\" fieldtype=\"one-to-many\" cfc=\"Item\" fkcolumn=\"shelfId\" structKeyColumn=\"itemCode\" structKeyType=\"string\"; }",
		    ( a, b ) -> new EntityRecord( "Item", "models.Item" ) );
		Element		o2m	= first( doc, "one-to-many" );
		assertThat( o2m.getAttribute( "classification" ) ).isEqualTo( "MAP" );
		assertThat( childElement( o2m, "map-key-class" ).getAttribute( "class" ) ).isEqualTo( "java.lang.String" );
		assertThat( childElement( o2m, "map-key-column" ).getAttribute( "name" ) ).isEqualTo( "itemCode" );
		assertThat( childElement( o2m, "join-column" ).getAttribute( "name" ) ).isEqualTo( "shelfId" );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------------
	 * Writer test helpers
	 * ------------------------------------------------------------------------------------------------------------------
	 */

	/** Parse a BoxLang entity source, build its metadata, and run the modern mapping writer, returning the DOM. */
	private Document writeXML( String sourceCode ) {
		return writeXML( sourceCode, null );
	}

	private Document writeXML( String sourceCode, BiFunction<String, Key, EntityRecord> entityLookup ) {
		IEntityMeta entityMeta = AbstractEntityMeta.autoDiscoverMetaType( getClassMetaFromCode( sourceCode ) );
		return new MappingXMLWriter( entityMeta, entityLookup, ormConfig ).generateXML();
	}

	/** First element with the given tag anywhere in the document, or null. */
	private static Element first( Document doc, String tag ) {
		NodeList nl = doc.getElementsByTagName( tag );
		return nl.getLength() > 0 ? ( Element ) nl.item( 0 ) : null;
	}

	/** First element with the given tag and name attribute, or null. */
	private static Element byName( Document doc, String tag, String name ) {
		NodeList nl = doc.getElementsByTagName( tag );
		for ( int i = 0; i < nl.getLength(); i++ ) {
			Element el = ( Element ) nl.item( i );
			if ( name.equals( el.getAttribute( "name" ) ) ) {
				return el;
			}
		}
		return null;
	}

	/** First descendant element of parent with the given tag, or null. */
	private static Element childElement( Element parent, String tag ) {
		NodeList nl = parent.getElementsByTagName( tag );
		return nl.getLength() > 0 ? ( Element ) nl.item( 0 ) : null;
	}

	/** Text content of the first descendant element of parent with the given tag, or null. */
	private static String childText( Element parent, String tag ) {
		Element el = childElement( parent, tag );
		return el == null ? null : el.getTextContent();
	}

	private IStruct getClassMetaFromCode( String code ) {
		try {
			ParsingResult result = new Parser().parse( code, BoxSourceType.BOXSCRIPT, true );
			if ( !result.isCorrect() ) {
				throw new ParseException( result.getIssues(), "" );
			}
			ClassMetadataVisitor visitor = new ClassMetadataVisitor( context );
			result.getRoot().accept( visitor );
			return visitor.getMetadata();
		} catch ( IOException e ) {
			throw new BoxRuntimeException( String.format( "Failed to parse metadata from source: [%s]", code ), e );
		}
	}
}
