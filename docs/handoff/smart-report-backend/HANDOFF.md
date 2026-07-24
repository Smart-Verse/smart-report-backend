# smart-report-backend — Handoff

> Estado consolidado em 22/07/2026.

## Responsabilidade e stack

API do SmartReport: autenticação humana e de integração, resolução de tenant, CRUD relacional, templates em MongoDB, planos, consumo e orquestração do PDF.

- Java 25.
- Spring Boot 3.5.16 e Spring Cloud 2025.0.2.
- Gonthera CLI 2.0.1.
- PostgreSQL, JPA, Flyway e MongoDB síncrono.
- Feign para `http://localhost:5071/report`.

## Estrutura relevante

- `.gonthera/project.json`: fonte de verdade dos contratos gerados.
- `src/main/java/com/smartverse/smartreportbackend_gen`: saída gerada; não editar.
- `handlers`: implementação dos endpoints customizados.
- `services`: regras de negócio.
- `repository`: consultas customizadas que estendem repositórios gerados.
- `config/interceptor`: separação dos fluxos humano e de integração.
- `src/main/resources/db/migration`: evolução Flyway.
- `src/main/resources/models/template`: presets editáveis.
- `docs/skill/smart-report-project/SKILL.md`: manual operacional completo.

## Contrato Gonthera

Entidades atuais incluem `userConfiguration`, `userConfirmation`, `repository`, `report`, `apiKey`, `subscriptionPlan`, `tenantSubscription` e `apiMonthlyUsage`.

DTO-only públicos incluem `apiKeySummary`, `createdApiKey`, `planOption` e `apiUsageHistoryItem`. Entidades administrativas usam `generateDefaultControllers: false`.

Enums gerados incluem `Theme`, `Language`, `EnumConfigContext` e `ReportTemplate`.

Fluxo obrigatório de alteração:

```bash
mvn gonthera-cli:validate
mvn gonthera-cli:generate-sources
mvn -DskipTests clean compile
```

Ler `docs/skill/entity-generator-project/SKILL.md` antes de alterar entidade, DTO, enum ou endpoint.

## Autenticação e tenant

`InterceptorConfig` somente escolhe o fluxo e limpa `TenantContext`.

- `ApplicationAuthenticationFlow`: rotas públicas, JWT e tenant humano.
- `IntegrationAuthenticationFlow`: `X-API-Key`, escopo, quota e tenant de integração.
- `ApiKeyBusinessService`: cria, lista, revoga e autentica credenciais.
- A chave tem formato `sr_live_<prefix>_<secret>`.
- Plaintext aparece uma vez; persistir somente hash SHA-256.
- API Key pode chamar apenas `POST /generateReport`.
- O tenant administrativo é usado para usuários, chaves, catálogo, assinaturas e consumo.
- Sempre restaurar o contexto anterior em `finally`.

Schemas relacionais seguem `<DB_NAME>_<TENANT>`. O MongoDB usa coleção pelo tenant em minúsculas. Migrar o schema antes do acesso.

## Relatórios e templates

`ReportService` salva o conteúdo no MongoDB, injeta os dados no documento e chama `ReportClient`.

Presets:

- `standard.*`: executivo;
- `list.*`: listagem;
- `chart.*`: indicadores e gráfico;
- `financial.*`: demonstrativo financeiro;
- `base.*`: compatibilidade com o padrão original.

Cada preset possui `.html`, `.css`, `.js` e `.json`. A interface pública deve apresentar apenas templates web, scripts, dados e diretivas suportadas; não expor detalhes do motor de interpretação.

## Endpoints principais

| Endpoint | Autenticação | Finalidade |
| --- | --- | --- |
| `authenticate`, `register`, `verifyURL` | Pública conforme contrato | Entrada e confirmação |
| CRUD `repository` e `report` | JWT | Biblioteca |
| `getUser` | JWT | Configuração do usuário |
| `getTemplate`, `saveTemplate` | JWT | Conteúdo do Studio |
| `generateReport` | JWT ou API Key | Gerar PDF |
| `getMetrics` | JWT | Métricas da biblioteca |
| `createApiKey`, `getApiKeys`, `revokeApiKey` | JWT | Credenciais |
| `getPlanOverview` | JWT | Plano, catálogo e consumo atual |
| `getApiUsageHistory` | JWT | Histórico mensal |

## Planos e franquia

A migração `V20260721210000001__Create_subscription_catalog.sql` cria e semeia:

- `subscription_plan`: código, nome, descrição, preço, limite, ordem, ativo e personalizado;
- `tenant_subscription`: plano vigente por tenant;
- `api_monthly_usage`: consumo por tenant e período `YYYY-MM`.

`PlanBusinessService`:

- cria vínculo `FREE` quando ausente;
- lê catálogo no schema administrativo;
- retorna overview e histórico;
- incrementa consumo em transação;
- bloqueia franquia finita esgotada com HTTP 402;
- considera limite nulo como negociado/ilimitado;
- restaura `TenantContext` após acesso administrativo.

`ApiMonthlyUsageCustomRepository` bloqueia o contador existente com `PESSIMISTIC_WRITE` e lista histórico em ordem decrescente. Em implantação com múltiplas instâncias, observar a corrida possível na criação do primeiro contador mensal do tenant.

## Confirmação de conta e pagamentos

O cadastro envia a confirmação de conta pela API HTTP do Resend. O remetente e a
chave vêm de `RESEND_FROM` e `RESEND_API_KEY`; a URL do botão usa
`FRONTEND_BASE_URL`. Cada token de confirmação também é a chave de idempotência do
envio.

`POST /payments/link` recebe `{ "planCode": "STARTER" }`, reaproveita uma
solicitação local pendente e chama `POST /paymentLink` do Smart Payment com
`service=REPORT`, preço em centavos e o JWT corrente. Planos gratuitos,
personalizados ou sem preço não geram checkout.

A confirmação definitiva chega pela fila
`smart.payment.confirmed.report`, exchange `smart.payment.events` e routing key
`payment.confirmed.REPORT`. `PaymentConfirmedListener` delega para
`PaymentBusinessService`, que registra o evento no inbox `processed_payment`,
confere serviço, pedido e valor, e altera `tenant_subscription` de forma
idempotente. Redirecionamento do navegador nunca ativa plano.

As tabelas `payment_request` e `processed_payment` são criadas pela migração
`V20260723220000001__Create_payment_inbox.sql`.

## Configuração

```text
DATABASE_SCHEMA_NAME=smartreport
DB_NAME=POSTGRES
DB_PASSWORD=<senha>
DB_USERNAME=postgres
SECRET_JWT=<segredo forte>
SERVER_PORT=5070
SERVICE_NAME=smartreport
MONGO_USER=smartreport
MONGO_PASSWORD=<senha>
RESEND_API_KEY=<re_...>
RESEND_FROM=SmartReport <no-reply@smartverse.com.br>
FRONTEND_BASE_URL=https://app.smartverse.com.br
PAYMENT_SERVICE_BASE_URL=https://app.smartverse.com.br/api/payment-service
RABBITMQ_HOST=<host>
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<usuário>
RABBITMQ_PASSWORD=<senha>
```

Swagger local: `http://localhost:5070/smartreport/swagger-ui/index.html`.

Confirmar no ambiente publicado a URL do core, credenciais dos bancos, CORS e proxy/base path `/api/smartreport`.

## Riscos e pendências

- Build oficial exige JDK 25 completo.
- Surefire pula testes no POM.
- A URL do core ainda está fixa no cliente Feign.
- `spring.main.allow-bean-definition-overriding=true` pode esconder colisões.
- Falhas de eventos de pagamento ficam com status `FAILED` no inbox; definir um
  job operacional de retry antes de exigir recuperação totalmente automática.
- Alteração comercial pode ser feita no catálogo, mas ainda não existe tela administrativa.
- A primeira criação concorrente de consumo mensal merece estratégia de upsert antes de escalar horizontalmente.
