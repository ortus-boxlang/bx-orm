package ortus.boxlang.modules.orm.config;

import ortus.boxlang.runtime.scopes.Key;

public class ORMKeys {

	public static final Key	ORMService				= Key.of( "ORMService" );
	public static final Key	TransactionManager		= Key.of( "TransactionManager" );
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
	public static final Key	ignoreParseErrors		= Key.of( "ignoreParseErrors" );
	/**
	 * OLD setting name. Deprecated. Use {@link ignoreParseErrors} instead.
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
	public static final Key	mappedSuperClass		= Key.of( "mappedSuperClass" );
	public static final Key	entityName				= Key.of( "entityName" );
	public static final Key	cacheUse				= Key.of( "cacheUse" );
	public static final Key	cacheName				= Key.of( "cacheName" );
	public static final Key	cacheInclude			= Key.of( "cacheInclude" );
	public static final Key	include					= Key.of( "include" );
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

	// association keys
	public static final Key	collectionType			= Key.of( "collectionType" );
	public static final Key	OneToOne				= Key.of( "OneToOne" );
	public static final Key	OneToMany				= Key.of( "OneToMany" );
	public static final Key	ManyToOne				= Key.of( "ManyToOne" );
	public static final Key	ManyToMany				= Key.of( "ManyToMany" );
	public static final Key	fetch					= Key.of( "fetch" );
	public static final Key	constrained				= Key.of( "constrained" );
	public static final Key	cascade					= Key.of( "cascade" );
	public static final Key	mappedBy				= Key.of( "mappedBy" );
	public static final Key	missingRowIgnored		= Key.of( "missingRowIgnored" );
	public static final Key	fkcolumn				= Key.of( "fkcolumn" );
	public static final Key	linkTable				= Key.of( "linkTable" );
	public static final Key	linkSchema				= Key.of( "linkSchema" );
	public static final Key	linkCatalog				= Key.of( "linkCatalog" );
	public static final Key	foreignKey				= Key.of( "foreignKey" );
	public static final Key	foreignKeyName			= Key.of( "foreignKeyName" );
	public static final Key	embedXML				= Key.of( "embedXML" );
	public static final Key	access					= Key.of( "access" );
	public static final Key	orderBy					= Key.of( "orderBy" );
	public static final Key	inverse					= Key.of( "inverse" );
	public static final Key	inverseJoinColumn		= Key.of( "inverseJoinColumn" );
	public static final Key	structKeyColumn			= Key.of( "structKeyColumn" );
	public static final Key	structKeyType			= Key.of( "structKeyType" );
	public static final Key	structKeyFormula		= Key.of( "structKeyFormula" );
	public static final Key	elementColumn			= Key.of( "elementColumn" );
	public static final Key	elementType				= Key.of( "elementType" );
	public static final Key	elementFormula			= Key.of( "elementFormula" );
	public static final Key	singularName			= Key.of( "singularName" );

	/**
	 * OLD setting name. Deprecated. Use {@link Key._CLASS} instead.
	 */
	public static final Key	cfc						= Key.of( "cfc" );

	// Hibernate or JPA annotations
	public static final Key	discriminator			= Key.of( "discriminator" );
	public static final Key	generatedValue			= Key.of( "generatedValue" );
	public static final Key	tableGenerator			= Key.of( "tableGenerator" );
	public static final Key	strategy				= Key.of( "strategy" );
	public static final Key	insertable				= Key.of( "insertable" );
	public static final Key	updateable				= Key.of( "updateable" );

	/**
	 * BIF argument keys
	 */
	public static final Key	forceinsert				= Key.of( "forceinsert" );
	public static final Key	entityNameList			= Key.of( "entityNameList" );

	/**
	 * ORM event keys
	 */
	public static final Key	event					= Key.of( "event" );
	// global-only event listener methods
	public static final Key	onEvict					= Key.of( "onEvict" );
	public static final Key	onDirtyCheck			= Key.of( "onDirtyCheck" );
	public static final Key	onDelete				= Key.of( "onDelete" );
	public static final Key	onClear					= Key.of( "onClear" );
	public static final Key	onAutoFlush				= Key.of( "onAutoFlush" );
	public static final Key	onFlush					= Key.of( "onFlush" );
	// global and entity event listener methods
	public static final Key	preLoad					= Key.of( "preLoad" );
	public static final Key	postLoad				= Key.of( "postLoad" );
	public static final Key	preInsert				= Key.of( "preInsert" );
	public static final Key	preUpdate				= Key.of( "preUpdate" );
	public static final Key	postDelete				= Key.of( "postDelete" );
	public static final Key	preDelete				= Key.of( "preDelete" );
	public static final Key	postUpdate				= Key.of( "postUpdate" );
	public static final Key	postInsert				= Key.of( "postInsert" );
	public static final Key	oldData					= Key.of( "oldData" );
}
