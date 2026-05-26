---
name: bx-orm-bif-development
description: "Use when creating ORM built-in functions (BIFs): extending BaseORMBIF, @BoxBIF annotation, invoke() method, argument patterns (entityName, idOrFilter, options), entity name resolution, ORMService access, ORMContext lookup, session handling, HQL execution, entity CRUD operations (EntityLoad, EntitySave, EntityDelete, EntityNew, EntityReload), and ORM utility BIFs (ORMEvictEntity, ORMFlush, ORMClearSession, ORMGetSession)."
version: "1.0.0"
domain: bx-orm
triggers: ORM BIF, entityLoad, entitySave, entityDelete, entityNew, entityReload, entityMerge, entityToQuery, ormFlush, ormClearSession, ormCloseSession, ormGetSession, ormExecuteQuery, BaseORMBIF, orm built-in function
role: expert
scope: bx-orm
related-skills: boxlang-core-dev-bif-development, bx-orm-session-management, bx-orm-hibernate-bridge
---

# BoxLang ORM — BIF Development

## Overview

ORM BIFs (Built-In Functions) provide the public API for BoxLang developers to interact with the ORM. They wrap Hibernate session operations — entity CRUD, session management, HQL queries — in BoxLang-callable functions that resolve the correct ORM context and datasource transparently.

## BIF Inventory

| Category | BIFs |
|---|---|
| **Entity CRUD** | `EntityNew`, `EntityLoad`, `EntityLoadByPK`, `EntityLoadByExample`, `EntitySave`, `EntityDelete`, `EntityMerge`, `EntityReload`, `EntityNameArray`, `EntityToQuery` |
| **Session Management** | `ORMGetSession`, `ORMGetSessionFactory`, `ORMCloseSession`, `ORMCloseAllSessions`, `ORMClearSession`, `ORMFlush`, `ORMFlushAll`, `ORMReload` |
| **Cache & Eviction** | `ORMEvictEntity`, `ORMEvictCollection`, `ORMEvictQueries` |
| **HQL Queries** | `ORMExecuteQuery` |
| **Metadata** | `ORMGetHibernateVersion` |

## BaseORMBIF — The Parent Class

All ORM BIFs extend `BaseORMBIF`, which extends the core `BIF` class and provides shared access to the ORM service:

```java
public abstract class BaseORMBIF extends BIF {

    protected ORMService ormService = (ORMService) runtime
        .getGlobalService( ORMKeys.ORMService );

    /**
     * Pull the entity name from a BoxLang class.
     */
    protected String getEntityName( IClassRunnable entity ) {
        return ORMService.getEntityName( entity );
    }

    /**
     * Retrieve the simple class name from a fully-qualified name.
     */
    protected String getClassNameFromFQN( String fqn ) {
        return ORMService.getClassNameFromFQN( fqn );
    }
}
```

## BIF Structure Pattern

Every ORM BIF follows this pattern:

```java
@BoxBIF
public class EntityLoad extends BaseORMBIF {

    // 1. Declare arguments in the constructor
    public EntityLoad() {
        super();
        declaredArguments = new Argument[] {
            new Argument( true,  "string", ORMKeys.entityName ),
            new Argument( false, "any",    ORMKeys.idOrFilter ),
            new Argument( false, "any",    ORMKeys.uniqueOrOrder ),
            new Argument( false, "struct", ORMKeys.options )
        };
    }

    // 2. Implement invoke() with context and arguments
    @Override
    public Object invoke( IBoxContext context, ArgumentsScope arguments ) {
        // 3. Resolve the ORM app and context
        ORMApp ormApp = ormService.getORMAppByContext( context );
        if ( ormApp == null ) {
            throw new BoxRuntimeException( "No ORM application configured" );
        }

        // 4. Get the ORM context (request/thread-scoped)
        IJDBCCapableContext jdbcContext = context
            .getParentOfType( IJDBCCapableContext.class );
        ORMContext ormContext = ORMContext.getForContext( jdbcContext );

        // 5. Resolve entity name
        String entityName = arguments.getAsString( ORMKeys.entityName );

        // 6. Get session for the appropriate datasource
        Key datasource = ormApp.getEntityDatasource( entityName );
        Session session = ormContext.getSession( datasource );

        // 7. Perform the operation
        // ...

        // 8. Return BoxLang-compatible result
        return result;
    }
}
```

## Entity CRUD BIF — Detailed Reference

### EntityNew

Creates a new entity instance without persisting:

```java
@BoxBIF
public class EntityNew extends BaseORMBIF {
    // Signature: entityNew( entityName, [properties] )
    // Returns: IClassRunnable (the new entity)
}
```

```js
// Usage
var vehicle = entityNew( "Vehicle", {
    make  : "Toyota",
    model : "Camry"
} )
// vehicle is NOT yet persisted; call entitySave( vehicle ) to persist
```

### EntityLoad

Loads entities with optional filtering, sorting, and pagination:

```java
@BoxBIF
public class EntityLoad extends BaseORMBIF {
    // Signature: entityLoad( entityName, [idOrFilter], [uniqueOrOrder], [options] )
    // Returns: IClassRunnable or Array (based on unique flag)
}
```

Options struct:
```js
var options = {
    unique     : false,    // Return single entity?
    ignorecase : false,    // Case-insensitive sort?
    offset     : 0,        // Pagination offset
    maxresults : null,     // Max results to return
    cacheable  : false,    // Use second-level cache?
    cachename  : null,     // Cache region name
    timeout    : null      // Query timeout (seconds)
}
```

### EntityLoadByPK

Loads a single entity by its primary key:

```java
@BoxBIF
public class EntityLoadByPK extends BaseORMBIF {
    // Signature: entityLoadByPK( entityName, id )
    // Returns: IClassRunnable or null
}
```

### EntitySave

Persists a new or updated entity:

```java
@BoxBIF
public class EntitySave extends BaseORMBIF {
    // Signature: entitySave( entity, [forceInsert] )
    // forceInsert: bypass dirty-checking, always INSERT
}
```

### EntityDelete

Removes an entity from the database:

```java
@BoxBIF
public class EntityDelete extends BaseORMBIF {
    // Signature: entityDelete( entity )
}
```

### EntityReload

Refreshes an entity from the database, discarding in-memory changes:

```java
@BoxBIF
public class EntityReload extends BaseORMBIF {
    // Signature: entityReload( entity )
}
```

### EntityMerge

Merges a detached entity state:

```java
@BoxBIF
public class EntityMerge extends BaseORMBIF {
    // Signature: entityMerge( entity )
}
```

## Session Management BIFs

### ORMFlush / ORMFlushAll

```java
@BoxBIF
public class ORMFlush extends BaseORMBIF {
    // Signature: ormFlush( [datasource] )
    // Flushes pending changes for the specified datasource (or default)
}

@BoxBIF
public class ORMFlushAll extends BaseORMBIF {
    // Signature: ormFlushAll()
    // Flushes all pending changes across all datasources
}
```

### ORMClearSession

```java
@BoxBIF
public class ORMClearSession extends BaseORMBIF {
    // Signature: ormClearSession( [datasource] )
    // Clears the session (detaches all entities)
}
```

### ORMCloseSession / ORMCloseAllSessions

```java
@BoxBIF
public class ORMCloseSession extends BaseORMBIF {
    // Signature: ormCloseSession( [datasource] )
}

@BoxBIF
public class ORMCloseAllSessions extends BaseORMBIF {
    // Signature: ormCloseAllSessions()
}
```

### ORMEvictEntity / ORMEvictCollection / ORMEvictQueries

Evict entities or query results from the session and/or second-level cache:

```java
@BoxBIF
public class ORMEvictEntity extends BaseORMBIF {
    // Signature: ormEvictEntity( [entityName], [id] )
    // Evicts specific entity or all entities from cache
}
```

## HQL BIF

### ORMExecuteQuery

Executes a raw HQL query:

```java
@BoxBIF
public class ORMExecuteQuery extends BaseORMBIF {
    // Signature: ormExecuteQuery( hql, [params], [unique], [options] )
    // params: Array (positional) or Struct (named)
}
```

```js
// Positional parameters
var results = ormExecuteQuery(
    "FROM Vehicle WHERE make = ? AND year > ?",
    [ "Toyota", 2020 ]
)

// Named parameters
var results = ormExecuteQuery(
    "FROM Vehicle WHERE make = :make AND year > :year",
    { make : "Toyota", year : 2020 }
)

// Unique result
var vehicle = ormExecuteQuery(
    "FROM Vehicle WHERE id = :id",
    { id : 42 },
    true  // unique
)
```

## Argument Patterns

### Required Pattern: entityName as first argument

Almost every entity BIF takes `entityName` as its first required argument:

```java
new Argument( true, "string", ORMKeys.entityName )
```

### Common Pattern: options as last argument

Optional behavior is grouped into an `options` struct:

```java
new Argument( false, "struct", ORMKeys.options )
```

### Overloaded Pattern: idOrFilter

`EntityLoad` accepts either an ID (string/number) or a filter struct as the second argument:

```java
new Argument( false, "any", ORMKeys.idOrFilter )
```

## Creating a New ORM BIF

```java
package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF
public class EntityCount extends BaseORMBIF {

    public EntityCount() {
        super();
        declaredArguments = new Argument[] {
            new Argument( true,  "string", ORMKeys.entityName ),
            new Argument( false, "struct", ORMKeys.filter )
        };
    }

    @Override
    public Object invoke( IBoxContext context, ArgumentsScope arguments ) {
        String entityName = arguments.getAsString( ORMKeys.entityName );

        ORMApp ormApp = ormService.getORMAppByContext( context );
        if ( ormApp == null ) {
            throw new BoxRuntimeException( "No ORM application configured" );
        }

        IJDBCCapableContext jdbcContext = context
            .getParentOfType( IJDBCCapableContext.class );
        ORMContext ormContext = ORMContext.getForContext( jdbcContext );

        Key datasource = ormApp.getEntityDatasource( Key.of( entityName ) );
        Session session = ormContext.getSession( datasource );

        Criteria criteria = session.createCriteria(
            ormApp.getEntityClass( entityName )
        );

        // Apply filter criteria
        if ( arguments.containsKey( ORMKeys.filter ) ) {
            IStruct filter = arguments.getAsStruct( ORMKeys.filter );
            filter.forEach( ( key, value ) -> {
                criteria.add( Restrictions.eq( key.getName(), value ) );
            } );
        }

        Long count = (Long) criteria
            .setProjection( Projections.rowCount() )
            .uniqueResult();

        return count;
    }
}
```

## File Locations

```
src/main/java/ortus/boxlang/modules/orm/bifs/
├── BaseORMBIF.java            # Parent class for all ORM BIFs
├── EntityNew.java
├── EntityLoad.java
├── EntityLoadByPK.java
├── EntityLoadByExample.java
├── EntitySave.java
├── EntityDelete.java
├── EntityMerge.java
├── EntityReload.java
├── EntityNameArray.java
├── EntityToQuery.java
├── ORMExecuteQuery.java
├── ORMGetSession.java
├── ORMGetSessionFactory.java
├── ORMFlush.java
├── ORMFlushAll.java
├── ORMClearSession.java
├── ORMCloseSession.java
├── ORMCloseAllSessions.java
├── ORMEvictEntity.java
├── ORMEvictCollection.java
├── ORMEvictQueries.java
├── ORMReload.java
└── ORMGetHibernateVersion.java
```

## Registration

ORM BIFs are auto-discovered by BoxLang's module system via the `META-INF/services` file:

```
src/main/resources/META-INF/services/ortus.boxlang.runtime.bifs.BIF
```

Each line in this file is the fully-qualified class name of a BIF:
```
ortus.boxlang.modules.orm.bifs.EntityNew
ortus.boxlang.modules.orm.bifs.EntityLoad
ortus.boxlang.modules.orm.bifs.EntitySave
...
```

## Best Practices

1. **Always extend `BaseORMBIF`** — never extend `BIF` directly for ORM functions; `BaseORMBIF` provides shared ORM service access and entity name resolution.
2. **Resolve ORM context explicitly** — use `context.getParentOfType( IJDBCCapableContext.class )` to find the JDBC-capable context, then get the ORM context from it.
3. **Guard against null `ORMApp`** — throw a descriptive `BoxRuntimeException` if no ORM application is configured, rather than letting a NullPointerException surface.
4. **Use `ORMKeys` for argument names** — all argument names should reference `ORMKeys` constants.
5. **Support both positional and named HQL parameters** — in `ORMExecuteQuery` and similar BIFs, detect whether `params` is an `Array` (positional) or `IStruct` (named).
6. **Return BoxLang-native types** — return `IClassRunnable`, `Array`, `IStruct`, `Boolean`, or `Number`; never raw Hibernate objects.
