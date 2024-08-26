package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.runtime.types.IStruct;

/**
 * Defines the metadata for an entity property.
 */
public interface IPropertyMeta {

	/**
	 * Get the persistent true/false nature of this property. This will ALWAYS be true, or this PropertyMeta instance would/should not exist.
	 */
	public boolean isPersistent();

	/**
	 * Get all property types:
	 * * simple `type`
	 * * SQL type
	 * * ORM type
	 * * fieldtype
	 */
	public IStruct getType();

	public boolean isImmutable();

	public boolean isUnique();

	public boolean isNullable();

	public boolean isOptimisticLock();

	public boolean isInsertable();

	public boolean isUpdatable();

	public boolean isLazy();

	/**
	 * Get all generator info keys:
	 * * generated=boolean (always true)
	 * * class=string class name
	 * * sequence=string
	 * * selectKey=string
	 * * params=struct
	 */
	public IStruct getGenerator();

}