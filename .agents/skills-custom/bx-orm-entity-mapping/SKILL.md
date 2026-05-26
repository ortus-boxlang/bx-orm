---
name: bx-orm-entity-mapping
description: "Use when working with BoxLang ORM entity mapping: entity discovery via MappingGenerator, EntityRecord construction, metadata inspection (IEntityMeta, ClassicEntityMeta, AbstractEntityMeta), HBM XML generation via HibernateXMLWriter, property metadata (IPropertyMeta, ClassicPropertyMeta), entity file scanning (.bx/.cfc), parallel processing thresholds, and save-mapping options."
version: "1.0.0"
domain: bx-orm
triggers: entity mapping, entity discovery, HBM XML, MappingGenerator, EntityRecord, HibernateXMLWriter, entity metadata, persistent CFC, bx entity, orm mapping, generate mapping, hbm.xml, IEntityMeta, ClassicEntityMeta, property meta, entity scanning
role: expert
scope: bx-orm
related-skills: bx-orm-hibernate-bridge, bx-orm-configuration, bx-orm-bif-development
---

# BoxLang ORM — Entity Mapping

## Overview

Entity mapping is the **BoxLang → Hibernate** bridge entry point. The `MappingGenerator` walks the configured entity directories, discovers persistent BoxLang classes (`.bx` / `.cfc`), parses their metadata via inspectors, and produces Hibernate `hbm.xml` mapping documents that the `SessionFactoryBuilder` feeds into Hibernate's `Configuration`.

## Data Flow

```mermaid
flowchart LR
    A[".bx / .cfc files<br/>on disk"] --> B["MappingGenerator<br/>scanEntityDirectories()"]
    B --> C["EntityRecord<br/>(entityName, classFQN, metadata)"]
    C --> D["IEntityMeta<br/>(ClassicEntityMeta)"]
    D --> E["HibernateXMLWriter<br/>generateMapping()"]
    E --> F["hbm.xml<br/>(saved to temp or alongside entity)"]
    F --> G["Hibernate Configuration<br/>addXML()"]
```

## Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `MappingGenerator` | `ortus.boxlang.modules.orm.mapping` | Walks directories, discovers entities, orchestrates XML generation |
| `EntityRecord` | `ortus.boxlang.modules.orm.mapping` | Value object holding entity name, FQN, class name, datasource, metadata, XML path |
| `IEntityMeta` | `ortus.boxlang.modules.orm.mapping.inspectors` | Interface for normalized entity metadata |
| `AbstractEntityMeta` | `ortus.boxlang.modules.orm.mapping.inspectors` | Shared base: property lists, discriminator, cache, batch size |
| `ClassicEntityMeta` | `ortus.boxlang.modules.orm.mapping.inspectors` | Translates CFML annotations (`persistent=true`, `fieldtype="id"`) into `IEntityMeta` |
| `IPropertyMeta` | `ortus.boxlang.modules.orm.mapping.inspectors` | Interface for normalized property metadata |
| `ClassicPropertyMeta` | `ortus.boxlang.modules.orm.mapping.inspectors` | Translates CFML property annotations into `IPropertyMeta` |
| `HibernateXMLWriter` | `ortus.boxlang.modules.orm.mapping` | Builds a DOM `Document` from `IEntityMeta` and serializes it to `hbm.xml` |

## Entity Discovery

### `MappingGenerator` Walk Algorithm

1. Reads `ormSettings.entityPaths` (array of directory paths) from `ORMConfig`.
2. For each path, recursively finds files ending in `.bx` or `.cfc`.
3. **Small directories** (&le; 20 entities): processed synchronously.
4. **Large directories** (&gt; 20 entities): processed asynchronously via virtual threads.
5. Each file is compiled to an `IClassRunnable` via `ClassLocator`.
6. Metadata is extracted from the compiled class and wrapped in an `EntityRecord`.

```java
// MappingGenerator constants
private static final String[] ENTITY_EXTENSIONS = { ".bx", ".cfc" };
private static final int    MAX_SYNCHRONOUS_ENTITIES = 20;
private static final String ENTITY_TEMP_FOLDER = "orm_mappings";
```

### Save-Mapping Behavior

| `saveMapping` | `saveAlongsideEntity` | Result |
|---|---|---|
| `true` (default) | `false` | XML files saved to temp directory (`orm_mappings/`) |
| `true` | `true` | XML files saved alongside each `.bx`/`.cfc` file |

```java
if ( saveAlongsideEntity ) {
    xmlFile = entityPath.resolveSibling( entityRecord.getClassName() + HBM_XML_EXT );
} else {
    xmlFile = tempDir.resolve( entityRecord.getClassName() + HBM_XML_EXT );
}
```

## EntityRecord Structure

```java
public class EntityRecord {
    String      entityName;     // e.g. "Vehicle" (from @Entity annotation or entityname)
    String      classFQN;       // e.g. "models.Vehicle"
    String      className;      // e.g. "Vehicle"
    Key         datasource;     // Datasource name this entity uses
    IStruct     metadata;       // Raw BoxLang class metadata struct
    Path        xmlFilePath;    // Path to generated .hbm.xml
    IEntityMeta entityMeta;     // Parsed normalized metadata
    String      resolverPrefix; // "bx" or "cfc" — used when instantiating
}
```

The `resolverPrefix` is parsed from the class path:
```java
this.resolverPrefix = parseResolverPrefix(
    (String) this.metadata.getOrDefault(Key.path, ClassLocator.BX_PREFIX)
);
```

## Entity Metadata Inspectors

### `IEntityMeta` — The Normalized Contract

All entity metadata flows through this interface so the `HibernateXMLWriter` works regardless of whether the entity uses CFML annotations or modern JPA-style annotations.

```java
public interface IEntityMeta {
    String      getEntityName();
    IStruct     getMeta();
    boolean     isSimpleEntity();    // Not derived from parent/discriminator
    boolean     isExtended();        // Has extends="ParentClass"
    String      getParentEntityName();
    String      getTableName();
    String      getCatalogName();
    String      getSchemaName();
    boolean     isImmutable();       // readOnly=true
    int         getBatchSize();
    IStruct     getCacheConfig();    // cacheUse, cacheName, cacheInclude
    IStruct     getDiscriminator();  // discriminatorColumn, discriminatorValue
    List<IPropertyMeta> getIdentifierProperties();
    List<IPropertyMeta> getPersistentProperties();
    List<IPropertyMeta> getAllProperties();
}
```

### `ClassicEntityMeta` — Annotation Translation

Translates traditional CFML annotations into the normalized interface:

| CFML Annotation | IEntityMeta Method |
|---|---|
| `entityname="Vehicle"` | `getEntityName()` |
| `table="vehicles"` | `getTableName()` |
| `schema="dbo"` | `getSchemaName()` |
| `catalog="mydb"` | `getCatalogName()` |
| `readOnly=true` | `isImmutable()` |
| `batchsize=25` | `getBatchSize()` |
| `cacheuse="read-write"`, `cachename="vehicles"` | `getCacheConfig()` |
| `discriminatorColumn="type"`, `discriminatorValue="car"` | `getDiscriminator()` |
| `extends="BaseEntity"` | `isExtended()`, `getParentEntityName()` |

### `IPropertyMeta` — Property Normalization

```java
public interface IPropertyMeta {
    String  getName();
    String  getType();           // "string", "numeric", "date", "boolean", "binary", "struct"
    String  getColumn();         // DB column name
    String  getFieldType();      // "id", "column", "one-to-many", "many-to-one", etc.
    String  getFormula();
    int     getLength();
    int     getScale();
    boolean isGenerated();       // generator="increment" or similar
    String  getGenerator();      // "increment", "identity", "uuid", etc.
    boolean isNotNull();
    IStruct getMeta();           // Raw property metadata
}
```

## HBM XML Generation

### `HibernateXMLWriter.generateMapping(IEntityMeta)`

Creates a DOM `Document` with the Hibernate `hibernate-mapping` DTD and builds the complete XML tree:

1. **Root element**: `<hibernate-mapping>` with entity/package attributes
2. **Class element**: `<class>` with table, schema, catalog, batch-size, optimistic-lock, mutable, discriminator
3. **Identifier**: `<id>` or `<composite-id>` for composite keys
4. **Properties**: `<property>`, `<many-to-one>`, `<one-to-many>`, `<component>`, etc.
5. **Cache**: `<cache>` element if caching is configured

The generated XML is written to the determined path (temp or alongside) and registered with Hibernate via:
```java
configuration.addXML( xmlString );
```

### Entity Registration Pattern

```java
// In SessionFactoryBuilder
for ( EntityRecord entity : entities ) {
    HibernateXMLWriter writer = new HibernateXMLWriter( ormConfig, strategy );
    String xmlMapping = writer.generateMapping( entity.getEntityMeta() );
    configuration.addXML( xmlMapping );
    // Optionally save to disk
    if ( ormConfig.saveMapping ) {
        writer.writeMappingToFile( entity.getXmlFilePath() );
    }
}
```

## Custom Entity Metadata Inspector

To add a new annotation style (e.g., JPA `@Entity` / `@Id` annotations on BoxLang classes), implement `IEntityMeta`:

```java
public class ModernEntityMeta extends AbstractEntityMeta {

    public ModernEntityMeta( IStruct entityMeta ) {
        super( entityMeta );
        // Parse @Entity, @Table, @Id, @Column annotations
        IStruct annotations = entityMeta.getAsStruct( Key.annotations );
        this.entityName = annotations.getAsString( Key.entity )
            .orElse( meta.getAsString( Key.simpleName ) );
        // ... etc
    }
}
```

Then update `EntityRecord.setEntityMeta()` to detect the annotation style and instantiate the correct inspector.

## File Locations

```
src/main/java/ortus/boxlang/modules/orm/mapping/
├── MappingGenerator.java       # Entity discovery orchestrator
├── EntityRecord.java           # Entity value object
├── HibernateXMLWriter.java     # DOM-based HBM XML builder
└── inspectors/
    ├── IEntityMeta.java        # Normalized entity metadata interface
    ├── IPropertyMeta.java      # Normalized property metadata interface
    ├── AbstractEntityMeta.java # Shared entity metadata base
    ├── ClassicEntityMeta.java  # CFML annotation parser
    ├── AbstractPropertyMeta.java
    └── ClassicPropertyMeta.java # CFML property annotation parser
```

## Best Practices

1. **Always work through `IEntityMeta`** — never depend on `ClassicEntityMeta` directly in the writer. This enables future annotation styles.
2. **Respect `MAX_SYNCHRONOUS_ENTITIES`** — the 20-entity threshold exists because large synchronous processing blocks the BoxLang runtime.
3. **`entityName` is NOT `classFQN`** — the entity name comes from the `entityname` annotation or simple class name; the FQN is the dot-delimited path.
4. **Validate early** — catch invalid entity configurations before Hibernate gets them; throw `BoxRuntimeException` with descriptive messages.
5. **Temp file cleanup** — mapping files in `orm_mappings/` should be cleaned up on ORM shutdown when `saveAlongsideEntity` is `false`.
