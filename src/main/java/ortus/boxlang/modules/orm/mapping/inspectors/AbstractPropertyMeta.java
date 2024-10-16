package ortus.boxlang.modules.orm.mapping.inspectors;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public abstract class AbstractPropertyMeta implements IPropertyMeta {

	protected IStruct				meta;
	protected IEntityMeta			definingEntity;
	protected IStruct				annotations;

	/**
	 * Parent entity name, for logging purposes
	 */
	protected String				entityName;
	protected String				name;
	protected boolean				isImmutable			= false;
	protected boolean				isOptimisticLock	= true;
	protected String				lazy;
	protected String				formula;
	protected String				sqlType;
	protected String				ormType;
	protected IStruct				generator;
	protected IStruct				column;
	protected IStruct				association;
	protected String				unsavedValue;

	protected FIELDTYPE				fieldType;

	protected final static Logger	logger				= LoggerFactory.getLogger( AbstractPropertyMeta.class );

	public AbstractPropertyMeta( String entityName, IStruct meta, IEntityMeta definingEntity ) {
		this.entityName		= entityName;
		this.meta			= meta;
		this.definingEntity	= definingEntity;
		this.annotations	= this.meta.getAsStruct( Key.annotations );
		this.name			= this.meta.getAsString( Key._NAME );
		this.column			= parseColumnAnnotations( this.annotations );
		this.generator		= parseGeneratorAnnotations( this.annotations );
		this.association	= parseAssociation( this.annotations );

		if ( !this.generator.isEmpty() && this.generator.containsKey( Key._CLASS ) ) {
			if ( List.of( "identity", "native", "increment" ).contains( this.generator.getAsString( Key._CLASS ) ) ) {
				this.annotations.putIfAbsent( ORMKeys.ORMType, "integer" );
			}
		}

		if ( annotations.containsKey( Key.sqltype ) ) {
			this.sqlType = annotations.getAsString( Key.sqltype );
		}

		this.annotations.putIfAbsent( ORMKeys.ORMType, annotations.getOrDefault( Key.type, "string" ) );
		this.ormType = annotations.getAsString( ORMKeys.ORMType );

	}

	/**
	 * Get the entity metadata for the entity which contains this property.
	 */
	public IEntityMeta getDefiningEntity() {
		return this.definingEntity;
	}

	/**
	 * Parse the column annotations. If property is not a basic column, returns an empty struct.
	 * 
	 * @param annotations All property metadata annotations.
	 * 
	 * @return Struct containing column meta:
	 *         <code>
	 * {
	 * 	name: "email",
	 * 	unique: true,
	 * 	nullable: false,
	 * 	length: 255,
	 * 	precision: 0,
	 * 	scale: 0,
	 * 	insertable: true,
	 * 	updatable: true,
	 * 	...
	 * }
	 * </code>
	 */
	protected abstract IStruct parseColumnAnnotations( IStruct annotations );

	/**
	 * Parse the generator annotations. If property is not generated, returns an empty struct.
	 * 
	 * @param annotations All property metadata annotations.
	 * 
	 * @return Struct containing generator meta:
	 *         <code>
	 * {
	 * 	class: "increment",
	 * 	selectKey: "foo",
	 * 	generated: "insert",
	 * 	sequence: "bar",
	 * 	params : { ... }
	 * }
	 * </code>
	 */
	protected abstract IStruct parseGeneratorAnnotations( IStruct annotations );

	/**
	 * Parse the association annotations. If property is not an association, (one-to-one, one-to-many, many-to-one, many-to-many) an empty struct is
	 * returned.
	 * 
	 * @param annotations All property metadata annotations.
	 * 
	 * @return Struct containing parsed and normalized association meta:
	 *         <code>
	 * {
	 * 	type: "one-to-one",
	 * 	unique: true,
	 * 	lazy: "proxy",
	 * 	fetch: "join",
	 * ...
	 * }
	 * </code>
	 */
	protected abstract IStruct parseAssociation( IStruct annotations );

	public IStruct getAnnotations() {
		return this.annotations;
	}

	public boolean isImmutable() {
		return this.isImmutable;
	}

	public boolean isOptimisticLock() {
		return this.isOptimisticLock;
	}

	public String getLazy() {
		return this.lazy;
	}

	public String getName() {
		return this.name;
	}

	public String getFormula() {
		return this.formula;
	}

	public String getUnsavedValue() {
		return this.unsavedValue;
	}

	public IStruct getColumn() {
		return this.column;
	}

	public IStruct getGenerator() {
		return this.generator;
	}

	public boolean isAssociationType() {
		return this.fieldType.isAssociationType();
	}

	public IStruct getAssociation() {
		return this.association;
	}

	public FIELDTYPE getFieldType() {
		return this.fieldType;
	}

	public IPropertyMeta setFieldType( FIELDTYPE fieldType ) {
		this.fieldType = fieldType;
		return this;
	}

	public String getORMType() {
		return this.ormType;
	}

	public String getSqlType() {
		return this.sqlType;
	}

	/**
	 * Translate the table name using the configured table naming strategy.
	 *
	 * @param tableName Table name to translate, like 'owner'.
	 * 
	 * @return Translated table name, like 'tblOwners'.
	 */
	protected String translateTableName( String tableName ) {
		// TODO: Translate the table name using the configured table naming strategy.
		return tableName;
	}

	/**
	 * Translate the column name using the configured column naming strategy.
	 *
	 * @param columnName column name to translate, like 'owner'.
	 * 
	 * @return Translated column name, like 'tblOwners'.
	 */
	protected String translateColumnName( String columnName ) {
		// TODO: Translate the column name using the configured column naming strategy.
		return columnName;
	}
}
