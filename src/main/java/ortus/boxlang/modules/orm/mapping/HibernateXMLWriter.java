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

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.hibernate.converters.BigDecimalConverter;
import ortus.boxlang.modules.orm.hibernate.converters.BigIntegerConverter;
import ortus.boxlang.modules.orm.hibernate.converters.BooleanConverter;
import ortus.boxlang.modules.orm.hibernate.converters.DateTimeConverter;
import ortus.boxlang.modules.orm.hibernate.converters.DoubleConverter;
import ortus.boxlang.modules.orm.hibernate.converters.IntegerConverter;
import ortus.boxlang.modules.orm.hibernate.converters.TimeConverter;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Generate a Hibernate XML mapping document for a given IEntityMeta instance, which represents the parsed entity metadata (whether classic or modern
 * syntax) in a normalized form.
 */
public class HibernateXMLWriter {

	/**
	 * Runtime
	 */
	private static final BoxRuntime			runtime			= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	private BoxLangLogger					logger;

	/**
	 * IEntityMeta instance which represents the parsed entity metadata in a normalized form.
	 * <p>
	 * The source of this metadata could be CFML persistent
	 * annotations like `persistent=true` and `fieldtype="id"` OR modern BoxLang-syntax, JPA-style annotations like `@Entity` and `@Id`.
	 */
	IEntityMeta								entity;

	/**
	 * XML Document root, created by the constructor.
	 * <p>
	 * This is the root element of the Hibernate mapping document, and will be returned by {@link #generateXML()}.
	 */
	Document								document;

	/**
	 * A function that takes a class name and returns an EntityRecord instance.
	 * <p>
	 * This is used to look up the metadata for associated entities when generating association elements.
	 */
	BiFunction<String, Key, EntityRecord>	entityLookup;

	/**
	 * ORM configuration settings.
	 */
	ORMConfig								ormConfig;

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
	    "wlm", "work", "write", "writetext", "xor", "year", "year_month", "zerofill", "zone" );

	/**
	 * Create a new Hibernate XML writer for the given entity metadata, using the provided entity lookup function to find associated entities.
	 *
	 * @param entity       The entity metadata to generate XML for.
	 * @param entityLookup A function that takes 1) an entity name, and 2) a datasource name, and returns an EntityRecord instance matching this combo
	 *                     (or null.)
	 * @param ormConfig    Whether to throw an exception when an error occurs during XML generation.
	 */
	public HibernateXMLWriter( IEntityMeta entity, BiFunction<String, Key, EntityRecord> entityLookup, ORMConfig ormConfig ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.entity			= entity;
		this.entityLookup	= entityLookup;
		this.ormConfig		= ormConfig;
		// Validation
		if ( !entity.isSubclass() && entity.getIdProperties().isEmpty() ) {
			logger.error( "Entity {} has no ID properties. Hibernate requires at least one.", entity.getEntityName() );
			if ( !this.ormConfig.ignoreParseErrors ) {
				throw new BoxRuntimeException( "Entity %s has no ID properties. Hibernate requires at least one.".formatted( entity.getEntityName() ) );
			}
		}

		this.document = createDocument();
	}

	/**
	 * Create a new XML document for populating with Hibernate mapping data.
	 *
	 * @return A new, empty XML document.
	 */
	public Document createDocument() {
		DocumentBuilderFactory	factory	= DocumentBuilderFactory.newInstance();
		DocumentBuilder			builder;
		try {
			builder = factory.newDocumentBuilder();

			DocumentType	doctype			= builder.getDOMImplementation().createDocumentType( "doctype", "-//Hibernate/Hibernate Mapping DTD 3.0//EN",
			    "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd" );
			Document		rootDocument	= builder.getDOMImplementation().createDocument( null, "hibernate-mapping", doctype );
			rootDocument.insertBefore( rootDocument.createComment(
			    """
			    \n~ Generated by the Ortus BoxLang ORM module for use in BoxLang web applications.
			    ~
			    ~ https://github.com/ortus-boxlang/bx-orm
			    ~ https://boxlang.io
			    ~ https://docs.jboss.org/hibernate/orm/5.0/manual/en-US/html/ch05.html
			    """
			), rootDocument.getDocumentElement() );
			return rootDocument;
		} catch ( ParserConfigurationException e ) {
			logger.error( "Error creating Hibernate XML document: {}", e.getMessage(), e );
			if ( this.ormConfig.ignoreParseErrors ) {
				return null;
			}
			throw new BoxRuntimeException( "Error creating Hibernate XML document: " + e.getMessage(), e );
		}
	}

	/**
	 * Generate the Hibernate XML mapping document, beginning with the &lt;class /&gt; element.
	 *
	 * @return The complete Hibernate XML mapping document.
	 */
	public Document generateXML() {
		// TODO: Track execution time and record in an XML comment prepended to the document.
		// comment with: source, compilation-time, datasource
		this.document.getDocumentElement().appendChild( generateClassElement() );
		return this.document;
	}

	/**
	 * Generate a &lt;property /&gt; element for the given property metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>name</li>
	 * <li>type</li>
	 * <li>column</li>
	 * <li>unsavedValue</li>
	 * <li>and many, many more to come</li>
	 * </ul>
	 *
	 * @param prop Property metadata in struct form
	 *
	 * @return A &lt;property /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generatePropertyElement( IPropertyMeta prop ) {
		IStruct	columnInfo	= prop.getColumn();

		Element	theNode		= this.document.createElement( "property" );
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", toHibernateType( prop.getORMType() ) );

		if ( prop.getFormula() != null ) {
			theNode.setAttribute( "formula", "( " + prop.getFormula() + " )" );
		} else {
			theNode.appendChild( generateColumnElement( prop ) );
		}
		if ( columnInfo.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.insertable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.updateable ) ) {
			theNode.setAttribute( "update", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.updateable ) ) );
		}
		if ( prop.getLazy() != null ) {
			theNode.setAttribute( "lazy", prop.getLazy() );
		}
		if ( !prop.isOptimisticLock() ) {
			theNode.setAttribute( "optimistic-lock", "false" );
		}

		return theNode;
	}

	/**
	 * Create a *-to-many association in these steps:
	 * 1. Create a collection element (bag, set, list, map, array, primitive-array)
	 * 2. Add attributes to the collection element
	 * 3. Add a key element
	 * 4. Add a one-to-many or many-to-many element
	 *
	 * @param prop Property meta, containing association and column metadata
	 *
	 * @return
	 */
	public Element generateToManyAssociation( IPropertyMeta prop ) {
		IStruct		association			= prop.getAssociation();
		IStruct		columnInfo			= prop.getColumn();

		// <bag>, <map>, <set>, etc.
		Element		collectionNode		= generateCollectionElement( prop, association, columnInfo );

		List<Key>	stringProperties	= List.of( ORMKeys.table, ORMKeys.schema, ORMKeys.catalog );
		populateStringAttributes( collectionNode, association, stringProperties );

		// <one-to-many> or <many-to-many>
		Element toManyNode = this.document.createElement( association.getAsString( Key.type ) );
		if ( association.containsKey( ORMKeys.orderBy ) ) {
			switch ( prop.getFieldType() ) {
				case MANY_TO_MANY -> toManyNode.setAttribute( "order-by", association.getAsString( ORMKeys.orderBy ) );
				default -> collectionNode.setAttribute( "order-by", association.getAsString( ORMKeys.orderBy ) );
			}

		}
		if ( association.containsKey( ORMKeys.inverseJoinColumn ) ) {
			// @TODO: Loop over all column values and create multiple <column> elements.
			toManyNode.setAttribute( "column", escapeReservedWords( association.getAsString( ORMKeys.inverseJoinColumn ) ) );
		}
		if ( association.containsKey( Key._CLASS ) ) {
			setEntityName( toManyNode, association.getAsString( Key._CLASS ), prop );
		} else {
			throw new BoxRuntimeException( "Missing required class name for relationship [%s] on entity".formatted( prop.getName() ) );
		}

		collectionNode.appendChild( toManyNode );
		return collectionNode;
	}

	/**
	 * Create a bag or map collection element for the given property metadata.
	 * <p>
	 * May also create &lt;map-key&gt; &lt;element&gt; xml nodes.
	 *
	 * @param prop        Property meta, containing association and column metadata
	 * @param association Association-specific metadata
	 * @param columnInfo  Column-specific metadata
	 *
	 * @return The collection node of either bag or map type.
	 */
	private Element generateCollectionElement( IPropertyMeta prop, IStruct association, IStruct columnInfo ) {
		String	type	= association.getAsString( ORMKeys.collectionType );
		// Cite: https://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/collections.html#d0e10663
		Element	theNode	= this.document.createElement( type );

		if ( type == "map" ) {
			if ( association.containsKey( ORMKeys.structKeyColumn ) ) {
				Element mapKeyNode = this.document.createElement( "map-key" );
				theNode.appendChild( mapKeyNode );
				// Note that Lucee doesn't support comma-delimited values in structKeyColumn
				mapKeyNode.setAttribute( "column", escapeReservedWords( association.getAsString( ORMKeys.structKeyColumn ) ) );
				if ( association.containsKey( ORMKeys.structKeyType ) ) {
					mapKeyNode.setAttribute( "type", association.getAsString( ORMKeys.structKeyType ) );
				}
				// NEW in BoxLang.
				if ( association.containsKey( ORMKeys.structKeyFormula ) ) {
					mapKeyNode.setAttribute( "formula", association.getAsString( ORMKeys.structKeyFormula ) );
				}
			}
		}
		if ( association.containsKey( ORMKeys.elementColumn ) ) {
			Element elementNode = this.document.createElement( "element" );
			theNode.appendChild( elementNode );
			// Note that Lucee doesn't support comma-delimited values in elementColumn
			elementNode.setAttribute( "column", escapeReservedWords( association.getAsString( ORMKeys.elementColumn ) ) );
			if ( association.containsKey( ORMKeys.elementType ) ) {
				elementNode.setAttribute( "type", association.getAsString( ORMKeys.elementType ) );
			}
			if ( association.containsKey( ORMKeys.elementFormula ) ) {
				elementNode.setAttribute( "formula", association.getAsString( ORMKeys.elementFormula ) );
			}
		}

		theNode.setAttribute( "name", prop.getName() );

		if ( association.containsKey( ORMKeys.batchsize ) ) {
			theNode.setAttribute( "batch-size", association.getAsString( ORMKeys.batchsize ) );
		}

		// non-string, non-simple attributes
		if ( association.containsKey( ORMKeys.inverse ) ) {
			theNode.setAttribute( "inverse", trueFalseFormat( association.getAsBoolean( ORMKeys.inverse ) ) );
		}
		if ( association.containsKey( ORMKeys.immutable ) ) {
			theNode.setAttribute( "mutable", trueFalseFormat( !association.getAsBoolean( ORMKeys.immutable ) ) );
		}
		if ( !prop.isOptimisticLock() ) {
			theNode.setAttribute( "optimistic-lock", "false" );
		}

		List<Key> stringProperties = List.of( ORMKeys.table, ORMKeys.schema, ORMKeys.lazy, ORMKeys.cascade, ORMKeys.where,
		    ORMKeys.fetch, ORMKeys.embedXML );
		populateStringAttributes( theNode, association, stringProperties );

		// @JoinColumn - https://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/collections.html#collections-foreignkeys
		if ( association.containsKey( Key.column ) ) {
			Element keyNode = this.document.createElement( "key" );
			// @TODO: Loop over all column values and create multiple <column> elements.
			keyNode.setAttribute( "column", escapeReservedWords( association.getAsString( Key.column ) ) );

			if ( association.containsKey( ORMKeys.mappedBy ) ) {
				keyNode.setAttribute( "property-ref", association.getAsString( ORMKeys.mappedBy ) );
			}
			theNode.appendChild( keyNode );
		}
		return theNode;
	}

	/**
	 * Generate a &lt;one-to-one/&gt; or &lt;many-to-one/&gt; association element for the given property metadata.
	 *
	 * @param prop Property metadata in struct form
	 *
	 * @return A &lt;one-to-one/&gt; or &lt;many-to-one/&gt;, element ready to add to a Hibernate mapping document
	 */
	public Element generateToOneAssociation( IPropertyMeta prop ) {
		IStruct	association	= prop.getAssociation();
		String	type		= association.getAsString( Key.type );
		Element	theNode		= this.document.createElement( type );

		if ( association.containsKey( Key._CLASS ) ) {
			setEntityName( theNode, association.getAsString( Key._CLASS ), prop );
		}

		List<Key> stringProperties = List.of( Key._NAME, ORMKeys.cascade, ORMKeys.fetch, ORMKeys.mappedBy, ORMKeys.access,
		    ORMKeys.lazy, ORMKeys.embedXML, ORMKeys.foreignKey );
		populateStringAttributes( theNode, association, stringProperties );

		if ( association.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( association.getAsBoolean( ORMKeys.insertable ) ) );
		}

		if ( association.containsKey( ORMKeys.updateable ) ) {
			theNode.setAttribute( "update", trueFalseFormat( association.getAsBoolean( ORMKeys.updateable ) ) );
		}
		if ( !type.equals( "one-to-one" ) && association.containsKey( ORMKeys.nullable ) ) {
			theNode.setAttribute( "not-null", trueFalseFormat( !association.getAsBoolean( ORMKeys.nullable ) ) );
		}
		// non-simple attributes
		if ( association.containsKey( ORMKeys.constrained ) && association.getAsBoolean( ORMKeys.constrained ) ) {
			theNode.setAttribute( "constrained", "true" );
		}
		if ( prop.getFormula() != null ) {
			theNode.setAttribute( "formula", prop.getFormula() );
		}
		if ( association.containsKey( Key.column ) ) {
			// @TODO: Loop over all column values and create multiple <column> elements.
			// Element columnNode = this.document.createElement( "column" );
			// columnNode.setAttribute( "name", escapeReservedWords( association.getAsString( Key.column ) ) );
			// theNode.appendChild( columnNode );
			theNode.setAttribute( "column", escapeReservedWords( association.getAsString( Key.column ) ) );
		}

		// for attributes specific to each association type
		switch ( type ) {
			case "one-to-one" :
				break;
			case "many-to-one" :
				if ( association.containsKey( ORMKeys.unique ) ) {
					theNode.setAttribute( "unique", trueFalseFormat( association.getAsBoolean( ORMKeys.unique ) ) );
				}
				if ( association.containsKey( ORMKeys.missingRowIgnored ) ) {
					theNode.setAttribute( "not-found", association.getAsString( ORMKeys.missingRowIgnored ) );
				}
				// @TODO: unique-key
				break;
		}
		return theNode;
	}

	/**
	 * Generate a &lt;column /&gt; element for the given column metadata.
	 * <p>
	 * A column element can be used in a property, id, key, or other element to define column metadata.
	 *
	 * @TODO: Refactor all key logic into a getPropertyColumn() method which groups and combines all the various column-specific annotations.
	 *
	 * @param prop Column metadata
	 *
	 * @return A &lt;column /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateColumnElement( IPropertyMeta prop ) {
		Element		theNode				= this.document.createElement( "column" );
		IStruct		columnInfo			= prop.getColumn();

		List<Key>	stringProperties	= List.of( Key._DEFAULT, Key.sqltype, ORMKeys.length, ORMKeys.precision, ORMKeys.scale, ORMKeys.uniqueKey );
		populateStringAttributes( theNode, columnInfo, stringProperties );

		// non-simple attributes
		if ( columnInfo.containsKey( Key._name ) ) {
			String value = columnInfo.getAsString( Key._name );
			if ( value != null && !value.isBlank() ) {
				theNode.setAttribute( "name", escapeReservedWords( value ) );
			}
		}
		if ( columnInfo.containsKey( ORMKeys.nullable ) ) {
			theNode.setAttribute( "not-null", trueFalseFormat( !columnInfo.getAsBoolean( ORMKeys.nullable ) ) );
		}
		if ( columnInfo.containsKey( ORMKeys.unique ) ) {
			theNode.setAttribute( "unique", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.unique ) ) );
		}
		// if ( prop.hasPropertyAnnotation( prop, ORMKeys.unsavedValue ) ) {
		// columnNode.setAttribute( "unsaved-value", prop.getPropertyAnnotation( prop, ORMKeys.unsavedValue ) );
		// }
		// if ( prop.hasPropertyAnnotation( prop, ORMKeys.check ) ) {
		// columnNode.setAttribute( "check", prop.getPropertyAnnotation( prop, ORMKeys.check ) );
		// }
		return theNode;
	}

	/**
	 * Generate a &lt;id /&gt; OR &lt;key-property /&gt; element for the given property metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>name</li>
	 * <li>type</li>
	 * <li>column</li>
	 * <li>unsavedValue</li>
	 * <li>access</li>
	 * <li>length</li>
	 * <li>and many, many more to come</li>
	 * </ul>
	 *
	 * @param prop Property metadata in struct form
	 *
	 * @return An id or key-property XML node ready to add to a Hibernate mapping class or composite-id element
	 */
	public Element generateIdElement( String elementName, IPropertyMeta prop ) {
		Element theNode = this.document.createElement( elementName );

		// compute defaults - move to ORMAnnotationInspector?
		// prop.getAsStruct( Key.annotations ).computeIfAbsent( ORMKeys.ORMType, ( key ) -> "string" );

		// set common attributes
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", toHibernateType( prop.getORMType() ) );
		if ( prop.getUnsavedValue() != null ) {
			theNode.setAttribute( "unsaved-value", prop.getUnsavedValue() );
		}

		theNode.appendChild( generateColumnElement( prop ) );

		if ( !prop.getGenerator().isEmpty() ) {
			theNode.appendChild( generateGeneratorElement( prop.getGenerator() ) );
		}

		return theNode;
	}

	/**
	 * Generate a &lt;discriminator&gt; element for the entity metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>@discriminator</li>
	 * <li>@discriminatorColumn</li>
	 * <li>@discriminatorType</li>
	 * </ul>
	 * <p>
	 * The resulting XML might look something like this:
	 * <code>
	* <discriminator
	column="discriminator_column"
	type="discriminator_type"
	force="true|false"
	insert="true|false"
	formula="arbitrary sql expression"
	/>
	</code>
	 *
	 * Returns nothing - document mutation is done in place
	 *
	 * @param classEl Parent &lt;class&gt; element to add the &lt;discriminator&gt; element to
	 * @param data    Discriminator metadata in struct form. If this is empty, no amendments will be made.
	 *
	 */
	public void addDiscriminatorData( Element classEl, IStruct data ) {
		if ( data.isEmpty() ) {
			return;
		}
		if ( data.containsKey( Key.value ) ) {
			classEl.setAttribute( "discriminator-value", data.getAsString( Key.value ) );
		}
		if ( data.containsKey( Key._name ) ) {
			Element theNode = this.document.createElement( "discriminator" );
			theNode.setAttribute( "column", escapeReservedWords( data.getAsString( Key._name ) ) );

			// set conditional attributes
			if ( data.containsKey( Key.type ) ) {
				theNode.setAttribute( "type", data.getAsString( Key.type ) );
			}
			if ( data.containsKey( Key.force ) ) {
				theNode.setAttribute( "force", ( String ) data.get( Key.force ) );
			}
			if ( data.containsKey( ORMKeys.insert ) ) {
				theNode.setAttribute( "insert", ( String ) data.get( ORMKeys.insert ) );
			}
			if ( data.containsKey( ORMKeys.formula ) ) {
				theNode.setAttribute( "formula", ( String ) data.get( ORMKeys.formula ) );
			}
			classEl.appendChild( theNode );
		}
	}

	/**
	 * Generate a &lt;generator/&gt; element for the given property metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>generator</li>
	 * <li>params</li>
	 * <li>sequence</li>
	 * <li>selectKey</li>
	 * <li>generated</li>
	 * </ul>
	 *
	 * @param generatorInfo Struct of fenerator metadata
	 *
	 * @return A &lt;generator /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateGeneratorElement( IStruct generatorInfo ) {
		Element theNode = this.document.createElement( "generator" );
		theNode.setAttribute( "class", generatorInfo.getAsString( Key._CLASS ) );

		IStruct params = new Struct();

		// generator=foreign
		if ( generatorInfo.containsKey( ORMKeys.property ) ) {
			params.put( "property", generatorInfo.getAsString( ORMKeys.property ) );
		}
		// generator=select
		if ( generatorInfo.containsKey( ORMKeys.selectKey ) ) {
			params.put( "key", generatorInfo.getAsString( ORMKeys.selectKey ) );
		}
		if ( generatorInfo.containsKey( ORMKeys.generated ) ) {
			params.put( "generated", generatorInfo.getAsString( ORMKeys.generated ) );
		}
		// generator=sequence|sequence-identity
		if ( generatorInfo.containsKey( ORMKeys.sequence ) ) {
			params.put( "sequence", generatorInfo.getAsString( ORMKeys.sequence ) );
		}
		if ( generatorInfo.containsKey( Key.params ) ) {
			params.putAll( generatorInfo.getAsStruct( Key.params ) );
		}
		params.forEach( ( key, value ) -> {
			Element paramEl = this.document.createElement( "param" );
			paramEl.setAttribute( "name", key.getName() );
			paramEl.setTextContent( value.toString() );
			theNode.appendChild( paramEl );
		} );
		return theNode;
	}

	/**
	 * Generate the top-level &lt;class /&gt; element containing entity mapping metadata.
	 *
	 * @return A &lt;class /&gt; element containing entity keys, properties, and other Hibernate mapping metadata.
	 */
	public Element generateClassElement() {
		Element	classElement	= !entity.isSubclass()
		    ? this.document.createElement( "class" )
		    : this.document.createElement( entity.getDiscriminator().get( Key.value ) != null ? "subclass" : "joined-subclass" );

		Element	entityElement	= classElement;

		// general class attributes:
		if ( entity.getEntityName() != null && !entity.getEntityName().isEmpty() ) {
			classElement.setAttribute( "entity-name", entity.getEntityName() );
		}
		if ( entity.isSubclass() ) {
			boolean isDiscriminated = entity.getDiscriminator().get( Key.value ) != null;

			if ( isDiscriminated ) {
				classElement.setAttribute( "discriminator-value", entity.getDiscriminator().getAsString( Key.value ) );
			}
			// @TODO: This should be refactored to the parent entity's entityRecord.getEntityMeta().getEntityName(), so we take advantage of our entity name
			// parsing / generation logic.
			IStruct	parentAnnotations	= entity.getParentMeta().getAsStruct( Key.annotations );
			String	extendsClass		= parentAnnotations.getAsString( ORMKeys.entityName );
			classElement.setAttribute( "extends", extendsClass == null ? entity.getParentMeta().getAsString( Key._name ) : extendsClass );
			// classElement.setAttribute( "name", CFC_MAPPING_PREFIX + entity.getMeta().getAsString( ORMKeys.classFQN ) );
			classElement.setAttribute( "lazy", "true" );

			entityElement = !isDiscriminated || parentAnnotations.getAsString( ORMKeys.table ).equals( entity.getTableName() ) ? classElement
			    : this.document.createElement( "join" );

			// Single-table subclases do not have a separate join element or key
			if ( !isDiscriminated || !parentAnnotations.getAsString( ORMKeys.table ).equals( entity.getTableName() ) ) {
				entityElement.setAttribute( "table", escapeReservedWords( entity.getTableName() ) );
				Element keyElement = this.document.createElement( "key" );
				keyElement.setAttribute( "column", escapeReservedWords( entity.getJoinColumn() ) );
				entityElement.appendChild( keyElement );
				if ( !classElement.equals( entityElement ) ) {
					classElement.appendChild( entityElement );
				}
			}
		}

		if ( !entity.getCache().isEmpty() && !entity.isSubclass() ) {
			classElement.appendChild( generateCacheElement( entity.getCache() ) );
		}

		if ( entity.isDynamicInsert() ) {
			classElement.setAttribute( "dynamic-insert", "true" );
		}
		if ( entity.isDynamicUpdate() ) {
			classElement.setAttribute( "dynamic-update", "true" );
		}
		if ( entity.getBatchSize() != null ) {
			classElement.setAttribute( "batch-size", StringCaster.cast( entity.getBatchSize() ) );
		}
		if ( entity.isLazy() ) {
			classElement.setAttribute( "lazy", "true" );
		}
		if ( entity.isSelectBeforeUpdate() ) {
			classElement.setAttribute( "rowid", "true" );
		}
		if ( entity.getOptimisticLock() != null ) {
			classElement.setAttribute( "optimistic-lock", entity.getOptimisticLock() );
		}

		if ( entity.isImmutable() ) {
			classElement.setAttribute( "mutable", "false" );
		}
		if ( entity.getRowID() != null ) {
			classElement.setAttribute( "rowid", entity.getRowID() );
		}
		if ( entity.getWhere() != null ) {
			classElement.setAttribute( "where", entity.getWhere() );
		}

		// And, if no discriminator or joinColumn is present:
		if ( entity.isSimpleEntity() ) {
			String tableName = entity.getTableName();
			if ( tableName != null ) {
				classElement.setAttribute( "table", escapeReservedWords( tableName ) );
			}
			if ( entity.getSchema() != null ) {
				classElement.setAttribute( "schema", entity.getSchema() );
			}
			if ( entity.getCatalog() != null ) {
				classElement.setAttribute( "catalog", entity.getCatalog() );
			}
		}

		// generate keys, aka <id> elements
		List<IPropertyMeta> idProperties = entity.getIdProperties();
		if ( idProperties.size() == 1 ) {
			entityElement.appendChild( generateIdElement( "id", idProperties.get( 0 ) ) );
		} else if ( !entity.isSubclass() && idProperties.size() > 1 ) {
			Element compositeIdNode = this.document.createElement( "composite-id" );
			idProperties.stream().forEach( ( prop ) -> {
				compositeIdNode.appendChild( generateIdElement( "key-property", prop ) );
			} );
			entityElement.appendChild( compositeIdNode );
		}

		if ( !entity.isSubclass() ) {
			addDiscriminatorData( entityElement, entity.getDiscriminator() );
		}

		// Both fieldtype=version and fieldtype=timestamp translate to a single <version> xml node.
		IPropertyMeta versionProperty = entity.getVersionProperty();
		if ( versionProperty != null ) {
			entityElement.appendChild( generateVersionElement( versionProperty ) );
		}

		// generate properties, aka <property> elements
		entity.getProperties()
		    .stream()
		    .map( ( propertyMeta ) -> generatePropertyElement( propertyMeta ) )
		    .forEach( entityElement::appendChild );

		// generate associations, aka <one-to-one>, <one-to-many>, etc.
		final Element entityElementFinal = entityElement;
		entity.getAssociations()
		    .stream()
		    .map( ( propertyMeta ) -> {
			    switch ( propertyMeta.getFieldType() ) {
				    case ONE_TO_ONE :
				    case MANY_TO_ONE :
					    return generateToOneAssociation( propertyMeta );
				    case ONE_TO_MANY :
				    case MANY_TO_MANY :
					    return generateToManyAssociation( propertyMeta );
				    default :
					    logger.warn( "Unhandled association/field type: {} on property {}", propertyMeta.getFieldType(), propertyMeta.getName() );
					    return null;
			    }
		    } )
		    .filter( node -> node != null )
		    // .forEach( classElement::appendChild );
		    .forEach( node -> {
			    // If this class is a subclass then we need to do things differently.
			    if ( entity.isSubclass() ) {
				    // Prepend the node to the <join> element which is the entityElement
				    // Insert the node before <join> (i.e., entityElement) in its parent
				    Node parent = entityElementFinal.getParentNode();
				    if ( parent == null ) {
					    // This is the use case where the inheritance has a key element and we must add ourselves after it
					    entityElementFinal.appendChild( node );
				    } else {
					    // This is the discriminator based inheritance, so we must add all relationships BEFORE the <join> element
					    parent.insertBefore( node, entityElementFinal );
				    }
			    } else {
				    classElement.appendChild( node );
			    }
		    } );

		// @TODO: generate <union-subclass> elements
		// @TODO: generate/handle optimistic lock

		return classElement;
	}

	/**
	 * Generate a &lt;cache/&gt; element for the given cache metadata.
	 * <p>
	 * Uses these annotations:
	 * <ul>
	 * <li>strategy</li>
	 * <li>region</li>
	 * </ul>
	 *
	 * @param cache Cache metadata in struct form
	 *
	 * @return A &lt;cache /&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateCacheElement( IStruct cache ) {
		Element		theNode				= this.document.createElement( "cache" );

		List<Key>	stringProperties	= List.of( ORMKeys.strategy, Key.region, ORMKeys.include );
		populateStringAttributes( theNode, cache, stringProperties );

		return theNode;
	}

	/**
	 * Generate a &lt;version/&gt; element for the given column metadata.
	 * <p>
	 * A version element defines an entity version value.
	 *
	 * @param prop Column metadata
	 *
	 * @return A &lt;version/&gt; element ready to add to a Hibernate mapping document
	 */
	public Element generateVersionElement( IPropertyMeta prop ) {
		Element	theNode		= this.document.createElement( "version" );
		IStruct	columnInfo	= prop.getColumn();

		// PROPERTY name
		theNode.setAttribute( "name", prop.getName() );
		theNode.setAttribute( "type", toHibernateType( prop.getORMType() ) );
		// COLUMN name
		if ( columnInfo.containsKey( Key._NAME ) ) {
			theNode.setAttribute( "column", escapeReservedWords( columnInfo.getAsString( Key._NAME ) ) );
		}
		if ( prop.getUnsavedValue() != null ) {
			theNode.setAttribute( "unsaved-value", prop.getUnsavedValue() );
		}
		if ( columnInfo.containsKey( ORMKeys.insertable ) ) {
			theNode.setAttribute( "insert", trueFalseFormat( columnInfo.getAsBoolean( ORMKeys.insertable ) ) );
		}
		return theNode;
	}

	/**
	 * Escape SQL reserved words in a table or column name.
	 *
	 * @param value The table or column name to escape
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

	public static String unescapeReservedWords( String value ) {
		return value.replaceAll( "`", "" );
	}

	/**
	 * Look up an entity from the entity map by class name and set it into the entity-name attribute on the provided node.
	 *
	 * @param theNode           XML node onw hich to populate entity-name attribute
	 * @param relationClassName Class name of the associated entity. If null or empty, this method will do nothing.
	 * @param prop              Property metadata, used for log messages if the entity lookup fails.
	 */
	private void setEntityName( Element theNode, String relationClassName, IPropertyMeta prop ) {
		if ( relationClassName == null || relationClassName.isBlank() ) {
			return;
		}
		Key				datasourceName		= this.entity.getDatasource().isEmpty() ? Key.defaultDatasource : Key.of( this.entity.getDatasource() );
		EntityRecord	associatedEntity	= entityLookup.apply( relationClassName, datasourceName );
		if ( associatedEntity == null ) {
			String message = String.format( "Could not find entity '%s' referenced in property '%s' on entity '%s'", relationClassName, prop.getName(),
			    prop.getDefiningEntity().getEntityName() );
			logger.error( message );
			if ( !this.ormConfig.ignoreParseErrors ) {
				throw new BoxRuntimeException( message );
			}
		} else {
			theNode.setAttribute( "entity-name", associatedEntity.getEntityName() );
		}
	}

	/**
	 * Convert a boolean value to a string representation of "true" or "false". Useful for XML-ifying booleans.
	 *
	 * @param value Boolean value to convert
	 */
	private String trueFalseFormat( Boolean value ) {
		return Boolean.TRUE.equals( value ) ? "true" : "false";
	}

	/**
	 * Populate simple string attributes on the given XML node for the given list of Keys existing in the given struct.
	 *
	 * @param theNode          XML node to populate
	 * @param association      Struct containing the attribute values. Any null or empty values will be skipped.
	 * @param stringProperties List of keys to populate as attributes on the XML node. i.e., `List.of( ORMKeys.table, ORMKeys.schema )`
	 */
	private void populateStringAttributes( Element theNode, IStruct association, List<Key> stringProperties ) {
		for ( Key propertyName : stringProperties ) {
			if ( association.containsKey( propertyName ) ) {
				String value = association.getAsString( propertyName );
				if ( value != null && !value.isBlank() ) {
					theNode.setAttribute( toHibernateAttributeName( propertyName ), value.trim() );
				}
			}
		}
	}

	/**
	 * Convert a BoxLang key name to a Hibernate XML attribute name.
	 *
	 * @param key BoxLang annotation name, like `sqltype` or `selectkey`
	 *
	 * @return Correct Hibernate XML attribute name, like `sql-type` or `key`
	 */
	private String toHibernateAttributeName( Key key ) {
		String name = key.getName().toLowerCase();
		switch ( name ) {
			// Warning: This may backfire if 'strategy' is used in a different context than the <cache> node.
			case "strategy" :
				return "usage";
			case "sqltype" :
				return "sql-type";
			case "selectkey" :
				return "key";
			case "mappedby" :
				return "property-ref";
			case "foreignkey" :
				return "foreign-key";
			case "embedxml" :
				return "embed-xml";
			case "orderby" :
				return "order-by";
			case "uniquekey" :
				return "unique-key";
			default :
				return name;
		}
	}

	/**
	 * Caster to convert a property `ormType` field value to a Hibernate type.
	 *
	 * @param propertyType Property type, like `datetime` or `string`
	 *
	 * @return The Hibernate-safe type, like `timestamp` or `string`
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
			case "bit", "bool" -> "converted::" + BooleanConverter.class.getName();
			case "yes-no", "yesno", "yes_no" -> "yes_no";
			case "true-false", "truefalse", "true_false" -> "true_false";
			case "big-decimal", "bigdecimal" -> "converted::" + BigDecimalConverter.class.getName();
			case "big-integer", "bigint", "biginteger" -> "converted::" + BigIntegerConverter.class.getName();
			case "int" -> "converted::" + IntegerConverter.class.getName();
			case "numeric", "number", "decimal" -> "converted::" + DoubleConverter.class.getName();
			case "datetime", "eurodate", "usdate" -> "converted::" + DateTimeConverter.class.getName();
			case "char", "nchar" -> "character";
			case "varchar", "nvarchar" -> "string";
			case "clob" -> "text";
			case "boolean" -> "converted::" + BooleanConverter.class.getName();
			case "timestamp" -> "converted::" + DateTimeConverter.class.getName();
			case "time" -> "converted::" + TimeConverter.class.getName();
			default -> propertyType;
		};
	}
}
