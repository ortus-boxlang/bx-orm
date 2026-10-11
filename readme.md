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

## Important Links

* [Documentation](https://bxorm.ortusbooks.com/)
* [ForgeBox](https://forgebox.io/view/bx-orm)
* [API docs](https://apidocs.ortussolutions.com/#/boxlang-modules/bx-orm/)
* [Source Code](https://github.com/ortus-boxlang/bx-orm)
* [Issue Tracker](https://ortussolutions.atlassian.net/browse/BLMODULES)

## Observability

bx-orm announces `onORMQuery`, `onORMFlush` and `onORMException` events for SQL and flush activity, and exposes Hibernate statistics through `ORMService.getStatistics()`. See [ORM Observability Events](docs/observability-events.md).

## Installing

```bash
box install bx-orm
# or, outside CommandBox
install-bx-module bx-orm
```

bx-orm 2.0 (Hibernate 7.4, BoxLang 1.18.0+) is in pre-release for testing. Ask for a pre-release by version, for example `box install bx-orm@2.0.0-beta.1`; the [releases page](https://github.com/ortus-boxlang/bx-orm/releases) lists them.

## Development

* **Branches:** 2.0 work happens on `next`; send pull requests there. `development` carries 1.x until 2.0.0 is final.
* **Requirements:** Java 21 and BoxLang 1.18.0. `./gradlew installModuleDependencies` downloads the BoxLang runtime jar and the `bx-mysql` module the tests use.
* **Database:** `docker-compose up -d mysql` starts the MySQL instance the test suite uses. Derby and SQLite tests are embedded.
* **Build and test:** `./gradlew build` (or `./gradlew test --tests '<pattern>'`). Run `./gradlew spotlessApply` before committing.
* **Pre-releases:** maintainers publish `2.0.0-<label>.N` from `next` with **Actions > Pre-Release (next)**.
* **Architecture and design:** see [DEVELOPER.md](DEVELOPER.md) and the skills in `.agents/skills-custom/`.

## Skills

This project includes AI agent skills in `.agents/skills/` to assist with development. To install them for use by your agents:

```bash
npx skills experimental_install
```

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
