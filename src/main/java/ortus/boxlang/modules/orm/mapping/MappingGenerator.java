package ortus.boxlang.modules.orm.mapping;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.ClassicEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.ModernEntityMeta;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ParseException;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MappingGenerator {

	/**
	 * The location to save the generated XML mapping files.
	 * <p>
	 * This could be a temporary directory, or (when `savemapping` is enabled) the same as the entity directory.
	 */
	private String						saveDirectory;

	/**
	 * Whether to save the mapping files alongside the entity files. (Default: false)
	 * <p>
	 * Alias for {@link ORMConfig#saveMapping}.
	 *
	 * <p>
	 * If true, the mapping files will be saved in the same directory as the entity files and {@link #xmlMappingLocation} will be ignored.
	 */
	private boolean						saveMappingAlongsideEntity;

	/**
	 * Map of discovered entities.
	 */
	private Map<String, EntityRecord>	entityMap;

	/**
	 * List of paths to search for entities.
	 */
	private List<Path>					entityPaths;

	/**
	 * ALL ORM configuration.
	 */
	private ORMConfig					config;

	private Key							fullPath	= Key.of( "_FULLPATH" );
	private Key							location	= Key.of( "location" );

	private static final Logger			logger		= LoggerFactory.getLogger( MappingGenerator.class );

	public MappingGenerator( IBoxContext context, ORMConfig config ) {
		this.entityMap					= new java.util.HashMap<>();
		this.config						= config;
		this.saveDirectory				= Path.of( FileSystemUtil.getTempDirectory(), "orm_mappings", String.valueOf( config.hashCode() ) ).toString();
		this.saveMappingAlongsideEntity	= config.saveMapping;
		this.entityPaths				= new java.util.ArrayList<>();
		// this.appDirectory = context.getParentOfType( ApplicationBoxContext.class ).getApplication().getApplicationDirectory();

		if ( !this.saveMappingAlongsideEntity ) {
			new File( this.saveDirectory ).mkdirs();
		}

		for ( String entityPath : config.entityPaths ) {
			this.entityPaths.add( FileSystemUtil.expandPath( context, entityPath ).absolutePath() );
		}
	}

	public MappingGenerator generateMappings() {
		final int			MAX_SYNCHRONOUS_ENTITIES	= 20;
		ArrayList<IStruct>	entities					= this.entityPaths.stream()
		    // @TODO: Resolve mappings for each entity path
		    .flatMap( path -> {
			    try {
				    // TODO: Return path.parent() alongside each discovered entity file so we know the entity location / mapping and can get a correct FQN.
				    return Files.walk( path )
				        // only files
				        .filter( Files::isRegularFile )
				        // Only .bx or .cfc class files
				        .filter( ( file ) -> StringUtils.endsWithAny( file.toString(), ".bx", ".cfc" ) )
				        // map to a BetterFQN instance containing the location and file name. Allows us to generate a proper FQN for the entity.
				        .map( file -> Struct.of( this.location, path.toString(), Key.file, file.toString() ) );
			    } catch ( IOException e ) {
				    if ( config.ignoreParseErrors ) {
					    e.printStackTrace();
					    logger.error( "Failed to walk path: [{}]", path, e );
				    } else {
					    throw new BoxRuntimeException( String.format( "Failed to walk path: [%s]", path ), e );
				    }
			    }
			    return null;
		    } )
		    // collect to ArrayList so we can parallelize the metadata load+introspection
		    .collect( java.util.stream.Collectors.toCollection( ArrayList::new ) );
		// TODO: Once this is all working, switch to parallel streams IF > 20ish entities
		if ( entities.size() > MAX_SYNCHRONOUS_ENTITIES ) {
			logger.debug( "Detected more than {} entities; parallelizing metadata introspection", MAX_SYNCHRONOUS_ENTITIES );
			// entities.parallelStream()...
		}
		entities.stream()
		    .map( ( possibleEntity ) -> {
			    logger.info( "Discovered BoxLang class at path {}; loading entity metadata", possibleEntity.getAsString( Key.file ) );
			    possibleEntity.put( Key.metadata, getClassMeta( Path.of( possibleEntity.getAsString( Key.file ) ) ) );
			    return possibleEntity;
		    } )
		    .filter( ( IStruct possibleEntity ) -> {
			    IStruct meta	= possibleEntity.getAsStruct( Key.metadata );
			    IStruct annotations = meta.getAsStruct( Key.annotations );
			    if ( annotations.containsKey( ORMKeys.persistent ) || annotations.containsKey( ORMKeys.entity ) ) {
				    logger.debug(
				        "Class is 'persistent'; generating XML: [{}] ",
				        meta.getAsString( Key.path ) );
				    return true;
			    } else {
				    logger.debug( "Class is unmarked or marked as as non-persistent; skipping: [{}]",
				        meta.getAsString( Key.path ) );
				    return false;
			    }
		    } )
		    .forEach( ( IStruct theEntity ) -> {
			    IStruct meta	= theEntity.getAsStruct( Key.metadata );
			    String entityName = readEntityName( meta );
			    String fqn		= new BetterFQN( Path.of( theEntity.getAsString( this.location ) ).getParent(), Path.of( meta.getAsString( Key.path ) ) )
			        .toString();
			    // TODO: Switch to .collect( Collectors.toMap(...) );
			    entityMap.put( entityName,
			        new EntityRecord(
			            entityName,
			            fqn,
			            writeXMLFile( meta )
			        )
			    );
		    } );

		return this;
	}

	private String readEntityName( IStruct meta ) {
		String	entityName	= null;
		var		annotations	= meta.getAsStruct( Key.annotations );
		if ( annotations.containsKey( ORMKeys.entityName ) ) {
			entityName = annotations.getAsString( ORMKeys.entityName );
		}
		if ( annotations.containsKey( ORMKeys.entity ) ) {
			entityName = annotations.getAsString( ORMKeys.entity );
		}
		if ( entityName == null || entityName.isBlank() ) {
			entityName = meta.getAsString( Key._name );
		}
		return entityName;
	}

	private IStruct getClassMeta( Path clazzPath ) {
		ParsingResult result = new Parser().parse( new File( clazzPath.toString() ) );
		if ( !result.isCorrect() ) {
			throw new ParseException( result.getIssues(), "" );
		}
		ClassMetadataVisitor visitor = new ClassMetadataVisitor();
		result.getRoot().accept( visitor );
		return visitor.getMetadata();
	}

	/**
	 * Get the map of discovered entities. Obviously, this is only useful once `mapEntities` has been called.
	 * 
	 * @return The map of discovered entities, where the key is the entity name and the value is an EntityRecord instance.
	 */
	public Map<String, EntityRecord> getEntityMap() {
		return entityMap;
	}

	/**
	 * Write the XML mapping file for the given entity metadata.
	 * 
	 * @param meta The entity metadata.
	 * 
	 * @return The path to the generated XML mapping file If `saveMappingAlongsideEntity` is true, the path will be the same as the entity file, but with
	 *         a `.hbm.xml` extension.
	 */
	private Path writeXMLFile( IStruct meta ) {
		String	name	= meta.getAsString( Key._name );
		String	path	= meta.getAsString( Key.path );
		String	fileExt	= path.substring( path.lastIndexOf( '.' ) );
		Path	xmlPath	= this.saveMappingAlongsideEntity
		    ? Path.of( path.replace( fileExt, ".hbm.xml" ) )
		    : Path.of( this.saveDirectory, name + ".hbm.xml" );
		try {
			logger.info( "Writing Hibernate XML mapping file for entity [{}] to [{}]", name, xmlPath );
			String finalXML = generateXML( meta );
			if ( finalXML.isEmpty() ) {
				logger.warn( "No XML mapping generated for entity [{}]; skipping file write", name );
			} else {
				Files.write( xmlPath, finalXML.getBytes() );
			}

		} catch ( IOException e ) {
			String message = String.format( "Failed to save XML mapping for class: [%s]", name );
			if ( config.ignoreParseErrors ) {
				logger.error( message );
				return null;
			}
			throw new BoxRuntimeException( message, e );
		}

		return xmlPath;
	}

	/**
	 * Generate the XML mapping for the given entity metadata.
	 * <p>
	 * Calls the HibernateXMLWriter to generate the XML mapping, then wraps it with a bit of pre and post XML to close out the file.
	 * 
	 * @param meta The entity metadata.
	 * 
	 * @return The full XML mapping for the entity as a string. If an exception was encountered and {@link ORMConfig#ignoreParseErrors} is true, an empty
	 *         string will be returned.
	 */
	private String generateXML( IStruct meta ) {
		try {
			IEntityMeta entity = null;
			if ( meta.getAsStruct( Key.annotations ).containsKey( ORMKeys.persistent ) ) {
				logger.info( "Class contains 'persistent' annotation; using ClassicEntityMeta: [{}]",
				    meta.getAsString( Key.path ) );
				entity = new ClassicEntityMeta( meta );
			} else {
				logger.debug( "Using ModernEntityMeta: [{}]",
				    meta.getAsString( Key.path ) );
				entity = new ModernEntityMeta( meta );
			}
			Document			doc			= new HibernateXMLWriter( entity ).generateXML();

			TransformerFactory	tf			= TransformerFactory.newInstance();
			Transformer			transformer	= tf.newTransformer();

			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "no" );
			transformer.setOutputProperty( OutputKeys.METHOD, "xml" );
			transformer.setOutputProperty( OutputKeys.DOCTYPE_PUBLIC, doc.getDoctype().getPublicId() );
			transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, doc.getDoctype().getSystemId() );

			StringWriter writer = new StringWriter();

			// transform document to string
			transformer.transform( new DOMSource( doc ), new StreamResult( writer ) );

			return writer.getBuffer().toString();
		} catch ( TransformerException e ) {
			String entityName = meta.getAsString( Key._name );
			logger.warn( "Failed to transform XML to string for entity [{}]", entityName, e );
			if ( !config.ignoreParseErrors ) {
				throw new BoxRuntimeException( String.format( "Failed to transform XML to string for entity [%s]", entityName ), e );
			}
		}

		return "";
	}
}
