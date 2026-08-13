Omitting a datasource name will return the default session factory:

```java
factory = ormGetSessionFactory();
```

Pass a datasource name when working with alternate datasources within the ORM app:

```java
factory = ormGetSessionFactory( "admin" );
```