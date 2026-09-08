Check whether an entity is attached to the current ORM session:

```java
manufacturer = entityNew( "Manufacturer", { name: "Audi Corp", address: "101 Audi Way" } );
attachedBeforeSave = entityIsAttached( manufacturer ); // false

entitySave( manufacturer );
attachedAfterSave = entityIsAttached( manufacturer ); // true
```

Clear the session and check the entity again:

```java
ormClearSession();
attachedAfterClear = entityIsAttached( manufacturer ); // false
```

For entities mapped to another datasource, `entityIsAttached()` checks that entity's datasource session:

```java
alternateEntity = entityNew( "AlternateDS", { id: createUUID(), name: "Alternate Entity" } );
entitySave( alternateEntity );

attached = entityIsAttached( alternateEntity ); // true
ormClearSession( "dsn2" );
attachedAfterClear = entityIsAttached( alternateEntity ); // false
```
