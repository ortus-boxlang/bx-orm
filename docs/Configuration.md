# Configuration

To point Boxlang ORM to your entity class files, use the `entityPaths` setting:

```js
// Application.bx
this.ormSettings = {
  entityPaths: ["models/"],
};
```

## CFML Compatibility

The following ORM settings have been renamed:

* `cfcLocation` -> `entityPaths`
* `skipCFCWithError` -> `ignoreParseErrors`

The following CFML interface names/paths have been changed as well:

* `CFIDE.orm.IEventHandler` ->  `orm.models.IEventHandler`
* `lucee.runtime.orm.naming.NamingStrategy` ->  `orm.models.INamingStrategy`.

And, the following setting defaults are changed in BoxLang from the traditional CFML defaults:

* `flushAtRequestEnd` - Defaults to `true` in ACF and Lucee, defaults to `false` in BoxLang
* `autoManageSession` - Defaults to `true` in ACF and Lucee, defaults to `false` in BoxLang
* `skipCFCWithError` - Defaults to `true` in ACF and Lucee. In BoxLang, this is implemented as `ignoreParseErrors`, which defaults to `false`.

To support these legacy setting names and/or default settings, install the [bx-compat-cfml](https://forgebox.io/view/bx-compat-cfml) module.

## NEW Settings

The following ORM settings are brand-new for BoxLang and do not exist in either Adobe ColdFusion or Lucee Server:

* `enableThreadedMapping` - Enable the use of threading to process ORM entities in parallel, greatly speeding up an ORM load. Default `true`.
* `quoteIdentifiers` - Enable quoted identifiers (table names, column names, etc.) to avoid erroring on reserved words. Default `true`.