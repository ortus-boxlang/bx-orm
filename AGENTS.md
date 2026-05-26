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
- **Before any commit**, run `gradle spotlessApply` to auto-format Java sources, and run `npx markdownlint-cli2 "**/*.md"` to lint all Markdown files. Fix any issues before committing.

## Tooling

- Gradle is used for building/compiling the java sources, running junit tests, and building the final boxlang module structure into a zip file for uploading to forgebox.io.
- Hibernate 5.6.15-FINAL serves as the ORM engine under the hood.
- Spotless is used for java source formatting.
- Docker-compose is used to stand up a simple mysql database for integration testing.

## Available Skills

Skills in `.agents/skills/` provide specialized workflows for AI agents. Install them via `npx skills experimental_install .agents/skills <target>`.

### BoxLang Core Development

- **boxlang-core-dev-async-tasks** — Async programming, BoxFuture, AsyncService, executors, BaseScheduler, ScheduledTask API, cron scheduling, task lifecycle
- **boxlang-core-dev-bif-development** — Creating BIFs with @BoxBIF annotation, invoke() method, argument handling, member functions, registering via modules
- **boxlang-core-dev-component-development** — Custom tags/components, attribute declarations, body/output handling, registering component paths
- **boxlang-core-dev-interceptors** — Observer/Intercepting Filter patterns, interceptor pools, BoxLang/Java/lambda interceptors, registration, interception points
- **boxlang-core-dev-logging** — Obtaining loggers via LoggingService, BoxLangLogger API, parameterized messages, logging configuration
- **boxlang-core-dev-module-development** — ModuleConfig.bx structure, lifecycle methods, registering interceptors/BIFs, Gradle build, ForgeBox publishing
- **boxlang-core-dev-runtime-architecture** — BoxRuntime services, IBoxContext hierarchy, scope chain, DynamicObject, type system, parsing pipeline, class loader isolation

### Java & Testing

- **java-expert** — Java services, API design, concurrency, performance profiling, dependency management, testing strategy, production hardening
- **junit-expert** — JUnit 5 lifecycle, parameterized tests, extensions, assertions, dynamic tests, test organization, parallel execution
- **mockito-expert** — Mock creation, argument matchers, stubbing, verification, argument captors, Answer implementations, Spy objects
- **testcontainers-expert** — Container lifecycle, reusable containers, network config, wait strategies, custom images, modules (PostgreSQL, MySQL, Kafka, Redis, LocalStack)

### Code Quality

- **code-reviewer** — PR review, architecture drift detection, bug risk assessment, severity-ranked findings
- **code-documenter** — Inline comments, docstrings, API references, onboarding guides, runbooks, consistency audits

### Engineering & Infrastructure

- **ortus-java-coding-standards** — Ortus formatting rules: indentation, spacing, brace placement, naming, alignment, comments
- **security-expert** — Authentication, authorization, secrets handling, input validation, secure coding, threat modeling
- **github-action-authoring** — Composite GitHub Actions, platform support, PATH issues, PowerShell steps, CI test jobs
