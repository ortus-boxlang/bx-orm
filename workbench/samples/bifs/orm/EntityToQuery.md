### Convert a Single Entity
Pass one loaded entity to get a single-row query result.

```java
result = entityToQuery( entityLoadByPK( "Vehicle", "1HGCM82633A123456" ), "Vehicle" );
```

### Convert an Entity Array
Pass an array of entities to get one row per entity.

```java
result = entityToQuery( entityLoad( "Vehicle", { Make = "Honda" } ), "Vehicle" );
```

You can also omit the entity name and let the function infer it from the entity type:

```java
result = entityToQuery( entityLoadByPK( "Vehicle", "1HGCM82633A123456" ) );
```

Note that for performance reasons we recommend passing the entity name explicitly.