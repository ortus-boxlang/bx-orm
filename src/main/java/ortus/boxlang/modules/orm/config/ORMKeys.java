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
	public static final Key	_transient				= Key.of( "transient" );
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
	public static final Key	sqlScript				= Key.of( "sqlScript" );
	public static final Key	table					= Key.of( "table" );
	public static final Key	useDBForMapping			= Key.of( "useDBForMapping" );
	/**
	 * OLD setting name. Deprecated. Use {@link ???} instead.
	 */
	public static final Key	skipCFCWithError		= Key.of( "skipCFCWithError" );
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
	public static final Key	entityName				= Key.of( "entityName" );
	public static final Key	fieldtype				= Key.of( "fieldtype" );
	public static final Key	generator				= Key.of( "generator" );
	public static final Key	unsavedValue			= Key.of( "unsavedValue" );
	public static final Key	generated				= Key.of( "generated" );
	public static final Key	sequence				= Key.of( "sequence" );
	public static final Key	selectKey				= Key.of( "selectKey" );
	public static final Key	property				= Key.of( "property" );
	public static final Key	notNull					= Key.of( "notNull" );
	public static final Key	nullable				= Key.of( "nullable" );
	public static final Key	immutable				= Key.of( "immutable" );
	public static final Key	readOnly				= Key.of( "readOnly" );
	public static final Key	where					= Key.of( "where" );
	public static final Key	rowid					= Key.of( "rowid" );
	public static final Key	optimisticLock			= Key.of( "optimisticLock" );
	public static final Key	selectBeforeUpdate		= Key.of( "selectBeforeUpdate" );
	public static final Key	lazy					= Key.of( "lazy" );
	public static final Key	batchsize				= Key.of( "batchsize" );
	public static final Key	dynamicInsert			= Key.of( "dynamicInsert" );
	public static final Key	dynamicUpdate			= Key.of( "dynamicUpdate" );
	public static final Key	discriminatorValue		= Key.of( "discriminatorValue" );
	public static final Key	discriminatorColumn		= Key.of( "discriminatorColumn" );
	public static final Key	discriminatorType		= Key.of( "discriminatorType" );
	public static final Key	discriminatorFormula	= Key.of( "discriminatorFormula" );
	public static final Key	joinColumn				= Key.of( "joinColumn" );
	public static final Key	formula					= Key.of( "formula" );
	public static final Key	check					= Key.of( "check" );
	public static final Key	dbDefault				= Key.of( "dbDefault" );
	public static final Key	index					= Key.of( "index" );
	public static final Key	length					= Key.of( "length" );
	public static final Key	precision				= Key.of( "precision" );
	public static final Key	scale					= Key.of( "scale" );
	public static final Key	unique					= Key.of( "unique" );
	public static final Key	uniqueKey				= Key.of( "uniqueKey" );
	public static final Key	insert					= Key.of( "insert" );
	public static final Key	update					= Key.of( "update" );
	public static final Key	uniqueKeyName			= Key.of( "uniqueKeyName" );
	public static final Key	timestamp				= Key.of( "timestamp" );

	// Hibernate or JPA annotations
	public static final Key	discriminator			= Key.of( "discriminator" );
	public static final Key	generatedValue			= Key.of( "generatedValue" );
	public static final Key	tableGenerator			= Key.of( "tableGenerator" );
	public static final Key	strategy				= Key.of( "strategy" );
	public static final Key	insertable				= Key.of( "insertable" );
	public static final Key	updateable				= Key.of( "updateable" );
}
