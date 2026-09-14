### Evict All Query Caches
Call with no arguments to clear all query cache regions.

```java
ormEvictQueries();
```

### Evict by Region and Datasource Cache
You can target a specific cache region and datasource.

```java
ormEvictQueries( "queries", "admin" );
```