# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

### 🐛 Fixed

* Foreign key must have same number of columns as the referenced primary key - Resolves [BLMODULES-41](https://ortussolutions.atlassian.net/browse/BLMODULES-41)
* Missing FKColumn on To-Many Relationship Should Check the Inverse Relationship for Column data - Resolves [BLMODULES-42](https://ortussolutions.atlassian.net/browse/BLMODULES-42)
* XMLWriter - Skip id,composite-id XML rendering on subclasses - Resolves [BLMODULES-38](https://ortussolutions.atlassian.net/browse/BLMODULES-38)
* XML Writer - Skip generator on composite keys - Resolves [BLMODULES-40](https://ortussolutions.atlassian.net/browse/BLMODULES-40)
* XMLWriter - Don't set insert or update on one-to-one elements - Resolves [BLMODULES-39](https://ortussolutions.atlassian.net/browse/BLMODULES-39)
* Fix support for 'params' attribute string notation - See [BLMODULES-40](https://ortussolutions.atlassian.net/browse/BLMODULES-40)

## [1.0.7] - 2025-04-14

### 🐛 Fixed

* Fix string casting error on `lazy` property annotation

## [1.0.6] - 2025-04-14

### 🐛 Fixed

* [Fixed support for custom naming strategies](https://github.com/ortus-boxlang/bx-orm/commit/8e68206e3d3f197a69fc12467c42c7c5de1c7eac)
* [Fixed "smart" naming strategy when entity name begins with an uppercase character](https://github.com/ortus-boxlang/bx-orm/commit/b47b51239a15530df245c5e12c36c48e10b09266)
* [Move compat configuration to bx-compat-cfml](https://github.com/ortus-boxlang/bx-orm/commit/c8b7173f1c0fc01646d3b3d980d9d889ab8c7686)
* [Fixed the two types of discriminator generation order](https://github.com/ortus-boxlang/bx-orm/commit/ea62a62fe1f4fe66bce58b4e27659b60faccb1aa)
* [fix bag element being appended to wrong node on subclasses](https://github.com/ortus-boxlang/bx-orm/commit/f82b2ac24e5d9cf1f43da5a8437c481be5e4f0c5)
* [change to use caster so that lazy=true does not error](https://github.com/ortus-boxlang/bx-orm/commit/00963873c44480e6597ac0e3962d66244c42c865)

### ⭐ Added

* [Add missing `date` property type](https://github.com/ortus-boxlang/bx-orm/commit/c6ec8a2e2dadfb344deb93edb7a1a2ccf8d0fb46)
* [Add alternate spellings for big decimal and big integer](https://github.com/ortus-boxlang/bx-orm/commit/5e199f9e5674c3a3802a5e225d45f187b0724e23)
* [Add flush after commit on transaction end](https://github.com/ortus-boxlang/bx-orm/commit/e2df378c261a2c0aea99749d7bf04cd688d57658)

## [1.0.5] - 2025-04-07

### 🐛 Fixed

* Removed debugging code

## [1.0.4] - 2025-04-06

* Metadata parsing throws error on empty class despite `skipCFCWithError` setting - Resolves [BLMODULES-37](https://ortussolutions.atlassian.net/browse/BLMODULES-37)

## [1.0.3] - 2025-04-06

## [1.0.2] - 2025-04-04

## [1.0.1] - 2025-04-04

## [1.0.0] - 2025-03-26

- First iteration of this module

[Unreleased]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.7...HEAD

[1.0.7]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.6...v1.0.7

[1.0.6]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.5...v1.0.6

[1.0.5]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.4...v1.0.5

[1.0.4]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.3...v1.0.4

[1.0.3]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.2...v1.0.3

[1.0.2]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.1...v1.0.2

[1.0.1]: https://github.com/ortus-boxlang/bx-orm/compare/v1.0.0...v1.0.1

[1.0.0]: https://github.com/ortus-boxlang/bx-orm/compare/2fe797c6330a5d110f3bfbc5ead058df9bdbe89e...v1.0.0
