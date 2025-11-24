# Copilot Instruction File for bx-orm

## High-Level Purpose

The `bx-orm` module provides Object-Relational Mapping (ORM) capabilities for the BoxLang  and boxlang JVM language. It enables developers to map BoxLang objects to relational database tables, manage database schema, and perform CRUD operations using a high-level, object-oriented approach. The module abstracts database interactions, allowing for more maintainable and database-agnostic application code.

bx-orm sits as a middleware between the boxlang dynamic JVM language and Hibernate ORM. It abstracts not only database operations, but the verbose Hibernate configuration syntax.

Due to issues with JPA requiring native java classes in entity configuration, bx-orm utilizes Hibernate 5.6.15-FINAL which enables dynamic java classes in place of Java source files. Hence all Hibernate integration code is written against Hibernate 5, not Hibernate 6 or 7.

## Module Structure and Design

- src/main/bx/**: Contains BoxLang source files, including the module settings file `ModuleConfig.bx` and boxlang interfaces for event handling, naming strategies, etc.
- src/main/java/**: Java implementation of the ORM engine
  - src/main/java/ortus/boxlang/modules/orm/bifs/**: Built-in functions for interacting with ORM entities or the ORM session.
  - src/main/java/ortus/boxlang/modules/orm/config/**: Hibernate configuration-related files, such as the event handler, connection provider, naming strategies, and base Hibernate configuration wrapper.
  - src/main/java/ortus/boxlang/modules/orm/hibernate/**: Houses hibernate interface implemenations for value casters (converters), the hibernate cache, and especially box class to hibernate entity proxy objects.
  - src/main/java/ortus/boxlang/modules/orm/mapping/**: Houses classes that assist in parsing boxlang ORM entities into ORM context/state and generating hibernate HBM.xml configuration files
- src/main/test/java/ortus/**: Junit tests
- src/main/test/java/ortus/tools/**: Base test files and util classes for assistance in writing junit tests
- src/main/test/resources/app/**: Test boxlang files for a test app. Includes ORM models, ORM configuration in Application.bx, and other boxlang test files.
- src/main/resources/**: Resource files such as configuration, metadata, and licensing.
- build/**: Build artifacts, generated sources, and documentation.
- bin/**: Packaged module binaries and metadata for distribution.

## Design Principles

- **Separation of Concerns**: Java code handles low-level ORM logic, while BoxLang code provides configuration and high-level integration.
- **Extensibility**: The module supports custom naming strategies, event handlers, and database dialects.
- **Testability**: Includes comprehensive test cases and seed data to ensure reliability across different environments.
- **Documentation**: Extensive documentation and examples are provided to help users understand and extend the module.

## Usage Guidance for Copilot
- Follow the established directory structure when adding new features.
- Prefer extending existing interfaces and base classes for new ORM features.
- Ensure new code is covered by tests in the `src/main/test/java/ortus/` directory.
- Ensure new features, bug fixes, security updates, etc. are added to `changelog.md` under `## [Unreleased]`.