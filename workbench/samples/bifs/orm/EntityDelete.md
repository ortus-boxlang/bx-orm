Delete an entity by passing the entity object to `entityDelete()`:

```java
entityDelete( entityLoadByPK( "Vehicle", "1HGCM82633A123456" ) );
```

Note that this operation will also remove associated child entities depending on the `cascade` configuration in the entity property mapping. In this case, we wish a deletion of a blog post to also delete all associated comments:

```java
property
    name="comments"
    cfc="Comment"
    fieldtype="one-to-many"
    inverse="true"
    cascade="delete";
...
entityDelete( entityLoadByPK( "blogPost", "779ccbb8-a444-11eb-ab6f-0290cc502ae3" ) );
```