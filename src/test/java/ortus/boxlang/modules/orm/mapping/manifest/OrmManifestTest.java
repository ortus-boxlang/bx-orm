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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

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

/**
 * Proves the ORM manifest round-trips: an entity's metadata serialized into the manifest JSON and read back regenerates
 * a byte-identical mapping, and the manifest's own header fields survive serialization.
 */
public class OrmManifestTest {

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
		context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		context.loadApplicationDescriptor( Path.of( "src/test/resources/app/index.bxs" ).toAbsolutePath().toUri() );
		context.getApplicationListener().onRequestStart( context, null );
		variables	= context.getScopeNearby( VariablesScope.name );
		ormConfig	= new ORMConfig(
		    Struct.of( "ignoreParseErrors", "true", "generateMappings", "true", "saveMapping", "true" ),
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

	@DisplayName( "A manifest's header fields survive a JSON round-trip" )
	@Test
	public void testHeaderRoundTrip() {
		OrmManifest	manifest	= new OrmManifest()
		    .setOrmVersion( "2.0.0" )
		    .setConfigFingerprint( "abc123" )
		    .setCombinedMappingXml( "<entity-mappings/>" );

		OrmManifest	back		= OrmManifest.fromJSON( manifest.toJSON() );

		assertThat( back.getFormatVersion() ).isEqualTo( OrmManifest.FORMAT_VERSION );
		assertThat( back.getOrmVersion() ).isEqualTo( "2.0.0" );
		assertThat( back.getConfigFingerprint() ).isEqualTo( "abc123" );
		assertThat( back.getCombinedMappingXml() ).isEqualTo( "<entity-mappings/>" );
	}

	@DisplayName( "Entity metadata stored in the manifest JSON regenerates byte-identical mapping XML after reload" )
	@Test
	public void testEntityMetadataRoundTrip() {
		String[]	samples		= {
		    "class persistent entityName=\"Simple\" table=\"simple\" { property name=\"id\" fieldtype=\"id\" generator=\"uuid\" ormType=\"string\"; property name=\"label\" ormType=\"string\"; }",
		    "class persistent entityName=\"Precise\" table=\"precise\" { property name=\"id\" fieldtype=\"id\" generator=\"increment\"; property name=\"amount\" ormType=\"big_decimal\" precision=\"19\" scale=\"4\"; property name=\"active\" ormType=\"boolean\"; }"
		};

		OrmManifest	manifest	= new OrmManifest().setOrmVersion( "2.0.0" );
		String[]	originalXml	= new String[ samples.length ];

		for ( int i = 0; i < samples.length; i++ ) {
			IStruct meta = getClassMetaFromCode( samples[ i ] );
			originalXml[ i ] = xmlFromMeta( meta );
			manifest.addEntity( new OrmManifest.Entity(
			    "Ent" + i,
			    "models.Ent" + i,
			    "default",
			    Struct.of( "path", "models/E" + i + ".bx", "hash", "deadbeef", "mtime", 1L, "size", 100L ),
			    meta,
			    originalXml[ i ]
			) );
		}

		// Full JSON round-trip.
		OrmManifest			back	= OrmManifest.fromJSON( manifest.toJSON() );
		List<EntityRecord>	records	= back.toEntityRecords();

		assertThat( records ).hasSize( samples.length );
		for ( int i = 0; i < samples.length; i++ ) {
			Document	doc			= new MappingXMLWriter( records.get( i ).getEntityMeta(), null, ormConfig ).generateXML();
			String		reloadedXml	= docToString( doc );
			assertThat( reloadedXml ).isEqualTo( originalXml[ i ] );
		}
	}

	private String xmlFromMeta( IStruct meta ) {
		return docToString( new MappingXMLWriter( AbstractEntityMeta.autoDiscoverMetaType( meta ), null, ormConfig ).generateXML() );
	}

	private String docToString( Document doc ) {
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
}
