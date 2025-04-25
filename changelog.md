# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

## [1.0.8] - 2025-04-25

### ⭐ Added

- Set hibernate version in build so `ORMGetHibernateVersion()` stays accurate - See [a8c7c16](https://github.com/ortus-boxlang/bx-orm/commit/a8c7c16d8b3ee766ab182aad490909a5509f10e4)

### 🐛 Fixed

- Foreign key must have same number of columns as the referenced primary key - Resolves [BLMODULES-41](https://ortussolutions.atlassian.net/browse/BLMODULES-41)
- Missing FKColumn on To-Many Relationship Should Check the Inverse Relationship for Column data - Resolves [BLMODULES-42](https://ortussolutions.atlassian.net/browse/BLMODULES-42)
- XMLWriter - Skip id,composite-id XML rendering on subclasses - Resolves [BLMODULES-38](https://ortussolutions.atlassian.net/browse/BLMODULES-38)
- XML Writer - Skip generator on composite keys - Resolves [BLMODULES-40](https://ortussolutions.atlassian.net/browse/BLMODULES-40)
- XMLWriter - Don't set insert or update on one-to-one elements - Resolves [BLMODULES-39](https://ortussolutions.atlassian.net/browse/BLMODULES-39)
- Fix support for 'params' attribute string notation - See [BLMODULES-40](https://ortussolutions.atlassian.net/browse/BLMODULES-40)

## [1.0.7] - 2025-04-14

### 🐛 Fixed

- Fix string casting error on `lazy` property annotation

## [1.0.6] - 2025-04-14

### 🐛 Fixed

- Fixed support for custom naming strategies - See [8e68206](https://github.com/ortus-boxlang/bx-orm/commit/8e68206e3d3f197a69fc12467c42c7c5de1c7eac)
- Fixed "smart" naming strategy when entity name begins with an uppercase character - See [b47b512](https://github.com/ortus-boxlang/bx-orm/commit/b47b51239a15530df245c5e12c36c48e10b09266)
- Move compat configuration to bx-compat-cfml - See [c8b7173](https://github.com/ortus-boxlang/bx-orm/commit/c8b7173f1c0fc01646d3b3d980d9d889ab8c7686)
- Fixed the two types of discriminator generation order - See [ea62a62](https://github.com/ortus-boxlang/bx-orm/commit/ea62a62fe1f4fe66bce58b4e27659b60faccb1aa)
- fix bag element being appended to wrong node on subclasses - See [f82b2ac](https://github.com/ortus-boxlang/bx-orm/commit/f82b2ac24e5d9cf1f43da5a8437c481be5e4f0c5)
- change to use caster so that lazy=true does not error - See [0096387](https://github.com/ortus-boxlang/bx-orm/commit/00963873c44480e6597ac0e3962d66244c42c865)

### ⭐ Added

- Add missing `date` property type - See [c6ec8a2](https://github.com/ortus-boxlang/bx-orm/commit/c6ec8a2e2dadfb344deb93edb7a1a2ccf8d0fb46)
- Add alternate spellings for big decimal and big integer - See [5e199f9](https://github.com/ortus-boxlang/bx-orm/commit/5e199f9e5674c3a3802a5e225d45f187b0724e23)
- Add flush after commit on transaction end - See [e2df378](https://github.com/ortus-boxlang/bx-orm/commit/e2df378c261a2c0aea99749d7bf04cd688d57658)

## [1.0.5] - 2025-04-07

### 🐛 Fixed

- Removed debugging code

## [1.0.4] - 2025-04-06

### 🐛 Fixed

- Metadata parsing throws error on empty class despite `skipCFCWithError` setting - Resolves [BLMODULES-37](https://ortussolutions.atlassian.net/browse/BLMODULES-37)

## [1.0.3] - 2025-04-06

### 🐛 Fixed

- EntityLoad returning incorrect results with criteria struct filter on parent properties - Resolves [BLMODULES-36](https://ortussolutions.atlassian.net/browse/BLMODULES-36)
- Hibernate Criteria Querys using `get` are returning proxies instead of the entity - Resolves [BLMODULES-35](https://ortussolutions.atlassian.net/browse/BLMODULES-35)
- ensure proxies in session are expanded when a load is requested - See [5b07e2c](https://github.com/ortus-boxlang/bx-orm/commit/5b07e2c1f0bf2bb4f3cb3c5fd15f15cee9bfd01d)
- Error on first ORM request after Application Timeout - Resolves [BLMODULES-30](https://ortussolutions.atlassian.net/browse/BLMODULES-30)
- BoxProxy Struct Implementation causes validation exceptions - Resolves [BLMODULES-33](https://ortussolutions.atlassian.net/browse/BLMODULES-33)

## [1.0.2] - 2025-04-04

No significant changes.

## [1.0.1] - 2025-04-04

### ⭐ Added

- Allow options as third arg to ORMExecuteQuery - See [b5efc84](https://github.com/ortus-boxlang/bx-orm/commit/b5efc840df6ddc96e87dd2d18b1bd3acd4de6002)
- Add handling for not null on to-one relationship - See [6792fb0](https://github.com/ortus-boxlang/bx-orm/commit/6792fb0e81a11105ce056803f2b28b873546ec02)

### 🐛 Fixed

- Attempt casting `uniqueOrOrder` to string in EntityLoad BIF - See [98f6734](https://github.com/ortus-boxlang/bx-orm/commit/98f67344e0df0d808f6bb749b4ae20b2cc8c9734)
- Ignore null `uniqueOrOrder` argument in EntityLoad BIF - See [394d9ba](https://github.com/ortus-boxlang/bx-orm/commit/394d9ba907a016103949da5a5d157ffb14672d61)
- Fix chicken/egg issues with app startup by lazy-initializing the EventHandler - See [699f15b](https://github.com/ortus-boxlang/bx-orm/commit/699f15b8c82704f8e101d1d1ee38be541e5ae618)
- WrongClassException when re-querying for the same object in a session - Resolves [BLMODULES-12](https://ortussolutions.atlassian.net/browse/BLMODULES-12)
- Disable `not-null` annotation usage on one-to-one relationships - See [c512848](https://github.com/ortus-boxlang/bx-orm/commit/c512848bba331c6282a5a5c5c2b99271b3f28863)
- fix explicit nulls on setters - See [819fffb](https://github.com/ortus-boxlang/bx-orm/commit/819fffbe58fb576e630f29d001aec5a38d8bf1b4)
- Auto-generated `has` methods are overriding declared methods in ORM entities - Resolves [BLMODULES-31](https://ortussolutions.atlassian.net/browse/BLMODULES-31)
- `x-to-one` generated `hasX()` methods are not returning the correct values - Resolves [BLMODULES-32](https://ortussolutions.atlassian.net/browse/BLMODULES-32)

## [1.0.0] - 2025-03-26

- First iteration of this module

[Unreleased]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.8...HEAD

[1.0.8]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.7...v1.0.8

[1.0.7]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.6...v1.0.7

[1.0.6]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.5...v1.0.6

[1.0.5]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.4...v1.0.5

[1.0.4]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.3...v1.0.4

[1.0.3]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.2...v1.0.3

[1.0.2]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.1...v1.0.2

[1.0.1]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.0...v1.0.1

[1.0.0]: https://github.com/ortus-boxlang/bx-orm/compare/2fe797c6330a5d110f3bfbc5ead058df9bdbe89e...v1.0.0
