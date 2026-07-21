---
name: gonthera-cli-project
description: Use this skill when working on the Gonthera CLI Maven plugin, especially when changing Java, .NET, Node.js, SQL generation, project.json schema, RabbitMQ messaging, relationships, DTO converters, Prisma generation, README, Docusaurus docs, or handoff documentation.
---

# Gonthera CLI Project

Use this skill for code or documentation changes in the `gonthera-cli` project.

## Project Purpose

`gonthera-cli` is a Maven plugin/JAR that reads `project.json` from the current working directory and generates code for service projects. It temporarily falls back to the legacy `properties.json` when `project.json` is absent.

Configuration lookup order:

1. `.gonthera/project.json` with optional separated files;
2. root `project.json`;
3. root legacy `properties.json`.

When `.gonthera` exists, its `project.json` is required. `entities.json`, `endpoints.json`, and `enums.json` contain arrays directly; `messaging.json` contains the messaging object. All sections may still remain inside `.gonthera/project.json`; each separated file overrides only its corresponding section. The loader merges them into the same `Properties` model used by single-file configuration.

Validation is available without generation through `mvn gonthera-cli:validate` or `gonthera-cli.exe --validate`. Validation requires `.gonthera`, checks JSON syntax and shape, rejects unknown properties at any nesting level, and then applies the shared semantic `ProjectValidator`. It must not create or delete generated output. Root configuration remains temporarily supported for generation only.

Supported targets:

- `JAVA`: Spring/JPA style generation under `src/main/java/<mainPackage>_gen`, organized into `entities`, `dtos`, `converters`, `repositories`, `services`, `controllers`, `endpoints`, `enums`, `common`, and `messaging` subpackages; resources remain under `src/main/resources`.

Java CRUD controllers delegate persistence, conversion, filtering, pagination, and transactions to generated `*Service` classes. `serviceAbstract: false` generates a concrete Spring `@Service`; `serviceAbstract: true` generates an abstract class without `@Service` and must produce a validator warning that the consumer needs a concrete Spring bean. Prefer `generateDefaultControllers` and `controllerAbstract`; accept `generateDefaultHandlers` and `handlerAbstract` only as deprecated aliases with warnings and new-name precedence. Do not apply this behavior to .NET without also changing its explicit DI registration.
- `DOTNET`: C# generation under `<mainPackage>_gen` and static files under `static`.
- `NODE`: TypeScript generation under `src/generated` plus `prisma/schema.prisma`.
- SQL: PostgreSQL script generation as `postgree.sql`.
- Messaging: RabbitMQ generation under `messaging.RabbitMq`.

## Java CRUD Architecture

For every Java entity that is not `onlyDTO`, the generated CRUD flow is:

```text
HTTP request
    -> <Entity>Controller
    -> <Entity>Service
    -> <Entity>Repository
    -> database
```

The generated classes have deliberately separate responsibilities:

- `controllers/<Entity>Controller.java` owns the HTTP boundary. It implements `common/CrudController`, receives request bodies and path variables, parses CRUD list query parameters, and delegates to the service. It must not perform repository access, DTO conversion, filtering, or transaction management.
- `services/<Entity>Service.java` owns the CRUD use-case implementation. It coordinates the repository, DTO converter, filtering, pagination, and transaction boundaries.
- `repositories/<Entity>Repository.java` owns Spring Data persistence.
- `converters/<Entity>DTOConverter.java` maps between generated DTOs and entities.
- `common/CrudController.java` defines the standard Spring MVC CRUD mappings shared by generated controllers.

The old generated names are a breaking compatibility boundary:

- `handlers/<Entity>Handler.java` became `controllers/<Entity>Controller.java`;
- `common/HandlerBase.java` became `common/CrudController.java`;
- custom classes that extended a generated Handler must extend the generated Controller instead;
- imports, class names, filenames, and tests in a consumer project must be migrated from `handlers`/`Handler` to `controllers`/`Controller`;
- `generateDefaultHandlers` and `handlerAbstract` remain accepted only as deprecated JSON aliases. New configuration and consumer code must use `generateDefaultControllers` and `controllerAbstract`.

### Generated service behavior

The generated `<Entity>Service` exposes overridable public methods:

- `save(dto)`: converts the DTO to a new entity, saves it, and converts the persisted entity back to a DTO;
- `update(dto, id)`: converts the DTO to an entity, forcibly applies the path `id` to the entity key, saves it, and returns the resulting DTO;
- `delete(id)`: calls `repository.deleteById(id)`;
- `get(id)`: obtains `repository.getReferenceById(id)` and converts the result to a DTO;
- `getAll(input)`: normalizes a non-positive offset to `1`, converts the public one-based page offset to Spring Data's zero-based page index, creates a `PageRequest`, applies `SpecificationFilter`, queries the repository, and returns `ResponseData` with total, contents, size, and the zero-based result offset.

Current list-query limitations must remain visible when adapting a consumer:

- the controller defaults `size` to `20` and `offset` to `1`;
- `filter`, `order`, and `displayFields` are read from query parameters;
- `filter` and `displayFields` are used by the service;
- `order` is currently captured but not applied by the generated Java service;
- there is currently no generated upper-bound validation for `size`;
- service methods are not `final`, and the generated repository, converter, and filter fields are `protected`, specifically so subclasses can override behavior when configured for customization.

Transaction rules:

- `save`, `update`, and `delete` use `@Transactional`;
- `get` and `getAll` use `@Transactional(readOnly = true)`;
- transaction ownership belongs to the service, not the controller;
- when an override changes transaction semantics, declare the appropriate `@Transactional` annotation explicitly on the overriding method;
- keep slow external calls and unrelated orchestration outside a database transaction unless the use case intentionally requires otherwise.

### Concrete and abstract service rules

`serviceAbstract` controls whether Gonthera supplies the Spring bean or only the reusable base implementation.

With `serviceAbstract: false` (the default):

- Gonthera generates a concrete `<Entity>Service` annotated with `@Service`;
- the generated controller can inject and use it immediately;
- use this mode when no service customization is needed;
- do not add another ordinary Spring bean extending this concrete service unless bean selection is intentionally resolved, because injection by `<Entity>Service` type can become ambiguous.

With `serviceAbstract: true`:

- Gonthera generates `public abstract class <Entity>Service` without `@Service`;
- its CRUD methods still have working concrete implementations and may be inherited, selectively overridden, or called through `super`;
- the generated abstract class is not a Spring bean and cannot satisfy controller injection by itself;
- the consumer must provide at least one concrete subclass registered as a Spring bean;
- normally there should be exactly one bean assignable to `<Entity>Service`; if there are multiple implementations, the consumer must resolve injection with `@Primary` or `@Qualifier`;
- the implementation must live outside generated `_gen` sources, because regeneration replaces generated files.

Recommended consumer implementation:

```java
package com.example.service.services;

import com.example.service_gen.dtos.CustomerDTO;
import com.example.service_gen.services.CustomerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerApplicationService extends CustomerService {

    @Override
    @Transactional
    public CustomerDTO save(CustomerDTO dto) {
        // Consumer-specific validation or normalization.
        return super.save(dto);
    }
}
```

Inherited `protected` dependencies are injected by Spring into the concrete subclass. Prefer overriding only the methods whose behavior actually differs. Calling `super` preserves the generated CRUD implementation; omitting it means the consumer owns the complete behavior and return contract of that method.

### Concrete and abstract controller rules

The controller flags are independent from `serviceAbstract`:

- `generateDefaultControllers: false`: no default controller is generated for that entity; the service is still generated unless `onlyDTO` is true. The consumer owns the complete HTTP adapter.
- `generateDefaultControllers: true` and `controllerAbstract: false`: generates a concrete `@RestController` ready to use.
- `generateDefaultControllers: true` and `controllerAbstract: true`: generates an abstract controller containing the standard mappings and delegation. The consumer must provide a concrete Spring controller subclass.

Recommended consumer implementation for an abstract generated controller:

```java
package com.example.service.controllers;

import com.example.service_gen.controllers.CustomerController;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerApplicationController extends CustomerController {
    // Override only endpoints that need consumer-specific HTTP behavior.
}
```

Rules for controller customization:

- keep custom concrete controllers outside `_gen`;
- do not redeclare the same class-level or method-level mappings unless the URL contract is intentionally being replaced;
- do not create a second concrete controller over the same generated routes while the default generated controller remains active, because Spring can report ambiguous mappings;
- controller overrides should handle HTTP concerns and delegate business/persistence work to the service;
- if the consumer wants total control of routes, prefer `generateDefaultControllers: false` and implement its own controller instead of subclassing the default concrete controller.

### Safe configuration combinations

Use these combinations when adapting a generated Java project:

The flags belong to each object inside `entities.json` or the `entities` array in `project.json`:

```json
{
  "entityName": "customer",
  "generateDefaultControllers": true,
  "controllerAbstract": true,
  "serviceAbstract": true,
  "onlyDTO": false,
  "entityFields": []
}
```

The empty `entityFields` above only highlights flag placement; it is not a valid complete entity. A real entity must declare its fields and exactly one key according to the validation contract.

| Goal | `generateDefaultControllers` | `controllerAbstract` | `serviceAbstract` | Consumer responsibility |
| --- | ---: | ---: | ---: | --- |
| CRUD ready without customization | `true` | `false` | `false` | None |
| Customize only business rules | `true` | `false` | `true` | Implement one concrete `@Service` subclass |
| Customize only the HTTP adapter | `true` | `true` | `false` | Implement one concrete `@RestController` subclass |
| Customize controller and service | `true` | `true` | `true` | Implement one concrete controller and one concrete service |
| Own all HTTP routes | `false` | ignored | either | Implement the controller; implement the service too only when `serviceAbstract` is `true` |

`onlyDTO: true` takes precedence over CRUD generation: the Java generator skips entity-backed repository, service, and default controller generation for that item. Do not instruct a consumer to subclass CRUD artifacts that do not exist for an `onlyDTO` entity.

### Validation and migration expectations

The validator must:

- reject non-boolean values for `generateDefaultControllers`, `controllerAbstract`, and `serviceAbstract`;
- warn when `generateDefaultHandlers` or `handlerAbstract` is used;
- use the new property when both a new name and its legacy alias are present, and warn about the conflict;
- warn when `serviceAbstract: true` because generation cannot verify that the consumer actually supplies a concrete Spring bean.

When asked to migrate a consumer project, inspect its generated-code extensions and Spring beans before editing. Rename Handler imports/classes to Controller, move business overrides to a concrete generated-service subclass where appropriate, keep HTTP-only overrides in the controller, and check for duplicate beans and duplicate request mappings after the migration.

## Must Read First

Before making non-trivial changes, inspect:

- `docs/handoff/gonthera-cli-project/HANDOFF.md`: implementation-oriented behavior and known limitations.
- `docs/handoff/HANDOFF_FRONTEND.md`: frontend-facing JSON and query contracts.
- `README.md`: user-facing instructions.
- `CHANGELOG.md`: current release notes and future improvements.
- Relevant generator package:
  - Java: `src/main/java/com/gonthera/cli/service/java`
  - .NET: `src/main/java/com/gonthera/cli/service/dotNet`
  - Node: `src/main/java/com/gonthera/cli/service/node`
  - Shared: `src/main/java/com/gonthera/cli/service/common`
  - Models: `src/main/java/com/gonthera/cli/model`

For docs-only changes in the Docusaurus project, inspect `docs-docusaurus/docs` and `docs-docusaurus/sidebars.js`.

## project.json Contract

The minimum valid shape must include arrays even when empty:

```json
{
  "mainPackage": "com.example.service",
  "projectName": "service-name",
  "language": "JAVA",
  "entities": [],
  "endpoints": [],
  "enums": []
}
```

Accepted `language` values are uppercase:

- `JAVA`
- `DOTNET`
- `NODE`

The structural validator rejects unknown JSON properties before generation. Do not document unsupported properties as active behavior, even though Gson itself would otherwise ignore them.

## Entity Field Shape

Use the real field shape:

```json
{
  "comment": "Identificador",
  "fieldName": "id",
  "list": false,
  "fieldProperties": {
    "fieldType": "uuid",
    "required": true,
    "valueDefault": ""
  },
  "metadata": {
    "nullable": false,
    "key": true
  }
}
```

Do not use simplified field examples like top-level `fieldType` or top-level `nullable`; the implemented model uses `fieldProperties.fieldType` and `metadata.nullable`.

## Relationships

Java and C# relationship generation are considered mature. Node relationship generation is initial and may require manual Prisma review.

Relationship fields use `relationShips`:

```json
{
  "fieldName": "parentCode",
  "list": false,
  "fieldProperties": {
    "fieldType": "costCenter",
    "required": false,
    "valueDefault": ""
  },
  "metadata": {
    "nullable": true,
    "key": false
  },
  "relationShips": {
    "fetchType": "LAZY",
    "relationShip": "ManyToOne",
    "bidirectional": false,
    "reference": true
  }
}
```

Rules:

- `bidirectional: false`: owner side. Generates FK/column and `@JoinColumn` in Java.
- `bidirectional: true`: inverse side. Uses `mappedBy` and does not generate SQL column.
- `mappedBy`: must point to the owner field, for example `children` maps by `parentCode`.
- `reference: true`: normally set on the FK/reference side; Java converters use it to avoid recursion.
- `list: true`: collection side, usually `OneToMany` or `ManyToMany`.
- For self-relations, declare the owner/reference field before the inverse field.

Example inverse side:

```json
{
  "fieldName": "children",
  "list": true,
  "fieldProperties": {
    "fieldType": "costCenter",
    "required": false,
    "valueDefault": ""
  },
  "metadata": {
    "nullable": false,
    "key": false
  },
  "relationShips": {
    "fetchType": "LAZY",
    "relationShip": "OneToMany",
    "mappedBy": "parentCode",
    "bidirectional": true,
    "reference": false
  }
}
```

## Messaging

Messaging is grouped by provider. Current provider:

```json
{
  "messaging": {
    "RabbitMq": {
      "pub": [],
      "sub": []
    }
  }
}
```

Rules:

- Generate RabbitMQ code only when `messaging.RabbitMq` exists and has at least one `pub` or `sub` channel.
- Do not generate RabbitMQ dependencies/configuration when `RabbitMq` is absent, null, or empty.
- Keep the exchange outside `project.json`; Java/.NET use annotation/attribute in consumer code, Node uses a concrete config class.
- The old `pup` typo must not be reintroduced. Use `pub`.
- The old `events`/`listeners` model is not implemented; do not document it as current behavior.

## Node.js Scope

Node generation currently includes:

- models/DTO contracts;
- enums;
- Prisma-friendly repositories;
- Express controllers and routes;
- endpoint contracts;
- RabbitMQ when configured;
- static files;
- SQL;
- `prisma/schema.prisma`.

Node does not generate:

- `package.json`;
- `tsconfig.json`;
- migrations;
- Java-style DTO converters.

Relationship caveat:

- Node/Prisma relation generation is a starting point.
- Self-relations, bidirectional relations, and `ManyToMany` should be documented as requiring manual review before migrations.
- Avoid claiming Node relationship parity with Java/C# until `GeneratePrisma` and generated TypeScript models are improved.

## Templates

The generator source uses `.mxsd` templates under `src/main/resources/xsd`.

Use templates for generated code instead of large inline string literals.

Template folders:

- `xsd/java`
- `xsd/dotnet`
- `xsd/node`
- `xsd/sql`

User-facing documentation should not explain internal templates unless the user explicitly asks about generator development.

## CRUD Filters

Treat filtering as target-specific behavior, not as a portable JPA/SQL query language.

- Java `SpecificationFilter` supports `eq`, `isNull`, `notNull`, `and`, `or`, and dotted relationship paths.
- Java string `eq` is a case-insensitive contains operation; UUID uses exact equality.
- Java numeric, boolean, and date equality is not safely converted by the current template.
- Do not claim reliable mixed `and`/`or` precedence, nested parentheses, escaping, comparison operators, or `in` support.
- .NET `DynamicFilter` is a separate dialect with `eq` and one logical operator kind per expression; collection paths use `*`.
- Node repositories currently ignore `filter` and use only pagination parameters.
- When filter behavior changes, update both entity handoffs and the relevant user documentation.

## Documentation Standards

Update docs when behavior changes:

- `README.md`: user-facing usage.
- `docs/handoff/gonthera-cli-project/HANDOFF.md`: implementation details and caveats.
- `docs/handoff/HANDOFF_FRONTEND.md`: frontend request construction and limitations.
- `CHANGELOG.md`: release notes and future improvements.
- `docs-docusaurus/docs`: detailed user documentation.

Docusaurus docs should focus on how to use the generator, not how the generator is implemented.

For .NET and Node docs, mention that consumers can download `gonthera-cli.exe` or `gonthera-cli-x.x.x.jar` and execute it in the project root, in the same folder as `project.json`.

## Validation

Prefer focused validation over full `mvn test` when generated `_gen` sources or local dependency state make full tests noisy.

Useful compile check for generator sources:

```bash
find src/main/java/com/gonthera/cli -name '*.java' ! -path '*cli_gen*' > /tmp/gonthera-cli-sources.txt
rm -rf /tmp/gonthera-cli-compile
mkdir -p /tmp/gonthera-cli-compile
javac -cp /home/geovane/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar:/home/geovane/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar:/home/geovane/.m2/repository/org/apache/logging/log4j/log4j-api/2.23.1/log4j-api-2.23.1.jar:/home/geovane/.m2/repository/org/apache/logging/log4j/log4j-core/2.23.1/log4j-core-2.23.1.jar:/home/geovane/.m2/repository/org/apache/maven/plugin-tools/maven-plugin-annotations/3.9.0/maven-plugin-annotations-3.9.0.jar:/home/geovane/.m2/repository/org/apache/maven/maven-plugin-api/3.9.3/maven-plugin-api-3.9.3.jar -d /tmp/gonthera-cli-compile @/tmp/gonthera-cli-sources.txt
```

Docusaurus validation:

```bash
cd docs-docusaurus
npm run build
```

When validating generation, create a temporary project under `/tmp`, copy or create `project.json`, execute `Main` or the compiled classes from that folder, then inspect generated output.

## Editing Rules

- Use existing package patterns; do not invent a parallel architecture.
- Keep manual generated-code templates in `.mxsd` files.
- Do not change generated output paths unless explicitly requested.
- Do not revert unrelated dirty files.
- Do not edit generated `_gen` output as source of truth.
- Keep Node relationship limitations honest in docs until implementation is complete.

## Church Lite SaaS and Permission Boundaries

- Declare subscription resources and features in `.gonthera/project.json` under `enums`; consume the generated `*_gen.enums` types. Do not maintain parallel manual enums.
- Use `generateDefaultControllers: false` for plan/catalog entities that must not expose CRUD endpoints.
- Use `serviceAbstract: true` when a generated CRUD needs subscription validation, then provide exactly one concrete Spring service outside `_gen` and override only the affected methods.
- Treat permission and subscription checks as cumulative. Permission grants access to an operation; it never unlocks a feature or capacity excluded by the tenant plan.
- Keep error namespaces distinct: `permission_access_denied` for user authorization and `subscription_*` for commercial restrictions.
- When switching to the administrative tenant, run migrations, update `TenantContext`, apply the schema interceptor, and restore the original tenant in `finally`. Do not interpolate schema names in business SQL.
- Flush and clear the persistence context before switching schemas inside one transaction to prevent cross-schema entity reuse.
- Regenerate with `gonthera-cli:generate-sources`, never the nonexistent `generate` goal, then compile with the project's required JDK.
