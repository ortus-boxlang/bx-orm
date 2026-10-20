# ORM Observability Events

bx-orm announces events for SQL, flushes and failures so tools (debuggers, monitors, profilers) can listen instead of reaching into Hibernate.
Register a normal BoxLang interceptor for the event names below.

There is no cost when nothing is listening. JDBC objects are only wrapped when a listener exists at the moment a connection is acquired, and
payloads are built lazily.

## Events

| Event | Fires | Payload |
| --- | --- | --- |
| `onORMQuery` | After each JDBC statement. Selects fire when the result set is closed, once the row count is known. | See below |
| `onORMFlush` | After each Hibernate session flush. | `inserts`, `updates`, `deletes`, `elapsedNanos`, `datasource`, `appName` |
| `onORMException` | When a statement fails. | `error`, `sql`, `datasource`, `appName` |

### `onORMQuery` payload

| Key | Description |
| --- | --- |
| `sql` | The SQL text sent to the driver. |
| `kind` | `select`, `insert`, `update`, `delete`, `ddl` or `other`. |
| `elapsedNanos` | Execution time in nanoseconds. Excludes time spent reading rows. |
| `rows` | Rows returned for selects, rows affected for DML, `-1` when unknown (for example DDL). |
| `datasource` | The datasource name. |
| `appName` | The unique ORM application name, as used by `ORMService.getORMApp()`. |
| `hql` | The HQL being run, when the statement came from `ormExecuteQuery()`. Otherwise `null`. |
| `entityName` | The entity being loaded, when the statement came from `entityLoad()` or `entityLoadByPK()`. Otherwise `null`. |
| `error` | The `Throwable` when the statement failed, otherwise `null`. |
| `params` | Ordered array of bound parameter values. Only present when `announceQueryParams` is `true`. |

A failed statement announces `onORMQuery` with `error` set, and `onORMException`. This includes SQL the database rejects while the statement is
being prepared.

Startup DDL (for example `dbcreate="dropcreate"`) is announced too, as long as the listener is registered before the application starts.

### `onORMFlush` counts

`inserts`, `updates` and `deletes` count the statements run during the flush, not entities. JDBC batching can make them differ from the number of
entities changed.

### Example

```javascript
// OrmWatcher.bx
class {

    function onORMQuery( data ) {
        // 100 ms
        if ( data.elapsedNanos > 100000000 ) {
            writeLog( text="Slow ORM #data.kind#: #data.sql#", type="warning" );
        }
    }

    function onORMException( data ) {
        writeLog( text="ORM failure on #data.datasource#: #data.error.getMessage()#", type="error" );
    }

}
```

Register it with `boxRegisterInterceptor()`, naming the events it listens to:

```javascript
boxRegisterInterceptor(
    interceptor : new OrmWatcher(),
    points      : [ "onORMQuery", "onORMException" ]
);
```

## Settings

Set these in `this.ormSettings`. Both default to `false`.

| Setting | Description |
| --- | --- |
| `announceQueryParams` | Include bound parameter values in `onORMQuery`. Values can be sensitive, so they are never announced unless you ask. |
| `generateStatistics` | Collect Hibernate statistics at startup, for `ORMService.getStatistics()`. Collection has a small cost. |

## Statistics

`ORMService.getStatistics( appName )` returns a struct of `appName` and a `datasources` struct with one entry per datasource. When statistics are
not collected, each entry is just `{ enabled: false }`.

Statistics can also be switched on and off at runtime with `ORMService.setStatisticsEnabled( appName, enabled )`. Counters start from zero when
switched on.

Enabled entries contain: `enabled`, `startTime`, `sessionOpenCount`, `sessionCloseCount`, `flushCount`, `connectCount`, `prepareStatementCount`,
`transactionCount`, `successfulTransactionCount`, `queryExecutionCount`, `queryExecutionMaxTime`, `queryExecutionMaxTimeQueryString`,
`entityLoadCount`, `entityFetchCount`, `entityInsertCount`, `entityUpdateCount`, `entityDeleteCount`, `collectionLoadCount`,
`collectionFetchCount`, `secondLevelCacheHitCount`, `secondLevelCacheMissCount`, `secondLevelCachePutCount`, `queryCacheHitCount` and
`queryCacheMissCount`.

## Other ORM events

`ORMPreConfigLoad` and `ORMPostConfigLoad` are now registered as interception points as well. `post_new` is announced when an entity is created.
