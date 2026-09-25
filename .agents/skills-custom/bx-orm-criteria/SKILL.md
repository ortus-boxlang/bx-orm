---
name: bx-orm-criteria
description: "Use when working on entityCriteria(), the fluent query builder: CriteriaBuilder (recorder, path/join resolution, HQL compiler, terminals), CriteriaMethods (the BoxLang method table, aliases, named args, not*/with* prefixes), CriteriaProjections, EntityModel (metamodel paths and did-you-mean), HqlParts (where-clause nodes), SqlCapture (getSQL through a StatementInspector), SqlFormat, and HQLQuery.ofNumbered/prepare."
version: "1.0.0"
domain: bx-orm
triggers: entityCriteria, criteria, CriteriaBuilder, CriteriaMethods, CriteriaProjections, EntityModel, HqlParts, SqlCapture, SqlFormat, getSQL, peekSQL, logSQL, withProjections, project, anyOf, allOf, subquery, paginate, chunk, each, pluck, with{Association}, createCriteria, cborm criteria
role: expert
scope: bx-orm
related-skills: bx-orm-bif-development, bx-orm-session-management, bx-orm-hibernate-bridge
---

# BoxLang ORM — entityCriteria()

## Shape

`entityCriteria( "Vehicle" )` returns a `CriteriaBuilder` (Java, `criteria/`). It is a **recorder**: building methods
change it and return it; terminal methods compile one HQL query, run it through `HQLQuery.ofNumbered(...).prepare(true)`
and never change the builder; `copy()` branches it.

| Class | Role |
| --- | --- |
| `CriteriaBuilder` | State (aliases, joins, where tree, projections, orders, paging, options, steps), path resolution, HQL compile, terminals, `getSQL`, `toString` |
| `CriteriaMethods` | Every BoxLang method: names and aliases, parameter names (named args), `not<Condition>` and `with<Association>` prefixes, did-you-mean for unknown methods, Java-method fallback (`getClass` for dumps) |
| `CriteriaProjections` | The `p` object of `project( ( p ) => ... )` |
| `EntityModel` | Hibernate JPA metamodel wrapper: attributes (case-insensitive), kind (basic, to-one, to-many, values, component), id name |
| `HqlParts` | Where-clause nodes: `Frag` (text, `Param`, subquery), `Group` (and/or), `Not`; `RenderContext` numbers `?1..?n` |
| `SqlCapture` | `StatementInspector` registered in `SessionFactoryBuilder`; while capturing it records the first SQL and throws `Captured`, so nothing runs |
| `SqlFormat` | Line breaks for `getSQL( format = true )` |

## Rules

- BoxLang reaches the builder through `IReferenceable` (`dereferenceAndInvoke`), not Java reflection. Add a method with
  `def( "name|alias", "param|alt,param2", isCondition, ( c, x, a ) -> ... )` in `CriteriaMethods`.
- Paths are resolved when the condition is added (`resolve`): case fixed from the metamodel, unknown names raise
  `orm.property.unknown` with did-you-mean. Dotted paths join: conditions inner, inside `anyOf`/`not` left
  (`optionalDepth`), order/projection left. Joins are keyed by `parentAlias.attribute` and reused. `assoc.id` uses the
  foreign key, no join.
- HQL aliases are internal (`bx_this`, `bx_jN`, `bx_sN`); user aliases map to them. In a subquery, unqualified paths
  start at the subquery entity and `this.` is the outer root.
- Values are always bound (`Param`), wrapped as `{ value : v }` so `QueryParameter` never reads a struct or entity as
  options. Association values (id or entity) are resolved by `HQLQuery`. Hibernate coerces types.
- Entity rows with a referenced to-many join are `select distinct`; `count()` uses `count(distinct root)`; fetch-only
  joins are skipped outside entity lists.
- `chunk`/`each` read in batches (keyset by id when there is no order, else offset), flush + clear after each batch.
- `getSQL` refuses when the app configured its own statement inspector.
- Events: `beforeCriteriaBuilderList/Count/Get`, `after...`, `onCriteriaBuilderAddition` (registered in `ORMService`).

## Tests

`src/test/java/ortus/boxlang/modules/orm/criteria/` (live, MySQL/MariaDB): Conditions, Joins, Shape, Terminals,
Subquery, Developer. Shared helpers in `CriteriaTestSupport`.
