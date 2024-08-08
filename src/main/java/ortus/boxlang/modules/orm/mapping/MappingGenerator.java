package ortus.boxlang.modules.orm.mapping;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MappingGenerator {

	private String						xmlMappingLocation;

	/**
	 * Map of discovered entities.
	 */
	private Map<String, EntityRecord>	entityMap;

	private Path						cfcPath;

	private IBoxContext					context;

	private static final Logger			logger	= LoggerFactory.getLogger( MappingGenerator.class );

	public MappingGenerator( IBoxContext context, String[] cfcLocations, String tempDirectory ) {
		this.entityMap			= new java.util.HashMap<>();
		this.context			= context;
		this.xmlMappingLocation	= tempDirectory;
		this.cfcPath			= FileSystemUtil.expandPath( context, cfcLocations[ 0 ] ).absolutePath();
	}

	public MappingGenerator generateMappings() {
		try {
			Files
			    .walk( this.cfcPath )
			    .filter( Files::isRegularFile )
			    .filter( ( path ) -> StringUtils.endsWithAny( path.toString(), ".bx", ".cfc" ) )
			    .map( ( clazzPath ) -> {
				    logger.warn( "Discovered BoxLang class at path {} ", clazzPath );
				    BetterFQN lookupPath = new BetterFQN( this.cfcPath.getParent(), clazzPath );
				    logger.warn( lookupPath.toString() );
				    // logger.warn( lookupPath.getParts().toString() );
				    DynamicObject bxClass = ClassLocator.getInstance().load( this.context, lookupPath.toString(), "bx" );

				    bxClass.invokeConstructor( this.context, Key.noInit );
				    return bxClass;
			    } )
			    .filter( MappingGenerator::isEntity )
			    .forEach( ( DynamicObject bxClass ) -> {
				    // @TODO stop saving the IClassRunnable and switch to box mapping
				    String entityName = extractName( ( IClassRunnable ) bxClass.getTargetInstance() );
				    entityMap.put( entityName, new EntityRecord(
				        entityName,
				        ( ( IClassRunnable ) bxClass.getTargetInstance() ).getName().toString(),
				        writeXMLFile( bxClass ) )
				    );
			    } );
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return this;
	}

	/**
	 * Get the map of discovered entities. Obviously, this is only useful once `mapEntities` has been called.
	 * 
	 * @return The map of discovered entities, where the key is the entity name and the value is an EntityRecord instance.
	 */
	public Map<String, EntityRecord> getEntityMap() {
		return entityMap;
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
		if ( bxClass.isInterface() ) {
			return false;
		}

		return ( ( IClassRunnable ) bxClass.getTargetInstance() ).getAnnotations().containsKey( ORMKeys.entity );
	}

	private String extractName( IClassRunnable bxInstance ) {
		return StringUtils.substringAfterLast( bxInstance.getName().toString(), "." );
	}
}
