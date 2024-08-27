package ortus.boxlang.modules.orm.mapping.inspectors;

import ortus.boxlang.runtime.types.IStruct;

/**
 * Defines the metadata for an entity property.
 */
public interface IPropertyMeta {

	/**
	 * Get the name of the property.
	 * <p>
	 * A more straightforward way to get the name of the property, which is also returned from the `getColumn()` struct method.
	 * 
	 * @return
	 */
	public String getName();

	public boolean isImmutable();

	public boolean isOptimisticLock();

	public boolean isLazy();

	public String getFormula();

	/**
	 * Get all annotations set on this property.
	 * <p>
	 * Allows for easy access to all annotations set on this property, without needing to update the IPropertyMeta interface and both implementations.
	 * 
	 * @return Struct with all annotations set on this property.
	 */
	public IStruct getAnnotations();

	/**
	 * Get all property type keys.
	 * <p>
	 * A full list of keys is shown below. Any or all of these keys may be absent.
	 * <ul>
	 * <li>`type` - Property type used in BoxLang class instances</li>
	 * <li>`sqltype` - Database column type used in table creation only</li>
	 * <li>`ormtype` - ORM type</li>
	 * <li>`fieldtype` - One of `id`, `column`, `one`-to-one, `one`-to-many, `many`-to-many, `many`-to-one, `collection`, `timestamp`, `version`</li>
	 * </ul>
	 */
	public IStruct getTypes();

	/**
	 * Get all generator info keys.
	 * <p>
	 * A full list of keys is shown below. Any or all of these keys may be absent.
	 * <ul>
	 * <li>generated=boolean (always true)</li>
	 * <li>class=string class name</li>
	 * <li>sequence=string</li>
	 * <li>selectKey=string</li>
	 * <li>params=struct</li>
	 * </ul>
	 */
	public IStruct getGenerator();

	/**
	 * Get all column info keys.
	 * <p>
	 * A full list of keys is shown below. Any of these keys may be absent from the struct EXCEPT `name`.
	 * <ul>
	 * <li>name=string</li>
	 * <li>length=integer</li>
	 * <li>precision=integer</li>
	 * <li>scale=integer</li>
	 * <li>unique=boolean</li>
	 * <li>nullable=boolean</li>
	 * <li>insertable=boolean</li>
	 * <li>updatable=boolean</li>
	 * <li>table=string</li>
	 * </ul>
	 * 
	 * @see https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0#a14330
	 * 
	 * @return Struct with column info keys.
	 */
	public IStruct getColumn();

}