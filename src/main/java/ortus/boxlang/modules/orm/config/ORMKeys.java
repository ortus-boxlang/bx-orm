package ortus.boxlang.modules.orm.config;

import ortus.boxlang.runtime.scopes.Key;

public class ORMKeys {

	public static final Key	ORMService				= Key.of( "ORMService" );
	public static final Key	ORM						= Key.of( "ORM" );
	public static final Key	ORMSession				= Key.of( "ORMSession" );
	public static final Key	ORMEnabled				= Key.of( "ormEnabled" );
	public static final Key	ORMSettings				= Key.of( "ormSettings" );
	public static final Key	autoGenMap				= Key.of( "autoGenMap" );
	public static final Key	autoManageSession		= Key.of( "autoManageSession" );
	public static final Key	cacheConfig				= Key.of( "cacheConfig" );
	public static final Key	cacheProvider			= Key.of( "cacheProvider" );
	public static final Key	datasource				= Key.of( "datasource" );
	public static final Key	dbcreate				= Key.of( "dbcreate" );
	public static final Key	dialect					= Key.of( "dialect" );
	public static final Key	entity					= Key.of( "entity" );
	public static final Key	eventHandling			= Key.of( "eventHandling" );
	public static final Key	eventHandler			= Key.of( "eventHandler" );
	public static final Key	flushAtRequestEnd		= Key.of( "flushAtRequestEnd" );
	public static final Key	logSQL					= Key.of( "logSQL" );
	public static final Key	namingStrategy			= Key.of( "namingStrategy" );
	public static final Key	ormConfig				= Key.of( "ormConfig" );
	public static final Key	ORMType					= Key.of( "ORMType" );
	public static final Key	persistent				= Key.of( "persistent" );
	public static final Key	saveMapping				= Key.of( "saveMapping" );
	public static final Key	schema					= Key.of( "schema" );
	public static final Key	catalog					= Key.of( "catalog" );
	public static final Key	secondaryCacheEnabled	= Key.of( "secondaryCacheEnabled" );
	public static final Key	skipCFCWithError		= Key.of( "skipCFCWithError" );
	public static final Key	sqlScript				= Key.of( "sqlScript" );
	public static final Key	table					= Key.of( "table" );
	public static final Key	useDBForMapping			= Key.of( "useDBForMapping" );
	/**
	 * OLD setting name. Deprecated. Use {@link entityPaths} instead.
	 */
	public static final Key	cfclocation				= Key.of( "cfclocation" );
	/**
	 * Path to locations of boxlang entity classes.
	 * <p>
	 * Usually relative to the application root, aka to `Application.bx`.
	 */
	public static final Key	entityPaths				= Key.of( "entityPaths" );

	/**
	 * Entity annotations
	 */
	public static final Key	fieldtype				= Key.of( "fieldtype" );
	public static final Key	generator				= Key.of( "generator" );
	public static final Key	unsavedValue			= Key.of( "unsavedValue" );
	public static final Key	generated				= Key.of( "generated" );
	public static final Key	sequence				= Key.of( "sequence" );
	public static final Key	selectKey				= Key.of( "selectKey" );
}
