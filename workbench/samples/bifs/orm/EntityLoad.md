## Loading an Entity Array

To load an array of Autos from the database where the `Make` is `Ford`, you would do the following:

```java
var allFords = entityLoad( "Auto", { Make = "Ford" } );
```

By default, all matching records are returned. If you want to paginate the result set, you can pass a struct with query options as the fourth argument:

```java
var firstTenFords = entityLoad(
    "Auto",                         // first arg: entity name
    { Make = "Ford" },              // second arg: criteria struct
    false,                          // third arg: false for "not unique"
    { maxResults = 10, offset = 0 } // fourth arg: option struct
);
```

### Loading Unique Entities

We can also load single, unique entity results by passing `true` in the third argument to indicate that we want a single, unique result. For example, to load an entity named `Auto` with an ID of `123`, you would do the following:

```java
var myEntity = entityLoad( "Auto", 123, true );
```

This will retrieve the entity from the database and populate the `myEntity` variable with its data. If the entity is not found, an error will be thrown.

You can also use the `entityLoad()` function to load an entity by its unique properties, even if they are not the primary key. For example, to load an `Auto` entity with a specific `VIN`, you could do the following:

```java
var myEntity = entityLoad( "Auto", { VIN = "1HGCM82633A123456" }, true );
```

This will search for the `Auto` entity with the specified `VIN` and return it if found.