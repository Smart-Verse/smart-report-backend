---
name: smart-report-project
description: Maintain and evolve the SmartReport workspace across its Angular frontend, Spring Boot backend, Gonthera-generated contracts, tenant-aware persistence, API-key integrations, report templates, and Puppeteer PDF renderer. Use when implementing, diagnosing, reviewing, or documenting SmartReport features, generated entities/DTOs/endpoints/enums, authentication, tenant switching, Studio behavior, template presets, API contracts, or cross-project changes.
---

# SmartReport Project

## Start with the workspace

Work from the SmartReport root and inspect the affected project before editing. Preserve existing user changes in dirty worktrees.

Treat these projects as independent applications:

- `smart-report-frontend`: Angular 21 SPA, PrimeNG, CodeMirror 6.
- `smart-report-backend`: Java 25, Spring Boot 3.5, Gonthera CLI, PostgreSQL, MongoDB.
- `smart-report-core`: Express and Puppeteer PDF renderer.

Read the root and affected-project handoffs before broad changes:

- `HANDOFF.md`
- `smart-report-backend/docs/handoff/smart-report-backend/HANDOFF.md`
- `smart-report-frontend/docs/handoff/smart-report-frontend/HANDOFF.md`
- `smart-report-core/docs/handoff/smart-report-core/HANDOFF.md`

## Follow the generator contract

Before any backend change involving an entity, DTO, enum, repository, service, CRUD controller, endpoint input/output, or generated metadata, read `../entity-generator-project/SKILL.md` completely.

Use `.gonthera/project.json` as the only source of truth for generated artifacts.

- Declare entities, DTO-only models, enums, fields, relationships, and endpoint contracts there.
- Use `onlyDTO: true` for public response shapes that must not expose persistence fields.
- Use `generateDefaultControllers: false` when HTTP behavior is custom or CRUD must not be exposed.
- Use `serviceAbstract: true` only when exactly one custom Spring service will extend the generated service.
- Extend generated repositories for custom queries instead of recreating the base repository.
- Implement generated endpoint interfaces in custom handlers outside `_gen`.
- Never manually edit or duplicate files under `smartreportbackend_gen`.

Run, in this order:

```bash
mvn gonthera-cli:validate
mvn gonthera-cli:generate-sources
mvn -DskipTests clean compile
```

Use JDK 25 for the real build. A temporary `-Djava.version=17` override is only an environment workaround and must not change the POM target.

## Preserve architecture boundaries

Keep HTTP adaptation in handlers/controllers and business/persistence rules in services.

- Generated interfaces own custom endpoint annotations and request/response contracts.
- Custom handlers map generated input/output objects and delegate.
- Custom services may extend abstract generated services.
- Custom repositories may extend generated repositories and use `@Primary` when both are discovered.
- Flyway migrations remain explicit deployment artifacts even when `postgree.sql` is regenerated.

Do not expose secret hashes, internal tenant catalog fields, or persistence-only DTOs in public responses.

## Handle authentication and tenant safely

Keep `InterceptorConfig` as a small orchestrator.

- `ApplicationAuthenticationFlow` owns JWT, anonymous routes, login/register, and human tenant resolution.
- `IntegrationAuthenticationFlow` owns `X-API-Key`, integration scope, and tenant activation.
- API keys may authenticate only `POST /generateReport`.
- Store API-key metadata and SHA-256 hashes in the admin catalog; reveal plaintext only at creation.
- Resolve the real tenant before report data access.
- Call tenant migration before accessing a tenant schema.
- Restore or clear `TenantContext` in `finally`/request completion paths.
- Never carry JPA entities across tenant switches or reuse persistence context state between schemas.

Generate `EnumConfigContext` from the `enumConfigContext` entry; never recreate it manually.

## Maintain report templates

Store editable preset files in `smart-report-backend/src/main/resources/models/template`.

Each preset requires matching `.html`, `.css`, `.js`, and `.json` files. Current names are:

- `standard`
- `list`
- `chart`
- `financial`

The generated `ReportTemplate` enum and `Report.templateType` select the preset during report creation. Add new choices through Gonthera and a Flyway migration, then update the Angular selector.

Keep templates self-contained, A4-safe, printable, and editable in the Studio. Prefer CSS/Vue over adding heavy runtime libraries. Validate every example JSON.


## Enforce subscription plans and API quotas

Keep commercial configuration in the admin schema; never hardcode plan prices, limits, descriptions, or display order in Angular.

- Model catalog, tenant subscription, and monthly usage through Gonthera with default controllers disabled.
- Seed only initial catalog values through Flyway; treat the database rows as runtime configuration.
- Default a tenant without an explicit subscription to `FREE`.
- Count only successful authentication through `X-API-Key` on the integration report-generation flow.
- Do not count template creation, editing, human JWT previews, or Studio activity.
- Keep template creation unlimited for every plan.
- Partition usage by tenant and `YearMonth`, update it transactionally, and lock the existing counter row while incrementing.
- Return HTTP 402 when a finite monthly quota has already been exhausted.
- Treat a null quota on a custom plan as negotiated/unlimited until an explicit limit is configured.
- Perform catalog access under the admin tenant and always restore the previous `TenantContext` in `finally`.
- Expose a generated DTO/endpoint for catalog and usage data; never expose subscription persistence entities directly.

## Maintain the frontend

Use standalone Angular components and shared CSS tokens such as `--surface`, `--text`, `--border`, and `--primary`. Support both `.app-dark` and light themes and responsive layouts.

Register global providers in `app.config.ts`. Lazy pages cannot depend on component-scoped providers.

Keep one active root HttpClient configuration:

```ts
provideHttpClient(withFetch(), withInterceptors([authInterceptor]))
```

The interceptor must:

- prefix business URLs with `environment.apiUrl`;
- attach the JWT cookie;
- leave `/assets/` and approved external upload URLs local/untouched;
- clear the client session on 401.

Logout must clear cookies, `localStorage`, and `sessionStorage`, then navigate with `replaceUrl`. Guards must return booleans or `UrlTree`, not navigate and return `true`.

When generated endpoint outputs wrap data, map the wrapper explicitly in services.

## Coordinate API and PDF changes

The production backend base URL is `https://app.smartverse.com.br/api/smartreport`.

The backend sends complete HTML to `smart-report-core` using `POST http://localhost:5071/report`. Coordinate any route, port, request, response, or PDF behavior change in both projects.

Treat report HTML and JSON as sensitive. Do not log generated documents, API keys, JWTs, or tenant secrets.

## Validate and hand off

Validate only the affected projects, but include cross-project builds when contracts change:

```bash
# backend
mvn gonthera-cli:validate
mvn -DskipTests clean compile

# frontend
npm run build

# core
node --check index.js
```

Update the root handoff and every affected project handoff after architecture, contract, setup, or workflow changes. Record current behavior, commands, limitations, and the next safe action; remove stale migration notes.
