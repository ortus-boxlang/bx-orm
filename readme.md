# ⚡︎ BoxLang Module: BoxLang ORM

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

## BoxLang ORM

### Configuration

Boxlang ORM supports the `cfcLocation` setting, but only for backwards compatibility. You should use the new `entityPaths` setting instead:

```js
// Application.bx
this.ormSettings = {
  entityPaths: ["models/"],
  datasource : "testDB"
};
```

## Persistent vs non-Persistent ORM Entities and Properties

> *For the purposes of this section, "persistent" here means that it is manageable by Hibernate ORM and worthy of populating FROM as well as persistent TO a database record upon saving the entity.*

By default, all boxlang class files located in your entity path directory(ies) are considered persistent. To expressly mark a class file as not an ORM entity, use the `persistent` annotation:

```js
@Persistent false
class {
...
}
// OR
class persistent="false" {
...
}
```

In the same vein, properties within an entity class file are considered persistent unless annotated otherwise:

```js
class {
	// this property is persistent by default:
	property name="name", type="string";

	// this property is NOT persistent
	@Persistent false
	property name="name", type="string";
	
	// or
	property name="name", type="string", persistent="false";
}
```

### Entity Annotations

There are a large number of annotations supported by bx-orm which allow great flexibility over how your entities are loaded, processed, and persistent by Hibernate ORM.

## Entity Annotations

### @Discriminator

Specifies discriminator data for the entity hierarchy. Supported keys are `name`, `type`, `value`, `force`, `insert`, `formula`. [See Hibernate 5 Discriminator docs](https://docs.jboss.org/hibernate/orm/5.0/manual/en-US/html/ch05.html#mapping-declaration-discriminator), but be aware we've simplified the syntax (key names) for BL.

```js
@Discriminator {
	"name": "discriminatorName",
	"type": "string",
	"value": "discriminatorValue",
	"force": true,
	"insert": true,
	"formula": "discriminatorFormula"
}
```

### @DiscriminatorValue

Specifies the value of the discriminator column for an entity. 

```js
@DiscriminatorValue("discriminatorValue")
```

> *Legacy - prefer `@Discriminator { "value" : "foo" }` syntax.*

### @DiscriminatorColumn

Specifies the discriminator column for the entity hierarchy.


```js
@DiscriminatorColumn("discriminatorColumnName")
```

> *Legacy - prefer `@Discriminator { "name" : "foobar" }` syntax.*

### @Entity

Specifies that the class is an entity. Since CFML and BoxLang both use entity paths to treat classes within a directory as persistent by default, this is mostly a no-op... but is still good practice for "documentation as code" purposes.

```js
@Entity
class MyEntity {
	// entity properties and methods
}
```

### @Transient - Coming Soon

Specifies that the class is NOT a persistent entity. Override the persistent-by-default nature of boxlang-orm.

```js
@Transient
class myRegularBoxlangClass {
	// ...
}
```

> *Not yet supported, please check back for updates.*

### @Table

Specifies the table name for the annotated entity.

```js
@Table("tableName")
class MyEntity {
	// entity properties and methods
}
```

### @Readonly or @Immutable

Specifies that the entity or mapped superclass is immutable.

```js
@Immutable
class MyEntity {
	// entity properties and methods
}
// OR
class readonly="true" MyEntity {
	// entity properties and methods
}
```

## Property Annotations

### Column (string)

Specifies the name of the database column. You can normally leave this blank, as bx-orm will use the property name as the column name.

```js
@Column "foo"
private name="bar";

// Older style
property name="bar" column="foo";
```

### NotNull (boolean)

Indicates that the column cannot contain null values.

```js
@NotNull
private name="bar";

// Older style
property name="bar" notNull="true";
```

### UnsavedValue (string)

Specifies the value that indicates an unsaved entity.

```js
@UnsavedValue "bar"
private name="bar";

// Older style
property name="bar" unsavedValue="foo";
```

### Check (string)

Adds an arbitrary SQL expression check constraint to the column.

```js
@check "foo > 0"
private name="bar";

// Older style
property name="bar" check="foo > 0";
```

### DBDefault (any)

Specifies the default value for the column in the database.

```js
@DBDefault "undefined"
private name="bar";

// Older style
property name="bar" dbDefault="undefined";
```

### Length (numeric)

Defines the length of a string column. For string columns, this will influence the `CHAR` or `VARCHAR` length.

```js
@length 255
private name="bar";

// Older style
property name="bar" length="255";
```

### Precision (numeric)

Specifies the precision for a decimal column.

```js
@Precision 10
private name="bar";

// Older style
property name="bar" precision="10";
```

### Scale (numeric)

Specifies the scale for a decimal column.

```js
@Scale "2"
private name="bar";

// Older style
property name="bar" scale="2";
```

### Sqltype (string)

Defines the SQL type of the column.

```js
@Sqltype "varchar"
private name="bar";

// Older style
property name="bar" sqltype="varchar";
```

### Unique (boolean)

Set a unique constraint on this column.

```js
@Unique
private name="bar";

// Older style
property name="bar" unique="true";
```

### Insert (boolean)

Specify whether the property should be included in SQL INSERT statements. Boolean.

```js
@insert false
property name="name";
property name="name" insert="false";
```

### Update (boolean)

Specify whether the property should be included in SQL UPDATE statements. Boolean.

```js
@update false
property name="name";
// OR
property name="name" update="false";
```

## Development

To get started hacking on boxlang-orm:

1. Clone the repo
2. Copy the latest boxlang binary jar to `src/test/resources/libs/boxlang-1.0.0-all.jar`
3. Copy/unzip the latest JDBC module of your choice to `src/test/resources/libs/modules/`, for example `src/test/resources/modules/bx-derby`.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
