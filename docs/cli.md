# Entity Mapping CLI

Test your entity mapping capabilities via this command:

```sh
java -cp /path/to/bx-orm-1.0.0-all.jar:/path/to/boxlang-1.0.0-snapshot-all.jar \
    ortus.boxlang.modules.orm.cli.GenerateMappings \
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
    ortus.boxlang.modules.orm.cli.GenerateMappings \
    --mapping MYMODULE:./modules/MYMODULE/ \
    --mapping cborm:./modules/cborm \
    --path ./models/orm \
    --debug
```
