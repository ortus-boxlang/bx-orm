Load all matching entities using a sample entity as the filter.

```java
sample = entityNew( "Manufacturer", { name: "Honda" } );
matches = entityLoadByExample( sample );
```

Pass `unique=true` to return a single matching entity:

```java
sample = entityNew( "Manufacturer", { name: "Honda" } );
singleMatch = entityLoadByExample( sample, true );
```