---
name: bx-orm-event-system
description: "Use when working with the ORM event system: Hibernate EventListener (Integrator), event type registration (PRE_INSERT, POST_LOAD, etc.), global entity listeners, TransactionManager interceptor (transaction lifecycle), ApplicationListener (app startup/shutdown), event arg construction, handler invocation, BoxLang interceptor pools, and event announcer integration."
version: "1.0.0"
domain: bx-orm
triggers: event listener, hibernate integrator, entity event, preInsert, postLoad, preUpdate, preDelete, TransactionManager, ApplicationListener, ORM transaction, event type registration, EventListenerRegistry, global listener, entity handler, interception point, onTransactionBegin
role: expert
scope: bx-orm
related-skills: bx-orm-session-management, bx-orm-configuration, boxlang-core-dev-interceptors
---

# BoxLang ORM — Event System

## Overview

The ORM event system bridges Hibernate's native event model with BoxLang's interception framework. It registers a Hibernate `Integrator` that hooks into every Hibernate lifecycle event (pre-insert, post-load, etc.) and translates those events into BoxLang interception announcements and entity-level event handler invocations.

## Architecture

```mermaid
flowchart TB
    subgraph "Hibernate Events"
        A["Hibernate Operation<br/>(save, load, delete, flush, evict)"]
    end
    A --> B["EventListener<br/>(Integrator)"]
    B --> C{"Has global listener?"}
    C -->|Yes| D["Invoke global listener<br/>(preLoad, postLoad, etc.)"]
    C -->|No| E["Skip global"]
    D --> F["Announce BoxLang<br/>interception point"]
    E --> F
    F --> G["BoxLang InterceptorService"]
    G --> H["TransactionManager<br/>(tx begin/commit/rollback)"]
    G --> I["ApplicationListener<br/>(app start/shutdown)"]

    subgraph "Entity-Level"
        B --> J{"Entity has handler?"}
        J -->|Yes| K["Invoke entity handler<br/>(preInsert, postUpdate, etc.)"]
        J -->|No| L["Skip entity handler"]
    end
```

## Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `EventListener` | `ortus.boxlang.modules.orm.config` | Hibernate `Integrator`; registers for all event types; dispatches to global & entity listeners |
| `TransactionManager` | `ortus.boxlang.modules.orm.interceptors` | BoxLang interceptor; manages Hibernate transaction lifecycle |
| `ApplicationListener` | `ortus.boxlang.modules.orm.interceptors` | BoxLang interceptor; manages ORM app startup/shutdown |

## EventListener — Hibernate Integrator

The `EventListener` implements `Integrator` and a dozen Hibernate event listener interfaces, giving it a hook into every phase of the entity lifecycle:

```java
public class EventListener
    implements Integrator,
               PreInsertEventListener, PostInsertEventListener,
               PreDeleteEventListener, PostDeleteEventListener, DeleteEventListener,
               PreUpdateEventListener, PostUpdateEventListener,
               PreLoadEventListener, PostLoadEventListener,
               FlushEventListener, AutoFlushEventListener,
               ClearEventListener, DirtyCheckEventListener, EvictEventListener {

    private DynamicObject globalListener;  // Optional global BoxLang listener
    private boolean       listenerReady;
}
```

### Event Registration (in `integrate()`)

```java
@Override
public void integrate( Metadata metadata, SessionFactoryImplementor sessionFactory,
                       SessionFactoryServiceRegistry serviceRegistry ) {
    EventListenerRegistry registry = serviceRegistry.getService( EventListenerRegistry.class );

    registry.prependListeners( EventType.PRE_INSERT, this );
    registry.prependListeners( EventType.POST_INSERT, this );
    registry.prependListeners( EventType.PRE_DELETE, this );
    registry.prependListeners( EventType.POST_DELETE, this );
    registry.prependListeners( EventType.DELETE, this );
    registry.prependListeners( EventType.PRE_UPDATE, this );
    registry.prependListeners( EventType.POST_UPDATE, this );
    registry.prependListeners( EventType.PRE_LOAD, this );
    registry.prependListeners( EventType.POST_LOAD, this );
    registry.prependListeners( EventType.AUTO_FLUSH, this );
    registry.prependListeners( EventType.FLUSH, this );
    registry.prependListeners( EventType.EVICT, this );
    registry.prependListeners( EventType.CLEAR, this );
    registry.prependListeners( EventType.DIRTY_CHECK, this );
}
```

### Event Dispatch Pattern

Each event handler follows the same pattern: build an args struct, optionally invoke the global listener, then invoke the entity-level handler:

```java
@Override
public boolean onPreInsert( PreInsertEvent event ) {
    IClassRunnable entity = unwrapEntity( event.getEntity() );
    IStruct args = Struct.of(
        "entity", entity
    );

    // 1. Global listener (if configured)
    if ( globalListener != null ) {
        globalListener.inoke( "preInsert", args );
    }

    // 2. Entity-level handler (if entity has a handler method)
    invokeEntityEvent( entity, "preInsert", args );

    return false;  // false = don't veto the operation
}
```

### Entity Unwrapping

Always unwrap `BoxProxy` before dispatching events:

```java
private IClassRunnable unwrapEntity( Object entity ) {
    if ( entity instanceof BoxProxy proxy ) {
        return proxy.getRunnable();
    }
    if ( entity instanceof IClassRunnable runnable ) {
        return runnable;
    }
    return null;  // Shouldn't happen for BoxLang entities
}
```

### Global Listener Setup

The global listener is lazily instantiated from the `eventHandler` configuration:

```java
private void ensureListenerReady() {
    if ( listenerReady ) return;

    String eventHandlerClass = ormConfig.getEventHandler();
    if ( eventHandlerClass != null && !eventHandlerClass.isEmpty() ) {
        this.globalListener = CLASS_LOCATOR
            .loadClass( eventHandlerClass )
            .invokeConstructor()
            .getTarget();
    }
    this.listenerReady = true;
}
```

## TransactionManager — Transaction Lifecycle

`TransactionManager` is a BoxLang interceptor that listens to BoxLang transaction events and translates them into Hibernate session transaction operations:

```java
public class TransactionManager extends BaseInterceptor {

    @InterceptionPoint
    public void onTransactionBegin( IStruct args ) {
        IBoxContext context = args.getAs( IBoxContext.class, Key.context );
        ORMApp ormApp = ormService.getORMAppByContext( context );

        ORMContext ormContext = ORMContext.getForContext( jdbcContext );

        ormApp.getDatasources().forEach( datasource -> {
            Session session = ormContext.getSession( datasource );

            if ( config.autoManageSession ) {
                session.flush();  // Flush pending ops before tx start
            }

            if ( session.isJoinedToTransaction() ) {
                return;  // Already in a transaction (nested txs not supported)
            }

            session.beginTransaction();
        } );
    }
}
```

### Transaction Events

| Interception Point | Hibernate Action |
|---|---|
| `onTransactionBegin` | `session.beginTransaction()` for each datasource |
| `onTransactionCommit` | `session.getTransaction().commit()` |
| `onTransactionRollback` | `session.getTransaction().rollback()` |
| `onTransactionEnd` | Cleanup (close sessions if auto-managed) |
| `onTransactionSetSavepoint` | `session.setSavepoint( name )` |
| `onTransactionRollbackSavepoint` | `session.rollbackToSavepoint( name )` |

### Auto-Managed Session Behavior

When `autoManageSession=true`:
```java
if ( config.autoManageSession ) {
    session.flush();  // Flush pending operations before transaction start
}
```

This provides Lucee compatibility:
- [extension-hibernate Transaction.java](https://github.com/Ortus-Solutions/extension-hibernate/blob/857aef3/extension/src/main/java/ortus/extension/orm/HibernateORMTransaction.java#L48)

## ApplicationListener — Application Lifecycle

`ApplicationListener` is a BoxLang interceptor that orchestrates ORM application creation and shutdown:

```java
public class ApplicationListener extends BaseInterceptor {

    @InterceptionPoint
    public void onApplicationStart( IStruct args ) {
        IBoxContext context = args.getAs( IBoxContext.class, Key.context );
        ormService.startupORMApplication( context );
    }

    @InterceptionPoint
    public void onApplicationEnd( IStruct args ) {
        IBoxContext context = args.getAs( IBoxContext.class, Key.context );
        ormService.shutdownORMApplication( context );
    }

    @InterceptionPoint
    public void onSessionStart( IStruct args ) {
        // Initialize request-scoped ORM context
    }

    @InterceptionPoint
    public void onSessionEnd( IStruct args ) {
        // Tear down request-scoped ORM context
    }
}
```

## Adding a New Hibernate Event Type

To listen to a Hibernate event type not yet covered by `EventListener`:

1. Implement the corresponding Hibernate listener interface (e.g., `RefreshEventListener`).
2. Register it in the `integrate()` method:
   ```java
   registry.prependListeners( EventType.REFRESH, this );
   ```
3. Implement the callback method:
   ```java
   @Override
   public void onRefresh( RefreshEvent event ) throws HibernateException {
       IStruct args = Struct.of( "entity", unwrapEntity( event.getEntity() ) );
       invokeEntityEvent( args.getAs( IClassRunnable.class, "entity" ), "postRefresh", args );
   }
   ```

## Custom Entity Event Handler

BoxLang entity classes can receive ORM events by implementing handler methods:

```js
// models/Vehicle.bx
class {
    property name="id" fieldtype="id" generator="increment";
    property name="make";
    property name="model";

    function preInsert() {
        log.info( "About to insert Vehicle: #this.make# #this.model#" )
    }

    function postLoad() {
        log.info( "Loaded Vehicle: #this.make# #this.model#" )
    }

    function preUpdate( struct oldData ) {
        log.info( "Updating Vehicle from #serializeJSON(oldData)#" )
    }
}
```

The `EventListener` automatically discovers and invokes these methods when the corresponding Hibernate event fires.

## File Locations

```
src/main/java/ortus/boxlang/modules/orm/
├── config/
│   └── EventListener.java           # Hibernate Integrator
└── interceptors/
    ├── TransactionManager.java      # BoxLang tx lifecycle interceptor
    └── ApplicationListener.java     # BoxLang app lifecycle interceptor
```

## Best Practices

1. **Always unwrap `BoxProxy` before event dispatch** — check `entity instanceof BoxProxy` and call `.getRunnable()` before passing to BoxLang handlers.
2. **Event handlers should not throw** — catch exceptions in event handlers to prevent Hibernate operations from failing due to listener errors.
3. **`requiresPostCommitHanding()` returns `false`** — BoxLang entities don't require post-commit handling; only override if your use case needs it.
4. **Use `prependListeners` not `appendListeners`** — prepending ensures the bx-orm listener runs before any other registered listeners.
5. **Global listeners are lazy** — the global `eventHandler` class is only instantiated on the first event, not at ORM startup.
6. **Transaction events fire per-datasource** — `TransactionManager` iterates all datasources in the ORM app; ensure all sessions participate.
