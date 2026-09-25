# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

### ❌ Removed

- Removed the class-less (MAP) entity representation and the `entityFacades` setting; the generated POJO facade is now the only representation. No application change needed.
- Removed the legacy `hbm.xml` writer (`HibernateXMLWriter`) and the `ormXmlMapping` setting; the module always emits the modern Hibernate 7 `mapping.xml`.

### ⚡ Changed

- `unique=true` on `ormExecuteQuery()` and `entityLoad()` (filter form) is strict: more than one matching row raises `orm.query.nonUnique` instead of silently returning the first. Pass `{ uniqueFirst : true }` for the old behavior. `unique` given inside the options struct is now honored.
- ORM errors are `orm.*` exceptions instead of raw Hibernate/JPA exceptions. Code catching Hibernate class names should catch `"orm"` instead.
- Unused named HQL parameters are logged as a warning.

- Upgraded the ORM engine from Hibernate 5.6.15 to 7.4.8. BoxLang-facing BIF behavior is preserved.
- Build and test against BoxLang 1.17.0 (was 1.11.0); the module's minimum BoxLang version is now 1.17.0 due to several updates we required in the new approach.
- Legacy dialect aliases (e.g. `MySQL57`, `Oracle10g`, `DerbyTenSeven`) now map to their Hibernate 7 equivalents with a one-time deprecation warning; community-dialect databases (SQLite, Derby, Firebird, …) resolve automatically.
- `ormGetSession()` / `ormGetSessionFactory()` return facade-aware wrappers so a BoxLang entity name or instance works against the raw Hibernate API.
- ORM operations inside a BoxLang `transaction{}` now ride the transaction's JDBC connection instead of the ORM running its own separate Hibernate transaction. BoxLang owns the real commit/rollback, so ORM writes are governed by the same demarcation as native `queryExecute` calls (rolled back together, committed together). The `TransactionManager` interceptor now only flushes the session on commit/end and clears it on rollback, and in-transaction ORM queries flush first so they observe their own pending writes (read-your-writes).

### 🚀 Added

- Clear ORM errors: every error bx-orm raises is an `orm.*` typed exception (catch all with `catch( "orm" e )`, or a family such as `"orm.query"`). Messages use BoxLang entity and property names (never generated facade class names), say how to fix the problem in `detail`, carry the context (entity, property, HQL, params, SQL, constraint) in `extendedInfo`, and suggest the right name for misspelled entities, properties, field types and ormtypes ("Did you mean [name]?"). Covers HQL syntax and unknown names, missing or mistyped query parameters, lazy loads after the session closed, unsaved associations, missing assigned ids, not-null and unique/foreign-key violations, stale (optimistic lock) updates, and wrong arguments to entity BIFs.
- `ormDiagnostics()`: the ORM's state for the current application (status, last startup error, entities per datasource, warnings, key settings, this request's open sessions). Never throws.
- Startup validation: duplicate entity names on one datasource are reported with both classes; unknown `ormtype` values are warned about and named if Hibernate fails to start; broken startups (bad entity path, fieldtype, cfc or datasource) raise one `orm.config` error, and the last failure is remembered for later ORM calls and `ormDiagnostics()`.
- `uniqueFirst` option for `ormExecuteQuery()` and `entityLoad()`: take the first row of a multi-row result.
- Entity inspection BIFs: `entityGetName()`, `entityGetDatasource()`, `entityGetId()`, `entityGetMetadata()` (cached per ORM application), `entityIsDirty()` and `entityGetDirtyProperties()` (Hibernate's own dirty check; no SQL for managed entities), `ormIsSessionDirty()` and `ormGetSessionStatistics()`.
- Event veto: a `preInsert`, `preUpdate` or `preDelete` handler (on the entity or the global `eventHandler`) that returns `false` cancels the operation. Vetoing the insert of a database-identity entity raises `orm.event.veto`, since Hibernate cannot skip that insert.

- POJO-facade entity representation (now the only representation): each entity maps to a generated Java facade whose accessors delegate to the BoxLang instance, unlocking `uuid` (and other) id generators, composite ids, `byte[]`, and full metamodel access.
- Facades are namespaced per ORM application, so same-named entities in different apps do not collide.
- Composite (multi-column) primary keys and entity inheritance (single-table + joined) in the facade representation.
- Associations and lazy proxies in the facade representation
- Modern Hibernate 7 `mapping.xml` output via `MappingXMLWriter`
- Value/element collections (`fieldtype="collection"`) as a JPA `<element-collection>`: array (BAG) and struct/map (MAP).
- New core `postNew` ORM event, fired by `entityNew()` on the entity's own `postNew( entity, entityName )` and the global `eventHandler`. (Hibernate has no instantiate event; previously only cborm offered `ORMPostNew`.)
- JMH benchmark suite (`src/jmh`) for entity CRUD, bulk hydration, and cold boot, plus a `jmhCompare` task benchmarking Hibernate 5 vs 7.
- Standalone boot + CRUD smoke tests for Derby, PostgreSQL, and MariaDB (Postgres/MariaDB gated by env vars).
- ORM manifest boot cache (`ormManifest` setting: `off`/`auto`/`trust`). In `auto` the resolved boot model (per-entity metadata + mapping) is written to `.bxorm/manifest.json` after each boot; in `trust` the app boots straight from that manifest with zero entity discovery, parsing or mapping generation (integrity-checked, fail-closed). Aimed at cold-start-heavy and large-entity apps. Off by default.
- The combined Hibernate `mapping.xml` is now fed to Hibernate in-memory (`addInputStream`) instead of via a temp file, removing a write+read on every boot.
- Pre-generated ByteBuddy facade bytecode is cached to `.bxorm/facades.jar` in `auto` mode and injected in `trust` mode, so production boots skip facade code generation.
- `bxorm` CLI (`boxlang module:orm <verb>`) to inspect the `.bxorm/` boot cache: `info`, `validate`, `entities`, `entity <name>`, `mappings`, `clear`, `version`, `help`. Reads the manifest only; the manifest is generated by booting once with `ormManifest="auto"`.
- `ormManifestLocation` ORM setting (`Application.bx` → `this.ormSettings`): choose where the `.bxorm/` boot cache is stored. Blank (default) keeps it at the application root; a relative path resolves against the app root, an absolute path is used as-is. The folder name stays `.bxorm`; only its parent moves. Mirrors the `bxorm` CLI `--dir` flag.
- `ormManifest="auto"` now starts a source watcher over the entity paths: editing an entity marks the ORM application for reload, and the next request reloads it automatically (no manual `ormReload()`). Generated `*.orm.xml` and the `.bxorm/` cache are ignored so a reload's own writes never loop. The watcher is stopped on shutdown/reload and degrades gracefully when the runtime has no watcher service.

### 🐛 Fixed

- Optimistic-locking `<version>` columns now work end-to-end.
- `text`/`clob` properties map to `TEXT`/`LONGTEXT` (via `<lob/>`) instead of an in-row `varchar`, avoiding MySQL row-size failures.
- An inverse one-to-many with no explicit `fkcolumn` is emitted as a `mapped-by` collection instead of synthesizing a join table.
- `binary`/`byte[]` properties are mapped and persisted (previously silently skipped).
- A capitalized association property name (e.g. `Client`) no longer breaks boot; `mapped-by` is decapitalized to match the facade accessor.
- A discriminated single-table subclass with a `<secondary-table>` now persists and deletes its own columns (emits `owned="true"`).
- A primary-key load (`entityLoadByPK`, `session.get`) of a session-managed entity no longer returns `null`.
- `removeX()` on a to-many removes by identity/equality, not by the owner's id property names.
- A to-many association can be structurally modified while iterating its getter
- Updating an entity with an association/collection no longer double-fires `preUpdate`/`postUpdate`.
- Hardened `entityLoad()` filter queries against HQL injection (the `order by` property is validated against the entity's properties).
- `entityLoad()` / `ormExecuteQuery()` accept a primary key or entity instance for a to-one association filter/parameter, resolving to a managed reference before binding.
- `ormExecuteQuery()` list parameters bound to an association resolve every element, not just the first.
- `entityLoadByExample()` builds predicates from the inheritance-aware property set and excludes ids, version, and associations.
- `entitySave()` on a detached entity leaves the passed-in object live and carrying generated ids/event changes (Hibernate 7 removed `saveOrUpdate()`).
- Date/time properties retain millisecond precision (`DateTimeConverter` maps to `java.sql.Timestamp`).
- Removed a stale `fieldtype="collection" … not yet supported` warning that logged on every value/element collection even though collections are now supported.
- Inherited to-many associations now get the stable-snapshot getter, so `parent.getChildren().each( c => parent.removeChild( c ) )` is safe for a collection declared on a persistent parent entity (previously only associations declared directly on the entity were protected).
- `removeX()` on an unmanaged (transient) collection now prefers an exact identity match, so a distinct-but-equal transient element is not removed by mistake.
- An owning `one-to-many` without `fkcolumn` reuses the target's back-reference foreign key instead of creating a join table.
- A struct-typed (`type="struct"`) `one-to-many`/`many-to-many` maps its key (`structKeyColumn`/`structKeyType`) and round-trips by key.
- A `fieldtype="timestamp"` version property boots and is stamped on save.
- Hibernate isolated work (e.g. sequence/table id allocation) inside a `transaction{}` runs on its own pooled connection, so it can no longer commit or roll back the surrounding BoxLang transaction mid-flight.
- `duplicate()` of an ORM entity produces an independent copy that saves its own state instead of sharing the original's Hibernate facade.
- ORM BIFs no longer fail with a `NullPointerException` when the application is not ORM-enabled or the ORM failed to start; they raise `orm.notEnabled` / `orm.notReady` with the reason.
- Entity lookup no longer fails with "No entities found for datasource" when every entity lives on a non-default datasource.
- A detached entity bound as an `ormExecuteQuery()` or `entityLoad()` filter parameter resolves correctly (the generated facade name no longer leaks into the lookup).
- `ormExecuteQuery()` now applies the `cacheable`, `cacheName` and `timeout` options (they were ignored), and `entityLoad()` applies `cacheName` as the query cache region.
- `entityLoad()` with `ignorecase` no longer wraps numeric or date sort properties in `lower()`.
- `ormFlush( datasource )` flushes that datasource's session instead of the default one.
- A failed flush at the end of a request still closes the request's ORM sessions.
- The `uniquekey` and `index` property annotations create their unique constraints and indexes again (lost in the move to `mapping.xml`). Properties sharing a name form one multi-column constraint or index; both accept a comma-separated list, and both work on `many-to-one` foreign keys.

## [1.7.0] - 2026-09-14

### ⭐ Added

- [BLMODULES-287](https://ortussolutions.atlassian.net/browse/BLMODULES-287) - Added `entityIsAttached()` to check whether an entity is attached to the ORM session for its datasource.
- New `ormSettings.hibernateProperties` setting: a flat struct of raw Hibernate property name/value pairs applied directly to the Hibernate `Configuration`, letting applications tune settings like `hibernate.connection.release_mode` without a custom Hibernate config file.
- Implemented the previously-unused `ormSettings.ormConfig` setting as a path to a `hibernate.properties`-formatted file, applied the same way as `hibernateProperties` (a conflicting key in `hibernateProperties` takes precedence). The `hibernate.cfg.xml` file format is not yet supported.
- Added SQLite dialect support. `ormSettings.dialect = "SQLite"` now resolves to `org.sqlite.hibernate.dialect.SQLiteDialect`, provided by the new `com.github.gwenn:sqlite-dialect` dependency (`org.hibernate:hibernate-community-dialects`, which houses the official SQLite dialect, is only published for Hibernate 6+ and is not available while this module is pinned to Hibernate 5).
- SQLite dialect resolution now uses JDBC metadata automatically when no explicit dialect is configured.

### 🐛 Fixed

- [BLMODULES-288](https://ortussolutions.atlassian.net/browse/BLMODULES-288) - Fixed support for Hibernate's built-in `uuid2` ORM generator.

## [1.6.7] - 2026-08-13

### 🐛 Fixed

- [BL-2612](https://ortussolutions.atlassian.net/browse/BL-2612) - `entityToQuery()` now includes inherited persistent properties (e.g., from a persistent parent entity) in query columns. Property collections internally use `LinkedHashSet` to prevent duplicate properties across the inheritance chain.
- Updated CI `GRADLE_VERSION` to `9.6.1` to match the Gradle wrapper and fix `NoSuchMethodError` with Shadow plugin 9.6.1
- Normalized ORM field generator values to be case-insensitive for built-in generators (including `uuid`) so mixed-case declarations are handled correctly.
- Custom generator values (non-built-in) are now validated against the classpath at mapping time; an invalid generator name throws a descriptive `BoxRuntimeException` listing valid built-in generators.

## [1.6.6] - 2026-07-22

### ⚡ Updates

- Toned down logging on the `ORMConnectionProvider` to avoid noisy logs on every connection acquisition.
- Dropped "No ORM application found" logs when ORM is disabled.

## [1.6.5] - 2026-05-26

### ⭐ Added

- New AI skills under `.agents/skills` and custom skills under `.agents/skills-custom`
- New Custom Skills for AI agents under `.agents/skills-custom` for ORM configuration and troubleshooting.
- Consolidation of AI instruction files to `AGENTS.md` with a pointer from `CLAUDE.md`

### ⚡ Updates

- Updated readme with AI skills information and setup.
- Dependabot quarterly
- Updated BoxLang testing to v1.13.0

### 🐛 Fixed

- Fix `ClassCastException` when saving entities with `ormtype="float"` properties containing Integer values (e.g., `default="0"`). Added missing `FloatConverter` and registered it in `HibernateXMLWriter`.

## [1.6.4] - 2026-05-13

### ⭐ Added

- Added debug logging for ORM startup metrics, including entity metadata parsing and Hibernate SessionFactory build times.

### 🐛 Fixed

- [BLMODULES-191](https://ortussolutions.atlassian.net/browse/BLMODULES-191) - Defensive code against disabled ORM logging causing boxlang cli startup failure.
- [BLMODULES-198](https://ortussolutions.atlassian.net/browse/BLMODULES-198) - Fix `missingRowIgnored` annotation not being applied to collection relationships.
- [BLMODULES-204](https://ortussolutions.atlassian.net/browse/BLMODULES-204) - Ensure transaction open only flushes session if `autoManageSession` is enabled.

## [1.6.3] - 2026-04-29

### 🐛 Fixed

- [BLMODULES-190](https://ortussolutions.atlassian.net/browse/BLMODULES-190) - Fix error on `one-to-many` associations when `inversejoincolumn` attribute is specified

## [1.6.2] - 2026-04-09

### 🐛 Fixed

- [BLMODULES-173](https://ortussolutions.atlassian.net/browse/BLMODULES-173) - Fixed issue with mutated entity metadata leaking across BoxLang applications.

## [1.6.1] - 2026-04-09

### 🐛 Fixed

- Fixed issue with `scale` annotation not properly cast to a string in `ClassicPropertyMeta`.

## [1.6.0] - 2026-04-02

### ⭐ Added

- Adds support for composite IDs.

### 🐛 Fixed

- Fixes an issue of lost mappings on entity discoveries, due to wrong parent context being sent.

## [1.5.0] - 2026-03-27

### 🐛 Fixed

- **Memory leak on ORM reload** — all open Hibernate sessions are now closed before tearing down `SessionFactory` instances, preventing stale session/factory references from blocking garbage collection after every `ORMReload()`.
- **Memory leak on session factory build failure** — `SessionFactoryBuilder` now wipes the `BootstrapServiceRegistry` when `buildSessionFactory()` throws, so the registry is not orphaned on the failure path.
- **Stale ORM context after reload** — the old `ORMContext` is removed from the JDBC context before rebuilding, and a fresh one is eagerly installed after the new app is live, eliminating null-window races for concurrent callers.
- **Null context in threaded scenarios** — `EntityTuplizer` and related components now obtain the box context safely when executing in a non-request thread.
- **Null pointer when the method does not exist on a tuplizer call** — added an existence check before invoking optional methods.
- Improved exception logging to include full stack traces throughout the ORM lifecycle.
- Fixed issue with `dbdefault` annotation not properly cast to a string in `ClassicPropertyMeta`.

### ⭐ Added

- Use a deterministic directory name based on config content rather than a hashcode of the config file path for generated mapping files, ensuring consistent mapping file usage across different environments and absolute paths.

### ⚡ Changed

- **Hot-path interception performance** — all `interceptorService.announce()` calls on hot code paths (entity instantiation, config load) are now guarded with `hasState()` checks and use lazy `Struct` suppliers, avoiding unnecessary struct allocation when no listeners are registered.
- `ORMConfig` now receives and threads the `IBoxContext` through `process()` and `getAppDefaultDatasource()` so the correct application datasource is resolved in all execution contexts.
- `ORMService.reloadApp()` now performs an atomic put-and-swap of the new/old `ORMApp` in the registry to minimize the disruption window for requests running concurrently with a reload.

## [1.4.1] - 2026-03-23

### ⛓️‍💥 Changed

- This version requires Boxlang Runtime `v1.11.x` and above

### 🗑 Deprecated

- The `autoGenMap` configuration setting is now deprecated in favor of `generateMappings`. Same function, different name. See [BLMODULES-119](https://ortussolutions.atlassian.net/browse/BLMODULES-119) for details.

### ⭐ Added

- [BLMODULES-119](https://ortussolutions.atlassian.net/browse/BLMODULES-119) - Add support for `generateMappings=false` (aliased as `autoGenMap` for backwards compatibility) to disable automatic mapping generation and require manual mapping files.

### 🐛 Fixed

- [BLMODULES-136](https://ortussolutions.atlassian.net/browse/BLMODULES-136) - Fix issue with naming strategy being double-applied on table and column identifiers.
- [BLMODULES-146](https://ortussolutions.atlassian.net/browse/BLMODULES-146)  - Fix issue where rollbacks, commits, etc. inside a transaction block were not properly scoped to the transaction block and could affect the entire session.

## [1.4.0] - 2026-02-06

### ⛓️‍💥 Changed

- This version requires Boxlang Runtime `v1.11.x` and above

### 🐛 Fixed

- [BLMODULES-120](https://ortussolutions.atlassian.net/browse/BLMODULES-120) - Add new settings for `lazy` and `defaultBatchSize` to provide compat implementations
- [BLMODULES-130](https://ortussolutions.atlassian.net/browse/BLMODULES-130) - Fix collection handling for java List objects - which hibernate returns
- Transaction management udpates to deal with complex nested transactions
- Ensure manual flush mode when `autoManageSession` is `false`
- Add savepoint interception for nested transactions and flush

## [1.3.0] - 2026-01-07

### 🐛 Fixed

- [BLMODULES-110](https://ortussolutions.atlassian.net/browse/BLMODULES-110) - Implement `EntityTuplizer.getEntityMode()` for relationship getters
- [BLMODULES-113](https://ortussolutions.atlassian.net/browse/BLMODULES-113) - Drop unnecessary logging on request end for non-ORM requests
- [BLMODULES-117](https://ortussolutions.atlassian.net/browse/BLMODULES-117) - Resolve transaction interception error when no ORM App is present
- [BL-2039](https://ortussolutions.atlassian.net/browse/BL-2039) - Fix "Datasource with name ... not found" in empty transactions on subsequent requests

### ⭐ Added

- [BLMODULES-118](https://ortussolutions.atlassian.net/browse/BLMODULES-118) - Enable `table`, `schema`, and `catalog` annotations for joined subclasses.
- [BL-2052](https://ortussolutions.atlassian.net/browse/BL-2052) - Move ORM context removal to shutdown listener for improved datasource cleanup on request end.

## [1.2.0] - 2025-12-05

### 🐛 Fixed

- [BLMODULES-102](https://ortussolutions.atlassian.net/browse/BLMODULES-102) - Fix ORM usage in threads causing ConcurrentModificationException
- [BLMODULES-109](https://ortussolutions.atlassian.net/browse/BLMODULES-109) - Fix queries on null relationships.

## [1.1.3] - 2025-11-04

### 🐛 Fixed

- [BLMODULES-94](https://ortussolutions.atlassian.net/browse/BLMODULES-94) - Fix incorrect casting in Getter
- [BLMODULES-96](https://ortussolutions.atlassian.net/browse/BLMODULES-96) - Fix ORMExecute query handling of WHERE clauses with object params
- [BLMODULES-101](https://ortussolutions.atlassian.net/browse/BLMODULES-101) - Fix NPE due to null oldState in PreUpdate event listener

## [1.1.2] - 2025-09-06

### 🐛 Fixed

- [BLMODULES-84](https://ortussolutions.atlassian.net/browse/BLMODULES-84) - Resolved incorrect location of many-to-one on discriminated child
- [BLMODULES-85](https://ortussolutions.atlassian.net/browse/BLMODULES-85) - Resolved an issue where transaction interception points would throw an error on non-orm-enabled applications
- [BLMODULES-88](https://ortussolutions.atlassian.net/browse/BLMODULES-88) - Resolved an issue where entity modifications during orm events were not persisting correctly to the database
- [BLMODULES-90](https://ortussolutions.atlassian.net/browse/BLMODULES-90) - Resolved an issue where numerics were not being coerced correctly to strings on applicable properties when used in ORMExecuteQuery

## [1.1.1] - 2025-08-27

### 🐛 Fixed

- Changed Array.fromString usage to use ListUtil as the method was removed from the Array class in v1.5.0 of the core
- [BLMODULES-83](https://ortussolutions.atlassian.net/browse/BLMODULES-83) - Resolved inheritiance and `mappedSuperClass` issues with 3+ levels
- [BLMODULES-80](https://ortussolutions.atlassian.net/browse/BLMODULES-80) - Fix for class relationships not being found due to core compiler casing changes

## [1.1.0] - 2025-08-04

### ⛓️‍💥 Changed

- This new version only works with the fixes on [BoxLang v1.4.0](https://boxlang.ortusbooks.com/readme/release-history/1.4.0)

### 🐛 Fixed

- Updates to Request Context based on BoxLang v1.4.0 updates
- Lots of dependency updates

## [1.0.11] - 2025-06-10

### 🐛 Fixed

- Fixed issue with default cache not being created when cache provider was empty - Resolves [BLMODULES-53](https://ortussolutions.atlassian.net/browse/BLMODULES-53)
- Fix error starting up on non-ORM apps - Resolves [BLMODULES-49](https://ortussolutions.atlassian.net/browse/BLMODULES-49)
- Add support for `tinyint` and `tinyinteger` ORM types - Resolves [BLMODULES-59](https://ortussolutions.atlassian.net/browse/BLMODULES-59)
- Skip type conversion on version properties - Resolves [BLMODULES-45](https://ortussolutions.atlassian.net/browse/BLMODULES-45)
- Drop ormApp instantiation in baseORMBIF - Fixes [BLMODULES-54](https://ortussolutions.atlassian.net/browse/BLMODULES-54)
- Improve default datasource look up and throw error if empty - See [BLMODULES-56](https://ortussolutions.atlassian.net/browse/BLMODULES-56)

## [1.0.10] - 2025-05-03

### 🐛 Fixed

- Throw or log an error when class annotation on association is an empty string - Resolves [BLMODULES-50](https://ortussolutions.atlassian.net/browse/BLMODULES-50)
- Fix support for `dataType` annotation on version properties - Resolves [BLMODULES-51](https://ortussolutions.atlassian.net/browse/BLMODULES-51)

### ⭐ Added

- Implement 'index' annotation - Resolves [BLMODULES-47](https://ortussolutions.atlassian.net/browse/BLMODULES-47)
- Implement multi-column support in `column` and `fkcolumn` - Resolves [BLMODULES-48](https://ortussolutions.atlassian.net/browse/BLMODULES-48)
- Implement cache support at the property level - Resolves [BLMODULES-52](https://ortussolutions.atlassian.net/browse/BLMODULES-52)

## [1.0.9] - 2025-04-29

### 🐛 Fixed

- Implement `elementType`,`elementColumn` annotations - Resolves [BLMODULES-46](https://ortussolutions.atlassian.net/browse/BLMODULES-46)
- Fixes for map collection when `structkeytype` or `structkeycolumn` are ignored - See [BLMODULES-45](https://ortussolutions.atlassian.net/browse/BLMODULES-45)
- Skip usage of `AttributeConverter` on identifier properties - Resolves [BLMODULES-44](https://ortussolutions.atlassian.net/browse/BLMODULES-44)

## [1.0.8] - 2025-04-25

### ⭐ Added

- Set hibernate version in build so `ORMGetHibernateVersion()` stays accurate - See [a8c7c16](https://github.com/ortus-boxlang/bx-orm/commit/a8c7c16d8b3ee766ab182aad490909a5509f10e4)

### 🐛 Fixed

- Foreign key must have same number of columns as the referenced primary key - Resolves [BLMODULES-41](https://ortussolutions.atlassian.net/browse/BLMODULES-41)
- Missing FKColumn on To-Many Relationship Should Check the Inverse Relationship for Column data - Resolves [BLMODULES-42](https://ortussolutions.atlassian.net/browse/BLMODULES-42)
- XMLWriter - Skip id,composite-id XML rendering on subclasses - Resolves [BLMODULES-38](https://ortussolutions.atlassian.net/browse/BLMODULES-38)
- XML Writer - Skip generator on composite keys - Resolves [BLMODULES-40](https://ortussolutions.atlassian.net/browse/BLMODULES-40)
- XMLWriter - Don't set insert or update on one-to-one elements - Resolves [BLMODULES-39](https://ortussolutions.atlassian.net/browse/BLMODULES-39)
- Fix support for 'params' attribute string notation - See [BLMODULES-40](https://ortussolutions.atlassian.net/browse/BLMODULES-40)

## [1.0.7] - 2025-04-14

### 🐛 Fixed

- Fix string casting error on `lazy` property annotation

## [1.0.6] - 2025-04-14

### 🐛 Fixed

- Fixed support for custom naming strategies - See [8e68206](https://github.com/ortus-boxlang/bx-orm/commit/8e68206e3d3f197a69fc12467c42c7c5de1c7eac)
- Fixed "smart" naming strategy when entity name begins with an uppercase character - See [b47b512](https://github.com/ortus-boxlang/bx-orm/commit/b47b51239a15530df245c5e12c36c48e10b09266)
- Move compat configuration to bx-compat-cfml - See [c8b7173](https://github.com/ortus-boxlang/bx-orm/commit/c8b7173f1c0fc01646d3b3d980d9d889ab8c7686)
- Fixed the two types of discriminator generation order - See [ea62a62](https://github.com/ortus-boxlang/bx-orm/commit/ea62a62fe1f4fe66bce58b4e27659b60faccb1aa)
- fix bag element being appended to wrong node on subclasses - See [f82b2ac](https://github.com/ortus-boxlang/bx-orm/commit/f82b2ac24e5d9cf1f43da5a8437c481be5e4f0c5)
- change to use caster so that lazy=true does not error - See [0096387](https://github.com/ortus-boxlang/bx-orm/commit/00963873c44480e6597ac0e3962d66244c42c865)

### ⭐ Added

- Add missing `date` property type - See [c6ec8a2](https://github.com/ortus-boxlang/bx-orm/commit/c6ec8a2e2dadfb344deb93edb7a1a2ccf8d0fb46)
- Add alternate spellings for big decimal and big integer - See [5e199f9](https://github.com/ortus-boxlang/bx-orm/commit/5e199f9e5674c3a3802a5e225d45f187b0724e23)
- Add flush after commit on transaction end - See [e2df378](https://github.com/ortus-boxlang/bx-orm/commit/e2df378c261a2c0aea99749d7bf04cd688d57658)

## [1.0.5] - 2025-04-07

### 🐛 Fixed

- Removed debugging code

## [1.0.4] - 2025-04-06

### 🐛 Fixed

- Metadata parsing throws error on empty class despite `skipCFCWithError` setting - Resolves [BLMODULES-37](https://ortussolutions.atlassian.net/browse/BLMODULES-37)

## [1.0.3] - 2025-04-06

### 🐛 Fixed

- EntityLoad returning incorrect results with criteria struct filter on parent properties - Resolves [BLMODULES-36](https://ortussolutions.atlassian.net/browse/BLMODULES-36)
- Hibernate Criteria Querys using `get` are returning proxies instead of the entity - Resolves [BLMODULES-35](https://ortussolutions.atlassian.net/browse/BLMODULES-35)
- ensure proxies in session are expanded when a load is requested - See [5b07e2c](https://github.com/ortus-boxlang/bx-orm/commit/5b07e2c1f0bf2bb4f3cb3c5fd15f15cee9bfd01d)
- Error on first ORM request after Application Timeout - Resolves [BLMODULES-30](https://ortussolutions.atlassian.net/browse/BLMODULES-30)
- BoxProxy Struct Implementation causes validation exceptions - Resolves [BLMODULES-33](https://ortussolutions.atlassian.net/browse/BLMODULES-33)

## [1.0.2] - 2025-04-04

No significant changes.

## [1.0.1] - 2025-04-04

### ⭐ Added

- Allow options as third arg to ORMExecuteQuery - See [b5efc84](https://github.com/ortus-boxlang/bx-orm/commit/b5efc840df6ddc96e87dd2d18b1bd3acd4de6002)
- Add handling for not null on to-one relationship - See [6792fb0](https://github.com/ortus-boxlang/bx-orm/commit/6792fb0e81a11105ce056803f2b28b873546ec02)

### 🐛 Fixed

- Attempt casting `uniqueOrOrder` to string in EntityLoad BIF - See [98f6734](https://github.com/ortus-boxlang/bx-orm/commit/98f67344e0df0d808f6bb749b4ae20b2cc8c9734)
- Ignore null `uniqueOrOrder` argument in EntityLoad BIF - See [394d9ba](https://github.com/ortus-boxlang/bx-orm/commit/394d9ba907a016103949da5a5d157ffb14672d61)
- Fix chicken/egg issues with app startup by lazy-initializing the EventHandler - See [699f15b](https://github.com/ortus-boxlang/bx-orm/commit/699f15b8c82704f8e101d1d1ee38be541e5ae618)
- WrongClassException when re-querying for the same object in a session - Resolves [BLMODULES-12](https://ortussolutions.atlassian.net/browse/BLMODULES-12)
- Disable `not-null` annotation usage on one-to-one relationships - See [c512848](https://github.com/ortus-boxlang/bx-orm/commit/c512848bba331c6282a5a5c5c2b99271b3f28863)
- fix explicit nulls on setters - See [819fffb](https://github.com/ortus-boxlang/bx-orm/commit/819fffbe58fb576e630f29d001aec5a38d8bf1b4)
- Auto-generated `has` methods are overriding declared methods in ORM entities - Resolves [BLMODULES-31](https://ortussolutions.atlassian.net/browse/BLMODULES-31)
- `x-to-one` generated `hasX()` methods are not returning the correct values - Resolves [BLMODULES-32](https://ortussolutions.atlassian.net/browse/BLMODULES-32)

## [1.0.0] - 2025-03-26

- First iteration of this module

[unreleased]: https://github.com/ortus-boxlang/bx-orm/compare/v1.7.0...HEAD
[1.7.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.7...v1.7.0
[1.6.7]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.6...v1.6.7
[1.6.6]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.5...v1.6.6
[1.6.5]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.4...v1.6.5
[1.6.4]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.3...v1.6.4
[1.6.3]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.2...v1.6.3
[1.6.2]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.1...v1.6.2
[1.6.1]: https://github.com/ortus-boxlang/bx-orm/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.4.1...v1.5.0
[1.4.1]: https://github.com/ortus-boxlang/bx-orm/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.1.3...v1.2.0
[1.1.3]: https://github.com/ortus-boxlang/bx-orm/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/ortus-boxlang/bx-orm/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/ortus-boxlang/bx-orm/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.11...v1.1.0
[1.0.11]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.10...v1.0.11
[1.0.10]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.9...v1.0.10
[1.0.9]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/ortus-boxlang/bx-orm/compare/2fe797c6330a5d110f3bfbc5ead058df9bdbe89e...v1.0.0
