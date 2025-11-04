### Creating Simple Entities

Creating an instance of an ORM entity is as simple as calling `entityNew()` with the name of the entity you wish to create. For example, to create a new instance of an entity named `Auto`, you would do the following:

```java
var myEntity = entityNew( "Auto" );
```

To populate the entity with initial values, you can pass a struct of properties as the second argument:

```java
var myEntity = entityNew( "Auto", {
    Make = "Toyota",
    Model = "Camry",
    Year = 2020
} );
```

### Ignoring Extra Properties

(Coming soon) By default, `ignoreExtra` is set to `false`, meaning that if you provide properties that do not exist on the entity, an error will be thrown. 

For example, this will throw an error if `Color` is not a defined property on the `Auto` entity:

```java
var myEntity = entityNew( "Auto", {
    Make = "Toyota",
    Model = "Camry",
    Year = 2020,
    Color = "Blue"
}, true );
```
To change this behavior, you can set the `ignoreExtra` argument to `true`:

```java
var myEntity = entityNew( "Auto", {
    Make = "Toyota",
    Model = "Camry",
    Year = 2020,
    Color = "Blue"
}, true );
```

This will create the entity and ignore the `Color` property if it does not exist on the `Auto` entity.