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

## Development

To get started hacking on boxlang-orm:

1. Clone the repo
2. Copy the latest boxlang binary jar to `src/test/resources/libs/boxlang-1.0.0-all.jar`
3. Copy/unzip the latest JDBC module of your choice to `src/test/resources/libs/modules/`, for example `src/test/resources/modules/bx-derby`.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
