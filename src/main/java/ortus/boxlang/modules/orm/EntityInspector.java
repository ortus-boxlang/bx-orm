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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.Session;
import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.stat.SessionStatistics;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMErrors;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.modules.orm.hibernate.BoxProxy;
import ortus.boxlang.modules.orm.hibernate.facade.BoxEntityFacade;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeSupport;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Read-only questions about entities, answered for the Phase 2 entity BIFs ({@code entityGetMetadata},
 * {@code entityGetName}, {@code entityGetId}, {@code entityGetDatasource}, {@code entityIsDirty},
 * {@code entityGetDirtyProperties}) and the session BIFs ({@code ormIsSessionDirty}, {@code ormGetSessionStatistics}).
 * <p>
 * Dirty checking uses Hibernate's own SPI, the same calls Hibernate makes when it flushes:
 * <ul>
 * <li>An entity in the session is compared with the state it was loaded with ({@code EntityEntry.getLoadedState()} plus
 * {@code EntityPersister.findDirty}); no SQL runs.</li>
 * <li>An entity outside the session is compared with a fresh read of its row ({@code EntityPersister.getDatabaseSnapshot}
 * plus {@code findModified}), as cborm does.</li>
 * <li>An entity that was never saved has no row, so it is not dirty (as in cborm).</li>
 * </ul>
 */
public final class EntityInspector {

	/**
	 * Static utility class; not instantiable.
	 */
	private EntityInspector() {
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Resolving the entity argument
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Resolve a BIF's entity argument (an entity instance, a lazy reference, or an entity name) to its entity record.
	 *
	 * @param app          The ORM application.
	 * @param entityOrName An entity instance, a lazy {@link BoxProxy}, or an entity name.
	 * @param bif          The BIF name, for error messages.
	 *
	 * @return The entity record.
	 *
	 * @throws ORMException {@code orm.entity.notFound} for an unknown name (with a suggestion), {@code orm.argument} for
	 *                      a value that is neither an entity nor a name.
	 */
	public static EntityRecord resolve( ORMApp app, Object entityOrName, String bif ) {
		if ( entityOrName instanceof String name ) {
			if ( name.isBlank() ) {
				throw new ORMException( ORMErrorType.ARGUMENT, bif + "() needs an entity instance or an entity name, but received an empty string.",
				    "Pass an entity, or its name such as \"User\"." );
			}
			return app.lookupEntity( name, true );
		}
		String name = entityNameOf( entityOrName );
		if ( name == null ) {
			String got = entityOrName == null ? "null" : describe( entityOrName );
			throw new ORMException( ORMErrorType.ARGUMENT,
			    String.format( "%s() needs an entity instance or an entity name, but received %s.", bif, got ),
			    "Pass an entity created with entityNew() or loaded with entityLoad(), or an entity name such as \"User\"." );
		}
		return app.lookupEntity( name, true );
	}

	/**
	 * The BoxLang entity name of an entity instance, lazy reference or facade, without loading a lazy reference.
	 *
	 * @param entity The value.
	 *
	 * @return The entity name, or null when the value is not an entity.
	 */
	public static String entityNameOf( Object entity ) {
		if ( entity instanceof BoxProxy proxy ) {
			return ORMErrors.entityName( proxy.getHibernateLazyInitializer().getEntityName() );
		}
		if ( entity instanceof BoxEntityFacade ) {
			return ORMService.getEntityName( FacadeSupport.unwrap( entity ) );
		}
		if ( entity instanceof IClassRunnable runnable && isPersistent( runnable ) ) {
			return ORMService.getEntityName( runnable );
		}
		return null;
	}

	/**
	 * Whether a BoxLang class is declared persistent (an ORM entity).
	 *
	 * @param runnable The class instance.
	 *
	 * @return True when its {@code persistent} annotation is true.
	 */
	private static boolean isPersistent( IClassRunnable runnable ) {
		Object persistent = runnable.getAnnotations().get( ORMKeys.persistent );
		return persistent != null && BooleanCaster.attempt( persistent ).getOrDefault( false );
	}

	/**
	 * A short description of a value's type for error messages.
	 *
	 * @param value The value (not null).
	 *
	 * @return E.g. "a struct", "an array", or "a Foo instance that is not persistent".
	 */
	private static String describe( Object value ) {
		if ( value instanceof IStruct ) {
			return "a struct";
		}
		if ( value instanceof Array ) {
			return "an array";
		}
		if ( value instanceof IClassRunnable runnable ) {
			return "a " + runnable.bxGetName().getName() + " instance that is not a persistent entity";
		}
		return "a " + value.getClass().getSimpleName();
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Metadata
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * Build the metadata struct returned by {@code entityGetMetadata()} for one entity. Called once per entity; the
	 * result is cached by {@link ORMApp#getEntityMetadata(String)}.
	 *
	 * @param app    The ORM application (to resolve association targets).
	 * @param record The entity record.
	 *
	 * @return The metadata struct.
	 */
	static IStruct buildMetadata( ORMApp app, EntityRecord record ) {
		IEntityMeta	meta	= record.getEntityMeta();
		IStruct		result	= new Struct( IStruct.TYPES.LINKED );
		result.put( Key.of( "entityName" ), record.getEntityName() );
		result.put( Key.of( "className" ), String.valueOf( record.getClassFQN() ) );
		result.put( Key.of( "datasource" ), record.getDatasource() == null ? "" : record.getDatasource().getName() );
		if ( meta == null ) {
			return result;
		}
		result.put( Key.of( "tableName" ), nullToEmpty( meta.getTableName() ) );
		result.put( Key.of( "schema" ), nullToEmpty( meta.getSchema() ) );
		result.put( Key.of( "catalog" ), nullToEmpty( meta.getCatalog() ) );
		result.put( Key.of( "parent" ), parentName( meta ) );
		result.put( Key.of( "readOnly" ), meta.isImmutable() );
		result.put( Key.of( "discriminator" ), discriminator( meta ) );

		Array	idProperties	= new Array();
		Array	idTypes			= new Array();
		for ( IPropertyMeta id : meta.getIdProperties() ) {
			idProperties.append( id.getName() );
			idTypes.append( nullToEmpty( id.getORMType() ) );
		}
		result.put( Key.of( "idProperties" ), idProperties );
		result.put( Key.of( "idType" ), idProperties.size() > 1 ? "composite" : ( idTypes.isEmpty() ? "" : idTypes.get( 0 ) ) );
		result.put( Key.of( "version" ), meta.getVersionProperty() == null ? "" : meta.getVersionProperty().getName() );

		Array	properties		= new Array();
		Array	associations	= new Array();
		Array	propertyNames	= new Array();
		for ( IPropertyMeta prop : meta.getAllPersistentProperties() ) {
			IPropertyMeta.FIELDTYPE type = prop.getFieldType();
			if ( type == IPropertyMeta.FIELDTYPE.ID ) {
				continue;
			}
			propertyNames.append( prop.getName() );
			if ( prop.isAssociationType() ) {
				associations.append( association( app, prop ) );
			} else {
				properties.append( property( prop ) );
			}
		}
		result.put( Key.of( "properties" ), properties );
		result.put( Key.of( "associations" ), associations );
		result.put( Key.of( "propertyNames" ), propertyNames );
		return result;
	}

	/**
	 * The metadata entry for a column-backed property (including the version and element collections).
	 *
	 * @param prop The property metadata.
	 *
	 * @return { name, column, ormtype, fieldtype, nullable, unique, length, precision, scale, formula, insertable, updatable }.
	 */
	private static IStruct property( IPropertyMeta prop ) {
		IStruct	column	= prop.getColumn() == null ? new Struct() : prop.getColumn();
		IStruct	entry	= new Struct( IStruct.TYPES.LINKED );
		entry.put( Key.of( "name" ), prop.getName() );
		entry.put( Key.of( "column" ), prop.getFormula() != null ? "" : nullToEmpty( column.getAsString( Key._name ) ) );
		entry.put( Key.of( "ormtype" ), nullToEmpty( prop.getORMType() ) );
		entry.put( Key.of( "fieldtype" ), fieldType( prop ) );
		entry.put( Key.of( "nullable" ), bool( column.get( ORMKeys.nullable ), true ) );
		entry.put( Key.of( "unique" ), bool( column.get( ORMKeys.unique ), false ) );
		entry.put( Key.of( "length" ), nullToEmpty( column.getAsString( Key.length ) ) );
		entry.put( Key.of( "precision" ), nullToEmpty( column.getAsString( ORMKeys.precision ) ) );
		entry.put( Key.of( "scale" ), nullToEmpty( column.getAsString( ORMKeys.scale ) ) );
		entry.put( Key.of( "formula" ), nullToEmpty( prop.getFormula() ) );
		entry.put( Key.of( "insertable" ), bool( column.get( ORMKeys.insertable ), true ) );
		entry.put( Key.of( "updatable" ), bool( column.get( ORMKeys.updateable ), true ) );
		return entry;
	}

	/**
	 * The metadata entry for an association.
	 *
	 * @param app  The ORM application (to resolve the target entity name).
	 * @param prop The association property metadata.
	 *
	 * @return { name, kind, target, cascade, lazy, inverse, fkcolumn, mappedBy, linkTable, orderBy }.
	 */
	private static IStruct association( ORMApp app, IPropertyMeta prop ) {
		IStruct	assoc	= prop.getAssociation() == null ? new Struct() : prop.getAssociation();
		IStruct	entry	= new Struct( IStruct.TYPES.LINKED );
		entry.put( Key.of( "name" ), prop.getName() );
		entry.put( Key.of( "kind" ), nullToEmpty( assoc.getAsString( Key.type ) ).isEmpty() ? fieldType( prop ) : assoc.getAsString( Key.type ) );
		entry.put( Key.of( "target" ), targetEntity( app, assoc.getAsString( Key._CLASS ) ) );
		entry.put( Key.of( "cascade" ), nullToEmpty( assoc.getAsString( ORMKeys.cascade ) ) );
		entry.put( Key.of( "lazy" ), nullToEmpty( prop.getLazy() ) );
		entry.put( Key.of( "inverse" ), bool( assoc.get( ORMKeys.inverse ), false ) );
		entry.put( Key.of( "fkcolumn" ), nullToEmpty( assoc.getAsString( Key.column ) ) );
		entry.put( Key.of( "mappedBy" ), nullToEmpty( assoc.getAsString( ORMKeys.mappedBy ) ) );
		entry.put( Key.of( "linkTable" ), nullToEmpty( assoc.getAsString( Key.table ) ) );
		entry.put( Key.of( "orderBy" ), nullToEmpty( assoc.getAsString( ORMKeys.orderBy ) ) );
		return entry;
	}

	/**
	 * The entity name an association's {@code cfc} points to: the entity whose name or class matches it.
	 *
	 * @param app The ORM application.
	 * @param cfc The {@code cfc} / {@code class} annotation value (a name or dotted path).
	 *
	 * @return The target entity name, or the raw value when it cannot be matched (e.g. an element collection).
	 */
	private static String targetEntity( ORMApp app, String cfc ) {
		if ( cfc == null || cfc.isBlank() ) {
			return "";
		}
		String simple = cfc.contains( "." ) ? cfc.substring( cfc.lastIndexOf( '.' ) + 1 ) : cfc;
		for ( EntityRecord record : app.getEntityRecords() ) {
			String fqn = String.valueOf( record.getClassFQN() );
			if ( record.getEntityName().equalsIgnoreCase( cfc ) || fqn.equalsIgnoreCase( cfc )
			    || fqn.toLowerCase( Locale.ROOT ).endsWith( "." + cfc.toLowerCase( Locale.ROOT ) ) ) {
				return record.getEntityName();
			}
		}
		for ( EntityRecord record : app.getEntityRecords() ) {
			String fqn = String.valueOf( record.getClassFQN() );
			if ( fqn.toLowerCase( Locale.ROOT ).endsWith( "." + simple.toLowerCase( Locale.ROOT ) ) || fqn.equalsIgnoreCase( simple ) ) {
				return record.getEntityName();
			}
		}
		return cfc;
	}

	/**
	 * The discriminator of an entity: its own column/value, with the column inherited from the parent when only the value
	 * is declared on a subclass.
	 *
	 * @param meta The entity metadata.
	 *
	 * @return { column, value }, or an empty struct when the entity has no discriminator.
	 */
	private static IStruct discriminator( IEntityMeta meta ) {
		IStruct	result	= new Struct( IStruct.TYPES.LINKED );
		IStruct	own		= meta.getDiscriminator();
		String	column	= own == null ? null : own.getAsString( Key._name );
		String	value	= own == null ? null : own.getAsString( Key.value );
		if ( ( column == null || column.isBlank() ) && meta.getParentMeta() != null ) {
			IStruct parentAnnotations = meta.getParentMeta().getAsStruct( Key.annotations );
			if ( parentAnnotations != null ) {
				column = parentAnnotations.getAsString( ORMKeys.discriminatorColumn );
			}
		}
		if ( ( column == null || column.isBlank() ) && ( value == null || value.isBlank() ) ) {
			return result;
		}
		result.put( Key.of( "column" ), nullToEmpty( column ) );
		result.put( Key.of( "value" ), nullToEmpty( value ) );
		return result;
	}

	/**
	 * The parent entity name of a subclass entity.
	 *
	 * @param meta The entity metadata.
	 *
	 * @return The parent entity name, or an empty string for a root entity.
	 */
	private static String parentName( IEntityMeta meta ) {
		if ( !meta.isSubclass() || meta.getParentMeta() == null ) {
			return "";
		}
		IStruct	parent		= meta.getParentMeta();
		IStruct	annotations	= parent.getAsStruct( Key.annotations );
		String	name		= annotations == null ? null : annotations.getAsString( ORMKeys.entityName );
		if ( name == null || name.isBlank() ) {
			name = parent.getAsString( Key.of( "simpleName" ) );
		}
		return nullToEmpty( name );
	}

	/**
	 * The fieldtype of a property as written in BoxLang, e.g. {@code many-to-one}.
	 *
	 * @param prop The property metadata.
	 *
	 * @return The lower-case, dash-separated field type.
	 */
	private static String fieldType( IPropertyMeta prop ) {
		return prop.getFieldType() == null ? "column" : prop.getFieldType().name().toLowerCase( Locale.ROOT ).replace( '_', '-' );
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Ids
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * The primary key of an entity, read from the instance (a lazy reference answers without being loaded).
	 *
	 * @param record The entity record.
	 * @param entity The entity instance or lazy reference.
	 *
	 * @return The id value; a struct of { property : value } for a composite id; null when the entity has no id yet.
	 */
	public static Object getId( EntityRecord record, Object entity ) {
		if ( entity instanceof BoxProxy proxy ) {
			return proxy.getHibernateLazyInitializer().getInternalIdentifier();
		}
		IClassRunnable runnable = entity instanceof BoxEntityFacade ? FacadeSupport.unwrap( entity ) : ( IClassRunnable ) entity;
		if ( record.getEntityMeta() == null ) {
			return null;
		}
		List<IPropertyMeta> ids = new ArrayList<>( record.getEntityMeta().getIdProperties() );
		if ( ids.isEmpty() ) {
			return null;
		}
		if ( ids.size() == 1 ) {
			return emptyToNull( runnable.getVariablesScope().get( Key.of( ids.get( 0 ).getName() ) ) );
		}
		IStruct	composite	= new Struct( IStruct.TYPES.LINKED );
		boolean	any			= false;
		for ( IPropertyMeta id : ids ) {
			Object value = emptyToNull( runnable.getVariablesScope().get( Key.of( id.getName() ) ) );
			composite.put( Key.of( id.getName() ), value );
			any = any || value != null;
		}
		return any ? composite : null;
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Dirty checking
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * The names of an entity's properties whose values differ from the stored ones.
	 *
	 * @param ormContext The request's ORM context.
	 * @param app        The ORM application.
	 * @param record     The entity record.
	 * @param entity     The entity instance or lazy reference.
	 *
	 * @return The dirty property names, in mapping order; empty for a clean, never-saved or not-yet-loaded entity.
	 */
	public static Array dirtyProperties( ORMContext ormContext, ORMApp app, EntityRecord record, Object entity ) {
		Array dirty = new Array();
		if ( entity instanceof BoxProxy proxy && proxy.getHibernateLazyInitializer().isUninitialized() ) {
			// A lazy reference nobody has loaded cannot have been changed.
			return dirty;
		}
		IClassRunnable runnable = entity instanceof BoxProxy proxy ? proxy.getRunnable()
		    : entity instanceof BoxEntityFacade ? FacadeSupport.unwrap( entity ) : ( IClassRunnable ) entity;
		if ( getId( record, runnable ) == null ) {
			// Never saved: there is no stored state to compare with.
			return dirty;
		}
		String				entityName	= record.getEntityName();
		Session				session		= ormContext.getSession( record.getDatasource() );
		SessionImplementor	si			= ( SessionImplementor ) session;
		EntityPersister		persister	= app.getEntityPersister( session, entityName );
		Object				facade		= FacadeSupport.wrap( ormContext.getFacadeNamespace(), entityName, runnable );
		Object[]			current		= persister.getValues( facade );
		EntityEntry			entry		= si.getPersistenceContextInternal().getEntry( facade );
		int[]				indexes;
		if ( entry != null ) {
			Object[] loaded = entry.getLoadedState();
			if ( loaded == null ) {
				return dirty;
			}
			indexes = persister.findDirty( current, loaded, facade, si );
		} else {
			Object id = persister.getIdentifier( facade, si );
			if ( id == null ) {
				return dirty;
			}
			Object[] snapshot = persister.getDatabaseSnapshot( id, si );
			if ( snapshot == null ) {
				return dirty;
			}
			indexes = persister.findModified( snapshot, current, facade, si );
		}
		if ( indexes == null ) {
			return dirty;
		}
		String[] names = persister.getPropertyNames();
		for ( int index : indexes ) {
			dirty.append( names[ index ] );
		}
		return dirty;
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Session statistics
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * The statistics of a session in BoxLang form, with keys written as {@code Entity#id} and {@code Entity.role#id}.
	 *
	 * @param session The Hibernate session.
	 *
	 * @return { entityCount, collectionCount, entityKeys, collectionKeys }.
	 */
	public static IStruct sessionStatistics( Session session ) {
		SessionStatistics	stats			= session.getStatistics();
		Array				entityKeys		= new Array();
		Array				collectionKeys	= new Array();
		for ( Object key : stats.getEntityKeys() ) {
			if ( key instanceof EntityKey ek ) {
				entityKeys.append( ORMErrors.entityName( ek.getEntityName() ) + "#" + ek.getIdentifier() );
			} else {
				entityKeys.append( ORMErrors.rewriteNames( String.valueOf( key ) ) );
			}
		}
		for ( Object key : stats.getCollectionKeys() ) {
			if ( key instanceof CollectionKey ck ) {
				collectionKeys.append( ORMErrors.rewriteNames( ck.getRole() ) + "#" + ck.getKey() );
			} else {
				collectionKeys.append( ORMErrors.rewriteNames( String.valueOf( key ) ) );
			}
		}
		IStruct result = new Struct( IStruct.TYPES.LINKED );
		result.put( Key.of( "entityCount" ), stats.getEntityCount() );
		result.put( Key.of( "collectionCount" ), stats.getCollectionCount() );
		result.put( Key.of( "entityKeys" ), entityKeys );
		result.put( Key.of( "collectionKeys" ), collectionKeys );
		return result;
	}

	/*
	 * ---------------------------------------------------------------------------------------------------------------
	 * Small helpers
	 * -------------------------------------------------------------------------------------------------------------
	 */

	/**
	 * An empty string for null.
	 *
	 * @param value The value.
	 *
	 * @return The value, or "" for null.
	 */
	private static String nullToEmpty( String value ) {
		return value == null ? "" : value;
	}

	/**
	 * Null for an unset value (null or an empty string), which is how an unsaved entity's id reads.
	 *
	 * @param value The value.
	 *
	 * @return The value, or null when it is null or an empty string.
	 */
	private static Object emptyToNull( Object value ) {
		return value instanceof String s && s.isEmpty() ? null : value;
	}

	/**
	 * A boolean metadata flag with a default.
	 *
	 * @param value        The raw value (may be null).
	 * @param defaultValue The value to use when it is null or not a boolean.
	 *
	 * @return The flag.
	 */
	private static boolean bool( Object value, boolean defaultValue ) {
		if ( value == null ) {
			return defaultValue;
		}
		return BooleanCaster.attempt( value ).getOrDefault( defaultValue );
	}
}
