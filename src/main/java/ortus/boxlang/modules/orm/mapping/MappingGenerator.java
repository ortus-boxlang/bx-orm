package ortus.boxlang.modules.orm.mapping;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.BoxScriptParser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ParseException;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MappingGenerator {

	/**
	 * The location to save the generated XML mapping files.
	 * <p>
	 * This could be a temporary directory, or (when `savemapping` is enabled) the same as the entity directory.
	 */
	private String						xmlMappingLocation;

	/**
	 * Map of discovered entities.
	 */
	private Map<String, EntityRecord>	entityMap;

	/**
	 * 
	 */
	private List<Path>					entityPaths;

	private IBoxContext					context;

	private static final Logger			logger	= LoggerFactory.getLogger( MappingGenerator.class );

	public MappingGenerator( IBoxContext context, String[] entityPaths, String saveDirectory ) {
		this.entityMap			= new java.util.HashMap<>();
		this.context			= context;
		this.xmlMappingLocation	= saveDirectory;
		this.entityPaths		= new java.util.ArrayList<>();
		for ( String entityPath : entityPaths ) {
			this.entityPaths.add( FileSystemUtil.expandPath( context, entityPath ).absolutePath() );
		}
	}

	public MappingGenerator generateMappings() {
		this.entityPaths.stream()
		    .filter( Files::isDirectory )
		    // TODO: resolve, in case it's a mapping
		    // TODO: ensure directory exists - if not, EITHER warn or throw exception
		    .forEach( ( path ) -> {
			    logger.warn( "Checking path for entities: [{}]", path );
			    try {
				    Files
				        .walk( path )
				        // TODO: Once this is all working, switch to parallel streams IF > 20ish entities
				        .filter( Files::isRegularFile )
				        .filter( ( file ) -> StringUtils.endsWithAny( file.toString(), ".bx", ".cfc" ) )
				        .map( ( clazzPath ) -> {
					        logger.trace( "Discovered BoxLang class at path {}; loading entity metadata", clazzPath );
					        return getClassMeta( new File( clazzPath.toString() ) );
				        } )
				        .filter( ( IStruct meta ) -> {
					        // if it's in a CFC location, it's persistent by default
					        IStruct classAnnotations = meta.getAsStruct( Key.annotations );
					        logger.trace( "Checking class [{}] for 'persistent' annotation", meta.getAsString( Key.path ) );
					        classAnnotations.computeIfAbsent( ORMKeys.persistent, ( key ) -> true );
					        if ( BooleanCaster.cast( classAnnotations.getOrDefault( ORMKeys.persistent, true ), false ) ) {
						        logger.trace( "A 'persistent' annotation found in entity [{}]; and is falsey; skipping class as non-persistent.",
						            meta.getAsString( Key.path ) );
						        return false;
					        } else {
						        logger.trace( "No 'persistent' annotation found (OR is truthy) for class [{}]; treating class as persistent.",
						            meta.getAsString( Key.path ) );
						        return true;
					        }
				        } )
				        .forEach( ( IStruct meta ) -> {
					        logger.warn( "Working with persistent entity: {}", meta.getAsString( Key.path ) );
					        writeXMLFile( meta );
				        } );
			    } catch ( IOException e ) {
				    // TODO Auto-generated catch block
				    e.printStackTrace();
			    }
		    } );

		return this;
	}

	private IStruct getClassMeta( File entityFile ) {
		try {
			ParsingResult result = new BoxScriptParser().parse( entityFile );
			if ( !result.isCorrect() ) {
				throw new ParseException( result.getIssues(), "" );
			}
			ClassMetadataVisitor visitor = new ClassMetadataVisitor();
			result.getRoot().accept( visitor );
			return visitor.getMetadata();
		} catch ( IOException e ) {
			// @TODO: Check `skipCFCWithError` setting before throwing exception
			throw new BoxRuntimeException( String.format( "Failed to parse metadata for class: [{}]", entityFile.getAbsolutePath() ), e );
		}
	}

	/**
	 * Get the map of discovered entities. Obviously, this is only useful once `mapEntities` has been called.
	 * 
	 * @return The map of discovered entities, where the key is the entity name and the value is an EntityRecord instance.
	 */
	public Map<String, EntityRecord> getEntityMap() {
		return entityMap;
	}

	private Path writeXMLFile( IStruct meta ) {
		String	name	= meta.getAsString( Key._name );
		Path	xmlPath	= Path.of( xmlMappingLocation, name + ".hbm.xml" );
		try {
			new File( xmlMappingLocation ).mkdirs();

			logger.warn( "Writing Hibernate XML mapping file for entity [{}] to [{}]", name, xmlPath );
			Files.write( xmlPath, generateXML( meta ).getBytes() );

		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			// @TODO: Check `skipCFCWithError` setting before throwing exception
			e.printStackTrace();
			throw new BoxRuntimeException( String.format( "Failed to generate Hibernate XML for class: [{}]", name ), e );
		}

		return xmlPath;
	}

	public String generateXML( IStruct meta ) {
		try {
			ORMAnnotationInspector	inspector	= new ORMAnnotationInspector( meta );
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
}
