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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.inspectors.AbstractEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * {@code useDBForMapping}: fills in what an entity leaves out from the database schema, for Adobe ColdFusion
 * compatibility. For each entity whose table already exists:
 * <ul>
 * <li>a plain property with no {@code ormtype}, {@code sqltype} or BoxLang {@code type} gets one from its column's JDBC
 * type;</li>
 * <li>a root entity with no id property gets {@code fieldtype="id"} on the properties mapped to the table's primary key
 * columns.</li>
 * </ul>
 * Foreign keys are not inferred. The annotations are added to the entity's raw metadata before its metadata is built, so
 * the rest of mapping generation sees them as if they were declared. Uses JDBC {@link DatabaseMetaData} only.
 */
public final class DatabaseMappingInspector {

	/** The logger. */
	private static final BoxLangLogger logger = BoxRuntime.getInstance().getLoggingService().getLogger( "orm" );

	/**
	 * Not instantiable.
	 */
	private DatabaseMappingInspector() {
	}

	/**
	 * A table's columns and primary key, keyed by lower-cased column name.
	 *
	 * @param types      Column name to JDBC type.
	 * @param primaryKey The primary key column names (lower-cased).
	 */
	record TableInfo( Map<String, Integer> types, Set<String> primaryKey ) {
	}

	/**
	 * Fill in the missing annotations of every entity from its table.
	 *
	 * Runs before the entity metadata is built (building it writes defaults, such as {@code ormtype="string"}, into the
	 * raw annotations), so it reads the table name, schema and ids from metadata built on a copy.
	 *
	 * @param entities The entities (raw metadata, datasource and class name filled in).
	 * @param context  The boot context (for the datasources).
	 */
	public static void apply( List<EntityRecord> entities, IBoxContext context ) {
		IJDBCCapableContext jdbc = ( IJDBCCapableContext ) context.getParentOfType( IJDBCCapableContext.class );
		for ( EntityRecord entity : entities ) {
			try {
				IEntityMeta	meta		= AbstractEntityMeta.autoDiscoverMetaType(
				    ortus.boxlang.runtime.util.DuplicationUtil.duplicateStruct( entity.getMetadata(), true ) );
				DataSource	datasource	= entity.getDatasource() == null ? jdbc.getConnectionManager().getDefaultDatasourceOrThrow()
				    : jdbc.getConnectionManager().getDatasourceOrThrow( entity.getDatasource() );
				TableInfo	table		= readTable( datasource, meta.getCatalog(), meta.getSchema(), meta.getTableName() );
				if ( table == null ) {
					logger.debug( "useDBForMapping: table [{}] of entity [{}] does not exist yet; nothing inferred.", meta.getTableName(),
					    entity.getEntityName() );
					continue;
				}
				fillIn( entity.getMetadata(), meta, table );
			} catch ( SQLException | RuntimeException e ) {
				logger.warn( "useDBForMapping: could not read the table of entity [{}]: {}", entity.getEntityName(), e.getMessage() );
			}
		}
	}

	/**
	 * Add the missing ormtype and id annotations to an entity's raw property metadata.
	 *
	 * @param rawMeta The entity's raw metadata struct (its properties' annotations are changed).
	 * @param meta    The entity's built metadata.
	 * @param table   The entity's table.
	 *
	 * @return True when an annotation was added.
	 */
	static boolean fillIn( IStruct rawMeta, IEntityMeta meta, TableInfo table ) {
		boolean	changed	= false;
		boolean	needsId	= !meta.isSubclass() && meta.getIdProperties().isEmpty();
		for ( Object item : rawMeta.getAsArray( Key.properties ) ) {
			if ( ! ( item instanceof IStruct property ) || ! ( property.get( Key.annotations ) instanceof IStruct annotations ) ) {
				continue;
			}
			if ( !isPlainColumn( annotations ) ) {
				continue;
			}
			String	column	= annotations.get( Key.column ) != null && !annotations.getAsString( Key.column ).isBlank()
			    ? annotations.getAsString( Key.column )
			    : property.getAsString( Key._name );
			Integer	type	= table.types().get( column.toLowerCase() );
			if ( type == null ) {
				continue;
			}
			boolean declaredType = !isBlank( annotations.get( Key.type ) ) && !"any".equalsIgnoreCase( annotations.getAsString( Key.type ).trim() );
			if ( isBlank( annotations.get( ORMKeys.ORMType ) ) && isBlank( annotations.get( ORMKeys.dataType ) ) && isBlank( annotations.get( Key.sqltype ) )
			    && !declaredType ) {
				String ormType = ormType( type );
				if ( ormType != null ) {
					annotations.put( ORMKeys.ORMType, ormType );
					changed = true;
				}
			}
			if ( needsId && table.primaryKey().contains( column.toLowerCase() ) ) {
				annotations.put( ORMKeys.fieldtype, "id" );
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Whether a property maps to a plain column (not an association, collection or version).
	 *
	 * @param annotations The property's annotations.
	 *
	 * @return True for a plain column or an id.
	 */
	private static boolean isPlainColumn( IStruct annotations ) {
		Object fieldType = annotations.get( ORMKeys.fieldtype );
		if ( isBlank( fieldType ) ) {
			return !Boolean.FALSE.equals( annotations.get( ORMKeys.persistent ) )
			    && !"false".equalsIgnoreCase( String.valueOf( annotations.get( ORMKeys.persistent ) ) );
		}
		String value = fieldType.toString().trim().toLowerCase();
		return value.equals( "column" ) || value.equals( "id" );
	}

	/**
	 * Whether a value is null or blank.
	 *
	 * @param value The value.
	 *
	 * @return True when null or blank.
	 */
	private static boolean isBlank( Object value ) {
		return value == null || value.toString().isBlank();
	}

	/**
	 * The ormtype for a JDBC column type.
	 *
	 * @param jdbcType A {@link Types} constant.
	 *
	 * @return The ormtype, or null for a type bx-orm does not infer.
	 */
	static String ormType( int jdbcType ) {
		return switch ( jdbcType ) {
			case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR -> "string";
			case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "text";
			case Types.INTEGER -> "integer";
			case Types.SMALLINT, Types.TINYINT -> "short";
			case Types.BIGINT -> "long";
			case Types.DECIMAL, Types.NUMERIC -> "big_decimal";
			case Types.REAL, Types.FLOAT -> "float";
			case Types.DOUBLE -> "double";
			case Types.BIT, Types.BOOLEAN -> "boolean";
			case Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "timestamp";
			case Types.TIME, Types.TIME_WITH_TIMEZONE -> "time";
			case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "binary";
			default -> null;
		};
	}

	/**
	 * Read a table's column types and primary key. The table name is tried as given, then upper- and lower-cased, since
	 * databases store unquoted names differently.
	 *
	 * @param datasource The datasource.
	 * @param catalog    The catalog, or null.
	 * @param schema     The schema, or null.
	 * @param table      The table name.
	 *
	 * @return The table, or null when it does not exist.
	 *
	 * @throws SQLException When the metadata cannot be read.
	 */
	static TableInfo readTable( DataSource datasource, String catalog, String schema, String table ) throws SQLException {
		try ( Connection connection = datasource.getConnection() ) {
			DatabaseMetaData metadata = connection.getMetaData();
			for ( String name : new LinkedHashSet<>( List.of( table, table.toUpperCase(), table.toLowerCase() ) ) ) {
				Map<String, Integer> types = new HashMap<>();
				try ( ResultSet columns = metadata.getColumns( blankToNull( catalog ), blankToNull( schema ), name, null ) ) {
					while ( columns.next() ) {
						types.put( columns.getString( "COLUMN_NAME" ).toLowerCase(), columns.getInt( "DATA_TYPE" ) );
					}
				}
				if ( types.isEmpty() ) {
					continue;
				}
				Set<String> primaryKey = new LinkedHashSet<>();
				try ( ResultSet keys = metadata.getPrimaryKeys( blankToNull( catalog ), blankToNull( schema ), name ) ) {
					while ( keys.next() ) {
						primaryKey.add( keys.getString( "COLUMN_NAME" ).toLowerCase() );
					}
				}
				return new TableInfo( types, primaryKey );
			}
		}
		return null;
	}

	/**
	 * Null for a blank string.
	 *
	 * @param value The value.
	 *
	 * @return The value, or null when blank.
	 */
	private static String blankToNull( String value ) {
		return value == null || value.isBlank() ? null : value;
	}
}
