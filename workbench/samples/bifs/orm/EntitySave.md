### Save a New Entity
Create a new entity and persist it in one flow.

```java
entitySave( entityNew( "Manufacturer", { name: "Audi Corp", address: "101 Audi Way" } ) );
```

### Force Insert Behavior
Use the second argument when you need explicit insert-oriented behavior.

```java
manufacturer = entityNew( "Manufacturer", { name: "Volvo", address: "123 Main St" } );
entitySave( manufacturer, true );
```