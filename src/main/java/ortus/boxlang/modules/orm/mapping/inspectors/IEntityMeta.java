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

import java.util.List;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * IEntityMeta instance which represents the parsed entity metadata in a normalized form.
 * <p>
 * The source of this metadata could be CFML persistent
 * annotations like `persistent=true` and `fieldtype="id"` OR modern BoxLang-syntax, JPA-style annotations like `@Entity` and `@Id`.
 */
public interface IEntityMeta {

	public String getEntityName();

	/**
	 * Determining whether thisEntity is "direct" or "derived". Aka, is the table name explicitly defined in the entity
	 * metadata, or is it derived from a parent class plus discriminator metadata.
	 * 
	 * @return true if the entity is simple and NOT derived from a parent class, a join, or discriminator metadata; else false.
	 */
	public boolean isSimpleEntity();

	/**
	 * Determining whether this entity is "extended" or not. Aka, does this entity have a parent class.
	 * <p>
	 * Parent metadata can be retrieved via {@link #getParentMeta()}.
	 * 
	 * @return True if the entity is extended via `extends="SomeParentClass"`, else false.
	 */
	public boolean isExtended();

	public boolean isImmutable();

	public boolean isDynamicInsert();

	public boolean isDynamicUpdate();

	public boolean isSelectBeforeUpdate();

	public String getDatasource();

	public String getTableName();

	public String getSchema();

	public String getCatalog();

	/**
	 * Get the discriminator info for this entity.
	 * 
	 * @return A struct of discriminator info - EMPTY if none defined, else a struct with the following keys (any of which may be null):
	 *         <ul>
	 *         <li>{@link Key#value} - The value of the discriminator column</li>
	 *         <li>{@link Key#_name} - The name of the discriminator column</li>
	 *         <li>{@link Key#type} - The type of the discriminator column</li>
	 *         <li>{@link Key#force} - Whether to force the discriminator column to be created</li>
	 *         <li>{@link ORMKeys#insert} - Whether to insert the discriminator column</li>
	 *         <li>{@link ORMKeys#formula} - A formula to use for the discriminator column</li>
	 *         </ul>
	 */
	public IStruct getDiscriminator();

	/**
	 * Get the second-level cache properties for this entity.
	 * 
	 * @return A struct of cache properties - EMPTY if none defined, else a struct with the following keys (any of which may be null):
	 *         <ul>
	 *         <li>{@link Key#region} - The cache region to use</li>
	 *         <li>{@link ORMKeys#strategy} - Caching strategy to use - one of NONE|NONSTRICT_READ_WRITE|READ_ONLY|READ_WRITE|TRANSACTIONAL</li>
	 *         <li>{@link ORMKeys#include} - How lazy properties are included in the cache - one of all|non-lazy</li>
	 *         </ul>
	 */
	public IStruct getCache();

	public Integer getBatchSize();

	public String getRowID();

	public boolean isLazy();

	public String getOptimisticLock();

	public String getWhere();

	/**
	 * Retrieve the parent metadata for this entity, if it exists.
	 * 
	 * @return Struct of parent metadata, or EMPTY if no parent metadata exists.
	 */
	public IStruct getParentMeta();

	/**
	 * Property methods
	 */

	/**
	 * Retrieve a list of all key properties.
	 */
	public List<IPropertyMeta> getIdProperties();

	/**
	 * Retrieve the version property, if it exists.
	 */
	public IPropertyMeta getVersionProperty();

	/**
	 * Retrieve a list of all "normal" properties.
	 * <p>
	 * Excludes fieldtype=ID, fieldtype=version, fieldtype=timestamp, relationship fieldtypes, etc.
	 */
	public List<IPropertyMeta> getProperties();

	/**
	 * Retrieve a list of all association properties.
	 * <p>
	 * Retrieves a list of association properties of one of these types:
	 * <ul>
	 * <li>one-to-one</li>
	 * <li>one-to-many</li>
	 * <li>many-to-one</li>
	 * <li>many-to-many</li>
	 * </ul>
	 */
	public List<IPropertyMeta> getAssociations();
}