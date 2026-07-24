---
name: smart-report-project
description: Maintain, teach, operate, and evolve the SmartReport workspace across its Angular frontend, Spring Boot backend, Gonthera-generated contracts, tenant-aware PostgreSQL and MongoDB persistence, API-key integrations, subscription quotas, editable report templates, Puppeteer PDF renderer, and SmartVerse public site. Use for implementation, onboarding, diagnosis, publication, generated entities/DTOs/endpoints/enums, authentication, tenant switching, Studio behavior, templates, plan consumption, API contracts, or cross-project changes.
---

# SmartReport Project

## Understand the system first

Treat SmartReport as four cooperating applications:

- `smart-report-frontend`: Angular 21 SPA used by people.
- `smart-report-backend`: Java 25 API and source of business rules.
- `smart-report-core`: isolated Node/Puppeteer PDF renderer.
- `site-smartverse`: static institutional site and product landings.

Read `HANDOFF.md` and the handoff of every affected project before broad work. Preserve dirty worktrees and unrelated user changes.

Use this request flow as the mental model:

```text
Human browser --JWT--> backend --HTML--> core --PDF--> backend --> browser
External system --X-API-Key + JSON--> backend --HTML--> core --PDF base64--> external system
```

Store relational metadata in PostgreSQL tenant schemas. Store editable template content in tenant-named MongoDB collections. Store shared security and commercial catalogs in the admin schema.

## Run the workspace

Use JDK 25 for the backend.

```bash
# terminal 1
cd smart-report-core
npm install
node index.js

# terminal 2
cd smart-report-backend
./mvnw spring-boot:run

# terminal 3
cd smart-report-frontend
npm install
npm start
```

Configure at least:

```text
DATABASE_SCHEMA_NAME=smartreport
DB_NAME=POSTGRES
DB_USERNAME=postgres
DB_PASSWORD=<password>
SECRET_JWT=<strong-secret>
SERVER_PORT=5070
SERVICE_NAME=smartreport
MONGO_USER=smartreport
MONGO_PASSWORD=<password>
```

Use `http://localhost:5070/smartreport/swagger-ui/index.html` for local API inspection. Production API base is `https://app.smartverse.com.br/api/smartreport`.

## Change generated backend contracts correctly

Read `../entity-generator-project/SKILL.md` completely before changing an entity, DTO, enum, generated repository/service/controller, or endpoint contract.

Treat `.gonthera/project.json` as the only source of truth.

1. Declare the entity, DTO, enum, relationship, or endpoint in `.gonthera/project.json`.
2. Set `onlyDTO: true` for response shapes without persistence.
3. Set `generateDefaultControllers: false` for admin tables, security data, custom behavior, or anything that must not expose CRUD.
4. Set `serviceAbstract: true` only when one custom service must extend the generated service.
5. Validate before generation.
6. Generate sources.
7. Implement behavior outside `smartreportbackend_gen`.
8. Add an explicit Flyway migration.
9. Compile and inspect the generated interface/DTO.

```bash
mvn gonthera-cli:validate
mvn gonthera-cli:generate-sources
mvn -DskipTests clean compile
```

Never edit `smartreportbackend_gen`. Regeneration deletes and recreates it.

For a custom endpoint:

1. Add the endpoint input/output to Gonthera.
2. Generate the interface.
3. Create a handler under `handlers/<domain>` implementing that interface.
4. Put business rules in `services/<domain>`.
5. Extend the generated repository under `repository/<domain>` for custom queries.
6. Return DTO-only outputs when persistence fields must remain private.

For a new entity field:

1. Add it to Gonthera.
2. Regenerate.
3. Add a forward-only Flyway migration.
4. Update converters/custom services and Angular contracts.
5. Never rely only on regenerated `postgree.sql` for an existing deployment.

## Preserve business boundaries

Keep handlers thin. Let handlers adapt HTTP and delegate. Put transactions, validation, tenant switching, and persistence orchestration in services.

Extend generated repositories instead of duplicating them. Add `@Primary` when Spring discovers both generated and custom repository types.

Do not expose entity objects for API Keys, subscription catalog internals, secret hashes, or administrative tables.

## Authenticate and resolve tenant safely

Keep `InterceptorConfig` small:

- delegate JWT requests to `ApplicationAuthenticationFlow`;
- delegate `X-API-Key` requests to `IntegrationAuthenticationFlow`;
- clear `TenantContext` after every request.

Human flow:

1. Allow only explicitly public authentication/registration/verification routes.
2. Validate the Authorization bearer token.
3. Read tenant from the authenticated user.
4. migrate/activate that tenant.

Integration flow:

1. Detect `X-API-Key`.
2. Reject every route except `POST /generateReport`.
3. Hash and authenticate the key in the admin catalog.
4. validate expiry/revocation.
5. enforce and reserve monthly quota.
6. activate the key owner tenant.
7. generate the report.

Never accept the effective tenant from public request data. Never carry JPA entities across a tenant switch. Restore the previous context in `finally`.

Keep the API Key format `sr_live_<prefix>_<secret>`. Show plaintext only after creation and persist only SHA-256 plus metadata.

## Work with PostgreSQL and MongoDB

Use PostgreSQL schemas named `<DB_NAME>_<TENANT>`. Run `DBMigration.loadMigrateTenants(tenant)` before accessing a new schema.

Use the admin schema for:

- authentication users;
- API-key catalog;
- subscription plans;
- tenant subscriptions;
- monthly API usage.

Use MongoDB collection `tenant.toLowerCase()` for HTML, CSS, JavaScript and JSON template content.

When temporarily selecting admin:

```java
var previous = TenantContext.getCurrentTenant();
try {
    TenantContext.setCurrentTenant("admin");
    // migrate and execute admin operation
} finally {
    TenantContext.setCurrentTenant(previous);
}
```

Do not omit the restoration even when the operation appears request-scoped.

## Maintain subscriptions and quotas

Keep `subscription_plan`, `tenant_subscription`, and `api_monthly_usage` generated with default controllers disabled.

Treat Flyway seeds as initial configuration and database rows as runtime truth. Never hardcode price, description, limit, or display order in Angular.

Rules:

- default missing subscriptions to `FREE`;
- keep template creation/editing unlimited;
- count only report generation authenticated by API Key;
- do not count Studio/JWT previews;
- partition counters by tenant and `YearMonth`;
- lock an existing counter row during increment;
- return HTTP 402 when a finite quota is exhausted;
- interpret a null custom quota as negotiated/unlimited;
- list history newest-first;
- allow authenticated legacy/admin accounts to read their overview;
- never expose catalog CRUD publicly.

Before horizontal scaling, replace first-counter creation with a database upsert or retry on the unique `(tenant, period)` constraint.

## Maintain report templates

Store presets under `src/main/resources/models/template`. Keep matching `.html`, `.css`, `.js`, and `.json` files for:

- `standard`;
- `list`;
- `chart`;
- `financial`.

Keep `base.*` compatible with the standard preset.

Add a preset by:

1. adding the generated `ReportTemplate` enum value;
2. adding a Flyway migration if persisted enum ordinals/values change;
3. creating all four resource files;
4. updating backend preset selection;
5. updating the Angular creation modal;
6. validating the example JSON and A4 output.

Use printable, self-contained HTML and CSS. Prefer built-in template directives and small helper functions over adding large libraries.

Public documentation must describe the supported contract, not the internal rendering technology:

- `{{ data.field }}` for interpolation;
- `v-if` and `v-else` for conditions;
- `v-for` for lists;
- `:key`, `:class`, and `:style` for dynamic attributes;
- named helpers in `script.js`.

Do not advertise the underlying template engine by name.

## Maintain the Angular application

Use standalone components, typed services, and shared theme tokens such as `--surface`, `--text`, `--border`, and `--primary`. Test light, dark, desktop, and mobile states.

Keep exactly one root HTTP configuration:

```ts
provideHttpClient(withFetch(), withInterceptors([authInterceptor]))
```

Ensure the interceptor:

- prefixes business URLs with `environment.apiUrl`;
- sends the JWT cookie;
- leaves `/assets/` and approved external uploads untouched;
- clears the session on 401.

Register providers needed by lazy pages at root level. Do not add a second `provideHttpClient`. Keep `MessageService` available to toast consumers.

On logout, clear cookies, `localStorage`, and `sessionStorage`, then navigate with `replaceUrl`.

Use `Intl.NumberFormat` for BRL unless `pt-BR` locale data is explicitly registered with Angular.

Current plan UI:

- Settings keeps the full Free-plan cards and monthly usage.
- Repository home shows a compact Free banner.
- Upgrade opens a modal with backend-provided catalog.
- Consumption history lives at `home/usageHistory`.
- Checkout and automatic subscription changes are not implemented.

## Maintain the renderer

The backend sends complete HTML to `POST http://localhost:5071/report`. Coordinate any route, port, payload, response, timeout, browser-launch, or PDF behavior change with `smart-report-core`.

Treat template HTML, JSON, and PDF as sensitive. Do not log document content.

Validate core syntax with:

```bash
node --check index.js
```

## Maintain the SmartVerse site

Keep product pages static and isolated:

- `site-smartverse/index.html`: institutional ecosystem page;
- `site-smartverse/church-lite/`: preserved Church Lite landing;
- `site-smartverse/smart-report/`: SmartReport landing.

Do not rewrite an existing product landing when adding another product. Create a new directory and link it from the institutional catalog. Keep public copy focused on user outcomes and avoid internal implementation details.

## Diagnose common failures

For HTTP 401, verify the `outh` cookie, Authorization header, JWT secret, and interceptor prefixing.

For HTTP 403, inspect the exception body and determine whether it comes from tenant validation, anonymous-route handling, API-key scope, or revoked/expired credentials. Authentication-library JWT failures normally return 401.

For HTTP 402, inspect the active `tenant_subscription`, current `api_monthly_usage`, and configured plan limit.

For wrong Angular URLs, verify there is one root interceptor and that only assets bypass `environment.apiUrl`.

For missing toast providers, verify `MessageService` is root-scoped.

For missing tables, verify Flyway ran against the effective tenant schema and that the migration exists as a deployment artifact.

For stale generated classes, run validate, generate, and clean compile in that order.

For template failures, reproduce with saved example JSON, inspect all four template files, and verify the core is reachable.

## Validate and hand off

Run checks proportional to the change:

```bash
# backend contract or Java
mvn gonthera-cli:validate
mvn -DskipTests clean compile

# frontend
npm run build

# core
node --check index.js

# public site
node --check ecosystem.js
node --check church-lite/app.js
```

Use JDK 25 for release validation. A temporary `-Djava.version=17` override may diagnose code in limited environments but must not change the POM.

After architecture, contract, environment, or workflow changes, update the root handoff and each affected project handoff. Record behavior, commands, limitations, deployment assumptions, and the next safe action.


## Current report layout and Studio component library

The report contract supports `pageFormat` and `pageOrientation`:

- formats: `A4`, `A3`, `A5`, `A6`, `LETTER`, `LEGAL`, `TABLOID`, `LEDGER`, `A0`, `A1`, `A2`, `THERMAL_58MM`, `THERMAL_80MM`;
- orientations: `PORTRAIT` and `LANDSCAPE`;
- thermal formats are portrait-only and use dynamic document height.

The Angular Studio exposes a right-hand component toolbox. Components are catalogued in `smart-report-frontend/src/app/pages/studio/report-component-catalog.ts`. Insertion writes formatted HTML at the cursor and merges CSS/JavaScript/data examples without overwriting existing JSON keys. Components should remain self-contained, use the supported Vue-compatible directives, and include a useful `data` example whenever they depend on dynamic values. The blank preset is named `BLANK` and contains an empty A4 report with base print CSS and `{}` data.

When adding a preset, update the generated contract through Gonthera, add the four matching files under `models/template`, add the Angular modal option and preview, and compile both projects. Do not expose renderer internals in public copy.

## API report response and frontend consumption

`POST /generateReport` returns JSON with a `report` field containing raw Base64 PDF content (without a `data:application/pdf;base64,` prefix). A browser client must decode it to bytes, create `new Blob([bytes], {type: 'application/pdf'})`, and open or download an object URL. API keys must remain on a trusted backend; public frontend code must never embed `X-API-Key`.

```ts
const {report} = await response.json();
const bytes = Uint8Array.from(atob(report), char => char.charCodeAt(0));
const pdf = new Blob([bytes], {type: 'application/pdf'});
const url = URL.createObjectURL(pdf);
window.open(url, '_blank');
setTimeout(() => URL.revokeObjectURL(url), 60_000);
```

## SmartVerse public site

`site-smartverse/smart-report` is a static landing page. Product screenshots live in `smart-report/img`, the “Por dentro” section presents the dashboard, creation modal, Studio, API keys and documentation, and screenshots open in an accessible lightbox with keyboard and mobile support. Preserve the SmartReport teal/cyan identity and keep product assets isolated from the root SmartVerse and Church Lite pages. Validate with `node --check site-smartverse/ecosystem.js` after changing shared interactions.
