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

	public boolean isExtended();

	public boolean isImmutable();

	public boolean isDynamicInsert();

	public boolean isDynamicUpdate();

	public boolean isSelectBeforeUpdate();

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

	public Integer getBatchSize();

	public String getRowID();

	public boolean isLazy();

	public String getOptimisticLock();

	public String getWhere();

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
}