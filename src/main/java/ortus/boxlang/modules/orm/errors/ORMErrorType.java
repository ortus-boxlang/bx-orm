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
package ortus.boxlang.modules.orm.errors;

/**
 * Every {@link ORMException} type. BoxLang matches {@code catch} types on a dotted prefix, so {@code catch( "orm" )}
 * catches all of them and {@code catch( "orm.query" )} catches only the query family.
 * <p>
 * The documentation's error catalog lists each type with its cause and fix; keep it in sync when adding one.
 */
public enum ORMErrorType {

	/** Anything ORM-related that has no more specific type. */
	GENERIC( "orm" ),

	/** The application is not ORM-enabled. */
	NOT_ENABLED( "orm.notEnabled" ),
	/** The ORM application failed to start or is not started yet. */
	NOT_READY( "orm.notReady" ),
	/** Invalid ORM settings or entity mapping, found while starting. */
	CONFIG( "orm.config" ),
	/** Hibernate refused the mapping while starting. */
	BOOT( "orm.boot" ),

	/** An entity name that does not exist. */
	ENTITY_NOT_FOUND( "orm.entity.notFound" ),
	/** A property name that does not exist on the entity. */
	PROPERTY_UNKNOWN( "orm.property.unknown" ),
	/** A property value that cannot be converted to its column type. */
	PROPERTY_TYPE( "orm.property.type" ),
	/** A BIF received the wrong kind of value. */
	ARGUMENT( "orm.argument" ),

	/** HQL that cannot be parsed. */
	QUERY_SYNTAX( "orm.query.syntax" ),
	/** HQL that parses but is not valid (bad function, bad path, wrong types). */
	QUERY_SEMANTIC( "orm.query.semantic" ),
	/** A missing, extra or badly typed query parameter. */
	QUERY_PARAMETER( "orm.query.parameter" ),
	/** A unique query that returned more than one row. */
	QUERY_NON_UNIQUE( "orm.query.nonUnique" ),

	/** A lazy association read after its ORM session was closed or cleared. */
	LAZY_NO_SESSION( "orm.lazy.noSession" ),
	/** A saved entity pointing to an entity that was never saved. */
	TRANSIENT( "orm.transient" ),
	/** An assigned id that was not set before saving. */
	ID_MISSING( "orm.id.missing" ),
	/** Two different objects for the same row in one session. */
	SESSION_DUPLICATE( "orm.session.duplicate" ),
	/** An optimistic-lock (version) conflict: the row changed since it was loaded. */
	STALE( "orm.stale" ),

	/** An event handler vetoed an operation that cannot be vetoed (e.g. the insert of an identity-id entity). */
	EVENT_VETO( "orm.event.veto" ),

	/** A database constraint rejected the change. */
	CONSTRAINT( "orm.constraint" ),
	/** A unique constraint rejected the change. */
	CONSTRAINT_UNIQUE( "orm.constraint.unique" ),
	/** A not-null constraint (or notnull="true") rejected the change. */
	CONSTRAINT_NOT_NULL( "orm.constraint.notNull" ),
	/** A foreign key rejected the change. */
	CONSTRAINT_FOREIGN_KEY( "orm.constraint.foreignKey" ),
	/** A check constraint rejected the change. */
	CONSTRAINT_CHECK( "orm.constraint.check" ),

	/** The database rejected the SQL for another reason. */
	SQL( "orm.sql" );

	private final String type;

	/**
	 * Bind an error type to its BoxLang type string.
	 *
	 * @param type The BoxLang exception type string.
	 */
	ORMErrorType( String type ) {
		this.type = type;
	}

	/**
	 * The BoxLang exception type string, e.g. {@code orm.query.syntax}.
	 *
	 * @return The type string used in {@code catch} statements.
	 */
	public String type() {
		return type;
	}
}
