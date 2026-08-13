```java
vehicle = entityLoadByPK( "Vehicle", "1HGCM82633A123456" );
ormGetSession().detach( vehicle );

vehicle.setModel( "Accord EX" );
entityMerge( vehicle );
ormFlush();
```