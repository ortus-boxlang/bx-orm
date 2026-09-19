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

import java.beans.Introspector;
import java.util.Set;
import java.util.function.BiFunction;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Generate a Hibernate 7 <em>modern</em> mapping document (root {@code <entity-mappings>} in namespace
 * {@code http://www.hibernate.org/xsd/orm/mapping}, version {@code 7.0}) for a given {@link IEntityMeta} instance.
 * <p>
 * This is the only mapping writer the module emits (the legacy {@code hbm.xml} DTD writer was removed; {@code hbm.xml} is
 * deprecated-for-removal in Hibernate). It traverses the normalized {@link IEntityMeta}/{@link IPropertyMeta} metadata and
 * emits the modern format.
 * <p>
 * In the default facade (POJO) representation each entity is emitted as {@code <entity name="Foo" class="...FooFacade">},
 * mapping the generated real facade class. In MAP mode the entity is class-less: {@code <entity name="Foo"
 * metadata-complete="true">} with NO {@code class} attribute. Attribute Java types are given via a {@code <type value="..."/>}
 * basic-type element and JPA {@code AttributeConverter}s via {@code <convert converter="FQCN"/>}.
 *
 * @since 1.5.0
 */
public class MappingXMLWriter {

	/**
	 * The Hibernate 7 modern mapping namespace.
	 */
	public static final String				ORM_NAMESPACE	= "http://www.hibernate.org/xsd/orm/mapping";

	/**
	 * The mapping format version emitted (required, fixed at 7.0 by the XSD).
	 */
	public static final String				ORM_VERSION		= "7.0";

	/**
	 * Set of SQL reserved words (any SQL dialect) that need to be escaped when used in identifiers.
	 */
	private static final Set<String>		RESERVED_WORDS	= Set.of( "absolute", "access", "accessible", "action", "add", "after", "alias",
	    "all", "allocate", "allow", "alter", "analyze", "and", "any", "application", "are", "array", "as", "asc",
	    "asensitive", "assertion", "associate", "asutime", "asymmetric", "at", "atomic", "audit", "authorization", "aux",
	    "auxiliary", "avg", "backup", "before", "begin", "between", "bigint", "binary", "bit", "bit_length", "blob",
	    "boolean", "both", "breadth", "break", "browse", "bufferpool", "bulk", "by", "cache", "call", "called", "capture",
	    "cardinality", "cascade", "cascaded", "case", "cast", "catalog", "ccsid", "change", "char", "char_length",
	    "character", "character_length", "check", "checkpoint", "clob", "close", "cluster", "clustered", "coalesce",
	    "collate", "collation", "collection", "collid", "column", "comment", "commit", "compress", "compute", "concat",
	    "condition", "connect", "connection", "constraint", "constraints", "constructor", "contains", "containstable",
	    "continue", "convert", "corresponding", "count", "count_big", "create", "cross", "cube", "current", "current_date",
	    "current_default_transform_group", "current_lc_ctype", "current_path", "current_role", "current_server",
	    "current_time", "current_timestamp", "current_timezone", "current_transform_group_for_type", "current_user", "cursor",
	    "cycle", "data", "database", "databases", "date", "day", "day_hour", "day_microsecond", "day_minute", "day_second",
	    "days", "db2general", "db2genrl", "db2sql", "dbcc", "dbinfo", "deallocate", "dec", "decimal", "declare", "default",
	    "defaults", "deferrable", "deferred", "delayed", "delete", "deny", "depth", "deref", "desc", "describe", "descriptor",
	    "deterministic", "diagnostics", "disallow", "disconnect", "disk", "distinct", "distinctrow", "distributed", "div",
	    "do", "domain", "double", "drop", "dsnhattr", "dssize", "dual", "dummy", "dump", "dynamic", "each", "editproc",
	    "else", "elseif", "enclosed", "encoding", "end", "end-exec", "end-exec1", "endexec", "equals", "erase", "errlvl",
	    "escape", "escaped", "except", "exception", "excluding", "exclusive", "exec", "execute", "exists", "exit", "explain",
	    "external", "extract", "false", "fenced", "fetch", "fieldproc", "file", "fillfactor", "filter", "final", "first",
	    "float", "float4", "float8", "for", "force", "foreign", "found", "free", "freetext", "freetexttable", "from", "full",
	    "fulltext", "function", "general", "generated", "get", "get_current_connection", "global", "go", "goto", "grant",
	    "graphic", "group", "grouping", "handler", "having", "high_priority", "hold", "holdlock", "hour", "hour_microsecond",
	    "hour_minute", "hour_second", "hours", "identified", "identity", "identity_insert", "identitycol", "if", "ignore",
	    "immediate", "in", "including", "increment", "index", "indicator", "infile", "inherit", "initial", "initially",
	    "inner", "inout", "input", "insensitive", "insert", "int", "int1", "int2", "int3", "int4", "int8", "integer",
	    "integrity", "intersect", "interval", "into", "is", "isobid", "isolation", "iterate", "jar", "java", "join", "key",
	    "keys", "kill", "language", "large", "last", "lateral", "leading", "leave", "left", "level", "like", "limit",
	    "linear", "lineno", "lines", "linktype", "load", "local", "locale", "localtime", "localtimestamp", "locator",
	    "locators", "lock", "lockmax", "locksize", "long", "longblob", "longint", "longtext", "loop", "low_priority", "lower",
	    "ltrim", "map", "master_ssl_verify_server_cert", "match", "max", "maxextents", "maxvalue", "mediumblob", "mediumint",
	    "mediumtext", "method", "microsecond", "microseconds", "middleint", "min", "minus", "minute", "minute_microsecond",
	    "minute_second", "minutes", "minvalue", "mlslabel", "mod", "mode", "modifies", "modify", "module", "month", "months",
	    "names", "national", "natural", "nchar", "nclob", "new", "new_table", "next", "no", "no_write_to_binlog", "noaudit",
	    "nocache", "nocheck", "nocompress", "nocycle", "nodename", "nodenumber", "nomaxvalue", "nominvalue", "nonclustered",
	    "none", "noorder", "not", "nowait", "null", "nullif", "nulls", "number", "numeric", "numparts", "nvarchar", "obid",
	    "object", "octet_length", "of", "off", "offline", "offsets", "old", "old_table", "on", "online", "only", "open",
	    "opendatasource", "openquery", "openrowset", "openxml", "optimization", "optimize", "option", "optionally", "or",
	    "order", "ordinality", "out", "outer", "outfile", "output", "over", "overlaps", "overriding", "package", "pad",
	    "parameter", "part", "partial", "partition", "path", "pctfree", "percent", "piecesize", "plan", "position",
	    "precision", "prepare", "preserve", "primary", "print", "prior", "priqty", "privileges", "proc", "procedure",
	    "program", "psid", "public", "purge", "queryno", "raiserror", "range", "raw", "read", "read_write", "reads",
	    "readtext", "real", "reconfigure", "recovery", "recursive", "ref", "references", "referencing", "regexp", "relative",
	    "release", "rename", "repeat", "replace", "replication", "require", "resignal", "resource", "restart", "restore",
	    "restrict", "result", "result_set_locator", "return", "returns", "revoke", "right", "rlike", "role", "rollback",
	    "rollup", "routine", "row", "rowcount", "rowguidcol", "rowid", "rownum", "rows", "rrn", "rtrim", "rule", "run",
	    "runtimestatistics", "save", "savepoint", "schema", "schemas", "scope", "scratchpad", "scroll", "search", "second",
	    "second_microsecond", "seconds", "secqty", "section", "security", "select", "sensitive", "separator", "session",
	    "session_user", "set", "sets", "setuser", "share", "show", "shutdown", "signal", "similar", "simple", "size",
	    "smallint", "some", "source", "space", "spatial", "specific", "specifictype", "sql", "sql_big_result",
	    "sql_calc_found_rows", "sql_small_result", "sqlcode", "sqlerror", "sqlexception", "sqlid", "sqlstate", "sqlwarning",
	    "ssl", "standard", "start", "starting", "state", "static", "statistics", "stay", "stogroup", "stores",
	    "straight_join", "style", "subpages", "substr", "substring", "successful", "sum", "symmetric", "synonym", "sysdate",
	    "sysfun", "sysibm", "sysproc", "system", "system_user", "table", "tablespace", "temporary", "terminated", "textsize",
	    "then", "time", "timestamp", "timezone_hour", "timezone_minute", "tinyblob", "tinyint", "tinytext", "to", "top",
	    "trailing", "tran", "transaction", "translate", "translation", "treat", "trigger", "trim", "true", "truncate",
	    "tsequal", "type", "uid", "under", "undo", "union", "unique", "unknown", "unlock", "unnest", "unsigned", "until",
	    "update", "updatetext", "upper", "usage", "use", "user", "using", "utc_date", "utc_time", "utc_timestamp", "validate",
	    "validproc", "value", "values", "varbinary", "varchar", "varchar2", "varcharacter", "variable", "variant", "varying",
	    "vcat", "view", "volumes", "waitfor", "when", "whenever", "where", "while", "window", "with", "within", "without",
	    "wlm", "work", "write", "writetext", "xor", "year", "year_month", "zerofill", "zone", "rank" );

	private static final BoxRuntime			runtime			= BoxRuntime.getInstance();

	private BoxLangLogger					logger;

	IEntityMeta								entity;

	Document								document;

	BiFunction<String, Key, EntityRecord>	entityLookup;

	ORMConfig								ormConfig;

	PhysicalNamingStrategy					namingStrategy;

	/**
	 * When the entity uses a secondary (join) table for its local properties (a discriminated single-table subclass that maps its own extra columns to a
	 * separate table), this holds that table name so property columns and to-one join columns are tagged with {@code table="..."}.
	 */
	private String							secondaryTableName;

	/**
	 * The JPA inheritance strategy ({@code SINGLE_TABLE} or {@code JOINED}) to declare on this entity when it is the root of an inheritance hierarchy, or
	 * {@code null} when it is not an inheritance root. Computed by {@link MappingGenerator} which can see all entities (and therefore an entity's
	 * subclasses).
	 */
	private final String					rootInheritanceStrategy;

	/**
	 * Create a new modern-mapping XML writer for the given entity metadata.
	 *
	 * @param entity       The entity metadata to generate XML for.
	 * @param entityLookup A function that takes an entity name and datasource name and returns an EntityRecord (or null).
	 * @param ormConfig    ORM configuration settings.
	 */
	public MappingXMLWriter( IEntityMeta entity, BiFunction<String, Key, EntityRecord> entityLookup, ORMConfig ormConfig ) {
		this( entity, entityLookup, ormConfig, null );
	}

	/**
	 * Create a new modern-mapping XML writer, supplying the precomputed inheritance strategy for an inheritance-hierarchy root.
	 *
	 * @param entity                  The entity metadata to generate XML for.
	 * @param entityLookup            A function that takes an entity name and datasource name and returns an EntityRecord (or null).
	 * @param ormConfig               ORM configuration settings.
	 * @param rootInheritanceStrategy {@code SINGLE_TABLE}/{@code JOINED} when this entity is a hierarchy root, else {@code null}.
	 */
	public MappingXMLWriter( IEntityMeta entity, BiFunction<String, Key, EntityRecord> entityLookup, ORMConfig ormConfig, String rootInheritanceStrategy ) {
		this.logger						= runtime.getLoggingService().getLogger( "orm" );
		this.entity						= entity;
		this.entityLookup				= entityLookup;
		this.ormConfig					= ormConfig;
		this.namingStrategy				= ormConfig.getNamingStrategyInstance();
		this.rootInheritanceStrategy	= rootInheritanceStrategy;

		if ( !entity.isSubclass() && entity.getIdProperties().isEmpty() ) {
			logger.error( "Entity {} has no ID properties. Hibernate requires at least one.", entity.getEntityName() );
			if ( !this.ormConfig.ignoreParseErrors ) {
				throw new BoxRuntimeException( "Entity %s has no ID properties. Hibernate requires at least one.".formatted( entity.getEntityName() ) );
			}
		}

		this.document = createDocument();
	}

	/**
	 * Create a new, empty {@code <entity-mappings>} document with the modern namespace and version. No DOCTYPE is emitted.
	 *
	 * @return A new, empty XML document.
	 */
	public Document createDocument() {
		DocumentBuilderFactory	factory	= DocumentBuilderFactory.newInstance();
		DocumentBuilder			builder;
		try {
			builder = factory.newDocumentBuilder();
			// Build a namespace-aware document: the root and every child element live in the ORM mapping namespace, which the serializer emits as the
			// default namespace and which Hibernate's MappingBinder auto-detects.
			Document	rootDocument	= builder.getDOMImplementation().createDocument( ORM_NAMESPACE, "entity-mappings", null );
			Element		root			= rootDocument.getDocumentElement();
			root.setAttribute( "version", ORM_VERSION );
			rootDocument.insertBefore( rootDocument.createComment(
			    """
			    \n~ Generated by the Ortus BoxLang ORM module for use in BoxLang web applications.
			    ~
			    ~ https://github.com/ortus-boxlang/bx-orm
			    ~ https://boxlang.io
			    ~ https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html
			    """
			), root );
			return rootDocument;
		} catch ( ParserConfigurationException e ) {
			logger.error( "Error creating Hibernate mapping.xml document: {}", e.getMessage(), e );
			if ( this.ormConfig.ignoreParseErrors ) {
				return null;
			}
			throw new BoxRuntimeException( "Error creating Hibernate mapping.xml document: " + e.getMessage(), e );
		}
	}

	/**
	 * Generate the modern mapping document, beginning with the {@code <entity>} element.
	 *
	 * @return The complete {@code <entity-mappings>} document.
	 */
	public Document generateXML() {
		this.document.getDocumentElement().appendChild( generateEntityElement() );
		return this.document;
	}

	/**
	 * Generate the top-level {@code <entity>} element. Elements are emitted in the order the mapping-7.0 XSD requires for the {@code entity} complex
	 * type.
	 *
	 * @return The {@code <entity>} element.
	 */
	public Element generateEntityElement() {
		Element	entityElement	= createEl( "entity" );

		// Entity identity depends on the representation mode. In facade (POJO) mode the entity is mapped to a real,
		// per-application generated facade class, so emit its `class` (plus `name` as the JPA/entity name); the real class
		// also lets Hibernate's id-generator resolver dereference a class name, unblocking uuid etc. In MAP mode the entity
		// is class-less: name only, NO class attribute.
		String	entityName		= entity.getEntityName();
		if ( entityName != null && !entityName.isEmpty() ) {
			entityElement.setAttribute( "name", entityName );
		}
		if ( this.ormConfig.entityFacades && entityName != null && !entityName.isEmpty() ) {
			entityElement.setAttribute( "class",
			    ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming.facadeClassName( this.ormConfig.facadeNamespace, entityName ) );
		} else {
			entityElement.setAttribute( "metadata-complete", "true" );
		}

		boolean	isSubclass			= entity.isSubclass();
		boolean	isDiscriminated		= isSubclass && entity.getDiscriminator().get( Key.value ) != null;
		boolean	hasSeparateTable	= false;
		String	joinColumn			= null;
		IStruct	parentAnnotations	= isSubclass ? entity.getParentMeta().getAsStruct( Key.annotations ) : null;

		// 1. <extends> (child element in the modern format)
		if ( isSubclass ) {
			String extendsName = parentAnnotations.getAsString( ORMKeys.entityName );
			if ( extendsName == null || extendsName.isBlank() ) {
				extendsName = entity.getParentMeta().getAsString( Key.simpleName );
			}
			Element extendsEl = createEl( "extends" );
			extendsEl.setTextContent( extendsName );
			entityElement.appendChild( extendsEl );

			hasSeparateTable	= !parentAnnotations.getAsString( ORMKeys.table ).equals( entity.getTableName() );
			joinColumn			= entity.getJoinColumn();
		}

		// 2. <table> / secondary-table / primary-key-join-column
		if ( !isSubclass ) {
			// Root / simple entity: its own physical table.
			appendTableElement( entityElement );
		} else if ( !isDiscriminated ) {
			// Joined-subclass: own table + primary-key-join-column back to the parent.
			appendTableElement( entityElement );
			if ( joinColumn != null ) {
				Element pkjc = createEl( "primary-key-join-column" );
				pkjc.setAttribute( "name", escapeReservedWords( joinColumn ) );
				entityElement.appendChild( pkjc );
			}
		} else if ( hasSeparateTable ) {
			// Discriminated single-table subclass whose extra columns live in a separate join table -> JPA @SecondaryTable.
			this.secondaryTableName = entity.getTableName();
			Element secondary = createEl( "secondary-table" );
			secondary.setAttribute( "name", escapeReservedWords( this.secondaryTableName ) );
			// owned="true" is REQUIRED for the discriminated subclass to manage (insert/update/delete) its own secondary
			// table. Unlike the @SecondaryRow annotation (whose owned defaults to true), Hibernate's orm.xml binding leaves a
			// <secondary-table> read-only when owned is omitted: the row is SELECTed (outer join) but never written, silently
			// dropping the subclass's own columns/FKs on insert and skipping the secondary-row delete (FK violations on
			// cascade). This is the modern-format equivalent of the classic hbm <join>, which Hibernate manages by default.
			secondary.setAttribute( "owned", "true" );
			if ( joinColumn != null ) {
				Element pkjc = createEl( "primary-key-join-column" );
				pkjc.setAttribute( "name", escapeReservedWords( joinColumn ) );
				secondary.appendChild( pkjc );
			}
			entityElement.appendChild( secondary );
		}

		// 3. sql-restriction (where)
		if ( !isSubclass && entity.getWhere() != null ) {
			Element restriction = createEl( "sql-restriction" );
			restriction.setTextContent( entity.getWhere() );
			entityElement.appendChild( restriction );
		}

		// 4. dynamic-insert / dynamic-update / select-before-update
		if ( entity.isDynamicInsert() ) {
			appendTextElement( entityElement, "dynamic-insert", "true" );
		}
		if ( entity.isDynamicUpdate() ) {
			appendTextElement( entityElement, "dynamic-update", "true" );
		}
		if ( entity.isSelectBeforeUpdate() ) {
			appendTextElement( entityElement, "select-before-update", "true" );
		}

		// 5. caching
		if ( !entity.getCache().isEmpty() && !isSubclass ) {
			entityElement.appendChild( generateCachingElement( entity.getCache() ) );
		}

		// 6. batch-size
		if ( entity.getBatchSize() != null ) {
			appendTextElement( entityElement, "batch-size", StringCaster.cast( entity.getBatchSize() ) );
		}

		// 7. lazy - subclasses are always lazy=true; root/simple entities only declare lazy when they are lazy (otherwise
		// Hibernate's own default applies, exactly as the HBM writer relies on).
		if ( isSubclass || entity.isLazy() ) {
			appendTextElement( entityElement, "lazy", "true" );
		}

		// 8. mutable (inverse of immutable)
		if ( entity.isImmutable() ) {
			appendTextElement( entityElement, "mutable", "false" );
		}

		// 9. optimistic-locking
		if ( entity.getOptimisticLock() != null ) {
			appendTextElement( entityElement, "optimistic-locking", entity.getOptimisticLock().trim().toUpperCase() );
		}

		// 10. inheritance strategy (declared on the root of a hierarchy so subclasses share the root's table (SINGLE_TABLE) or declare their own joined
		// tables (JOINED)). MappingGenerator computes the strategy since it can see an entity's subclasses.
		if ( !isSubclass && rootInheritanceStrategy != null ) {
			Element inheritance = createEl( "inheritance" );
			inheritance.setAttribute( "strategy", rootInheritanceStrategy );
			entityElement.appendChild( inheritance );
		}

		// 11. discriminator-value + discriminator-column
		if ( isDiscriminated ) {
			appendTextElement( entityElement, "discriminator-value", entity.getDiscriminator().getAsString( Key.value ) );
		}
		if ( !isSubclass ) {
			addDiscriminatorData( entityElement, entity.getDiscriminator() );
		}

		// 11. <attributes>
		Element attributes = createEl( "attributes" );

		// ids
		if ( !isSubclass ) {
			Set<IPropertyMeta> idProperties = entity.getIdProperties();
			// Both single and composite ids are emitted as one-or-more <id> elements in the modern format.
			idProperties.forEach( prop -> attributes.appendChild( generateIdElement( prop, idProperties.size() == 1 ) ) );

			IPropertyMeta versionProperty = entity.getVersionProperty();
			if ( versionProperty != null ) {
				attributes.appendChild( generateVersionElement( versionProperty ) );
			}
		}

		// basic properties
		entity.getProperties()
		    .stream()
		    .map( this::generateBasicElement )
		    .filter( node -> node != null )
		    .forEach( attributes::appendChild );

		// associations
		entity.getAssociations()
		    .stream()
		    .map( propertyMeta -> {
			    switch ( propertyMeta.getFieldType() ) {
				    case ONE_TO_ONE :
				    case MANY_TO_ONE :
					    return generateToOneAssociation( propertyMeta );
				    case ONE_TO_MANY :
				    case MANY_TO_MANY :
					    return generateToManyAssociation( propertyMeta );
				    case COLLECTION :
					    return generateElementCollection( propertyMeta );
				    default :
					    logger.warn( "Unhandled association/field type: {} on property {}", propertyMeta.getFieldType(), propertyMeta.getName() );
					    return null;
			    }
		    } )
		    .filter( node -> node != null )
		    .forEach( attributes::appendChild );

		entityElement.appendChild( attributes );
		return entityElement;
	}

	/**
	 * Append a {@code 
	 * 
	<table>
	 * } element (with schema/catalog) for the entity, if it has a table name.
	 */
	private void appendTableElement( Element entityElement ) {
		String tableName = entity.getTableName();
		if ( tableName == null ) {
			return;
		}
		Element tableEl = createEl( "table" );
		tableEl.setAttribute( "name", escapeReservedWords( tableName ) );
		if ( entity.getSchema() != null ) {
			tableEl.setAttribute( "schema", entity.getSchema() );
		}
		if ( entity.getCatalog() != null ) {
			tableEl.setAttribute( "catalog", entity.getCatalog() );
		}
		entityElement.appendChild( tableEl );
	}

	/**
	 * Generate an {@code <id>} element for the given (id or composite-key) property.
	 *
	 * @param prop           Property metadata.
	 * @param allowGenerator Whether an id generator may be emitted (false for composite-key parts, which do not support generators).
	 */
	public Element generateIdElement( IPropertyMeta prop, boolean allowGenerator ) {
		Element	theNode		= createEl( "id" );
		IStruct	columnInfo	= prop.getColumn();

		theNode.setAttribute( "name", prop.getName() );

		// <column>
		appendColumns( theNode, columnInfo );

		// generator -> <generated-value> + <generic-generator>
		if ( allowGenerator && !prop.getGenerator().isEmpty() ) {
			appendGenerator( theNode, prop );
		} else if ( !allowGenerator && !prop.getGenerator().isEmpty() ) {
			logger.error( "Composite ID elements do not support generators. Ignoring generator for property [{}] on entity [{}].",
			    prop.getName(), entity.getEntityName() );
		}

		// <unsaved-value>
		if ( prop.getUnsavedValue() != null ) {
			appendTextElement( theNode, "unsaved-value", prop.getUnsavedValue() );
		}

		// Java type (basic-type-group) MUST be last per the id XSD sequence.
		appendBasicType( theNode, prop, false );

		return theNode;
	}

	/**
	 * Append the id-generator declaration.
	 * <p>
	 * IMPORTANT: BoxLang entities are dynamic (MAP) models with a {@code null} backing class. Hibernate 7's JPA-centric id-generator resolver
	 * ({@code IdGeneratorResolverSecondPass}) dereferences the entity class name while resolving a {@code <generated-value>}, which NPEs for a class-less
	 * entity — <em>except</em> for the special {@code generator="increment"} fast path, and except for standalone generator annotations (such as
	 * {@code <uuid-generator/>}) that do not rely on {@code @GeneratedValue}. We therefore map the legacy Hibernate generator strategies our models use
	 * (uuid, increment) onto those two NPE-safe forms.
	 */
	private void appendGenerator( Element idNode, IPropertyMeta prop ) {
		IStruct	generatorInfo	= prop.getGenerator();
		String	strategy		= generatorInfo.getAsString( Key._CLASS );
		if ( strategy == null ) {
			return;
		}
		String normalizedStrategy = strategy.trim().toLowerCase();

		switch ( normalizedStrategy ) {
			case "assigned" -> {
				// Application-assigned identifier: no @GeneratedValue in JPA (the id is set by the application).
			}
			case "uuid", "uuid2", "guid" -> {
				if ( this.ormConfig.entityFacades ) {
					// Facade (POJO) mode: the entity is a real class with a concrete String id, so use the legacy string UUID
					// generator (Hibernate's `uuid` = UUIDHexGenerator, a 32-char hex String). Emitting @UuidGenerator here
					// (`<uuid-generator/>`) instead forces a java.util.UUID identifier JdbcType, which round-trips through HQL but
					// makes a primary-key load (session.get / byId) bind the id as a UUID and miss the VARCHAR id column. The
					// real facade class lets Hibernate's id-generator resolver dereference the generic-generator strategy.
					String	generatorName	= entity.getEntityName() + "_" + prop.getName() + "_generator";
					Element	generatedValue	= createEl( "generated-value" );
					generatedValue.setAttribute( "generator", generatorName );
					idNode.appendChild( generatedValue );

					Element genericGenerator = createEl( "generic-generator" );
					genericGenerator.setAttribute( "name", generatorName );
					genericGenerator.setAttribute( "class", "uuid" );
					idNode.appendChild( genericGenerator );
				} else {
					// MAP (class-less) mode: a standalone @UuidGenerator (does not rely on @GeneratedValue), which is NPE-safe
					// for a dynamic entity that has no reflective id member.
					idNode.appendChild( createEl( "uuid-generator" ) );
				}
			}
			case "increment" -> {
				// The one legacy strategy with an NPE-safe fast path in IdGeneratorResolverSecondPass (matched by the literal generator name).
				Element generatedValue = createEl( "generated-value" );
				generatedValue.setAttribute( "generator", "increment" );
				idNode.appendChild( generatedValue );
			}
			case "identity", "native" -> {
				Element generatedValue = createEl( "generated-value" );
				generatedValue.setAttribute( "strategy", "IDENTITY" );
				idNode.appendChild( generatedValue );
			}
			default -> {
				// Other legacy generators (sequence, foreign, seqhilo, custom classes, and any carrying <param>s) cannot currently be expressed for a
				// dynamic (class-less) entity in the modern format without tripping Hibernate's class-name-dependent id-generator resolver. None are used
				// by the current entity models. Emit the generic-generator form (correct for class-backed entities) and warn.
				// TODO: Revisit if/when Hibernate makes the modern id-generator resolver dynamic-entity aware, or add per-strategy NPE-safe handling.
				logger.warn(
				    "ORM mapping.xml writer: id generator strategy [{}] on property [{}] of entity [{}] may not resolve for a dynamic (class-less) entity in the modern mapping format.",
				    strategy, prop.getName(), entity.getEntityName() );
				String	generatorName	= entity.getEntityName() + "_" + prop.getName() + "_generator";
				Element	generatedValue	= createEl( "generated-value" );
				generatedValue.setAttribute( "generator", generatorName );
				idNode.appendChild( generatedValue );

				Element genericGenerator = createEl( "generic-generator" );
				genericGenerator.setAttribute( "name", generatorName );
				genericGenerator.setAttribute( "class", strategy );

				IStruct params = new ortus.boxlang.runtime.types.Struct();
				if ( generatorInfo.containsKey( ORMKeys.property ) ) {
					params.put( "property", generatorInfo.getAsString( ORMKeys.property ) );
				}
				if ( generatorInfo.containsKey( ORMKeys.selectKey ) ) {
					params.put( "key", generatorInfo.getAsString( ORMKeys.selectKey ) );
				}
				if ( generatorInfo.containsKey( ORMKeys.generated ) ) {
					params.put( "generated", generatorInfo.getAsString( ORMKeys.generated ) );
				}
				if ( generatorInfo.containsKey( ORMKeys.sequence ) ) {
					params.put( "sequence", generatorInfo.getAsString( ORMKeys.sequence ) );
				}
				if ( generatorInfo.containsKey( Key.params ) ) {
					params.putAll( generatorInfo.getAsStruct( Key.params ) );
				}
				params.forEach( ( key, value ) -> {
					Element paramEl = createEl( "parameter" );
					paramEl.setAttribute( "name", key.getName() );
					paramEl.setAttribute( "value", value.toString() );
					genericGenerator.appendChild( paramEl );
				} );
				idNode.appendChild( genericGenerator );
			}
		}
	}

	/**
	 * Generate a {@code <version>} element for the given property.
	 */
	public Element generateVersionElement( IPropertyMeta prop ) {
		Element	theNode		= createEl( "version" );
		IStruct	columnInfo	= prop.getColumn();
		theNode.setAttribute( "name", prop.getName() );
		appendColumns( theNode, columnInfo );
		// NOTE: the modern <version> element only accepts <column> + <temporal>; a non-temporal/non-int version type cannot be expressed here.
		return theNode;
	}

	/**
	 * Generate a JPA {@code <element-collection>} for a value/element collection ({@code fieldtype="collection"}): a
	 * collection of scalar values held in a separate collection table and joined back to the owning entity. An array maps
	 * to a {@code BAG}; a struct maps to a {@code MAP} with a key column. The element value is declared as a plain
	 * {@code <column>} plus an explicit {@code <java-type>} (never a converter - the modern element-collection schema makes
	 * {@code <column>} and {@code <convert>} mutually exclusive), which pairs with the facade's {@code List<Object>}
	 * accessor whose element-type check short-circuits on {@code Object}.
	 *
	 * @param prop The value-collection property.
	 *
	 * @return The {@code <element-collection>} element.
	 */
	private Element generateElementCollection( IPropertyMeta prop ) {
		IStruct	assoc	= prop.getAssociation();
		Element	node	= createEl( "element-collection" );
		node.setAttribute( "name", prop.getName() );

		String	collectionType	= assoc.getAsString( ORMKeys.collectionType );
		boolean	isMap			= "map".equalsIgnoreCase( collectionType );
		node.setAttribute( "classification", isMap ? "MAP" : "BAG" );

		// 1. collection-structure-group (must precede the value): map key class + column, or an order-by for a list/bag.
		// The map key uses <map-key-class> (a plain Java class) + <map-key-column>; the value uses <java-type> (a Hibernate
		// JavaType descriptor) below - the two deliberately take different forms per the modern schema.
		if ( isMap ) {
			Element mapKeyClass = createEl( "map-key-class" );
			mapKeyClass.setAttribute( "class", elementJavaClass( assoc.getAsString( ORMKeys.structKeyType ) ) );
			node.appendChild( mapKeyClass );
			String keyColumn = assoc.getAsString( ORMKeys.structKeyColumn );
			if ( keyColumn != null ) {
				Element mapKeyColumn = createEl( "map-key-column" );
				mapKeyColumn.setAttribute( "name", escapeReservedWords( keyColumn ) );
				node.appendChild( mapKeyColumn );
			}
		} else if ( assoc.containsKey( ORMKeys.orderBy ) && assoc.getAsString( ORMKeys.orderBy ) != null ) {
			Element orderBy = createEl( "order-by" );
			orderBy.setTextContent( assoc.getAsString( ORMKeys.orderBy ) );
			node.appendChild( orderBy );
		}

		// 2. element value: <column> + explicit <java-type> (basic-type-group).
		String	elementColumn	= assoc.containsKey( ORMKeys.elementColumn ) && assoc.getAsString( ORMKeys.elementColumn ) != null
		    ? assoc.getAsString( ORMKeys.elementColumn )
		    : prop.getName();
		Element	column			= createEl( "column" );
		column.setAttribute( "name", escapeReservedWords( elementColumn ) );
		node.appendChild( column );
		Element javaType = createEl( "java-type" );
		javaType.setTextContent( elementJavaType( assoc.getAsString( ORMKeys.elementType ) ) );
		node.appendChild( javaType );

		// 3. collection-table + join-column back to the owner.
		Element	collectionTable	= createEl( "collection-table" );
		String	tableName		= assoc.getAsString( Key.table );
		if ( tableName != null ) {
			collectionTable.setAttribute( "name", escapeReservedWords( tableName ) );
		}
		String joinColumn = assoc.getAsString( Key.column );
		if ( joinColumn != null ) {
			Element joinColumnEl = createEl( "join-column" );
			joinColumnEl.setAttribute( "name", escapeReservedWords( joinColumn ) );
			collectionTable.appendChild( joinColumnEl );
		}
		node.appendChild( collectionTable );
		return node;
	}

	/**
	 * Map a scalar ORM type to the Hibernate {@code JavaType} descriptor class used to declare an element-collection
	 * element (or map key) type via {@code <java-type>}. Hibernate's {@code <java-type>} expects a {@code BasicJavaType}
	 * descriptor FQN (e.g. {@code StringJavaType}), NOT a plain Java class (e.g. {@code java.lang.String}) - passing the
	 * latter throws {@code ClassCastException} during metadata resolution.
	 *
	 * @param ormType The scalar ORM type (may be {@code null}, treated as string).
	 *
	 * @return The fully-qualified Hibernate {@code JavaType} descriptor class name for that scalar.
	 */
	private static String elementJavaType( String ormType ) {
		String	type	= ormType == null ? "string" : ormType.trim().toLowerCase();
		String	simple	= switch ( type ) {
							case "int", "integer" -> "IntegerJavaType";
							case "long", "biginteger", "big_integer", "bigint" -> "LongJavaType";
							case "short", "tinyint", "tinyinteger" -> "ShortJavaType";
							case "float" -> "FloatJavaType";
							case "double", "numeric", "number", "decimal" -> "DoubleJavaType";
							case "bigdecimal", "big_decimal" -> "BigDecimalJavaType";
							case "boolean", "bit", "bool", "yesno", "truefalse" -> "BooleanJavaType";
							case "timestamp", "datetime", "date", "eurodate", "usdate" -> "InstantJavaType";
							default -> "StringJavaType";
						};
		return "org.hibernate.type.descriptor.java." + simple;
	}

	/**
	 * Map a scalar ORM type to its plain fully-qualified Java class, used for a map key via {@code <map-key-class>} (which,
	 * unlike the value's {@code <java-type>}, takes a real Java class rather than a Hibernate {@code JavaType} descriptor).
	 *
	 * @param ormType The scalar ORM type (may be {@code null}, treated as string).
	 *
	 * @return The fully-qualified Java class name for that scalar.
	 */
	private static String elementJavaClass( String ormType ) {
		String type = ormType == null ? "string" : ormType.trim().toLowerCase();
		return switch ( type ) {
			case "int", "integer" -> "java.lang.Integer";
			case "long", "biginteger", "big_integer", "bigint" -> "java.lang.Long";
			case "short", "tinyint", "tinyinteger" -> "java.lang.Short";
			case "float" -> "java.lang.Float";
			case "double", "numeric", "number", "decimal" -> "java.lang.Double";
			case "bigdecimal", "big_decimal" -> "java.math.BigDecimal";
			case "boolean", "bit", "bool", "yesno", "truefalse" -> "java.lang.Boolean";
			case "timestamp", "datetime", "date", "eurodate", "usdate" -> "java.time.Instant";
			default -> "java.lang.String";
		};
	}

	/**
	 * Generate a {@code <basic>} element for the given (normal) property.
	 */
	public Element generateBasicElement( IPropertyMeta prop ) {
		boolean	isBinary	= prop.getFormula() == null && "binary".equals( toHibernateType( prop.getORMType() ) );

		IStruct	columnInfo	= prop.getColumn();

		if ( isBinary ) {
			// byte[] ("binary") has no AttributeConverter and no <target> simple-type interpretation. In facade (POJO) mode the
			// entity is a real class with a concrete byte[] accessor (see SessionFactoryBuilder), so emit a plain <basic> whose
			// SQL type Hibernate infers (VARBINARY/BLOB) from that accessor - no <convert> and no <java-type> override, both of
			// which would force it back to Object/JAVA_OBJECT. In MAP mode the entity is class-less, byte[] cannot be expressed
			// (the "[B" ClassDetails is not registered), so it is skipped with a warning.
			if ( !this.ormConfig.entityFacades ) {
				logger.warn(
				    "ORM mapping.xml writer: binary (byte[]) property [{}] on entity [{}] cannot be mapped for a class-less (MAP) entity in the modern format and was skipped.",
				    prop.getName(), entity.getEntityName() );
				return null;
			}
			Element binaryNode = createEl( "basic" );
			binaryNode.setAttribute( "name", prop.getName() );
			if ( !prop.isOptimisticLock() ) {
				binaryNode.setAttribute( "optimistic-lock", "false" );
			}
			appendColumns( binaryNode, columnInfo );
			return binaryNode;
		}

		Element theNode = createEl( "basic" );

		theNode.setAttribute( "name", prop.getName() );
		if ( !prop.isOptimisticLock() ) {
			theNode.setAttribute( "optimistic-lock", "false" );
		}

		// <column> or <formula>
		if ( prop.getFormula() != null ) {
			Element formulaEl = createEl( "formula" );
			formulaEl.setTextContent( "( " + prop.getFormula() + " )" );
			theNode.appendChild( formulaEl );
		} else {
			appendColumns( theNode, columnInfo );
		}

		// A `text`/`clob` property maps to a large-object (CLOB) column - i.e. a `TEXT`/`LONGTEXT`, stored off-row - not an
		// in-row `varchar(length)`. Emitting `<lob/>` (JPA @Lob) is both semantically correct and avoids blowing a database's
		// maximum row size: e.g. two `text length="8000"` columns emitted as `varchar(8000)` push a table past MySQL's 65535
		// byte row limit and CREATE TABLE fails (MariaDB tolerates it, so this only surfaced on MySQL). We also drop the
		// column `length`, because an explicit length makes Hibernate emit `varchar(length)` even for a LOB; without it the
		// LOB type resolves to `TEXT`/`LONGTEXT`. `<lob/>` precedes the basic-type group in the schema.
		if ( prop.getFormula() == null && "text".equals( toHibernateType( prop.getORMType() ) ) ) {
			org.w3c.dom.NodeList columns = theNode.getElementsByTagName( "column" );
			for ( int i = 0; i < columns.getLength(); i++ ) {
				( ( Element ) columns.item( i ) ).removeAttribute( "length" );
			}
			theNode.appendChild( createEl( "lob" ) );
		}

		// converter OR type (basic-type-group)
		appendBasicType( theNode, prop, true );

		return theNode;
	}

	/**
	 * Append the Java-type declaration for a basic/id attribute: either a {@code <convert converter="FQCN"/>} (for our JPA AttributeConverters) or a
	 * {@code <type value="hibernateType"/>} element (mirroring the legacy {@code type="..."} attribute).
	 *
	 * @param theNode      The {@code <basic>} or {@code <id>} element.
	 * @param prop         Property metadata.
	 * @param allowConvert Whether a {@code <convert>} is permitted (true for basic; ids in our models are never converted).
	 */
	private void appendBasicType( Element theNode, IPropertyMeta prop, boolean allowConvert ) {
		String	normalizedType	= toHibernateType( prop.getORMType() );
		String	converterFQCN	= allowConvert ? converterFor( normalizedType ) : null;
		if ( converterFQCN != null ) {
			Element convertEl = createEl( "convert" );
			convertEl.setAttribute( "converter", converterFQCN );
			theNode.appendChild( convertEl );
			// Our converters are all AttributeConverter<Object, X>, so the dynamic-model attribute's Java type is Object. Hibernate requires an explicit
			// Java type for dynamic (MAP) attributes; the converter alone is not enough.
			Element javaType = createEl( "java-type" );
			javaType.setTextContent( "org.hibernate.type.descriptor.java.ObjectJavaType" );
			theNode.appendChild( javaType );
			return;
		}
		// Non-converted attribute of a dynamic (MAP) model: declare the Java type via <target> (documented for dynamic models).
		String targetType = simpleTargetFor( normalizedType );
		if ( targetType == null ) {
			logger.warn( "Unmapped ORM type [{}] on property [{}] of entity [{}]; defaulting <target> to String.",
			    normalizedType, prop.getName(), entity.getEntityName() );
			targetType = "String";
		}
		Element target = createEl( "target" );
		target.setTextContent( targetType );
		theNode.appendChild( target );
		// "text" is a large character type; force a long-varchar JDBC type so the dialect emits TEXT/CLOB rather than VARCHAR(255).
		if ( "text".equals( normalizedType ) ) {
			appendTextElement( theNode, "jdbc-type-code", "-1" );
		}
	}

	/**
	 * Map a normalized Hibernate type name to a {@code <target>} value understood by Hibernate's {@code SimpleTypeInterpretation} (a simple Java class
	 * name), or null if there is no simple mapping.
	 */
	private String simpleTargetFor( String normalizedType ) {
		return switch ( normalizedType ) {
			case "string", "text" -> "String";
			case "character" -> "Character";
			case "integer" -> "Integer";
			case "long" -> "Long";
			case "short" -> "Short";
			case "double" -> "Double";
			case "float" -> "Float";
			case "biginteger" -> "BigInteger";
			case "bigdecimal" -> "BigDecimal";
			case "boolean" -> "Boolean";
			case "timestamp" -> "java.sql.Timestamp";
			// byte[] is not a SimpleTypeInterpretation target, but it is Serializable; Hibernate then stores it as raw bytes (VARBINARY).
			case "binary" -> "Serializable";
			default -> null;
		};
	}

	/**
	 * Generate a {@code <caching>} element for the given cache metadata.
	 */
	public Element generateCachingElement( IStruct cache ) {
		Element	theNode	= createEl( "caching" );
		String	usage	= cache.getAsString( ORMKeys.strategy );
		if ( usage != null && !usage.isBlank() ) {
			theNode.setAttribute( "access", usage.trim().toUpperCase().replace( '-', '_' ) );
		}
		if ( cache.containsKey( Key.region ) && cache.getAsString( Key.region ) != null ) {
			theNode.setAttribute( "region", cache.getAsString( Key.region ) );
		}
		if ( cache.containsKey( ORMKeys.include ) && cache.getAsString( ORMKeys.include ) != null ) {
			theNode.setAttribute( "includeLazy", Boolean.toString( "all".equalsIgnoreCase( cache.getAsString( ORMKeys.include ) ) ) );
		}
		return theNode;
	}

	/**
	 * Add discriminator column/formula metadata to a root entity element.
	 */
	public void addDiscriminatorData( Element entityEl, IStruct data ) {
		if ( data.isEmpty() || !data.containsKey( Key._name ) && !data.containsKey( ORMKeys.formula ) ) {
			return;
		}
		if ( data.containsKey( ORMKeys.formula ) && data.get( ORMKeys.formula ) != null ) {
			Element	formulaEl	= createEl( "discriminator-formula" );
			Element	fragment	= createEl( "fragment" );
			fragment.setTextContent( ( String ) data.get( ORMKeys.formula ) );
			formulaEl.appendChild( fragment );
			if ( data.containsKey( Key.type ) && data.get( Key.type ) != null ) {
				formulaEl.setAttribute( "discriminator-type", data.getAsString( Key.type ).trim().toUpperCase() );
			}
			if ( data.containsKey( Key.force ) && data.get( Key.force ) != null ) {
				formulaEl.setAttribute( "force-selection", data.get( Key.force ).toString() );
			}
			entityEl.appendChild( formulaEl );
			return;
		}
		if ( data.containsKey( Key._name ) ) {
			Element theNode = createEl( "discriminator-column" );
			theNode.setAttribute( "name", escapeReservedWords( data.getAsString( Key._name ) ) );
			if ( data.containsKey( Key.type ) && data.get( Key.type ) != null ) {
				theNode.setAttribute( "discriminator-type", data.getAsString( Key.type ).trim().toUpperCase() );
			}
			if ( data.containsKey( Key.force ) && data.get( Key.force ) != null ) {
				theNode.setAttribute( "force-selection", data.get( Key.force ).toString() );
			}
			entityEl.appendChild( theNode );
		}
	}

	/**
	 * Generate a {@code <many-to-one>} or {@code <one-to-one>} association element.
	 */
	public Element generateToOneAssociation( IPropertyMeta prop ) {
		IStruct	association	= prop.getAssociation();
		String	type		= association.getAsString( Key.type );
		Element	theNode		= createEl( type );

		theNode.setAttribute( "name", prop.getName() );

		// target-entity holds the (dynamic) entity name.
		String targetEntity = associationTargetEntity( resolveEntityName( association.getAsString( Key._CLASS ), prop ) );
		if ( targetEntity != null ) {
			theNode.setAttribute( "target-entity", targetEntity );
		}

		// fetch: a lazy proxy -> LAZY; explicit lazy="false" -> EAGER.
		String lazy = association.containsKey( ORMKeys.lazy ) ? association.getAsString( ORMKeys.lazy ) : prop.getLazy();
		if ( lazy != null ) {
			theNode.setAttribute( "fetch", lazy.equalsIgnoreCase( "false" ) ? "EAGER" : "LAZY" );
		}

		// mappedBy (one-to-one inverse side)
		if ( association.containsKey( ORMKeys.mappedBy ) && association.getAsString( ORMKeys.mappedBy ) != null ) {
			theNode.setAttribute( "mapped-by", facadeAttributeName( association.getAsString( ORMKeys.mappedBy ) ) );
		}

		// optional (inverse of not-null); one-to-one supports optional too.
		if ( association.containsKey( ORMKeys.nullable ) ) {
			theNode.setAttribute( "optional", trueFalse( association.getAsBoolean( ORMKeys.nullable ) ) );
		}

		// not-found (missing row ignored)
		if ( association.containsKey( ORMKeys.missingRowIgnored ) && !type.equals( "one-to-one" ) ) {
			Object mri = association.get( ORMKeys.missingRowIgnored );
			if ( mri != null && "ignore".equalsIgnoreCase( mri.toString() ) ) {
				theNode.setAttribute( "not-found", "IGNORE" );
			}
		}

		// join-formula / join-column(s)
		if ( prop.getFormula() != null ) {
			Element joinFormula = createEl( "join-formula" );
			joinFormula.setTextContent( prop.getFormula() );
			theNode.appendChild( joinFormula );
		} else if ( association.containsKey( Key.column ) && association.getAsString( Key.column ) != null ) {
			for ( String col : association.getAsString( Key.column ).split( "," ) ) {
				theNode.appendChild( buildJoinColumn( col.trim(), association ) );
			}
		}

		// cascade
		Element cascade = buildCascadeElement( association );
		if ( cascade != null ) {
			theNode.appendChild( cascade );
		}

		return theNode;
	}

	/**
	 * Build a single {@code <join-column>} element, applying nullable/insertable/updatable and secondary-table tagging.
	 */
	private Element buildJoinColumn( String columnName, IStruct association ) {
		Element joinColumn = createEl( "join-column" );
		joinColumn.setAttribute( "name", escapeReservedWords( columnName ) );
		if ( association.containsKey( ORMKeys.nullable ) ) {
			joinColumn.setAttribute( "nullable", trueFalse( association.getAsBoolean( ORMKeys.nullable ) ) );
		}
		if ( association.containsKey( ORMKeys.insertable ) ) {
			joinColumn.setAttribute( "insertable", trueFalse( association.getAsBoolean( ORMKeys.insertable ) ) );
		}
		if ( association.containsKey( ORMKeys.updateable ) ) {
			joinColumn.setAttribute( "updatable", trueFalse( association.getAsBoolean( ORMKeys.updateable ) ) );
		}
		if ( this.secondaryTableName != null ) {
			joinColumn.setAttribute( "table", escapeReservedWords( this.secondaryTableName ) );
		}
		return joinColumn;
	}

	/**
	 * Generate a {@code <one-to-many>} or {@code <many-to-many>} association element.
	 */
	public Element generateToManyAssociation( IPropertyMeta prop ) {
		IStruct	association	= prop.getAssociation();
		String	type		= association.getAsString( Key.type );
		Element	theNode		= createEl( type );

		theNode.setAttribute( "name", prop.getName() );

		String targetEntity = associationTargetEntity( resolveEntityName( association.getAsString( Key._CLASS ), prop ) );
		if ( targetEntity != null ) {
			theNode.setAttribute( "target-entity", targetEntity );
		}

		// classification (bag/set/list/map)
		String collectionType = association.getAsString( ORMKeys.collectionType );
		if ( collectionType != null ) {
			theNode.setAttribute( "classification", collectionType.trim().toUpperCase() );
		}

		// fetch
		String lazy = association.containsKey( ORMKeys.lazy ) ? association.getAsString( ORMKeys.lazy ) : null;
		if ( lazy != null ) {
			theNode.setAttribute( "fetch", lazy.equalsIgnoreCase( "false" ) ? "EAGER" : "LAZY" );
		}

		// orphan removal (also emit cascade-remove for delete-orphan cascade)
		boolean	orphanRemoval	= false;
		String	cascadeStr		= association.containsKey( ORMKeys.cascade ) ? association.getAsString( ORMKeys.cascade ) : null;
		if ( cascadeStr != null && cascadeStr.toLowerCase().contains( "orphan" ) ) {
			orphanRemoval = true;
		}
		if ( orphanRemoval ) {
			theNode.setAttribute( "orphan-removal", "true" );
		}

		boolean	isInverse		= association.containsKey( ORMKeys.inverse ) && association.getAsBoolean( ORMKeys.inverse );
		boolean	isManyToMany	= type.equals( "many-to-many" );

		// order-by (child element, must come before join structures)
		if ( association.containsKey( ORMKeys.orderBy ) && association.getAsString( ORMKeys.orderBy ) != null ) {
			appendTextElement( theNode, "order-by", association.getAsString( ORMKeys.orderBy ) );
		}

		// batch-size (child element)
		if ( association.containsKey( ORMKeys.batchsize ) && association.getAsString( ORMKeys.batchsize ) != null ) {
			appendTextElement( theNode, "batch-size", association.getAsString( ORMKeys.batchsize ) );
		}

		String mappedBy = association.containsKey( ORMKeys.mappedBy ) ? association.getAsString( ORMKeys.mappedBy ) : null;
		if ( mappedBy == null && isInverse ) {
			mappedBy = resolveMappedBy( prop, isManyToMany );
		}

		if ( mappedBy != null ) {
			theNode.setAttribute( "mapped-by", facadeAttributeName( mappedBy ) );
		} else if ( isManyToMany ) {
			// Owning many-to-many: <join-table> with join-column (key) + inverse-join-column.
			Element joinTable = createEl( "join-table" );
			if ( association.containsKey( ORMKeys.table ) && association.getAsString( ORMKeys.table ) != null ) {
				joinTable.setAttribute( "name", escapeReservedWords( association.getAsString( ORMKeys.table ) ) );
			}
			if ( association.containsKey( Key.column ) && association.getAsString( Key.column ) != null ) {
				for ( String col : association.getAsString( Key.column ).split( "," ) ) {
					Element jc = createEl( "join-column" );
					jc.setAttribute( "name", escapeReservedWords( col.trim() ) );
					joinTable.appendChild( jc );
				}
			}
			if ( association.containsKey( ORMKeys.inverseJoinColumn ) && association.getAsString( ORMKeys.inverseJoinColumn ) != null ) {
				for ( String col : association.getAsString( ORMKeys.inverseJoinColumn ).split( "," ) ) {
					Element ijc = createEl( "inverse-join-column" );
					ijc.setAttribute( "name", escapeReservedWords( col.trim() ) );
					joinTable.appendChild( ijc );
				}
			}
			theNode.appendChild( joinTable );
		} else if ( association.containsKey( Key.column ) && association.getAsString( Key.column ) != null ) {
			// Owning one-to-many: FK join-column(s).
			for ( String col : association.getAsString( Key.column ).split( "," ) ) {
				Element jc = createEl( "join-column" );
				jc.setAttribute( "name", escapeReservedWords( col.trim() ) );
				theNode.appendChild( jc );
			}
		}

		// cascade
		Element cascade = buildCascadeElement( association );
		if ( cascade != null ) {
			theNode.appendChild( cascade );
		}

		return theNode;
	}

	/**
	 * Resolve the {@code mapped-by} property name for an inverse collection by finding the owning attribute on the target entity.
	 *
	 * @param prop         The inverse collection property.
	 * @param isManyToMany Whether this is a many-to-many (search the target's collections) or one-to-many (search the target's to-one associations).
	 *
	 * @return The owning property name, or null if it cannot be resolved.
	 */
	/**
	 * Translate a BoxLang property name to the persistent-attribute name Hibernate registers for the entity, for a
	 * name-based cross-reference such as a collection's {@code mapped-by}.
	 * <p>
	 * In facade (POJO) mode the entity is a real class and Hibernate discovers its attributes by JavaBean introspection of
	 * the generated {@code getFoo()}/{@code setFoo()} accessors, which decapitalizes the leading character (so a BoxLang
	 * property {@code Owner} becomes the attribute {@code owner}). A {@code mapped-by} must name that attribute, not the
	 * original BoxLang property name, or Hibernate cannot resolve the inverse side. In MAP mode the entity is class-less and
	 * the attribute name is taken from the XML verbatim, so the name is returned unchanged.
	 *
	 * @param boxLangPropertyName The BoxLang property name.
	 *
	 * @return The Hibernate attribute name to reference.
	 */
	private String facadeAttributeName( String boxLangPropertyName ) {
		if ( !this.ormConfig.entityFacades || boxLangPropertyName == null || boxLangPropertyName.isEmpty() ) {
			return boxLangPropertyName;
		}
		return Introspector.decapitalize( boxLangPropertyName );
	}

	private String resolveMappedBy( IPropertyMeta prop, boolean isManyToMany ) {
		IStruct	association	= prop.getAssociation();
		String	targetClass	= association.getAsString( Key._CLASS );
		if ( targetClass == null ) {
			return null;
		}
		Key				datasourceName		= this.entity.getDatasource().isEmpty() ? this.ormConfig.datasource : Key.of( this.entity.getDatasource() );
		EntityRecord	associatedEntity	= entityLookup.apply( targetClass, datasourceName );
		if ( associatedEntity == null ) {
			return null;
		}
		IEntityMeta associatedEntityMeta = associatedEntity.getEntityMeta();
		if ( associatedEntityMeta == null ) {
			IStruct meta = associatedEntity.getMetadata();
			meta.put( ORMKeys.classFQN, associatedEntity.getClassFQN() );
			meta.put( Key.datasource, datasourceName );
			associatedEntityMeta = AbstractEntityMeta.autoDiscoverMetaType( meta );
		}

		if ( isManyToMany ) {
			String joinTable = association.getAsString( ORMKeys.table );
			return associatedEntityMeta.getAssociations()
			    .stream()
			    .filter( remote -> remote.getFieldType() == IPropertyMeta.FIELDTYPE.MANY_TO_MANY )
			    .filter( remote -> remote != prop )
			    // The owning side (not itself inverse) with the same join table.
			    .filter( remote -> {
				    IStruct remoteAssoc	= remote.getAssociation();
				    boolean remoteInverse = remoteAssoc.containsKey( ORMKeys.inverse ) && remoteAssoc.getAsBoolean( ORMKeys.inverse );
				    return !remoteInverse && joinTable != null && joinTable.equalsIgnoreCase( remoteAssoc.getAsString( ORMKeys.table ) );
			    } )
			    .map( IPropertyMeta::getName )
			    .findFirst()
			    .orElse( null );
		}

		// one-to-many inverse: find the owning to-one on the target entity. Prefer a match on this collection's key column
		// (the FK), and when the collection declares no explicit fkcolumn (the FK is defined only on the inverse side), fall
		// back to the to-one whose class points back to THIS entity. Emitting a mapped-by (rather than nothing) keeps the
		// collection non-owning, so Hibernate does NOT synthesize a default join table for it.
		String	keyColumn		= association.getAsString( Key.column );
		String	thisEntityName	= this.entity.getEntityName();
		return associatedEntityMeta.getAssociations()
		    .stream()
		    .filter( remote -> remote.getFieldType() == IPropertyMeta.FIELDTYPE.MANY_TO_ONE || remote.getFieldType() == IPropertyMeta.FIELDTYPE.ONE_TO_ONE )
		    .filter( remote -> {
			    IStruct remoteAssoc	= remote.getAssociation();
			    String remoteColumn	= remoteAssoc.getAsString( Key.column );
			    if ( keyColumn != null && remoteColumn != null && keyColumn.equalsIgnoreCase( remoteColumn ) ) {
				    return true;
			    }
			    // No explicit fkcolumn on the inverse collection: match the target's to-one that points back to this entity.
			    if ( keyColumn == null ) {
				    String remoteClass = remoteAssoc.getAsString( Key._CLASS );
				    if ( remoteClass == null ) {
					    return false;
				    }
				    EntityRecord backReference = entityLookup.apply( remoteClass, datasourceName );
				    return backReference != null && thisEntityName.equalsIgnoreCase( backReference.getEntityName() );
			    }
			    return false;
		    } )
		    .map( IPropertyMeta::getName )
		    .findFirst()
		    .orElse( null );
	}

	/**
	 * Build a {@code <cascade>} element from the association's cascade string, or null if there is no cascade.
	 */
	private Element buildCascadeElement( IStruct association ) {
		if ( !association.containsKey( ORMKeys.cascade ) || association.getAsString( ORMKeys.cascade ) == null ) {
			return null;
		}
		String cascadeStr = association.getAsString( ORMKeys.cascade ).trim();
		if ( cascadeStr.isEmpty() ) {
			return null;
		}
		Element cascade = createEl( "cascade" );
		for ( String token : cascadeStr.split( "," ) ) {
			token = token.trim().toLowerCase();
			switch ( token ) {
				case "all" :
				case "all-delete-orphan" :
					cascade.appendChild( createEl( "cascade-all" ) );
					break;
				case "persist" :
				case "save-update" :
				case "create" :
					cascade.appendChild( createEl( "cascade-persist" ) );
					break;
				case "merge" :
					cascade.appendChild( createEl( "cascade-merge" ) );
					break;
				case "delete" :
				case "remove" :
				case "delete-orphan" :
					cascade.appendChild( createEl( "cascade-remove" ) );
					break;
				case "refresh" :
					cascade.appendChild( createEl( "cascade-refresh" ) );
					break;
				case "detach" :
				case "evict" :
					cascade.appendChild( createEl( "cascade-detach" ) );
					break;
				case "replicate" :
					cascade.appendChild( createEl( "cascade-replicate" ) );
					break;
				case "lock" :
					cascade.appendChild( createEl( "cascade-lock" ) );
					break;
				default :
					// unknown cascade token; ignore
					break;
			}
		}
		return cascade.hasChildNodes() ? cascade : null;
	}

	/**
	 * Append one or more {@code <column>} elements (comma-delimited names produce multiple) for the given column metadata.
	 */
	private void appendColumns( Element parent, IStruct columnInfo ) {
		String rawName = columnInfo.getOrDefault( Key._name, "" ).toString();
		if ( rawName.isBlank() ) {
			parent.appendChild( generateColumnElement( columnInfo, null ) );
			return;
		}
		for ( String column : rawName.split( "," ) ) {
			parent.appendChild( generateColumnElement( columnInfo, column.trim() ) );
		}
	}

	/**
	 * Generate a single {@code <column>} element.
	 *
	 * @param columnInfo Column metadata.
	 * @param columnName The specific column name to use, or null to fall back to the metadata name.
	 */
	public Element generateColumnElement( IStruct columnInfo, String columnName ) {
		Element	theNode	= createEl( "column" );

		String	name	= columnName != null ? columnName : columnInfo.getAsString( Key._name );
		if ( name != null && !name.isBlank() ) {
			theNode.setAttribute( "name", escapeReservedWords( name ) );
		}
		if ( columnInfo.containsKey( ORMKeys.nullable ) ) {
			theNode.setAttribute( "nullable", trueFalse( columnInfo.getAsBoolean( ORMKeys.nullable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.unique ) ) {
			theNode.setAttribute( "unique", trueFalse( columnInfo.getAsBoolean( ORMKeys.unique ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insertable", trueFalse( columnInfo.getAsBoolean( ORMKeys.insertable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.updateable ) ) {
			theNode.setAttribute( "updatable", trueFalse( columnInfo.getAsBoolean( ORMKeys.updateable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.length ) && columnInfo.get( ORMKeys.length ) != null ) {
			theNode.setAttribute( "length", columnInfo.get( ORMKeys.length ).toString() );
		}
		if ( columnInfo.containsKey( ORMKeys.precision ) && columnInfo.get( ORMKeys.precision ) != null ) {
			theNode.setAttribute( "precision", columnInfo.get( ORMKeys.precision ).toString() );
		}
		if ( columnInfo.containsKey( ORMKeys.scale ) && columnInfo.get( ORMKeys.scale ) != null ) {
			theNode.setAttribute( "scale", columnInfo.get( ORMKeys.scale ).toString() );
		}
		if ( columnInfo.containsKey( Key.sqltype ) && columnInfo.getAsString( Key.sqltype ) != null && !columnInfo.getAsString( Key.sqltype ).isBlank() ) {
			theNode.setAttribute( "column-definition", columnInfo.getAsString( Key.sqltype ) );
		}
		// Secondary-table tagging for join-table columns.
		if ( this.secondaryTableName != null ) {
			theNode.setAttribute( "table", escapeReservedWords( this.secondaryTableName ) );
		}
		// <default> is a child element in the modern format.
		if ( columnInfo.containsKey( Key._DEFAULT ) && columnInfo.get( Key._DEFAULT ) != null ) {
			String def = columnInfo.get( Key._DEFAULT ).toString();
			if ( !def.isBlank() ) {
				Element defaultEl = createEl( "default" );
				defaultEl.setTextContent( def );
				theNode.appendChild( defaultEl );
			}
		}
		return theNode;
	}

	/**
	 * Resolve the value emitted as an association's {@code target-entity}. In facade mode the association targets the
	 * target entity's per-application generated facade class (the real {@code @Entity}); in MAP mode it targets the
	 * (dynamic) entity name.
	 *
	 * @param resolvedEntityName The target entity's resolved entity name, or {@code null}.
	 *
	 * @return The value to emit for {@code target-entity}, or {@code null} if the input was {@code null}.
	 */
	private String associationTargetEntity( String resolvedEntityName ) {
		if ( resolvedEntityName == null || !this.ormConfig.entityFacades ) {
			return resolvedEntityName;
		}
		return ortus.boxlang.modules.orm.hibernate.facade.EntityFacadeNaming.facadeClassName( this.ormConfig.facadeNamespace, resolvedEntityName );
	}

	/**
	 * Look up an entity by class name and return its resolved entity name.
	 */
	private String resolveEntityName( String relationClassName, IPropertyMeta prop ) {
		if ( relationClassName == null || relationClassName.isBlank() ) {
			throw new BoxRuntimeException(
			    "Missing required class name for relationship '%s' on entity '%s'".formatted( prop.getName(), this.entity.getEntityName() ) );
		}
		Key				datasourceName		= this.entity.getDatasource().isEmpty() ? this.ormConfig.datasource : Key.of( this.entity.getDatasource() );
		EntityRecord	associatedEntity	= entityLookup.apply( relationClassName, datasourceName );
		if ( associatedEntity == null ) {
			String message = String.format( "Could not find entity '%s' on datasource '%s' referenced in property '%s' on entity '%s'", relationClassName,
			    datasourceName, prop.getName(), prop.getDefiningEntity().getEntityName() );
			if ( !this.ormConfig.ignoreParseErrors ) {
				throw new BoxRuntimeException( message );
			}
			logger.error( message );
			return null;
		}
		return associatedEntity.getEntityName();
	}

	/**
	 * Create a namespace-qualified element in the ORM mapping namespace.
	 */
	private Element createEl( String name ) {
		return this.document.createElementNS( ORM_NAMESPACE, name );
	}

	/**
	 * Append a simple text-content child element.
	 */
	private void appendTextElement( Element parent, String name, String value ) {
		Element el = createEl( name );
		el.setTextContent( value );
		parent.appendChild( el );
	}

	private String trueFalse( Boolean value ) {
		return Boolean.TRUE.equals( value ) ? "true" : "false";
	}

	/**
	 * Return the FQCN of the JPA AttributeConverter for the given normalized Hibernate type, or null if the type is not converted.
	 * <p>
	 * Returns the JPA {@code AttributeConverter} class name for a normalized type (no {@code converted::} prefix), or {@code null} when none applies.
	 */
	protected String converterFor( String normalizedType ) {
		return switch ( normalizedType ) {
			case "time" -> "ortus.boxlang.modules.orm.hibernate.converters.TimeConverter";
			case "boolean", "yes_no", "true_false" -> "ortus.boxlang.modules.orm.hibernate.converters.BooleanConverter";
			case "timestamp" -> "ortus.boxlang.modules.orm.hibernate.converters.DateTimeConverter";
			case "double" -> "ortus.boxlang.modules.orm.hibernate.converters.DoubleConverter";
			case "float" -> "ortus.boxlang.modules.orm.hibernate.converters.FloatConverter";
			case "short" -> "ortus.boxlang.modules.orm.hibernate.converters.ShortConverter";
			case "integer" -> "ortus.boxlang.modules.orm.hibernate.converters.IntegerConverter";
			case "biginteger" -> "ortus.boxlang.modules.orm.hibernate.converters.BigIntegerConverter";
			case "long" -> "ortus.boxlang.modules.orm.hibernate.converters.LongConverter";
			case "bigdecimal" -> "ortus.boxlang.modules.orm.hibernate.converters.BigDecimalConverter";
			case "string" -> "ortus.boxlang.modules.orm.hibernate.converters.StringConverter";
			default -> null;
		};
	}

	/**
	 * Escape SQL reserved words in a table or column name.
	 *
	 * @param value The table or column name to escape.
	 *
	 * @return The value quoted in backticks if it is a reserved word, otherwise unchanged.
	 */
	public static String escapeReservedWords( String value ) {
		if ( value == null || value.isBlank() ) {
			return value;
		}
		if ( RESERVED_WORDS.contains( value.toLowerCase() ) ) {
			return "`" + value + "`";
		}
		return value;
	}

	/**
	 * Caster to convert a property {@code ormType} field value to a Hibernate type.
	 *
	 * @param propertyType Property type, like {@code datetime} or {@code string}.
	 *
	 * @return The Hibernate-safe type, like {@code timestamp} or {@code string}.
	 */
	public static String toHibernateType( String propertyType ) {
		// basic normalization
		propertyType	= propertyType.trim().toLowerCase();
		// grab "varchar" from "varchar(50)"
		propertyType	= propertyType.replaceAll( "\\(.+\\)", "" );
		// grab "biginteger" from "java.math.biginteger", etc.
		propertyType	= propertyType.substring( propertyType.lastIndexOf( "." ) + 1 );

		return switch ( propertyType ) {
			case "blob", "byte[]" -> "binary";
			case "bit", "bool" -> "boolean";
			case "tinyint", "tinyinteger" -> "short";
			case "yes-no", "yesno", "yes_no" -> "yes_no";
			case "true-false", "truefalse", "true_false" -> "true_false";
			case "big-decimal", "big_decimal" -> "bigdecimal";
			case "big-integer", "bigint", "big_integer" -> "biginteger";
			case "int" -> "integer";
			case "numeric", "number", "decimal" -> "double";
			case "eurodate", "usdate", "date", "datetime" -> "timestamp";
			case "char", "nchar" -> "character";
			case "varchar", "nvarchar" -> "string";
			case "clob" -> "text";
			default -> propertyType;
		};
	}
}
