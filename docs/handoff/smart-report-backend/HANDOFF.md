# smart-report-backend — Handoff

## Responsabilidade e stack

API do SmartReport: autenticação, tenant, CRUD relacional, conteúdo MongoDB e orquestração do PDF.

- Java 25, Spring Boot 3.5.16, Spring Cloud 2025.0.2.
- Gonthera CLI 2.0.1.
- PostgreSQL/JPA/Flyway e MongoDB síncrono.
- Feign para o core em `http://localhost:5071/report`.

## Gonthera

`.gonthera/project.json` é a fonte de verdade. A saída fica em `src/main/java/com/smartverse/smartreportbackend_gen` e recursos gerados em `src/main/resources`.

Entidades: `userConfiguration`, `userConfirmation`, `repository`, `report`, `apiKey`; DTOs públicos de API Key usam `onlyDTO`. Enums incluem `Theme`, `Language`, `EnumConfigContext` e `ReportTemplate`.

Nunca editar `_gen`. Ler `docs/skill/entity-generator-project/SKILL.md` antes de alterar artefatos gerados.

```bash
./mvnw gonthera-cli:validate
./mvnw gonthera-cli:generate-sources
./mvnw -DskipTests clean compile
```

## Autenticação e tenant

`InterceptorConfig` apenas orquestra e limpa `TenantContext`.

- `ApplicationAuthenticationFlow`: JWT, rotas públicas e tenant humano.
- `IntegrationAuthenticationFlow`: `X-API-Key`, escopo e tenant de integração.
- `ApiKeyHandler` implementa `CreateApiKey`, `GetApiKeys` e `RevokeApiKey` gerados.
- `ApiKeyBusinessService` estende o service abstrato gerado.
- O segredo `sr_live_<prefix>_<secret>` aparece uma vez; somente hash SHA-256 é persistido no admin.
- A chave só pode chamar `POST /generateReport`.

Schemas PostgreSQL seguem `<DB_NAME>_<TENANT>`; coleções MongoDB usam tenant em minúsculas. Migrar o schema antes de acessar e nunca transportar entidades entre tenants.

## Relatórios e modelos

`ReportService` salva conteúdo no MongoDB, monta o HTML e chama `ReportClient`. O campo gerado `Report.templateType` seleciona os arquivos em `src/main/resources/models/template`:

- `standard.*`: executivo;
- `list.*`: listagem;
- `chart.*`: métricas e gráfico CSS/Vue;
- `financial.*`: demonstrativo financeiro.

Cada modelo possui HTML, CSS, JS e JSON. `base.*` permanece compatível com o modelo padrão. Flyway adiciona `template_type` com padrão ordinal 0.

Endpoints relevantes: `saveTemplate`, `getTemplate`, `generateReport`, `getMetrics`, `createApiKey`, `getApiKeys`, `revokeApiKey`.

## Configuração

```text
DATABASE_SCHEMA_NAME=smartreport
DB_NAME=POSTGRES
DB_PASSWORD=<senha>
DB_USERNAME=postgres
SECRET_JWT=<segredo>
SERVER_PORT=5070
SERVICE_NAME=smartreport
MONGO_USER=smartreport
MONGO_PASSWORD=password
```

Swagger: `http://localhost:5070/smartreport/swagger-ui/index.html`.

## Riscos

- Build real exige JDK 25 completo.
- Surefire pula testes no POM; compile não representa cobertura funcional.
- URL do core e algumas configurações ainda são fixas.
- `spring.main.allow-bean-definition-overriding=true` pode esconder colisões.
- Trocas de tenant exigem restauração em `finally` ou no fim da requisição.

Regras completas do workspace: `docs/skill/smart-report-project/SKILL.md`.

## Planos e consumo de API

O catálogo é centralizado no schema `admin`:

- `subscription_plan`: nome, descrição, preço, franquia, ordem, situação e indicador de plano personalizado;
- `tenant_subscription`: plano vigente de cada tenant;
- `api_monthly_usage`: contador por tenant e período `YYYY-MM`.

A migração `V20260721210000001__Create_subscription_catalog.sql` semeia os valores comerciais iniciais. Eles podem ser administrados diretamente no catálogo, sem rebuild do frontend. Tenants sem vínculo recebem `FREE`.

`PlanBusinessService` troca temporariamente para o tenant administrativo, restaura o contexto em `finally`, calcula o overview e incrementa o consumo em transação. `IntegrationAuthenticationFlow` contabiliza somente a geração autenticada por `X-API-Key`; criação de templates e visualização humana pelo Studio não consomem franquia. Limite excedido retorna HTTP 402.

O contrato público gerado `GET getPlanOverview` é implementado por `PlanHandler`. As entidades `subscriptionPlan`, `tenantSubscription`, `apiMonthlyUsage` e o DTO `planOption` têm controllers CRUD padrão desabilitados.

`GET getApiUsageHistory` retorna os registros de `api_monthly_usage` do tenant autenticado em ordem decrescente de período, usando o DTO-only gerado `apiUsageHistoryItem`.

