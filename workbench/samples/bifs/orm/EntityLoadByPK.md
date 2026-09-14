### Load by Primary Key
Load entity by primary key:

```java
vehicle = entityLoadByPK( "Vehicle", "1HGCM82633A123456" );
```

### Load by Composite Key

Pass a struct when the entity key is composite.

```java
vehicleType = entityLoadByPK( "VehicleType", { make: "Ford", model: "Fusion" } );
```