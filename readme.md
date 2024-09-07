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

All boxlang class files located in your entity path directory(ies) are considered non-persistent by default. To mark an entity as persistent, use the `@Entity` annotation.

```js
@Entity
class {
...
}
```

For drop-in compatibility with CFML engines, we also support the historical `persistent` annotation:
```js
class persistent="false" {
...
}
```

But once a class is marked as persistent using `@Entity` or `persistent=true`, properties within that entity class file are considered persistent unless annotated otherwise:

```js
@Entity
class {
	// this property is persistent by default:
	property name="name", type="string";
}
```

Use the `Transient` annotation to mark a specific property as NOT persistable:

```js
@Entity
class {
	// this property is persistent by default:
	property name="name", type="string";

	// this property is NOT persistent
	@Transient
	property name="foo", type="string";
}
```

Once again, we support the historical `persistent="false"` syntax for CFML compatibility:

```js
@Entity
class {
	// this property is persistent by default:
	property name="name", type="string";

	// this property is NOT persistent
	property name="foo", type="string" persistent="false";
}
```

### Entity Annotations

There are a large number of annotations supported by bx-orm which allow great flexibility over how your entities are loaded, processed, and persistent by Hibernate ORM.

## Entity Annotations

### @Entity

Specifies that the class is an ORM entity.

```js
@Entity
class {
	// entity properties and methods
}
```

### Persistent (Legacy)

Specifies that the class is an ORM entity.

```js
class persistent="true" {
	// entity properties and methods
}
```

> *Legacy - prefer `@Entity` or `@Transient` syntax.*

### @Transient - Coming Soon

Specifies that the class is NOT a persistent entity. This is the default for boxlang and CFML ORM applications, but sometimes spelling it out is the right way to go. 😉

```js
@Transient
class myRegularBoxlangClass {
	// ...
}
```

### @Discriminator

Specifies discriminator data for the entity hierarchy. Supported keys are `name`, `type`, `value`, `force`, `insert`, `formula`. [See Hibernate 5 Discriminator docs](https://docs.jboss.org/hibernate/orm/5.0/manual/en-US/html/ch05.html#mapping-declaration-discriminator), but be aware we've simplified the key names for BL.

```js
@Discriminator {
	"name": "myDiscriminatorColumn",
	"value": "myDiscriminatorValue",
	"formula": "discriminatorFormula"
	"type": "string",
	"force": true,
	"insert": true,
}
```

### @DiscriminatorValue

Specifies the value of the discriminator column for an entity. 

```js
class discriminatorValue="foo" {
	// entity properties and methods
}
```

> *Legacy - prefer `@Discriminator { "value" : "foo" }` syntax.*

### DiscriminatorColumn

Specifies the discriminator column for the entity hierarchy.

```js
class discriminatorColumn="foobar" {
	// entity properties and methods
}
```

> *Legacy - prefer `@Discriminator { "name" : "foobar" }` syntax.*

> *Not yet supported, please check back for updates.*

### @Table

Specifies the table name for the annotated entity.

```js
@Table("tableName")
class MyEntity {
	// entity properties and methods
}
```

### @Immutable

Specifies that the entity or mapped superclass is immutable.

```js
@Immutable
class MyEntity {
	// entity properties and methods
}
```

### @Readonly (Legacy)

Specifies that the entity or mapped superclass is readonly, aka "immutable".

```js
class readonly="true" MyEntity {
	// entity properties and methods
}
```

> *Legacy - prefer `@Immutable`*

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

## FieldType (Legacy)

Specify a specific type of ORM property:

* `column` - The default. This property is a "simple" database column-backed ORM property.
* `id` - Designate this property as the primary key.
* `version` - Designate this property as a entity versioning strategy. Can only be used once per entity.
* `timestamp` - Same as `version`, but for datetime fields.
* `one-to-one` - This property represents a one-to-one relationship with another entity type
* `one-to-many` - This property represents a one-to-many relationship with another entity type
* `many-to-many` - This property represents a many-to-many relationship with another entity type
* `many-to-one` - This property represents a many-to-one relationship with another entity type
* `collection` - 

```js
property
	name="invoices"
	fieldtype="one-to-many"
	cfc="models.orm.Invoice"
	fkcolumn="invoiceID";
```

> *Legacy - prefer `@MyFieldType`, for example `@Version` or `@ManyToMany`*

## @Column

Designate this property as a simple column property.

```js
@Column
property name="title";
```

Replaces the traditional `fieldtype="column"` syntax.

## @Id

Designate this property as the primary key.

```js
@Id
property name="id";
```

Replaces the traditional `fieldtype="id"` syntax.

## @Version

Designate this property as a versioning value to use when versioning the entity. Can only be used on a single property per entity.

```js
@Version
property name="version";
```

Replaces the traditional `fieldtype="version"` syntax.

## Development

To get started hacking on boxlang-orm:

1. Clone the repo
2. Copy the latest boxlang binary jar to `src/test/resources/libs/boxlang-1.0.0-all.jar`
3. Copy/unzip the latest JDBC module of your choice to `src/test/resources/libs/modules/`, for example `src/test/resources/modules/bx-derby`.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
