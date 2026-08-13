Flush the ORM session for the default datasource:

```java
ormFlush();
```

Flush the ORM session for a secondary, named datasource:

```java
ormFlush( "admin" );
```