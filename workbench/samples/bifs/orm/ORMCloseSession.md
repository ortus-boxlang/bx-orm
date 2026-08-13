Close the ORM session for the default datasource:

```java
ormCloseSession();
```

Close the ORM session for a secondary, named datasource:

```java
ormCloseSession( "admin" );
```