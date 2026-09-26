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

import org.hibernate.LockMode;
import org.hibernate.Timeouts;
import org.hibernate.persister.entity.EntityPersister;

import jakarta.persistence.Timeout;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Turns the BoxLang lock options ({@code mode}, {@code timeout}, {@code skipLocked}) into Hibernate lock settings, for
 * {@code entityLock()}, {@code entityLoadByPK( name, id, { lock : mode } )} and the criteria {@code lock()}.
 * <p>
 * Modes:
 * <ul>
 * <li>{@code read}: a shared lock ({@code select ... for share}); others can read, nobody can change the row.</li>
 * <li>{@code write}: an exclusive lock ({@code select ... for update}); nobody else can lock or change the row.</li>
 * <li>{@code force}: an exclusive lock that also increments the entity's version (versioned entities only).</li>
 * </ul>
 * Locks are held by the database until the transaction ends, so they only mean something inside {@code transaction{}}.
 */
public final class EntityLocking {

	/** The {@code timeout} option: seconds to wait for a lock (0 means do not wait). */
	private static final Key TIMEOUT = Key.of( "timeout" );

	/**
	 * Not instantiable.
	 */
	private EntityLocking() {
	}

	/**
	 * The Hibernate lock mode for a BoxLang lock mode name.
	 *
	 * @param mode      {@code read}, {@code write} or {@code force} (case-insensitive); null or blank means {@code write}.
	 * @param operation The BIF or method, for the error message.
	 *
	 * @return The lock mode.
	 *
	 * @throws ORMException {@code orm.argument} for an unknown mode.
	 */
	public static LockMode mode( Object mode, String operation ) {
		String value = mode == null ? "write" : mode.toString().trim().toLowerCase();
		return switch ( value ) {
			case "", "write" -> LockMode.PESSIMISTIC_WRITE;
			case "read" -> LockMode.PESSIMISTIC_READ;
			case "force" -> LockMode.PESSIMISTIC_FORCE_INCREMENT;
			default -> throw new ORMException( ORMErrorType.ARGUMENT,
			    operation + "() does not know the lock mode [" + mode + "].",
			    "Use \"read\" (shared lock), \"write\" (exclusive lock, the default) or \"force\" (exclusive lock that also increments the version)." );
		};
	}

	/**
	 * The lock timeout from an options struct.
	 *
	 * @param options The options ({@code timeout} in seconds, {@code skipLocked}); may be null.
	 *
	 * @return The timeout, or null to use the database default (wait).
	 */
	public static Timeout timeout( IStruct options ) {
		if ( options == null ) {
			return null;
		}
		return timeout( options.get( TIMEOUT ), BooleanCaster.cast( options.getOrDefault( ORMKeys.skipLocked, false ) ) );
	}

	/**
	 * The lock timeout for a number of seconds and a skip-locked flag.
	 *
	 * @param seconds    Seconds to wait (0 or less means do not wait); null or blank to use the database default.
	 * @param skipLocked True to skip locked rows instead of waiting (wins over seconds).
	 *
	 * @return The timeout, or null to use the database default (wait).
	 */
	public static Timeout timeout( Object seconds, boolean skipLocked ) {
		if ( skipLocked ) {
			return Timeouts.SKIP_LOCKED;
		}
		if ( seconds == null || seconds.toString().isBlank() ) {
			return null;
		}
		int value = IntegerCaster.cast( seconds );
		return value <= 0 ? Timeouts.NO_WAIT : Timeout.seconds( value );
	}

	/**
	 * The Hibernate find options for a lock, for {@code Session.find}.
	 *
	 * @param mode    The lock mode.
	 * @param options The options ({@code timeout}, {@code skipLocked}); may be null.
	 *
	 * @return The find options.
	 */
	public static List<jakarta.persistence.FindOption> findOptions( LockMode mode, IStruct options ) {
		List<jakarta.persistence.FindOption> result = new ArrayList<>();
		result.add( mode );
		Timeout timeout = timeout( options );
		if ( timeout != null ) {
			result.add( timeout );
		}
		return result;
	}

	/**
	 * Fail clearly when a lock is taken outside {@code transaction{}}: a database lock lasts until the transaction ends,
	 * so Hibernate refuses to take one without a transaction.
	 *
	 * @param ormContext The ORM context of the request.
	 * @param operation  The BIF or method, for the message.
	 *
	 * @throws ORMException {@code orm.argument} outside a transaction.
	 */
	public static void requireTransaction( ORMContext ormContext, String operation ) {
		if ( !ormContext.isInTransaction() ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    operation + "() takes a database lock, which needs a transaction.",
			    "Run it inside transaction { }; the lock is released when the transaction ends." );
		}
	}

	/**
	 * Fail clearly when {@code force} is used on an entity without a version property.
	 *
	 * @param mode       The lock mode.
	 * @param persister  The entity's persister.
	 * @param entityName The BoxLang entity name, for the message.
	 * @param operation  The BIF or method, for the message.
	 *
	 * @throws ORMException {@code orm.argument} when the entity is not versioned.
	 */
	public static void checkForce( LockMode mode, EntityPersister persister, String entityName, String operation ) {
		if ( mode == LockMode.PESSIMISTIC_FORCE_INCREMENT && !persister.isVersioned() ) {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    operation + "() cannot use lock mode \"force\" on [" + entityName + "], which has no version property.",
			    "Add a property with fieldtype=\"version\" (or \"timestamp\") to the entity, or use lock mode \"write\"." );
		}
	}
}
