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
package ortus.boxlang.modules.orm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMConnectionProvider;
import ortus.boxlang.modules.orm.hibernate.BoxPersisterFactory;
import ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeFactory;
import ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Configures and starts up Hibernate - specifically, configures and starts up a session factory specific to a single datasource.
 *
 * @since 1.0.0
 */
public class SessionFactoryBuilder {

	/**
	 * Runtime
	 */
	private static final BoxRuntime	runtime	= BoxRuntime.getInstance();

	/**
	 * The logger for this class. We may log warnings or errors if we encounter
	 * unsupported ORM configuration.
	 */
	private BoxLangLogger			logger;

	/**
	 * The ORM datasource name which this session factory should be tied to.
	 */
	private Key						datasourceName;

	/**
	 * The ORM configuration for this session factory.
	 */
	private ORMConfig				ormConfig;

	/**
	 * The discovered entities for this session factory.
	 */
	private List<EntityRecord>		entities;

	/**
	 * The BoxLang context for this session factory.
	 */
	private IJDBCCapableContext		context;

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Static Helpers
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Get a unique key for the given context/datasource combination.
	 *
	 * @param context        The BoxLang context for this session factory.
	 * @param datasourceName The ORM datasource for this session factory.
	 *
	 * @return a unique key for the given context/datasource combination.
	 */
	public static Key getUniqueName( IBoxContext context, Key datasourceName ) {
		return Key.of( ORMService.getAppNameFromContext( context ) + "_" + datasourceName.getName() );
	}

	/**
	 * ------------------------------------------------------------------------------------------------------------
	 * Constructor(s)
	 * ------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Constructor
	 *
	 * @param context        The BoxLang context for this session factory.
	 * @param datasourceName The ORM datasource for this session factory.
	 * @param ormConfig      The ORM configuration for this session factory.
	 * @param entities       The discovered entities for this session factory.
	 */
	public SessionFactoryBuilder( IJDBCCapableContext context, Key datasourceName, ORMConfig ormConfig, List<EntityRecord> entities ) {
		this.ormConfig		= ormConfig;
		this.context		= context;
		this.datasourceName	= datasourceName;
		this.entities		= entities;
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
	}

	/**
	 * Get a unique name for this session factory.
	 *
	 * @return a unique name for this session factory, based on the application name and datasource name.
	 */
	public Key getUniqueName() {
		return SessionFactoryBuilder.getUniqueName( this.context, datasourceName );
	}

	/**
	 * Build the Hibernate session factory.
	 * <p>
	 * This method will generate entity mappings if `ormConfig.generateMappings` is true, as well as parse the ORM configuration and set up the Hibernate
	 * configuration.
	 *
	 * @return a Hibernate session factory ready for use.
	 */
	public SessionFactory build() {
		// Sadly, JAXB hardcodes the context classloader, so we have to temporarily set the context classloader to whatever classloader is used to load this
		// module's dependencies.
		ClassLoader		oldClassLoader	= Thread.currentThread().getContextClassLoader();
		ModuleRecord	moduleRecord	= runtime.getModuleService().getModuleRecord( Key.of( "orm" ) );
		Thread.currentThread().setContextClassLoader( moduleRecord.classLoader );

		// Make sure we clean up the classloader when we're done.
		SessionFactory	factory	= null;
		Configuration	configuration;
		try {
			configuration	= buildConfiguration();
			factory			= configuration.buildSessionFactory();
		} finally {
			Thread.currentThread().setContextClassLoader( oldClassLoader );
			// Only close the BootstrapServiceRegistry on failure. On the success path,
			// Hibernate's service registry chain (BootstrapRegistry → StandardServiceRegistry
			// → SessionFactoryServiceRegistry) remains live and is torn down transitively when
			// SessionFactory.close() is called. Closing it here on success destroys that chain
			// immediately, causing UnknownServiceException on the next openSession() call.
			if ( factory == null ) {
				ormConfig.closeBootstrapRegistry();
			}
		}

		return factory;
	}

	/**
	 * Configure the Hibernate session factory with the ORM configuration, entity mappings, etc.
	 *
	 * @return a populated Hibernate configuration object
	 */
	private Configuration buildConfiguration() {
		Configuration			configuration	= ormConfig.toHibernateConfig();
		Properties				properties		= new Properties();
		Collection<ClassLoader>	classLoaders	= new ArrayList<>();
		classLoaders.add( runtime.getModuleService().getModuleRecord( Key.of( "orm" ) ).classLoader );
		classLoaders.add( runtime.getClass().getClassLoader() );

		// Any configuration which needs a specific java type (such as the connection provider instance) goes here
		properties.put( AvailableSettings.CONNECTION_PROVIDER, new ORMConnectionProvider( this.datasourceName ) );
		properties.put( AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread" );
		properties.put( AvailableSettings.CLASSLOADERS, classLoaders );
		properties.put( AvailableSettings.GLOBALLY_QUOTED_IDENTIFIERS, StringCaster.cast( ormConfig.quoteIdentifiers ) );

		Map<String, EntityRecord> entityMap = this.entities
		    .stream()
		    .collect( java.util.stream.Collectors.toMap( entity -> entity.getEntityName().toLowerCase().trim(), entity -> entity ) );

		// In facade mode, generate one real POJO facade class per entity (into the module classloader Hibernate resolves
		// against) BEFORE the session factory parses the mapping XML, which references those facade FQNs via <class name>.
		if ( ormConfig.entityFacades ) {
			generateEntityFacades( entityMap.values() );
		}

		// Route every entity persister through the BoxLang representation strategy (Hibernate 6+/7+ replacement for the
		// Hibernate 5 tuplizer). See BoxPersisterFactory for why this goes through the persister factory service.
		properties.put( "hibernate.persister.factory", new BoxPersisterFactory( entityMap, ormConfig.entityFacades ) );

		// Collect XML mapping files and add them to the Hibernate configuration.
		// The modern mapping.xml format resolves a dynamic entity's <extends> superclass eagerly (Hibernate registers each dynamic class as its file is
		// processed and does NOT defer the lookup), so a subclass file must be added AFTER its parent's file. We therefore order the files by inheritance
		// depth (roots first). The legacy HBM format is order-independent, so this ordering is harmless for it too.
		List<EntityRecord> ordered = entityMap.values()
		    .stream()
		    .sorted( java.util.Comparator.comparingInt( e -> mappingRank( e, entityMap ) ) )
		    .toList();

		if ( ormConfig.ormXmlMapping ) {
			// The modern mapping.xml format needs every dynamic (class-less) entity definition processed as one coherent unit: when entities live in
			// separate files, Hibernate can stub an as-yet-undefined entity referenced by an association (or a subclass), leaving it without its
			// superclass or id member and breaking inheritance/id-generation. Merge all per-entity <entity> elements (parent-first) into a single
			// <entity-mappings> document and hand Hibernate that one file.
			configuration.addFile( buildCombinedMappingFile( ordered ) );
		} else {
			ordered.stream()
			    .map( EntityRecord::getXmlFilePath )
			    .map( Path::toString )
			    .forEach( configuration::addFile );
		}

		configuration.addProperties( properties );

		return configuration;
	}

	/**
	 * Compute the inheritance depth of an entity (0 for a root/non-subclass, 1 for a direct subclass, etc.) by walking its {@code extends} chain through
	 * the entity map. Used to order mapping files parent-first for the modern {@code mapping.xml} format.
	 *
	 * @param entity    The entity record.
	 * @param entityMap Map of lower-cased entity name to EntityRecord.
	 * @param visited   Guard against cyclic {@code extends} chains.
	 *
	 * @return The inheritance depth.
	 */
	/**
	 * Compute a processing rank so mapping files are added parent-first AND every entity that participates in inheritance is processed before the
	 * standalone entities that merely reference it.
	 * <p>
	 * This matters for the modern {@code mapping.xml} format: when Hibernate processes an entity that has a to-one/collection pointing at a subclass, it
	 * eagerly resolves (and stubs) the target's {@code ClassDetails}. If the subclass has not been defined yet, the stub is created <em>without</em> its
	 * superclass link, so the subclass silently loses its inherited id/table (its {@code hasParents} becomes false). Ordering all inheritance-involved
	 * entities (hierarchy roots first, then subclasses by depth) ahead of the standalone entities avoids this.
	 *
	 * @return 0 for a hierarchy root (an entity that has subclasses), the inheritance depth (&ge;1) for a subclass, or {@link Integer#MAX_VALUE} for a
	 *         standalone entity.
	 */
	/**
	 * Merge the given (parent-first ordered) entities' modern {@code mapping.xml} files into a single {@code <entity-mappings>} document and write it to
	 * a
	 * temp file, returning that file's path. Feeding Hibernate one combined document lets it process all dynamic (class-less) entity definitions in a
	 * single coherent unit, avoiding cross-file stubbing of not-yet-defined entities.
	 *
	 * @param ordered The entities, ordered so parents/hierarchy-roots precede subclasses and standalone entities.
	 *
	 * @return Absolute path of the combined mapping file.
	 */
	private String buildCombinedMappingFile( List<EntityRecord> ordered ) {
		try {
			var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware( true );
			var						builder		= factory.newDocumentBuilder();
			org.w3c.dom.Document	combined	= builder.getDOMImplementation().createDocument(
			    ortus.boxlang.modules.orm.mapping.MappingXMLWriter.ORM_NAMESPACE, "entity-mappings", null );
			combined.getDocumentElement().setAttribute( "version", ortus.boxlang.modules.orm.mapping.MappingXMLWriter.ORM_VERSION );

			for ( EntityRecord entity : ordered ) {
				Path xmlPath = entity.getXmlFilePath();
				if ( xmlPath == null ) {
					continue;
				}
				org.w3c.dom.Document	doc			= builder.parse( xmlPath.toFile() );
				org.w3c.dom.NodeList	entities	= doc.getElementsByTagNameNS( ortus.boxlang.modules.orm.mapping.MappingXMLWriter.ORM_NAMESPACE,
				    "entity" );
				for ( int i = 0; i < entities.getLength(); i++ ) {
					combined.getDocumentElement().appendChild( combined.importNode( entities.item( i ), true ) );
				}
			}

			Path	out			= java.nio.file.Files.createTempFile( "bxorm-combined-" + this.datasourceName.getName() + "-", ".orm.xml" );
			var		transformer	= javax.xml.transform.TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty( javax.xml.transform.OutputKeys.INDENT, "yes" );
			try ( var os = java.nio.file.Files.newOutputStream( out ) ) {
				transformer.transform( new javax.xml.transform.dom.DOMSource( combined ), new javax.xml.transform.stream.StreamResult( os ) );
			}
			out.toFile().deleteOnExit();
			return out.toString();
		} catch ( Exception e ) {
			throw new ortus.boxlang.runtime.types.exceptions.BoxRuntimeException( "Failed to build combined ORM mapping.xml document", e );
		}
	}

	/**
	 * Generate a real POJO facade class for each entity and register it, so Hibernate can map the entity to a real class
	 * with a real id member (enabling {@code uuid} and other id generation). Only invoked in facade mode.
	 * <p>
	 * The facade's fully-qualified name and id Java type are computed by {@link EntityFacadeNaming}, so they match the
	 * {@code <class name=...>} and id {@code type} the mapping XML writer emits. The facade is injected into the ORM
	 * module classloader (the one Hibernate resolves mapped classes against).
	 *
	 * @param entities The discovered entity records for this session factory.
	 */
	private void generateEntityFacades( Collection<EntityRecord> entities ) {
		ClassLoader loader = runtime.getModuleService().getModuleRecord( Key.of( "orm" ) ).classLoader;
		for ( EntityRecord entity : entities ) {
			IEntityMeta meta = entity.getEntityMeta();
			if ( meta == null ) {
				continue;
			}

			java.util.Set<IPropertyMeta> idProps = meta.getIdProperties();
			if ( idProps.size() != 1 ) {
				// Composite ids (and id-less subclasses) are not yet wired for facades. Fail fast unless errors are ignored.
				String message = "Entity facades require exactly one id property; entity [" + entity.getEntityName() + "] has " + idProps.size()
				    + ". Composite ids are not yet supported in facade mode.";
				if ( ormConfig.ignoreParseErrors ) {
					logger.error( message );
					continue;
				}
				throw new ortus.boxlang.runtime.types.exceptions.BoxRuntimeException( message );
			}

			IPropertyMeta										idProp		= idProps.iterator().next();
			EntityFacadeFactory.PropertySpec					idSpec		= new EntityFacadeFactory.PropertySpec(
			    idProp.getName(),
			    EntityFacadeNaming.idJavaType( idProp.getORMType() )
			);

			// Every other persistent property (normal columns, version/timestamp, associations) gets an Object accessor;
			// bx-orm's JPA AttributeConverters are declared AttributeConverter<Object, ?>, matching an Object attribute type.
			java.util.List<EntityFacadeFactory.PropertySpec>	propSpecs	= new java.util.ArrayList<>();
			for ( IPropertyMeta prop : meta.getAllPersistentProperties() ) {
				if ( prop.getName().equalsIgnoreCase( idProp.getName() ) ) {
					continue;
				}
				propSpecs.add( new EntityFacadeFactory.PropertySpec( prop.getName(), Object.class ) );
			}

			// Use the IEntityMeta entity name so the FQN matches, byte-for-byte, the <class name=...> the mapping writer emits.
			String		facadeFQN	= EntityFacadeNaming.facadeClassName( meta.getEntityName() );
			Class<?>	facadeClass	= EntityFacadeFactory.generate( facadeFQN, idSpec, propSpecs, loader );
			FacadeSupport.register( meta.getEntityName(), facadeClass );
			FacadeSupport.register( entity.getEntityName(), facadeClass );
			logger.trace( "Generated entity facade [{}] for entity [{}]", facadeFQN, meta.getEntityName() );
		}
	}

	private static int mappingRank( EntityRecord entity, Map<String, EntityRecord> entityMap ) {
		var meta = entity.getEntityMeta();
		if ( meta != null && meta.isSubclass() ) {
			return inheritanceDepth( entity, entityMap, new java.util.HashSet<>() );
		}
		// A root entity that is extended by at least one subclass must be processed before the rest.
		String name = entity.getEntityName();
		if ( name != null ) {
			boolean hasChildren = entityMap.values().stream().anyMatch( candidate -> {
				var cm = candidate.getEntityMeta();
				if ( cm == null || !cm.isSubclass() ) {
					return false;
				}
				var pm = cm.getParentMeta();
				if ( pm == null || pm.isEmpty() ) {
					return false;
				}
				var		pa			= pm.getAsStruct( ortus.boxlang.runtime.scopes.Key.annotations );
				String	parentName	= pa != null ? pa.getAsString( ortus.boxlang.modules.orm.config.ORMKeys.entityName ) : null;
				if ( parentName == null || parentName.isBlank() ) {
					parentName = pm.getAsString( ortus.boxlang.runtime.scopes.Key.simpleName );
				}
				return parentName != null && parentName.equalsIgnoreCase( name );
			} );
			if ( hasChildren ) {
				return 0;
			}
		}
		return Integer.MAX_VALUE;
	}

	private static int inheritanceDepth( EntityRecord entity, Map<String, EntityRecord> entityMap, java.util.Set<String> visited ) {
		var meta = entity.getEntityMeta();
		if ( meta == null || !meta.isSubclass() ) {
			return 0;
		}
		var parentMeta = meta.getParentMeta();
		if ( parentMeta == null || parentMeta.isEmpty() ) {
			return 0;
		}
		var		parentAnnotations	= parentMeta.getAsStruct( ortus.boxlang.runtime.scopes.Key.annotations );
		String	parentName			= parentAnnotations != null
		    ? parentAnnotations.getAsString( ortus.boxlang.modules.orm.config.ORMKeys.entityName )
		    : null;
		if ( parentName == null || parentName.isBlank() ) {
			parentName = parentMeta.getAsString( ortus.boxlang.runtime.scopes.Key.simpleName );
		}
		if ( parentName == null || parentName.isBlank() || !visited.add( parentName.toLowerCase().trim() ) ) {
			return 0;
		}
		EntityRecord parent = entityMap.get( parentName.toLowerCase().trim() );
		if ( parent == null ) {
			return 1;
		}
		return 1 + inheritanceDepth( parent, entityMap, visited );
	}
}
