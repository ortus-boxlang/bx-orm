package ortus.boxlang.orm.mapping;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;

import ortus.boxlang.compiler.IBoxpiler;
import ortus.boxlang.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.runnables.RunnableLoader;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MappingGenerator {

	private String xmlMappingLocation;

	public MappingGenerator( String tempDirectory ) {
		this.xmlMappingLocation = tempDirectory;
	}

	public Map<String, EntityRecord> mapEntities( IBoxContext context, String[] cfcLocations ) {
		Path			cfcPath	= Path.of( FileSystemUtil.expandPath( ( IBoxContext ) context, cfcLocations[ 0 ] ) );
		RunnableLoader	loader	= RunnableLoader.getInstance();
		try {
			return Files
			    .walk( cfcPath )
			    .filter( Files::isRegularFile )
			    .filter( ( path ) -> StringUtils.endsWithAny( path.toString(), ".bx", ".cfc" ) )
			    .map( ( clazzPath ) -> {
				    // TODO figure out the package path
				    return DynamicObject.of( loader.loadClass( clazzPath,
				        IBoxpiler.getPackageName( clazzPath.toFile() ),
				        ( IBoxContext ) context ) ).invokeConstructor( context );
			    } )
			    .filter( MappingGenerator::isEntity )
			    .map( ( bxClass ) -> {
				    return new EntityRecord(
				        extractName( ( IClassRunnable ) bxClass.getTargetInstance() ),
				        ( Class<IClassRunnable> ) bxClass.getTargetClass(),
				        writeXMLFile( bxClass )
				    );
			    } )
			    .collect( Collectors.toMap( ( record ) -> record.entityName(), ( record ) -> record ) );
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private Path writeXMLFile( DynamicObject bxClass ) {
		String	name	= extractName( ( IClassRunnable ) bxClass.getTargetInstance() );
		Path	xmlPath	= Path.of( xmlMappingLocation, name + ".hbm.xml" );
		try {
			new File( xmlMappingLocation ).mkdirs();

			Files.write( xmlPath, generateXML( bxClass ).getBytes() );

		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return xmlPath;
	}

	public String generateXML( DynamicObject bxClass ) {
		IClassRunnable bxInstance = ( IClassRunnable ) bxClass.getTargetInstance();
		try {
			ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( bxInstance );
			Document				doc			= new HibernateXMLWriter().generateXML( inspector );

			TransformerFactory		tf			= TransformerFactory.newInstance();
			Transformer				transformer	= tf.newTransformer();

			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "no" );
			transformer.setOutputProperty( OutputKeys.METHOD, "xml" );
			transformer.setOutputProperty( OutputKeys.DOCTYPE_PUBLIC, doc.getDoctype().getPublicId() );
			transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, doc.getDoctype().getSystemId() );

			StringWriter writer = new StringWriter();

			// transform document to string
			transformer.transform( new DOMSource( doc ), new StreamResult( writer ) );

			return writer.getBuffer().toString();
		} catch ( TransformerConfigurationException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch ( TransformerException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "test";
	}

	public static boolean isEntity( DynamicObject bxClass ) {
		return ( ( IClassRunnable ) bxClass.getTargetInstance() ).getAnnotations().containsKey( ORMKeys.entity );
	}

	private String extractName( IClassRunnable bxInstance ) {
		return StringUtils.substringAfterLast( bxInstance.getName().toString(), "." );
	}
}
