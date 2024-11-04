# BoxLang ORM

## Table Of Contents

* [Configuration](./Configuration.md)
* [Entity Mapping](./Mapping.md)

## Compatibility

BoxLang ORM strives to be 100% backwards-compatible with the Adobe ColdFusion and Lucee Server Hibernate ORM implementations. With that said, several older syntaxes and setting names have been changed (with backwards-compatible aliases) for further improvements.

For more details, see:

* [Configuration Compatibility](./Configuration.md#compatibility)
* [Entity Mapping Compatibility](./Mapping.md#compatibility)
* [BIF Compatibility](./bifs/README.md#compatibility)

## NEW Functionality

`bx-orm` improves on traditional CFML engines with some additional functionality. Check out the list here:

* [BIFs](#bifs)

### BIFS

* `entityNameArray()` supports an optional `datasource` argument to filter the result by datasource
* `entityNameList()` supports an optional `datasource` argument to filter the result by datasource

## Entity Mapping CLI

Test your entity mapping capabilities via this command:

```sh
java -cp /path/to/bx-orm-1.0.0-all.jar:/path/to/boxlang-1.0.0-snapshot-all.jar \
    ortus.boxlang.modules.orm.tools.MappingGenerator \
    --path /my/app/models/orm
```

Where `/path/to/bx-orm-1.0.0-all.jar` is the path to the compiled bx-orm .jar file, and `/path/to/boxlang-1.0.0-snapshot-all.jar` is the path to the compiled boxlang .jar file.

Options:

* `--path MY_PATH` - Required. Relative path to the ORM entity files.
* `--failFast` - Inverse of the legacy CFML configuration `skipCFCWithError`. If `--failFast` is passed, the .xml generation will abort if any entity class files fail to parse.
* `--mapping foo:/path/to/foo` - Specify a mapping to a module or other location. This may be necessar for resolving `extends=` values if you are extending a class from another directory.
* `--debug` - Spit out debug logging. Not necessary for your first run, but this may be helpful in debugging mapping errors.

Here's a full example:

```
java -cp /path/to/bx-orm-1.0.0-all.jar:/path/to/boxlang-1.0.0-snapshot-all.jar \
    ortus.boxlang.modules.orm.tools.MappingGenerator \
    --mapping MYMODULE:./modules/MYMODULE/ \
    --mapping cborm:./modules/cborm \
    --path ./models/orm \
    --debug
```