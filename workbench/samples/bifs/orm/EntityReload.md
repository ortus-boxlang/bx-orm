Reload an entity by passing the entity object directly:

```java
manufacturer = entityLoadByPK( "Manufacturer", 1 );
reloaded = entityReload( manufacturer );
```

Reload all in-session entities by entity name:

```java
manufacturer = entityLoadByPK( "Manufacturer", 1 );
reloaded = entityReload( "manufacturer" );
```