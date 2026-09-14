Clear the ORM session for the default datasource:

```java
ormClearSession();
```

Since an ORM session is datasource-specific, you can clear a specific session by passing the datasource name:

```java
ormClearSession( "hr" );
```