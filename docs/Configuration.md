# Configuration

To point Boxlang ORM to your entity class files, use the `entityPaths` setting:

```js
// Application.bx
this.ormSettings = {
  entityPaths: ["models/"],
};
```

For backwards compatibility, Boxlang ORM supports the `cfcLocation` alias as well:

```js
// Application.bx
this.ormSettings = {
  // LEGACY:
  cfcLocation: ["models/"],
};
```

In addition, the `skipCFCWithError` configuration setting has been renamed to `ignoreParseErrors`:

```js
// Application.bx
this.ormSettings = {
  ignoreParseErrors: true
};
```

Once again, the historical `skipCFCWithError` key is still supported in our compatibility layer:

```js
// Application.bx
this.ormSettings = {
  // LEGACY:
  skipCFCWithError: true
};
```

## Breaking Changes

The following setting defaults are changed in BoxLang from the traditional CFML defaults:

* `flushAtRequestEnd` - Defaults to `true` in ACF and Lucee, defaults to `false` in BoxLang
* `autoManageSession` - Defaults to `true` in ACF and Lucee, defaults to `false` in BoxLang
* `skipCFCWithError` - Defaults to `true` in ACF and Lucee. In BoxLang, this is implemented as `ignoreParseErrors`, which defaults to `false`.

## Compatibility

The following ORM settings have been renamed:

* `cfcLocation` -> `entityPaths`
* `skipCFCWithError` -> `ignoreParseErrors`

The following CFML interface names/paths have been changed as well:

* `CFIDE.orm.IEventHandler` ->  `orm.models.IEventHandler`
* `lucee.runtime.orm.naming.NamingStrategy` ->  `orm.models.INamingStrategy`.

## NEW Settings

The following ORM settings are brand-new for BoxLang and do not exist in either Adobe ColdFusion or Lucee Server:

* `enableThreadedMapping` - Enable the use of threading to process ORM entities in parallel, greatly speeding up an ORM load.