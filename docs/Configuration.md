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

## Compatibility

The following ORM settings have been renamed:

* `cfcLocation` -> `entityPaths`
* `skipCFCWithError` -> `ignoreParseErrors`