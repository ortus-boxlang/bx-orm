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
package ortus.boxlang.modules.orm.mapping.manifest;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingXMLWriter;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Exercises the manifest service: build from an entity map, write/read the {@code .bxorm/} folder atomically, rehydrate
 * to an entity map that regenerates byte-identical mapping XML, and the integrity + fail-closed guards.
 */
public class ManifestServiceTest {

	static BoxRuntime	instance;
	RequestBoxContext	context;
	IScope				variables;
	ORMConfig			ormConfig;

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( false );
	}

	@BeforeEach
	public void setupEach() {
		// A bare request context is enough to parse entity code and build an ORMConfig; we deliberately do NOT load an
		// application descriptor or fire onRequestStart, so no Hibernate/datasource boot happens. These tests exercise
		// pure manifest build/write/read/rehydrate + XML generation and must run without any database.
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		variables	= context.getScopeNearby( VariablesScope.name );
		ormConfig	= new ORMConfig(
		    Struct.of( "datasource", "myds", "ignoreParseErrors", "true", "generateMappings", "true", "saveMapping", "true" ),
		    context.getRequestContext()
		);
	}

	@AfterEach
	public void teardownEach() {
		variables.clear();
		RequestBoxContext.removeCurrent();
		context.shutdown();
	}

	@DisplayName( "build -> write -> read -> rehydrate regenerates byte-identical mapping XML and preserves datasource grouping" )
	@Test
	public void testFullCycle( @TempDir Path folder ) {
		EntityRecord					simple		= makeRecord( "Simple",
		    "class persistent entityName=\"Simple\" table=\"simple\" { property name=\"id\" fieldtype=\"id\" generator=\"uuid\" ormType=\"string\"; property name=\"label\" ormType=\"string\"; }" );
		EntityRecord					other		= makeRecord( "Other",
		    "class persistent entityName=\"Other\" table=\"other\" { property name=\"id\" fieldtype=\"id\" generator=\"increment\"; property name=\"n\" ormType=\"integer\"; }" );

		String							simpleXml	= simple.getXmlMapping();
		String							otherXml	= other.getXmlMapping();

		Key								ds			= Key.of( "myds" );
		Map<Key, List<EntityRecord>>	map			= new LinkedHashMap<>();
		map.put( ds, new ArrayList<>( List.of( simple, other ) ) );

		// build -> write -> read -> rehydrate
		ManifestService.write( ManifestService.build( map, ormConfig, "test-1.0" ), folder );
		assertThat( Files.exists( folder.resolve( ManifestService.MANIFEST_NAME ) ) ).isTrue();
		assertThat( Files.exists( folder.resolve( ManifestService.CHECKSUM_NAME ) ) ).isTrue();

		OrmManifest						loaded	= ManifestService.read( folder, true );
		Map<Key, List<EntityRecord>>	rebuilt	= ManifestService.toEntityMap( loaded );

		assertThat( rebuilt ).containsKey( ds );
		assertThat( rebuilt.get( ds ) ).hasSize( 2 );

		// Rehydrated entities regenerate byte-identical mapping XML.
		assertThat( xmlFor( rebuilt.get( ds ).get( 0 ) ) ).isEqualTo( simpleXml );
		assertThat( xmlFor( rebuilt.get( ds ).get( 1 ) ) ).isEqualTo( otherXml );
	}

	@DisplayName( "trust-mode read fails closed when the manifest is missing" )
	@Test
	public void testFailClosedWhenMissing( @TempDir Path folder ) {
		assertThrows( BoxRuntimeException.class, () -> ManifestService.read( folder, true ) );
		assertThat( ManifestService.read( folder, false ) ).isNull();
	}

	@DisplayName( "a tampered/corrupt manifest fails the integrity check" )
	@Test
	public void testIntegrityGuard( @TempDir Path folder ) throws IOException {
		EntityRecord					simple	= makeRecord( "Simple",
		    "class persistent entityName=\"Simple\" table=\"simple\" { property name=\"id\" fieldtype=\"id\" generator=\"uuid\" ormType=\"string\"; }" );
		Map<Key, List<EntityRecord>>	map		= new LinkedHashMap<>();
		map.put( Key.of( "myds" ), new ArrayList<>( List.of( simple ) ) );
		ManifestService.write( ManifestService.build( map, ormConfig, "test-1.0" ), folder );

		// Corrupt the manifest without updating the checksum -> integrity check must reject it.
		Path manifestFile = folder.resolve( ManifestService.MANIFEST_NAME );
		Files.write( manifestFile, ( new String( Files.readAllBytes( manifestFile ), StandardCharsets.UTF_8 ) + " " ).getBytes( StandardCharsets.UTF_8 ) );

		assertThrows( BoxRuntimeException.class, () -> ManifestService.read( folder, true ) );
	}

	private EntityRecord makeRecord( String name, String code ) {
		IStruct			meta	= getClassMetaFromCode( code );
		EntityRecord	record	= new EntityRecord( name, "models." + name, meta, Key.of( "myds" ) );
		record.setEntityMeta( AbstractEntityMeta.autoDiscoverMetaType( meta ) );
		record.setXmlMapping( xmlFor( record ) );
		return record;
	}

	private String xmlFor( EntityRecord record ) {
		Document doc = new MappingXMLWriter( record.getEntityMeta(), null, ormConfig ).generateXML();
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
			StringWriter writer = new StringWriter();
			transformer.transform( new DOMSource( doc ), new StreamResult( writer ) );
			return writer.toString();
		} catch ( Exception e ) {
			throw new RuntimeException( e );
		}
	}

	private IStruct getClassMetaFromCode( String code ) {
		try {
			ParsingResult			result	= new Parser().parse( code, BoxSourceType.BOXSCRIPT, true );
			ClassMetadataVisitor	visitor	= new ClassMetadataVisitor( context );
			result.getRoot().accept( visitor );
			return visitor.getMetadata();
		} catch ( IOException e ) {
			throw new RuntimeException( e );
		}
	}

	@DisplayName( "resolveFolder defaults to the app root and honors a configured location" )
	@Test
	public void testResolveFolderLocation( @TempDir Path tmp ) {
		// Default (null/blank) → .bxorm at the application root, unchanged behavior.
		Path rootDefault = ManifestService.resolveFolder( context );
		assertThat( rootDefault.getFileName().toString() ).isEqualTo( ManifestService.FOLDER_NAME );
		assertThat( ManifestService.resolveFolder( context, null ) ).isEqualTo( rootDefault );
		assertThat( ManifestService.resolveFolder( context, "   " ) ).isEqualTo( rootDefault );

		// Absolute location → <location>/.bxorm, used as-is.
		Path abs = ManifestService.resolveFolder( context, tmp.toAbsolutePath().toString() );
		assertThat( abs ).isEqualTo( tmp.toAbsolutePath().resolve( ManifestService.FOLDER_NAME ) );

		// Relative location → resolved (still ends in .bxorm, and carries the relative segment).
		Path rel = ManifestService.resolveFolder( context, "build/tmp/manifest-here" );
		assertThat( rel.getFileName().toString() ).isEqualTo( ManifestService.FOLDER_NAME );
		assertThat( rel.toString() ).contains( "manifest-here" );
	}
}
