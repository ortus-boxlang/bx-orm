# bx-orm Developer Guide: The Hibernate 5 → 7 Migration

> Audience: contributors and reviewers of `bx-orm`. This document explains **why** the
> module is built the way it is, the design decisions behind the Hibernate 5.6 → 7.4
> migration, and where we intend to take it next. For day-to-day API usage see the end-user
> docs (`ortus-docs/bxorm`); for architecture deep-dives per subsystem see
> `.agents/skills-custom/`.

---

## 1. What bx-orm is

`bx-orm` is the middleware that lets the dynamic **BoxLang** JVM language use **Hibernate ORM**
as its persistence engine. It hides both raw database access and Hibernate's verbose
configuration behind BoxLang-native BIFs (`entityNew`, `entitySave`, `entityLoad`,
`ormExecuteQuery`, …) and `Application.bx` ORM settings.

```
BoxLang app  ──►  bx-orm (this module)  ──►  Hibernate ORM 7.4  ──►  JDBC / RDBMS
   BIFs &          representation bridge,        session factory,
 Application.bx    mapping generation,           dialects, caching
                   events, type conversion
```

The module runs on **Hibernate ORM 7.4.x** (upgraded from 5.6.15). All Hibernate
integration code targets Hibernate 7, not 5.

---

## 2. The core problem the migration had to solve

A BoxLang entity is **not a normal Java class**. It is an `IClassRunnable` — a dynamic object
that implements `java.util.Map`. Hibernate 5 supported non-POJO entities through **Tuplizers**
(`EntityTuplizer` / `Tuplizer`), which let us describe how to instantiate an entity, read a
property, and proxy it, for a "dynamic-map" representation.

**Hibernate 6+ removed Tuplizers.** Instead it resolves how a managed type is represented
through a single internal component, `ManagedTypeRepresentationResolver`, which Hibernate
**hard-codes** to its own standard implementation. There is no drop-in replacement for the old
dynamic-map tuplizer.

So the migration's central question was: *how do we make Hibernate 7 manage a BoxLang
`IClassRunnable` as an entity when Hibernate 7 only wants to manage real Java classes?*

```mermaid
flowchart LR
    subgraph H5["Hibernate 5.6"]
        T["Dynamic-map Tuplizer<br/>(pluggable per entity)"]
    end
    subgraph H7["Hibernate 7.4"]
        R["ManagedTypeRepresentationResolver<br/>(hard-coded, internal)"]
    end
    H5 -. "removed in 6+" .-> H7
    style T fill:#fde,stroke:#b36
    style R fill:#def,stroke:#36b
```

---

## 3. Two representations we evaluated

| Option | What it is | Verdict |
| --- | --- | --- |
| **MAP (class-less)** | Keep entities as dynamic maps; describe them to Hibernate as class-less `<entity metadata-complete="true">` with no backing class. | Worked for basic CRUD, but Hibernate 7's JPA-centric machinery could not express several documented features against a class-less entity: `uuid`/most id generators, composite ids, `byte[]`/binary attributes, and full metamodel access. **Removed.** |
| **POJO facade** ✅ | Generate a **real Java class per entity** (a "facade") whose accessors delegate their state to the backing BoxLang instance. Hibernate manages the facade; developers only ever touch the BoxLang class. | Superset of MAP behavior; unlocks the features above. **This is now the only representation.** |

We shipped both during development behind an `entityFacades` flag, proved parity, then
**removed MAP mode entirely** — the facade is the single representation path. (`entityFacades`
is gone.)

---

## 4. How the facade bridge is wired into Hibernate 7

Because Hibernate 7 hard-codes its representation resolver, we inject ours through the one
**public, pluggable SPI** that lets us reach it: the `hibernate.persister.factory` service.
Everything hangs off that single, supported seam — we do **not** fork or patch Hibernate.

```mermaid
flowchart TD
    A["hibernate.persister.factory<br/>(public SPI service)"] --> B["BoxPersisterFactory"]
    B --> C["BoxRepresentationResolver<br/>(ManagedTypeRepresentationResolver)"]
    C -->|"entity: by name, then facade-FQN"| D["BoxEntityRepresentationStrategy"]
    C -.->|"embeddable: components & composite ids<br/>(only internal touch-point)"| E["ManagedTypeRepresentationResolverStandard.INSTANCE"]
    D --> F["BoxClassInstantiator"]
    D --> G["BoxProxyFactory → BoxProxy / BoxLazyInitializer"]
    D --> H["FacadePropertyAccess"]
    D --> I["BoxEntityNameResolver"]
    style A fill:#efe,stroke:#3a3
    style E fill:#fee,stroke:#c33
```

- **`BoxPersisterFactory`** — registered as the `hibernate.persister.factory`. It owns the
  resolver and hands it to Hibernate.
- **`BoxRepresentationResolver`** — our `ManagedTypeRepresentationResolver`. For **entities** it
  returns our strategy; for **embeddables** (components / composite ids) it delegates to
  Hibernate's `internal` `ManagedTypeRepresentationResolverStandard.INSTANCE`.
- **`BoxEntityRepresentationStrategy`** — supplies the BoxLang-aware pieces: the
  `BoxClassInstantiator` (creates a facade + backing instance), `BoxProxyFactory` (lazy
  proxies), `FacadePropertyAccess` (read/write that tolerates a raw `IClassRunnable` owner), and
  `BoxEntityNameResolver`.

### The one internal touch-point (know this before you upgrade Hibernate)

Hibernate exposes **no public factory** for the default *embeddable* representation strategy.
So `BoxRepresentationResolver`'s embeddable branch delegates to the `internal`
`ManagedTypeRepresentationResolverStandard.INSTANCE`. That single, guarded delegation is the
**only** non-public Hibernate touch-point in the module, and it is the first thing to re-check
if a future Hibernate release relocates or renames it.

---

## 5. The facade, concretely

For each mapped entity the module generates a real Java class (e.g. `UserFacade`) whose
getters/setters **delegate to the backing BoxLang `IClassRunnable`** — the two share one state
store. Facade classes are **namespaced per ORM application** (`facadeNamespace`), so two apps in
the same JVM that each map a `User` generate distinct facade classes instead of colliding.

```mermaid
sequenceDiagram
    participant Dev as BoxLang code
    participant BIF as entitySave() BIF
    participant FS as FacadeSupport (wrap/unwrap)
    participant HB as Hibernate 7 Session
    Dev->>BIF: entitySave( user )   // user = IClassRunnable
    BIF->>FS: wrap(namespace, "User", user)
    FS-->>BIF: UserFacade (delegates state to user)
    BIF->>HB: session.persist("...UserFacade", facade)
    HB-->>FS: manages facade("writes generated id back onto `user`")
    BIF-->>Dev: same live `user` instance (now carries id/changes)
```

Key rules that fall out of this design:

- **Developers only ever handle BoxLang classes.** Facades are wrapped/unwrapped at the BIF
  boundary and never leak to user code.
- **Hibernate entity-name = the facade class FQN**; the BoxLang name is the JPA/HQL *import*
  alias. By-name Session/metamodel calls are routed through the import name, and
  `BoxEntityNameResolver` reports the facade FQN.
- `ormGetSession()` / `ormGetSessionFactory()` return **facade-aware wrappers**
  (`FacadeAwareHibernate`, a JDK dynamic proxy) so BoxLang entity names/instances work against
  the raw Hibernate API too.
- **One facade classloader per build.** Every ORM build (first boot and each `ormReload()`) gets a
  fresh `FacadeClassLoader`, registered with Hibernate through the bootstrap registry
  (`applyClassLoader`). A classloader can define a class name only once, so this is what lets a
  reload define a changed facade (same FQN, new property) instead of reusing the old class.
- **Self-contained accessors.** Facade getters/setters are static calls
  (`EntityFacadeFactory.Accessors.get/set` with the property name baked in as constants), not
  ByteBuddy `MethodDelegation` to an interceptor instance. The generated bytecode therefore needs
  no class initializer and can be written to `facades.jar` and defined again later as plain bytes.
- **The namespace comes from the booted app.** BIFs resolve it via `ORMContext.getFacadeNamespace()`
  (the `ORMApp`'s config), never the per-request `ORMConfig`, which only ever holds the default.
- **Instances made with `new`** carry no namespace stamp; `FacadeSupport.wrapInstance` falls back to the
  current request's ORM application, so they work even when they only reach Hibernate through a cascade.
- **`entityReload()` on a detached entity.** Hibernate 7 refuses to refresh or `lock()`-reattach a detached
  entity. `EntityReload` loads the row, rebinds that managed facade to the caller's object
  (`FacadeSupport.rebind`, which sets the facade's `boxState`) and refreshes, so the object is managed
  again. If another variable already holds the managed row, it refreshes that one and copies the mapped
  properties onto the caller's object instead.
- **`duplicate()` never shares a facade.** Root facades are `Serializable` with a `writeReplace`
  that yields a marker, so a deep copy of an entity drops the original's memoized facade, and
  `FacadeSupport` ignores any memoized facade that does not wrap the instance it is stored on.
- **To-many getters: snapshot reads, live writes.** On a managed entity, `getChildren()` returns a
  `ToManyGetterView`. Reads and iteration use a snapshot taken when the getter was called, so
  `parent.getChildren().each( c -> parent.removeChild( c ) )` visits every child. Writes through it
  (`append`/`arrayAppend`, `set`, `remove`) also go to the managed collection, so
  `parent.getChildren().append( c )` is persisted as it was on Hibernate 5.
- **A getter the developer wrote is kept.** The ORM only installs its to-many getter when the entity
  has no getter of that name, or the existing one is BoxLang's generated accessor (`GeneratedGetter`).

---

## 6. Mapping generation: modern `mapping.xml` (not `hbm.xml`)

Hibernate's legacy `hbm.xml` DTD format is deprecated-for-removal, so we removed our
`HibernateXMLWriter` and now emit only the **modern Hibernate 7 `mapping.xml`** format (root
`<entity-mappings>`, namespace `http://www.hibernate.org/xsd/orm/mapping`, version `7.0`).

```mermaid
flowchart LR
    S["BoxLang entity<br/>(.bx source + annotations)"] --> M["MappingGenerator<br/>(discovers entities per datasource)"]
    M --> I["IEntityMeta / IPropertyMeta<br/>(normalized metadata)"]
    I --> W["MappingXMLWriter"]
    W --> X["modern mapping.xml<br/>&lt;entity name='User' class='...UserFacade'&gt;"]
    X --> SF["SessionFactoryBuilder → Hibernate Configuration"]
```

The writer emits each entity as `<entity name="User" class="...UserFacade">` and covers ids,
`increment`/`uuid`/`identity` generators, JPA `AttributeConverter`s (`<convert>`), `<version>`,
discriminators, single-table inheritance (incl. a per-subclass `<secondary-table owned="true">`),
joined inheritance, `many-to-one` / `one-to-many` / `many-to-many`, composite ids, formulas,
`<element-collection>` (array→BAG, struct→MAP), and second-level cache.

**Hibernate 5 parity defaults.** JPA's `mapping.xml` defaults differ from classic `hbm.xml`, so the
writer states them explicitly to keep existing apps behaving the same:

- `many-to-one` / `one-to-one` always get an explicit `fetch`: `LAZY` unless `lazy="false"` or
  `fetch="join"` (JPA defaults to-one to `EAGER`).
- The hierarchy root writes its own `discriminator-value` (JPA would default it to the entity name).
- A `one-to-one` without `fkcolumn` and without `mappedBy` shares the primary key
  (`<primary-key-join-column/>`). As in hbm, only a `constrained="true"` side carries the foreign key;
  when the target has a `constrained` one-to-one pointing back, this side is written as its inverse
  (`mapped-by`) so the rows insert in the right order.
- An owning `one-to-many` without `fkcolumn` reuses the target's back-reference `fkcolumn` (the
  target's to-one pointing at this entity) instead of JPA's default join table.
- A collection `where` becomes `<sql-restriction>`; a struct to-many writes `map-key-class` +
  `map-key-column`/`map-key-formula`. Both follow the XSD element order (order-by, map-key,
  batch-size, sql-restriction, join).
- A `fieldtype="timestamp"` version is typed `java.time.Instant`.
- `uniquekey` and `index` property annotations become table-level `<unique-constraint>` and
  `<index>` children of `<table>` (JPA has no column-level form). Properties that share a name are
  grouped into one multi-column constraint or index, both annotations accept a comma-separated list,
  both work on a `many-to-one` foreign key, and an entity without `table=` still gets a nameless
  `<table>` just to carry them.

---

## 7. Events

BoxLang ORM events reach user code through two channels, unified by a single dispatcher:

- **Hibernate lifecycle events** (`preInsert`, `postLoad`, …) arrive at `EventListener` (a
  Hibernate `Integrator`).
- **`postNew`** has no Hibernate equivalent (Hibernate has no "instantiate" event), so it is
  fired from the `entityNew()` BIF.

Both go through **`ORMEventDispatcher`**, which invokes the entity's own event method and the
configured global `eventHandler` class identically. The global handler is loaded **once per ORM
application** and shared across datasources and the `postNew` path.

```mermaid
flowchart TD
    subgraph Sources
      A["Hibernate lifecycle<br/>(EventListener Integrator)"]
      B["entityNew() BIF<br/>(postNew)"]
    end
    A --> D["ORMEventDispatcher"]
    B --> D
    D --> E["entity's own method<br/>e.g. postNew(entity, entityName)"]
    D --> F["global eventHandler class<br/>e.g. postNew(entity, entityName)"]
```

---

## 8. Transactions: riding the BoxLang connection

BoxLang owns transactions through the `transaction{}` block. The ORM does **not** run a Hibernate
transaction of its own; it **rides the BoxLang transaction's JDBC connection** so that ORM writes
and native `queryExecute` calls land on the same connection and are governed by one demarcation
unit — committed together, rolled back together. BoxLang performs the real JDBC commit/rollback.

Four pieces cooperate:

- **`ORMConnectionProvider`** — `getConnection()` asks BoxLang's `ConnectionManager` for the
  connection, which is transaction-aware: inside a `transaction{}` bound to this datasource it
  returns the transaction's shared connection, otherwise a fresh pooled one. `closeConnection()`
  **skips** closing a connection that belongs to the active transaction (BoxLang owns its
  lifecycle). `supportsAggressiveRelease()` returns `true`.
- **`SessionFactoryBuilder`** — sets `CONNECTION_HANDLING` to
  `DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT`, so the session acquires a connection per
  statement and releases it immediately instead of holding one for its lifetime. Each acquisition
  therefore re-reads the *current* transaction connection (or a fresh pooled one outside a
  transaction). This is what makes "ride the transaction connection" work across a request, and it
  avoids a stale reference to a connection BoxLang closed at transaction end.
- **`TransactionManager`** (interceptor) — synchronizes the session with BoxLang's transaction
  events without owning demarcation: **flush** on commit/end (so pending SQL is emitted on the
  shared connection before BoxLang commits it), **clear** on rollback (so the session drops state
  BoxLang rolls back at the JDBC level).
- **`ORMContext.flushForQuery(session)`** — Hibernate suppresses auto-flush-before-query when no
  Hibernate transaction is in progress (and we run none), so the query choke points
  (`ORMApp.loadEntitiesByFilter`, `HQLQuery.execute`) call this to flush first when a transaction
  is active, giving in-transaction ORM queries read-your-writes.

One exception to riding the connection: Hibernate **isolated work** (sequence/table id allocation
via `JdbcIsolationDelegate`) commits or rolls back the connection it is handed. Inside a
transaction `ORMConnectionProvider` detects that caller (a short `StackWalker` check, only when a
transaction is active) and hands it a fresh pooled connection, so id allocation can never commit
the surrounding `transaction{}` mid-flight.

**Connection selection and release, per statement:**

```mermaid
flowchart TD
    Q["Hibernate needs a connection<br/>(per statement)"] --> P["ORMConnectionProvider.getConnection()"]
    P --> C{"ConnectionManager.isInTransaction()<br/>for this datasource?"}
    C -- yes --> T["return the transaction's<br/>shared connection"]
    C -- no --> F["return a fresh<br/>pooled connection"]
    T --> R["after the statement:<br/>closeConnection(conn)"]
    F --> R
    R --> S{"conn == active<br/>transaction connection?"}
    S -- yes --> K["skip close<br/>(BoxLang owns it)"]
    S -- no --> X["conn.close()<br/>(return to pool)"]
```

**Transaction lifecycle** (the ORM flushes/clears; BoxLang does the real commit/rollback):

```mermaid
sequenceDiagram
    participant BX as "BoxLang transaction{}"
    participant TM as "TransactionManager"
    participant HS as "Hibernate Session"
    participant CX as "shared JDBC connection"

    BX->>TM: onTransactionBegin
    Note over BX,CX: BoxLang sets autocommit=0 on the shared connection
    BX->>HS: entitySave(...) — pending in session
    BX->>HS: EntityLoad(...) — in-transaction query
    HS->>TM: flushForQuery() (read-your-writes)
    TM->>CX: INSERT ... (uncommitted)
    HS->>CX: SELECT ... (sees its own pending writes)
    alt commit
      BX->>TM: onTransactionCommit
      TM->>CX: flush pending SQL
      BX->>CX: COMMIT
    else rollback
      BX->>TM: onTransactionRollback
      TM->>HS: session.clear()
      BX->>CX: ROLLBACK
    end
    BX->>TM: onTransactionEnd
    TM->>CX: final flush (no-op if already committed/cleared)
```

**Nested transactions** are governed entirely by BoxLang. With the default runtime setting
(`enableNestedTransactions=false`, still experimental), BoxLang **flattens** a nested
`transaction{}` into the single outer demarcation unit: a nested `transactionCommit()` performs a
real JDBC commit on the shared connection (it commits the parent too). The `TransactionManager`
does not try to reinterpret this — it treats every event uniformly and lets BoxLang decide what
actually commits. Savepoint-based nesting (where a child commit/rollback is scoped) only exists
when the runtime enables that experimental flag.

---

## 9. Design decisions, at a glance

| Decision | Why |
| --- | --- |
| Inject via `hibernate.persister.factory` SPI | It is the one **public** seam that reaches Hibernate 7's representation resolver; no forking. |
| Generate POJO **facades**, drop class-less MAP | Facades are a superset: they unlock `uuid`/other generators, composite ids, `byte[]`, and full metamodel — none expressible for a class-less entity. |
| Namespace facades per application | Prevent same-named entities in different apps from colliding on one global facade class. |
| Emit **modern `mapping.xml`**, remove `hbm.xml` | `hbm.xml` is deprecated-for-removal; keeps us Hibernate-8 ready. |
| One documented internal touch-point (embeddables) | Hibernate has no public embeddable-strategy factory; isolate and guard the single delegation. |
| Facade-aware Session/SessionFactory wrappers | Preserve the pre-Hibernate-7 behavior consumers such as cborm rely on (by-name calls, `IClassRunnable` args). |
| Single `ORMEventDispatcher` for all events | One resolution/invocation path for Hibernate events and the bx-orm-native `postNew`. |
| ORM rides the BoxLang transaction connection (no own Hibernate tx) | One demarcation unit for ORM + native queries; BoxLang owns commit/rollback, so its transaction model governs ORM writes. |
| Per-statement connection release (`RELEASE_AFTER_STATEMENT` + aggressive release) | Lets each statement re-read the current transaction connection and avoids a stale reference after BoxLang closes it at transaction end. |
| One `orm.*` exception family, translated at the BIF boundary | Developers catch and read one kind of error in BoxLang terms; Hibernate's internal names and messages stay out of their way. |
| Strict `unique` with a `uniqueFirst` opt-out | Silently dropping rows hid bugs; the strict meaning matches Hibernate and the CFML engines. |

---

## 10. Migration notes (5.6 → 7.4)

- **Tuplizer → representation strategy** injected via `hibernate.persister.factory` (see §4).
- **`saveOrUpdate()` removed** — `entitySave()` now uses `persist()` for new entities and
  `merge()` for detached ones, then copies managed state back so the caller's instance stays
  live and carries generated ids/event changes.
- **`hbm.xml` writer removed** — modern `mapping.xml` only.
- **Dialects** — legacy version-specific aliases (`MySQL57`, `Oracle10g`, `DerbyTenSeven`, …)
  are mapped to the current version-detecting dialect with a one-time deprecation warning;
  databases that moved to `hibernate-community-dialects` (SQLite, Derby, Firebird, …) resolve to
  that artifact automatically.
- **Removed the `entityFacades` and `ormXmlMapping` settings** — the facade + modern-mapping
  paths are now the only paths.
- **Errors** are `orm.*` `ORMException`s instead of raw Hibernate exceptions (see §10a). Code that
  caught Hibernate class names should catch `"orm"` (or a narrower `orm.*` type) instead.

### Testing notes

- **One Derby database per test class.** Never boot the same application against the same
  in-memory Derby database from two test classes. After rows are deleted, Derby's background
  cleanup thread (`rawStoreDaemon`) can fail under BoxLang's `DynamicClassLoader` (the thread has no
  context classloader) and leave a lock held. The next boot's `dbcreate="dropcreate"` schema drop
  then waits on that lock (60s per table), and in CI the second class ran without its tables. Each
  Derby test app has its own `jdbc:derby:memory:<name>` (for example `facadeSemanticsApp` reuses the
  `mappingRegressionApp` entities under its own app name and DB); `ManifestLifecycleBootTest` uses a
  fresh DB name per test.
- **Transaction tests** use a unique row value per test (`uniqueName(...)`) and check commits from a
  separate pooled connection (`committedCount(...)`), so they never depend on test order and never
  pass without asserting anything.

---

## 10a. Errors and diagnostics

Hibernate's exceptions talk about generated facade classes, SQL type codes and internal state. bx-orm
translates every one of them into a single exception family a BoxLang developer can act on.

**One family, dotted types.** Every error bx-orm raises is an `ORMException` (a `BoxRuntimeException`)
whose `type` starts with `orm`. BoxLang matches `catch` types on a dotted prefix, so
`catch( "orm" e )` catches everything and `catch( "orm.query" e )` one family. The full list is in
`errors/ORMErrorType` (and in the docs error catalog):

| Family | Types |
| --- | --- |
| Setup | `orm.notEnabled`, `orm.notReady`, `orm.config`, `orm.boot` |
| Names and values | `orm.entity.notFound`, `orm.property.unknown`, `orm.property.type`, `orm.argument` |
| Queries | `orm.query.syntax`, `orm.query.semantic`, `orm.query.parameter`, `orm.query.nonUnique` |
| Session and state | `orm.lazy.noSession`, `orm.transient`, `orm.id.missing`, `orm.session.duplicate`, `orm.stale` |
| Events | `orm.event.veto` |
| Database | `orm.constraint` (`.unique`, `.notNull`, `.foreignKey`, `.check`), `orm.sql` |

**Message shape.** `message` says what went wrong using BoxLang entity and property names; `detail`
says how to fix it; `extendedInfo` is a struct with the context (`entityName`, `property`, `hql`,
`params`, `sql`, `constraint`, `originalMessage`, `hibernateException`, …). Generated facade class
names never appear: `ORMErrors.rewriteNames` maps them back through a registry filled when facades are
registered (`FacadeSupport.entityNameForFacade`). Names that do not exist get a "Did you mean" from
an edit distance that counts a swapped pair of letters as one step (`nmae` → `name`).

```mermaid
flowchart LR
    B["BIF call<br/>(BaseORMBIF.invoke)"] --> T["ORMErrors.translate"]
    F["Flushes: request end,<br/>transaction commit, read-your-writes"] --> T
    L["Lazy loads: BoxProxy,<br/>to-many getter snapshot"] --> T
    S["ORM startup / ormReload<br/>(ORMService.recordBootFailure)"] --> T
    T --> E["ORMException<br/>type orm.*"]
```

**Where translation happens.**

- `BaseORMBIF.invoke` wraps every BIF. Its error context carries the BIF name, the HQL and params,
  and the app's entity and property names for suggestions (`ORMApp.errorContext`).
- `ORMContext.flush( session, operation )` is used for the request-end flush, the transaction
  commit flush (`TransactionManager`) and the read-your-writes flush. A failed request-end flush still
  closes every session.
- `BoxProxy.getRunnable` and the `ToManyGetterView` snapshot translate lazy-load failures.
- `ORMService.startupApp` / `reloadApp` translate startup failures and remember the last one per
  application (`getBootFailure`). A failure that is not a Hibernate exception (bad entity path,
  fieldtype, cfc, datasource) becomes `orm.config` with its original message.
- Errors that are not ORM-related, for example one thrown by the developer's own event handler,
  pass through unchanged. `ormGetSession()` / `ormGetSessionFactory()` stay raw on purpose.

**Validation before Hibernate starts** (`ORMApp.validateEntities`): duplicate entity names on one
datasource are an `orm.config` error naming both classes; an `ormtype` outside the known set is logged
as a warning and, if Hibernate then fails to start, named as the likely cause with a suggestion.

**Readiness.** Every BIF gets its ORM application through `ORMService.requireORMApp` /
`ORMContext.requireORMApp`, never `getORMApp()` plus a null check. A missing application is an
`orm.notEnabled` or `orm.notReady` error that repeats the last startup failure, instead of a
`NullPointerException`.

**Behavior changes that came with this work.**

- `unique=true` (`ormExecuteQuery`, `entityLoad` with a filter) is strict: more than one match is an
  `orm.query.nonUnique` error, as in Hibernate and the CFML engines. The new `uniqueFirst` option
  (`{ uniqueFirst : true }`) keeps the old take-the-first-row behavior. bx-orm fetches at most two
  rows to check.
- `unique` passed inside the options struct is now honored.
- Extra named HQL parameters that the query does not use are logged as a warning.
- `ORMApp.lookupEntity` skips datasources without entities, so an app whose entities all live on a
  non-default datasource no longer fails every lookup with "No entities found for datasource".
- A detached entity bound as an HQL or filter parameter resolves by its BoxLang name (the query
  metadata names the facade class).

**`ormDiagnostics()`** returns the ORM's state for the current application and never throws:
`status` (`running`, `failed`, `notStarted`, `notEnabled`), `startupError`, entities per datasource,
`warnings`, the settings that most often explain surprises, and this request's open sessions (entity
count, dirty flag). It reads only; it never opens a session.

**Tests.** `errors/ORMErrorsTest` (unit, every mapping), `errors/ORMErrorMessagesTest` (live, 30+
common mistakes asserting type and message), `errors/BootErrorsTest` (broken startups in
`src/test/resources/bootErrorApp`, one in-memory Derby DB per scenario, including a failed
`ormReload()`), `bifs/ORMDiagnosticsTest`.

**Adding a new error.** Add the type to `ORMErrorType`, map it in `ORMErrors.map` (or throw an
`ORMException` directly where bx-orm detects the problem), add a unit case to `ORMErrorsTest`, a
live case to `ORMErrorMessagesTest`, and a row to the docs error catalog.

---

## 10b. Inspection BIFs, event veto and query options

**Inspection BIFs.** Eight read-only BIFs answer "what is this entity and what changed" without
touching Hibernate directly. All of them live in `bifs/` and delegate to one utility class,
`EntityInspector`, so the rules are in one place.

| BIF | Takes | Returns |
| --- | --- | --- |
| `entityGetName( entity )` | instance or name | The declared entity name (proxies answer without loading). |
| `entityGetDatasource( entity )` | instance or name | The datasource name. |
| `entityGetId( entity )` | instance | The id; a struct for composite ids; null when unsaved. A lazy proxy answers without loading. |
| `entityGetMetadata( entity )` | instance or name | Table, ids, version, discriminator, properties, associations. |
| `entityIsDirty( entity )` | instance | True when a persistent property differs from the database. |
| `entityGetDirtyProperties( entity )` | instance | The names of those properties. |
| `ormIsSessionDirty( [datasource] )` | datasource | True when a flush would write something. |
| `ormGetSessionStatistics( [datasource] )` | datasource | `entityCount`, `collectionCount`, `entityKeys`, `collectionKeys`. |

`EntityInspector.resolve` turns the argument into an `EntityRecord`: a string is looked up by name
(with "Did you mean"), an instance by its class, a proxy by its lazy initializer. Anything else, such
as a struct, is an `orm.argument` error. The BIFs that need state (`entityGetId`, `entityIsDirty`,
`entityGetDirtyProperties`) reject a name with `orm.argument`.

**Metadata is cached.** `ORMApp.getEntityMetadata` builds the struct once per entity and keeps it for
the life of the ORM application; `ormReload()` builds a new application and so a fresh cache. Each
call returns a deep copy, so callers may change it. Keys: `entityName`, `className`, `datasource`,
`tableName`, `schema`, `catalog`, `parent`, `readOnly`, `discriminator{column, value}`,
`idProperties`, `idType` (`composite` for more than one), `version`, `properties[]` (name, column,
ormtype, fieldtype, nullable, unique, length, precision, scale, formula, insertable, updatable),
`associations[]` (name, kind, target, cascade, lazy, inverse, fkcolumn, mappedBy, linkTable,
orderBy) and `propertyNames`.

**Dirty checking uses Hibernate's own SPI**, the same code Hibernate runs at flush:

- Managed entity: the entry's loaded state (`EntityEntry.getLoadedState`) against the current values
  with `EntityPersister.findDirty`. No SQL.
- Detached entity: one `select` (`EntityPersister.getDatabaseSnapshot`) and `findModified`.
- Never saved (no id), or an uninitialized proxy: not dirty (cborm parity).

Indexes map back to names through `persister.getPropertyNames()`, so collections and associations
are reported by their property name.

**Event veto.** `preInsert`, `preUpdate` and `preDelete` handlers (the entity's own method or the
global `eventHandler`) cancel the operation by returning `false` (also the strings `"false"` or
`"no"`). Returning nothing or anything else lets it continue, so existing `void` handlers are not
affected. `EventListener.announceVetoable` always calls both handlers, then returns `true` to
Hibernate when either vetoed (`ORMEventDispatcher.isVeto`). Hibernate's semantics are kept:

- A vetoed insert writes no row, but the entity stays in the session. Remove it
  (`ormGetSession().evict( entity )` or `ormClearSession()`) before changing it, or the next flush
  tries to update a row that does not exist (`orm.stale`).
- A vetoed update writes nothing and keeps its changes: the entity stays dirty and the update, and
  the `preUpdate` call, repeat on every flush. Call `entityReload( entity )` to discard the change.
- A vetoed delete keeps the row; the entity is removed from the session.
- An entity whose id comes from the database (`generator="identity"`) cannot skip its insert:
  Hibernate needs the `INSERT` to get the id. A veto there is an `orm.event.veto` error that says
  so, instead of Hibernate's bare `null identifier` assertion.

**Query options** are applied in one place, `HQLQuery.applyCacheAndTimeout`, for both
`ormExecuteQuery()` and `entityLoad()`:

- `cacheable`: cache the result in the second-level query cache (needs `secondaryCacheEnabled`).
- `cacheName` (alias `cacheRegion`): the cache region; implies `cacheable` unless `cacheable` is
  given explicitly. Previously ignored.
- `timeout`: seconds; `0` means none. Previously ignored by `ormExecuteQuery()`.
- `entityLoad()` `ignorecase` now wraps only text properties in `lower()` for the sort; numbers and
  dates are sorted as-is (`lower()` on them fails on some databases).

**`ormFlush( datasource )`** now flushes that datasource's session. It used to flush the default
one and ignore the argument.

**Tests.** `bifs/EntityInspectionBIFsTest` (46, live), `config/EventVetoTest` (17, live, fixtures
`VetoThing` and `VetoIdentityThing`, global veto in `events/EventHandler.bx`),
`HQLQueryOptionsTest` (unit, Mockito), `bifs/QueryOptionsTest` (live).

---

## 11. The ORM manifest boot cache (`.bxorm/`)

Boot has three costs: entity **discovery** (walking the tree), **metadata parsing** (per entity,
scales linearly with entity count), **mapping generation**, and Hibernate's own `SessionFactory`
build (a fixed floor we cannot avoid). Profiling a cold boot showed metadata parsing + mapping
generation dominate for large-entity apps, while Hibernate's build is a fixed ~2s floor. The
manifest cache targets the part we *can* remove.

**The `ormManifest` setting** (default `off`):

| Mode | Behavior |
| --- | --- |
| `off` | Discover, parse and generate every boot (unchanged legacy behavior). |
| `auto` | Discover normally, then write the resolved boot model to `.bxorm/manifest.json` so it stays current. Dev mode. |
| `trust` | Boot **straight from** `.bxorm/manifest.json` — no discovery, parsing or mapping generation. Integrity-checked and **fail-closed** (a missing/corrupt manifest is a hard error, never a silent fallback). Production mode. |

**Location.** By default the `.bxorm/` folder lives at the application root. Set `ormManifestLocation`
in `Application.bx` (`this.ormSettings`) to put it elsewhere: a relative path resolves against the
app root, an absolute path is used as-is, and the folder name is always `.bxorm` (only its parent
moves). The `bxorm` CLI mirrors this with `--dir=<path>`. `ManifestService.resolveFolder(context,
location)` is the single resolver all boot reads/writes go through.

**Performance (estimated).** These are extrapolations from cold-boot profiling, not a fresh
benchmark run — replace them with formal `./gradlew jmhCompare` numbers when available. A cold boot
pays four costs: entity **discovery**, **metadata parsing** (scales with entity count),
**mapping generation**, and **facade codegen** — plus Hibernate's own `SessionFactory` build, a
fixed **~2s floor** we cannot remove. Profiling showed the first four dominate for large-entity
apps. `trust` mode removes all four: it reads a single `manifest.json` and injects the pre-built
`facades.jar`, so boot collapses toward the ~2s Hibernate floor.

| App size | `off` cold boot (discover + parse + map + codegen + ~2s build) | `trust` cold boot (manifest read + ~2s build) | Estimated cold-start speedup |
| --- | --- | --- | --- |
| Small (a handful of entities) | Floor-dominated | ≈ floor | Modest — the fixed ~2s build dominates either way |
| Large (dozens of entities) | Parse/map/codegen is the **majority** of above-floor time | ≈ floor + one file read | **Considerable** — an estimated ~2–4× faster cold start |

The boost grows with entity count: the removed work scales with the number of entities, while
`trust` mode's cost (one manifest read + facade injection) is effectively flat. Zero entity-file
I/O and no ByteBuddy codegen at boot are the two levers.

**What the manifest stores** (`OrmManifest`, serialized as JSON via BoxLang's own `JSONUtil` so it
round-trips through BoxLang types): a format version, an ORM version stamp, a config fingerprint,
and per entity its name, class FQN, datasource, a source-file fingerprint (`path`/`hash`/`mtime`/
`size`), the **normalized metadata struct** (exactly what `AbstractEntityMeta.autoDiscoverMetaType`
consumes), and its generated `<entity>` mapping XML. Rehydration rebuilds each `IEntityMeta` from
the stored metadata and produces a **byte-identical** mapping — this invariant is the core test.

**Zero-I/O boot.** The combined Hibernate `mapping.xml` is now handed to Hibernate **in memory**
via `Configuration.addInputStream()` (previously a temp file was written and re-read). `EntityRecord`
carries its mapping XML in memory; a `trust`-mode boot reads the manifest (plus `facades.jar`) and
feeds Hibernate from memory. The only entity-file I/O is the staleness check below: one `stat` per
entity, and a re-hash only when size or mtime changed.

**Staleness guard.** Before a `trust` boot, `ManifestService.verify(manifest, config, version)`
compares the running app against the manifest and fails closed, naming every change:

- the ORM settings fingerprint (dialect, datasource, naming strategy, facade namespace i.e.
  application name, `dbcreate`, `quoteIdentifiers`, `entityPaths`);
- the bx-orm module version (skipped when either side is a `dev`/unreplaced build);
- each entity source file still at its recorded path (same size and mtime are trusted, otherwise it
  is re-hashed).

A source that is not at its recorded path (manifest generated on a build machine with a different
checkout) cannot be checked and is skipped, and newly added entity files are not detected (trust mode
does no discovery). Regenerate the manifest with an `auto` boot after changing entities.

**Integrity guard (v1).** `write()` is atomic (temp + move) and emits a `manifest.sha256` sidecar;
`read()` recomputes and compares it, failing closed on mismatch (detects corruption / naive edits).
Stronger tamper-resistance (an HMAC/signature keyed by a deploy secret) is a documented v2 option.

**`facades.jar` bytecode cache.** In `auto`, the ByteBuddy-generated facade bytecode is captured into
`.bxorm/facades.jar` after the session factories build (only facades with no live class initializer
are captured, see "Self-contained accessors" in §5); in `trust`, the jar's bytes are handed to that
build's `FacadeClassLoader`, which defines them instead of re-running codegen, with a transparent
ByteBuddy fallback if a class cannot be defined. ByteBuddy stays a dependency (it is only skipped at
runtime, not removed).

### The `bxorm` CLI

`box.json` declares the `bxorm` executable and `ModuleConfig.main()` dispatches to the Java
`ManifestCli`. Every verb inspects the on-disk `.bxorm/` boot cache **without booting Hibernate**.
The folder is resolved against the working directory, overridable with `--dir=<path>`.

```text
boxlang module:orm <verb> [args]      # or, via the declared executable:  bxorm <verb> [args]
```

| Verb | What it does |
| --- | --- |
| `info` (default) | Manifest header (versions, config fingerprint) + entity count |
| `validate` | Integrity- and format-check the manifest; non-zero exit on failure |
| `entities` | List the entities recorded in the manifest |
| `entity <name>` | Show one entity's class, datasource, source and mapping XML (case-insensitive) |
| `mappings` | Print the combined Hibernate `mapping.xml` |
| `clear` | Delete the `.bxorm/` boot cache |
| `version` | Module + manifest format versions |
| `help` | Show usage (aliases: `-h`, `--help`; `version` also as `-v`/`--version`) |

Generation is intentionally **not** a verb: `auto` mode writes the manifest on every boot, which is
the supported generation path.

#### Example output

Captured from real `ManifestCli` runs against a two-entity manifest (`Person`, `Passport`, no
`facades.jar`). Paths are shown as `/app/.bxorm` for readability.

```text
$ boxlang module:orm info
📦 ORM manifest [/app/.bxorm]
  format version   : 1
  built by ORM     : 2.0.0
  config fingerprint: 20ccab44a96d
  entities         : 2
  integrity        : ✅ checksummed
  facades.jar      : ➖ absent
```

```text
$ boxlang module:orm validate
✅ ORM manifest at [/app/.bxorm] is valid (2 entities, format 1).
```

A tampered `manifest.json` fails validation with exit code `1`:

```text
$ boxlang module:orm validate
❌ ORM manifest is INVALID: ORM manifest integrity check failed at [/app/.bxorm/manifest.json]: checksum mismatch. The manifest is corrupt or was modified; regenerate it.
```

```text
$ boxlang module:orm entities
📦 Entities in the ORM manifest (2):
  • Person  [models.Person]  datasource=appDB
  • Passport  [models.Passport]  datasource=appDB
```

The mapping XML below is truncated (`…`):

```text
$ boxlang module:orm entity person
🔎 Entity [Person]
  class     : models.Person
  datasource: appDB
  source    : {
  mtime : 1790166074296,
  path : "/app/models/Person.bx",
  size : 464,
  hash : "5fe43e29806515a3f0ea6fca47191cfba3a6f0a422b3654c0c1b5bda9089fdf8"
}
  mapping XML:
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!--
~ Generated by the Ortus BoxLang ORM module for use in BoxLang web applications.
~
~ https://github.com/ortus-boxlang/bx-orm
~ https://boxlang.io
~ https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html
--><entity-mappings version="7.0" xmlns="http://www.hibernate.org/xsd/orm/mapping">
    <entity class="ortus.boxlang.modules.orm.hibernate.facade.generated.default.PersonFacade" name="Person">
        <table name="feat_person"/>
…
```

`mappings` prints each entity's mapping document in turn (truncated here after the first ~15 lines):

```text
$ boxlang module:orm mappings
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!--
~ Generated by the Ortus BoxLang ORM module for use in BoxLang web applications.
~
~ https://github.com/ortus-boxlang/bx-orm
~ https://boxlang.io
~ https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html
--><entity-mappings version="7.0" xmlns="http://www.hibernate.org/xsd/orm/mapping">
    <entity class="ortus.boxlang.modules.orm.hibernate.facade.generated.default.PersonFacade" name="Person">
        <table name="feat_person"/>
        <batch-size>25</batch-size>
        <attributes>
            <id name="id">
                <column name="id"/>
                <generated-value generator="Person_id_generator"/>
…
```

```text
$ boxlang module:orm version
🏷️  bx-orm module : 2.0.0
manifest format expected : 1
manifest format on disk  : 1
manifest built by ORM    : 2.0.0
```

```text
$ boxlang module:orm help
📦 bxorm - ORM manifest boot-cache tool

Usage: boxlang module:orm <verb> [args]

Verbs:
  info                 Show the manifest header and entity count (default).
  validate             Integrity- and format-check the manifest; non-zero exit on failure.
  entities             List the entities recorded in the manifest.
  entity <name>        Show one entity's class, datasource, source and mapping XML.
  mappings             Print the combined Hibernate mapping XML.
  clear                Delete the .bxorm/ boot cache.
  version              Show module and manifest format versions.
  help                 Show this help.

The manifest is generated by booting your app once with ormManifest="auto";
in production, ormManifest="trust" loads it with no discovery or parsing.
```

An unknown verb exits with code `1` and prints the error followed by the same usage text:

```text
$ boxlang module:orm frobnicate
❌ Unknown verb [frobnicate].

📦 bxorm - ORM manifest boot-cache tool
…
```

```text
$ boxlang module:orm clear
🧹 Cleared the ORM boot cache at [/app/.bxorm].
```

After `clear`, `info` reports the absence (exit `0`), while `validate` fails closed (exit `1`):

```text
$ boxlang module:orm info
ℹ️  No ORM manifest found at [/app/.bxorm]. Boot the app once with ormManifest="auto" to generate it. If your app sets ormManifestLocation, point the CLI at it with --dir=<that folder>.
```

```text
$ boxlang module:orm validate
❌ No ORM manifest found at [/app/.bxorm]. Boot the app once with ormManifest="auto" to generate it. If your app sets ormManifestLocation, point the CLI at it with --dir=<that folder>.
```

**Auto-mode self-watcher.** In `auto` mode, `ORMApp.startup` calls `ORMService.ensureEntityWatcher`,
which starts one `ORMEntityWatcher` per application (idempotent) over the entity paths (recursive,
500ms debounce), built on BoxLang's `WatcherService`. A file-change event fires on a background
thread that has neither a request/JDBC context nor the `loadApplicationDescriptor`/`onRequestStart`
initialization a reload needs, so the watcher does not reload directly — attempting a reload there
fails to open a datasource connection. Instead its listener flags the app for reload
(`ORMService.dirtyApps`). `ORMService.getORMAppByContext` — which runs on a real request thread with
a fully-initialized, thread-valid context — sees the flag, clears it (`Set.remove`, so a single
request reloads while concurrent ones do not), and calls `reloadApp`. This is also the correct
semantics: a reload is only observable through a request (edit a file, hit the app, see the change).
The watcher is owned by the `ORMService`, not the `ORMApp`, so a reload (which swaps the `ORMApp` but
not the entity paths) leaves it running and it keeps detecting changes across reloads; it is stopped
only on a real `shutdownApp`. Generated `*.orm.xml` and any `/.bxorm/` path are ignored by the
listener so a reload's own writes never retrigger it. Startup is wrapped so a runtime without a
watcher service just logs a warning and disables live reload. Class: `ORMEntityWatcher`; wired via
`ORMService.ensureEntityWatcher`/`stopEntityWatcher` and the reload check in `getORMAppByContext`.

Code: `ortus.boxlang.modules.orm.mapping.manifest` (`OrmManifest`, `ManifestService`, `ManifestCli`)
and `ortus.boxlang.modules.orm.config.ORMEntityWatcher`; wired in `ORMApp.startup` →
`resolveEntityMap`, `EntityFacadeFactory` (facade jar load/write), `ModuleConfig.main`,
`ORMService.ensureEntityWatcher`, and the auto-reload check in `ORMService.getORMAppByContext`.

## 12. Where we go next

- **cborm-compatible path** — the V2 plan: new BIFs (Phase 2), the fluent `entityCriteria()` (Phase 3),
  GORM-inspired additions, dynamic finders, cborm calling bx-orm BIFs, a live cborm test run, and
  opt-in static class helpers. Casting is not a phase: bx-orm casts ids itself and Hibernate 7 coerces
  query parameters, so cborm's `idCast()`/`autoCast()` just return their value.

---

## 13. Where the code lives

| Area | Package / path |
| --- | --- |
| BIFs | `ortus.boxlang.modules.orm.bifs` |
| Config, events, dialects, naming | `ortus.boxlang.modules.orm.config` |
| Facade bridge (representation, proxies, wrap/unwrap) | `ortus.boxlang.modules.orm.hibernate` and `…/hibernate/facade` |
| Mapping generation & the modern writer | `ortus.boxlang.modules.orm.mapping` |
| Errors and diagnostics | `ortus.boxlang.modules.orm.errors` (`ORMException`, `ORMErrorType`, `ORMErrors`), `bifs/ORMDiagnostics` |
| ORM manifest boot cache (`.bxorm/`) | `ortus.boxlang.modules.orm.mapping.manifest` |
| Session lifecycle | `ORMService` → `ORMApp` → `ORMContext`, `SessionFactoryBuilder` |
| Subsystem deep-dives | `.agents/skills-custom/` |

> **Before committing:** run `gradle spotlessApply` (Java formatting) and
> `npx markdownlint-cli2 "**/*.md"` (Markdown), and add a `changelog.md` entry under
> `## [Unreleased]`.
