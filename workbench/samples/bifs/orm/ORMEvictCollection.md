Evict all cached entries for a collection mapping:

```java
ormEvictCollection( "Manufacturer", "vehicles" );
```

You can also pass a primary key to evict the collection for a specific entity:

```java
record = entityLoadByPK( "Manufacturer", 1 );
ormEvictCollection( "Manufacturer", "vehicles", record.getId() );
```