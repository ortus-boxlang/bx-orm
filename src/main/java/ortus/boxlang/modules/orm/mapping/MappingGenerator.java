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
import org.w3c.dom.Document;

import ortus.boxlang.compiler.ast.visitor.ClassMetadataVisitor;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.exceptions.ParseException;
import ortus.boxlang.runtime.util.FileSystemUtil;
import ortus.boxlang.runtime.util.ResolvedFilePath;

/**
 * Hibernate entity mapping generator. Responsible for walking the file tree and generating hibernate mapping configurations for each discovered
 * persistent entity.
 */
public class MappingGenerator {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime						= BoxRuntime.getInstance();

	/**
	 * The temp folder to save the generated XML mapping files.
	 */
	private static final String		ENTITY_TEMP_FOLDER			= "orm_mappings";

	/**
	 * The valid file extensions for entity files.
	 */
	private static final String[]	ENTITY_EXTENSIONS			= { ".bx", ".cfc" };

	/**
	 * The maximum number of entities to process synchronously.
	 */
	private static final int		MAX_SYNCHRONOUS_ENTITIES	= 20;

	/**
	 * The logger for the ORM application.
	 */
	protected BoxLangLogger			logger;

	/**
	 * The location to save the generated XML mapping files.
	 * <p>
	 * This could be a temporary directory, or (when `savemapping` is enabled) the same as the entity directory.
	 */
	private String					saveDirectory				= null;

	/**
	 * Whether to save the mapping files alongside the entity files. (Default: false)
	 * <p>
	 * Alias for {@link ORMConfig#saveMapping}.
	 *
	 * <p>
	 * If true, the mapping files will be saved in the same directory as the entity files and {@link #xmlMappingLocation} will be ignored.
	 */
	private boolean					saveAlongsideEntity;

	/**
	 * List of paths to search for entities.
	 */
	private List<Path>				entityPaths					= new ArrayList<>();

	/**
	 * ALL ORM configuration.
	 */
	private ORMConfig				config;

	/**
	 * List of discovered entities.
	 */
	private List<EntityRecord>		entities					= new ArrayList<>();

	/**
	 * The JDBC-capable boxlang context, used to look up datasources referenced in the ORM config or on the entities themselves.
	 */
	private IJDBCCapableContext		context;

	/**
	 * Construct a new MappingGenerator instance.
	 *
	 * @param context The JDBC-capable context.
	 * @param config  The ORM configuration.
	 */
	public MappingGenerator( IJDBCCapableContext context, ORMConfig config ) {
		this.logger					= runtime.getLoggingService().getLogger( "orm" );
		this.config					= config;
		this.saveAlongsideEntity	= config.saveMapping;
		this.context				= context;
		// this.appDirectory = context.getParentOfType( ApplicationBoxContext.class ).getApplication().getApplicationDirectory();

		if ( !this.saveAlongsideEntity ) {
			this.saveDirectory = Path.of( FileSystemUtil.getTempDirectory(), ENTITY_TEMP_FOLDER, String.valueOf( config.hashCode() ) ).toString();
			new File( this.saveDirectory ).mkdirs();
		}

		for ( String entityPath : config.entityPaths ) {
			this.entityPaths.add( FileSystemUtil.expandPath( context, entityPath ).absolutePath() );
		}

		if ( this.entityPaths.isEmpty() ) {
			this.entityPaths.add( FileSystemUtil.expandPath( context, "." ).absolutePath() );
			logger.warn(
			    "No entity paths found in ORM configuration; defaulting to app root. Which makes things REAALLLLYYYY SLOW!! (You should STRONGLY consider setting an 'entityPaths' array in your ORM settings.)"
			);
		}
	}

	/**
	 * Retrieve the entity map for this session factory, constructing them if necessary.
	 * We return a Map of datasource names to a list of EntityRecords.
	 *
	 * @param context   The JDBC-capable context.
	 * @param ormConfig The ORM configuration.
	 *
	 * @return a map of datasource UNIQUE names to a list of EntityRecords.
	 */
	public static Map<Key, List<EntityRecord>> discoverEntities( IJDBCCapableContext context, ORMConfig ormConfig ) {
		if ( !ormConfig.autoGenMap ) {
			// Skip mapping generation and load the pre-generated mappings from `ormConfig.entityPaths`
			throw new BoxRuntimeException( "ORMConfiguration setting `autoGenMap=false` is currently unsupported." );
		} else {
			// generate xml mappings on the fly, saving them either to a temp directory or alongside the entity class files if `ormConfig.saveMapping` is true.
			return new MappingGenerator( context, ormConfig )
			    .generateMappings()
			    .getEntityDatasourceMap();
		}
	}

	/**
	 * Generate the mappings for all classes discovered in the entity paths which are marked as persistent entities.
	 * <p>
	 * This method will generate the XML mapping files for all discovered entities and store them in the entity map.
	 */
	public MappingGenerator generateMappings() {
		ArrayList<IStruct>	classes		= discoverBLClasses( this.entityPaths );
		boolean				doParallel	= config.enableThreadedMapping && classes.size() > MAX_SYNCHRONOUS_ENTITIES;

		if ( doParallel ) {

			logger.debug( "Found more than {} entities; parallelizing metadata introspection", MAX_SYNCHRONOUS_ENTITIES );

			this.entities = classes.parallelStream()
			    // Parse class metadata
			    .map( this::readMeta )
			    // Filter out non-persistent entities
			    .filter( ( IStruct possibleEntity ) -> isPersistentEntity( possibleEntity.getAsStruct( Key.metadata ) ) )
			    // Convert to EntityRecord
			    .map( this::toEntityRecord )
			    .toList();
		} else {
			this.entities = classes.stream()
			    // Parse class metadata
			    .map( this::readMeta )
			    // Filter out non-persistent entities
			    .filter( ( IStruct possibleEntity ) -> isPersistentEntity( possibleEntity.getAsStruct( Key.metadata ) ) )
			    // Convert to EntityRecord
			    .map( this::toEntityRecord )
			    .toList();
		}

		// Generate XML mapping files for each entity
		// Change this to a stream if we will be doing parallel processing
		for ( EntityRecord entity : this.entities ) {
			entity.setXmlFilePath( writeXMLFile( entity ) );
		}

		return this;
	}

	/**
	 * Discover all classes in the given entity paths.
	 * <p>
	 * Does NOT determine if the classes are persistent, nor does it load metadata. This method is a simple file walk, nothing more.
	 *
	 * @param entityPaths The list of paths to search for entities.
	 *
	 * @return A list of structs containing the location and file name of each discovered entity.
	 */
	private ArrayList<IStruct> discoverBLClasses( List<Path> entityPaths ) {
		return entityPaths
		    .stream()
		    .flatMap( path -> {
			    try {
				    // TODO: Return path.parent() alongside each discovered entity file so we know the entity location / mapping and can get a correct FQN.
				    return Files.walk( path )
				        // only files
				        .filter( Files::isRegularFile )
				        // Only .bx or .cfc class files
				        .filter( file -> StringUtils.endsWithAny( file.toString(), ENTITY_EXTENSIONS ) )
				        // map to a struct instance containing the location and file name. We need both to generate the FQN.
				        .map( file -> Struct.of(
				            ORMKeys.location, path.toString(),
				            Key.file, file.toString()
				        ) );
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
	}

	/**
	 * Determine if the given entity metadata is marked as a persistent entity using either the classic (`persistent=true`) or modern (`@Entity`) metadata
	 * syntax.
	 *
	 * @param entityMeta The entity metadata struct.
	 *
	 * @return True if the entity is marked as persistent.
	 */
	private boolean isPersistentEntity( IStruct entityMeta ) {
		IStruct annotations = entityMeta.getAsStruct( Key.annotations );
		if ( annotations.containsKey( ORMKeys.entity ) ||
		    ( annotations.containsKey( ORMKeys.persistent ) &&
		        BooleanCaster.cast( annotations.getOrDefault( ORMKeys.persistent, "false" ) ) ) ) {
			if ( logger.isDebugEnabled() )
				logger.debug(
				    "Class is 'persistent'; generating XML: [{}] ",
				    entityMeta.getAsString( Key.path )
				);
			return true;
		} else {

			if ( logger.isDebugEnabled() )
				logger.debug(
				    "Class is unmarked or marked as as non-persistent; skipping: [{}]",
				    entityMeta.getAsString( Key.path )
				);

			return false;
		}
	}

	/**
	 * Parse the given class file and load metadata for the entity.
	 *
	 * @param possibleEntity The entity struct to read metadata for.
	 *
	 * @return The entity struct with the metadata added.
	 */
	private IStruct readMeta( IStruct possibleEntity ) {
		if ( logger.isDebugEnabled() )
			logger.debug( "Loading metadata for class {}", possibleEntity.getAsString( Key.file ) );

		possibleEntity.put( Key.metadata, getClassMeta( Path.of( possibleEntity.getAsString( Key.file ) ) ) );
		return possibleEntity;
	}

	/**
	 * Convert the given entity struct to an EntityRecord instance.
	 *
	 * @param theEntity Struct containing `location`, `path`, and `metadata` keys.
	 *
	 * @return EntityRecord instance.
	 */
	private EntityRecord toEntityRecord( IStruct theEntity ) {
		IStruct				meta			= theEntity.getAsStruct( Key.metadata );
		IStruct				annotations		= meta.getAsStruct( Key.annotations );
		String				entityName		= readEntityName( meta );
		ResolvedFilePath	entityRootPath	= FileSystemUtil.contractPath( context, meta.getAsString( Key.path ) );
		String				fqn				= entityRootPath.getBoxFQN().toString();
		if ( fqn == null || fqn.isBlank() ) {
			throw new BoxRuntimeException( "Failed to generate FQN for entity: " + entityName );
		}

		if ( logger.isDebugEnabled() )
			logger.debug( "Generated FQN for entity [{}]: [{}]", entityName, fqn );
		Key datasourceName = Key.of( config.datasource );
		if ( annotations.containsKey( Key.datasource ) ) {
			datasourceName = Key.of( annotations.getAsString( Key.datasource ) );
		}
		return new EntityRecord(
		    entityName,
		    fqn,
		    meta,
		    datasourceName
		);
	}

	/**
	 * Determine the entity name from the given entity metadata.
	 *
	 * @param meta The entity metadata.
	 *
	 * @return The defined entity name, or the class name if no entity name is defined.
	 */
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

	/**
	 * Parse the given class file and load metadata for the entity using BoxLang's `ClassMetadataVisitor` for speedy metadata parsing.
	 *
	 * @param clazzPath Full path to the class file.
	 *
	 * @return The entity metadata struct.
	 */
	private IStruct getClassMeta( Path clazzPath ) {
		if ( logger.isDebugEnabled() )
			logger.debug( "Parsing class file: [{}]", clazzPath );

		ParsingResult result = new Parser().parse( new File( clazzPath.toString() ) );
		if ( !result.isCorrect() ) {
			throw new ParseException( result.getIssues(), "" );
		}
		ClassMetadataVisitor visitor = new ClassMetadataVisitor( this.context );
		result.getRoot().accept( visitor );
		return visitor.getMetadata();
	}

	/**
	 * Get a list of discovered entities by datasource name. Obviously, this is only useful once `generateMappings` has been called.
	 *
	 * @return The map of discovered entities, where the key is the datasource name and the value is a List of EntityRecord instances for that datasource.
	 */
	public Map<Key, List<EntityRecord>> getEntityDatasourceMap() {
		return this.entities.stream().collect( java.util.stream.Collectors.groupingBy( EntityRecord::getDatasource ) );
	}

	/**
	 * Write the XML mapping file for the given entity metadata.
	 *
	 * @param entity EntityRecord containing the entity metadata.
	 *
	 * @return The path to the generated XML mapping file If `saveMappingAlongsideEntity` is true, the path will be the same as the entity file, but with
	 *         a `.hbm.xml` extension.
	 */
	private Path writeXMLFile( EntityRecord entity ) {
		IStruct	meta	= entity.getMetadata();
		String	name	= meta.getAsString( Key._name );
		String	path	= meta.getAsString( Key.path );
		String	fileExt	= path.substring( path.lastIndexOf( '.' ) );
		Path	xmlPath	= this.saveAlongsideEntity
		    ? Path.of( path.replace( fileExt, ".hbm.xml" ) )
		    : Path.of( this.saveDirectory, name + ".hbm.xml" );
		try {
			if ( logger.isDebugEnabled() )
				logger.debug( "Writing Hibernate XML mapping file for entity [{}] to [{}]", name, xmlPath );

			String finalXML = generateXML( entity );
			Files.write( xmlPath, !finalXML.isEmpty() ? finalXML.getBytes() : new byte[ 0 ] );

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
	 * @param entity The EntityRecord instance.
	 *
	 * @return The full XML mapping for the entity as a string. If an exception was encountered and {@link ORMConfig#ignoreParseErrors} is true, an empty
	 *         string will be returned.
	 */
	private String generateXML( EntityRecord entity ) {
		try {
			IStruct meta = entity.getMetadata();
			// We need this for inheritance
			meta.put( ORMKeys.classFQN, entity.getClassFQN() );
			// ensure the 'datasource' key is populated with our default logic
			meta.computeIfAbsent( Key.datasource, ( key ) -> entity.getDatasource() );
			IEntityMeta entityMeta = AbstractEntityMeta.autoDiscoverMetaType( meta );
			entity.setEntityMeta( entityMeta );

			Document			doc			= new HibernateXMLWriter( entityMeta, this::entityLookup, !this.config.ignoreParseErrors ).generateXML();

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
			logger.warn( "Failed to transform XML to string for entity [{}]", entity.getEntityName(), e );
			if ( !config.ignoreParseErrors ) {
				throw new BoxRuntimeException( String.format( "Failed to transform XML to string for entity [%s]", entity.getEntityName() ), e );
			}
		}

		return "";
	}

	/**
	 * Lookup an entity by class name and (optionally) datasource name.
	 * <p>
	 * Useful for determining relationship entity names based off the provided class name.
	 *
	 * @param className      The class name to lookup.
	 * @param datasourceName The datasource name to match on, if any.
	 *
	 * @return EntityRecord instance or null.
	 */
	public EntityRecord entityLookup( String className, Key datasourceName ) {
		return this.entities
		    .stream()
		    .filter( ( EntityRecord e ) -> {
			    return e.getClassName().equalsIgnoreCase( className )
			        && ( datasourceName == null || e.getDatasource().equals( datasourceName ) );
		    } )
		    .findFirst()
		    .orElse( null );
	}
}
