```java
entitySave( entityNew( "Manufacturer", { name: "Dodge", address: "101 Dodge Circle" } ) );
entitySave( entityNew( "AlternateDS", { id: createUUID(), name: "test" } ) );

ormFlushAll();
```