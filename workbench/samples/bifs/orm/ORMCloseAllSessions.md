```java
mySession = ormGetSession();
alternateSession = ormGetSession( "admin" );

ormCloseAllSessions();
```