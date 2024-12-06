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

	/**
	 * Get the entity metadata for the entity which contains this property.
	 */
	public IEntityMeta getDefiningEntity();

	public boolean isImmutable();

	public boolean isOptimisticLock();

	public String getLazy();

	public String getFormula();

	public String getUnsavedValue();

	/**
	 * Get all annotations set on this property.
	 * <p>
	 * Allows for easy access to all annotations set on this property, without needing to update the IPropertyMeta interface and both implementations.
	 * 
	 * @return Struct with all annotations set on this property.
	 */
	public IStruct getAnnotations();

	/**
	 * Get all generator info metadata.
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
	 * Get all column info metadata.
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
	 * <li>default=string</li>
	 * </ul>
	 * 
	 * @see https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0#a14330
	 * 
	 * @return Struct with column info keys.
	 */
	public IStruct getColumn();

	/**
	 * Get all association metadata.
	 * <p>
	 * <ul>
	 * <li>type=string - one-to-one|one-to-many|many-to-one|many-to-many</li>
	 * <li>name=string</li>
	 * <li>targetEntity=string</li>
	 * <li>fetch=string - join|select</li>
	 * <li>cascade=string</li>
	 * <li>constrained=boolean</li>
	 * <li>mappedBy=string</li>
	 * <li>lazy=string - proxy|no-proxy|false</li>
	 * <li>etc, etc.</li>
	 * </ul>
	 * 
	 * @return Struct with association metadata.
	 */
	public IStruct getAssociation();

	/**
	 * Is this property an association type?
	 * 
	 * @return True if fieldtype is one of one-to-one, one-to-many, many-to-one, or many-to-many.
	 */
	public boolean isAssociationType();

	/**
	 * Get the SQL type, aka `property sqltype="varchar"...`.
	 */
	public String getSqlType();

	/**
	 * Get the ORM type, aka `property ormtype="string"...`.
	 */
	public String getORMType();

	/**
	 * Get the property fieldtype.
	 * <p>
	 * One of `id`, `column`, `one`-to-one, `one`-to-many, `many`-to-many, `many`-to-one, `collection`, `timestamp`,
	 * `version`
	 */
	public FIELDTYPE getFieldType();

	/**
	 * Field type setter which allows amending the property type after instantiation.
	 * 
	 * @param fieldType Field type to set, like FIELDTYPE.COLUMN, FIELDTYPE.ASSOCIATION, etc.
	 */
	public IPropertyMeta setFieldType( FIELDTYPE fieldType );

	public enum FIELDTYPE {

		COLUMN,
		ASSOCIATION,
		COLLECTION,
		TIMESTAMP,
		VERSION,
		ID,
		TRANSIENT,
		ONE_TO_ONE,
		ONE_TO_MANY,
		MANY_TO_ONE,
		MANY_TO_MANY;

		/**
		 * Return true if the field type is one of one-to-one, one-to-many, many-to-one, or many-to-many.
		 */
		public boolean isAssociationType() {
			return this == ONE_TO_ONE || this == ONE_TO_MANY || this == MANY_TO_ONE || this == MANY_TO_MANY;
		}

		/**
		 * Convert a string to a FIELDTYPE enum. Returns null if no match. It is up to the implementor to detect nulls and validate/throw an exception.
		 * 
		 * @param fieldType String representation of the field type, like "id", "column", "one-to-one", etc.
		 */
		public static FIELDTYPE fromString( String fieldType ) {
			return switch ( fieldType.toUpperCase() ) {
				case "ID" -> FIELDTYPE.ID;
				case "COLUMN" -> FIELDTYPE.COLUMN;
				case "ONE-TO-ONE" -> FIELDTYPE.ONE_TO_ONE;
				case "ONE-TO-MANY" -> FIELDTYPE.ONE_TO_MANY;
				case "MANY-TO-ONE" -> FIELDTYPE.MANY_TO_ONE;
				case "MANY-TO-MANY" -> FIELDTYPE.MANY_TO_MANY;
				case "COLLECTION" -> FIELDTYPE.COLLECTION;
				case "TIMESTAMP" -> FIELDTYPE.TIMESTAMP;
				case "VERSION" -> FIELDTYPE.VERSION;
				default -> null;
			};
		}
	}
}