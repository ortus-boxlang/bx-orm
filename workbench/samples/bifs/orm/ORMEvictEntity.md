### Evict All Entities by Type
Clear second-level cache entries for a full entity type.

```java
ormEvictEntity( "Manufacturer" );
```

### Evict Entity by Primary Key
Pass the primary key to remove a single entity cache entry.

```java
record = entityLoadByPK( "Manufacturer", 1 );
ormEvictEntity( "Manufacturer", record.getId() );
```